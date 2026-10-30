#!/usr/bin/env python3
"""Pinned Open Images host reference; no Android integration or mobile conversion."""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
import os
from pathlib import Path
import platform
import tarfile
import time
import urllib.request

import numpy as np
from PIL import Image

SPEC = json.loads(Path(__file__).with_name("openimages.json").read_text())
TARGET = SPEC["target"]


def verify_file(path: Path, expected: dict) -> None:
    if path.stat().st_size != expected["bytes"] or hashlib.sha256(path.read_bytes()).hexdigest() != expected["sha256"]:
        raise ValueError(f"Unverified size/SHA-256: {path.name}")


def fetch_file(path: Path, expected: dict, download: bool) -> None:
    if not path.exists() and download:
        path.parent.mkdir(parents=True, exist_ok=True)
        temporary = path.with_name(path.name + ".part")
        try:
            with urllib.request.urlopen(expected["url"], timeout=60) as response, temporary.open("wb") as target:
                size = 0
                while chunk := response.read(65536):
                    size += len(chunk)
                    if size > expected["bytes"]:
                        raise ValueError("Download exceeds pinned size")
                    target.write(chunk)
            verify_file(temporary, expected)
            temporary.replace(path)
        finally:
            temporary.unlink(missing_ok=True)
    verify_file(path, expected)


def prepare_model(cache: Path, download: bool) -> Path:
    model = SPEC["model"]
    archive = cache / "openimages-v4-v1.tar.gz"
    fetch_file(archive, {"url": model["fixed_download"], "bytes": model["archive_bytes"],
                         "sha256": model["archive_sha256"]}, download)
    directory = cache / "model"
    directory.mkdir(parents=True, exist_ok=True)
    with tarfile.open(archive) as source:
        members = source.getmembers()
        if len(members) != len(model["files"]) or {m.name for m in members} != set(model["files"]):
            raise ValueError("Unexpected archive entries")
        for member in members:
            expected = model["files"][member.name]
            if not member.isfile() or member.size != expected["bytes"]:
                raise ValueError("Unexpected archive entry type/size")
            path = directory / member.name  # Exact allowlist above; never extract arbitrary paths.
            if not path.exists():
                data = source.extractfile(member).read()
                if hashlib.sha256(data).hexdigest() != expected["sha256"]:
                    raise ValueError("Archive entry SHA-256 mismatch")
                path.write_bytes(data)
            verify_file(path, expected)
    return directory


def stairs_from_outputs(outputs: dict) -> list[dict]:
    # The pinned archive reverses these two string fields relative to its model card.
    mids = [x.decode("utf-8") for x in outputs["detection_class_names"].reshape(-1)]
    names = [x.decode("utf-8") for x in outputs["detection_class_entities"].reshape(-1)]
    labels = outputs["detection_class_labels"].reshape(-1)
    scores = outputs["detection_scores"].reshape(-1)
    boxes = outputs["detection_boxes"]
    count = len(scores)
    if boxes.shape != (count, 4) or not len(mids) == len(names) == len(labels) == count:
        raise ValueError("Output lengths/shapes disagree")
    if not np.isfinite(boxes).all() or not np.isfinite(scores).all() or np.any((scores < 0) | (scores > 1)):
        raise ValueError("Invalid boxes/scores")
    if np.any((boxes < 0) | (boxes > 1)) or np.any(boxes[:, 2:] < boxes[:, :2]):
        raise ValueError("Invalid normalized yxyx boxes")
    if any(not mid.startswith("/m/") for mid in mids) or any(name.startswith("/m/") for name in names):
        raise ValueError("MID/display fields changed; inspect the actual model contract")
    result = []
    for i, mid in enumerate(mids):
        if (mid == TARGET["mid"]) != (int(labels[i]) == TARGET["label"]):
            raise ValueError("Stairs MID and numeric label disagree")
        if mid == TARGET["mid"]:
            if names[i] != TARGET["display_name"]:
                raise ValueError("Stairs display name changed")
            result.append({"score": float(scores[i]), "box_yxyx": boxes[i].tolist(),
                           "label": int(labels[i]), "name": names[i]})
    return result


def gt_max_iou(predictions: list[dict], ground_truth: list[dict], confidence=.35) -> list[float]:
    """Independent per-GT overlaps, not one-to-one assignment, recall, or AP."""
    result = []
    for item in ground_truth:
        gt = np.array([float(item[key]) for key in ["YMin", "XMin", "YMax", "XMax"]])
        best = 0.0
        for prediction in predictions:
            if prediction["score"] < confidence:
                continue
            box = np.array(prediction["box_yxyx"])
            intersection = np.maximum(0, np.minimum(gt[2:], box[2:]) - np.maximum(gt[:2], box[:2])).prod()
            union = np.maximum(0, gt[2:] - gt[:2]).prod() + np.maximum(0, box[2:] - box[:2]).prod() - intersection
            best = max(best, float(intersection / union) if union > 0 else 0.0)
        result.append(best)
    return result


def run(cache: Path, portrait: Path, download: bool) -> dict:
    # Set before importing TensorFlow. Keep this environment separate from the ONNX audit.
    os.environ["TF_ENABLE_ONEDNN_OPTS"] = "0"
    os.environ["TF_NUM_INTRAOP_THREADS"] = "1"
    os.environ["TF_NUM_INTEROP_THREADS"] = "1"
    directory = prepare_model(cache, download)
    if hashlib.sha256(portrait.read_bytes()).hexdigest() != SPEC["portrait_sha256"]:
        raise ValueError("Portrait fixture SHA-256 mismatch")
    for fixture in SPEC["fixtures"]:
        fetch_file(cache / (fixture["image_id"] + ".jpg"), fixture, download)
    import tensorflow as tf
    import PIL
    at = time.perf_counter()
    loaded = tf.saved_model.load(str(directory))
    load_ms = (time.perf_counter() - at) * 1000
    fn = loaded.signatures["default"]
    inputs = {k: {"dtype": v.dtype.name, "shape": v.shape.as_list()} for k, v in fn.structured_input_signature[1].items()}
    outputs = {k: {"dtype": v.dtype.name, "shape": v.shape.as_list()} for k, v in fn.structured_outputs.items()}
    if inputs != {"images": {"dtype": "float32", "shape": [None, None, None, 3]}}:
        raise ValueError("Input signature changed")
    expected_outputs = {"detection_boxes": {"dtype": "float32", "shape": [None, 4]},
                        "detection_scores": {"dtype": "float32", "shape": [None, 1]},
                        "detection_class_labels": {"dtype": "int64", "shape": [None, 1]},
                        "detection_class_names": {"dtype": "string", "shape": [None, 1]},
                        "detection_class_entities": {"dtype": "string", "shape": [None, 1]}}
    if outputs != expected_outputs:
        raise ValueError("Output signature changed")
    report = {"status": "passed", "scope": "Host reference contract/repeatability only; not mobile performance or accuracy acceptance",
              "spec": SPEC, "script_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
              "source_commit": os.environ.get("GITHUB_SHA"), "load_ms": load_ms,
              "versions": {"tensorflow": tf.__version__, "numpy": np.__version__, "pillow": PIL.__version__},
              "host": {"architecture": platform.machine(), "cpu_count": os.cpu_count()},
              "threads": 1, "oneDNN": False, "inputs": inputs, "outputs": outputs,
              "operations": dict(Counter(op.type for op in fn.graph.get_operations())),
              "observed_mid_field": "detection_class_names", "observed_display_field": "detection_class_entities", "fixtures": []}
    with Image.open(portrait) as source:
        portrait_image = source.convert("RGB")
    cases = [("black", Image.new("RGB", (640, 480), "black"), None),
             ("gray", Image.new("RGB", (640, 480), (114, 114, 114)), None),
             ("white", Image.new("RGB", (640, 480), "white"), None), ("portrait", portrait_image, None)]
    for fixture in SPEC["fixtures"]:
        with Image.open(cache / (fixture["image_id"] + ".jpg")) as source:
            cases.append((fixture["image_id"], source.convert("RGB"), fixture))
    for name, image, annotation in cases:
        tensor = np.asarray(image, dtype=np.float32)[None] / np.float32(255)
        timings = []; first = None
        for _ in range(2):
            at = time.perf_counter()
            actual = {key: value.numpy() for key, value in fn(images=tf.convert_to_tensor(tensor)).items()}
            timings.append((time.perf_counter() - at) * 1000)
            if first is not None and (first.keys() != actual.keys() or any(not np.array_equal(first[k], actual[k]) for k in first)):
                raise ValueError(f"Repeated identical input changed output: {name}")
            first = actual
        predictions = stairs_from_outputs(first)
        case = {"name": name, "width": image.width, "height": image.height,
                "input_sha256": hashlib.sha256(tensor.tobytes()).hexdigest(),
                "first_ms": timings[0], "repeat_ms": timings[1], "same_repeated_output": True,
                "total_detections": len(first["detection_scores"]), "stairs": predictions,
                "stairs_count_at_035": sum(p["score"] >= .35 for p in predictions)}
        if annotation:
            case["gt_max_iou_at_035"] = gt_max_iou(predictions, annotation["boxes"])
        report["fixtures"].append(case)
        print(f'{name}: stairs_at_035={case["stairs_count_at_035"]} repeat_ms={timings[1]:.2f}', flush=True)
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache", type=Path, required=True)
    parser.add_argument("--portrait", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--download", action="store_true")
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    try:
        report = run(args.cache, args.portrait, args.download)
    except Exception as error:
        args.output.write_text(json.dumps({"status": "failed", "error_type": type(error).__name__, "error": str(error)[:1000]}) + "\n")
        raise
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print("OPENIMAGES_REFERENCE_PASS; contract and repeated output only", flush=True)


if __name__ == "__main__":
    main()
