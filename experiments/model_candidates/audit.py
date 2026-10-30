#!/usr/bin/env python3
"""Isolated CPU/contract audit. Candidate weights are never copied into the Android app."""
from __future__ import annotations

import argparse
import ast
from collections import Counter
import hashlib
import json
import os
from pathlib import Path
import platform
import statistics
import time
import urllib.request
import urllib.error

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
SPEC = json.loads((HERE / "candidates.json").read_text())["sidewalk"]
EDGE = 640


def verify_model(path: Path) -> None:
    if path.stat().st_size != SPEC["bytes"] or hashlib.sha256(path.read_bytes()).hexdigest() != SPEC["sha256"]:
        raise ValueError("Candidate model does not match the pinned size/SHA-256")


def fetch_model(path: Path) -> None:
    if path.exists():
        verify_model(path)
        return
    url = f'https://huggingface.co/{SPEC["repository"]}/resolve/{SPEC["revision"]}/{SPEC["filename"]}'
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".part")
    try:
        for attempt in range(3):
            try:
                with urllib.request.urlopen(url, timeout=30) as response, temporary.open("wb") as target:
                    size = 0
                    while chunk := response.read(65536):
                        size += len(chunk)
                        if size > SPEC["bytes"]:
                            raise ValueError("Candidate download exceeds pinned size")
                        target.write(chunk)
                break
            except (urllib.error.URLError, TimeoutError):
                if attempt == 2:
                    raise
                time.sleep(attempt + 1)
        verify_model(temporary)
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)


def prepare(image: Image.Image) -> tuple[np.ndarray, dict]:
    width, height = image.size
    if width <= 0 or height <= 0:
        raise ValueError("Invalid source dimensions")
    scale = min(EDGE / width, EDGE / height)
    resized = (round(width * scale), round(height * scale))
    left = round((EDGE - resized[0]) / 2 - .1)
    top = round((EDGE - resized[1]) / 2 - .1)
    padded = Image.new("RGB", (EDGE, EDGE), (114, 114, 114))
    padded.paste(image.convert("RGB").resize(resized, Image.Resampling.BILINEAR), (left, top))
    pixels = np.asarray(padded, dtype=np.float32) / np.float32(255)
    tensor = np.ascontiguousarray(pixels.transpose(2, 0, 1)[None])
    return tensor, {"width": width, "height": height, "scale": scale, "left": left, "top": top,
                    "resized_width": resized[0], "resized_height": resized[1]}


def parse_names(text: str) -> list[str]:
    # Metadata contains a Python dictionary literal; never execute it with eval().
    values = ast.literal_eval(text)
    if not isinstance(values, dict) or any(type(key) is not int for key in values) or set(values) != set(range(SPEC["class_count"])):
        raise ValueError("Non-contiguous or unexpected class metadata")
    names = [values[i] for i in range(len(values))]
    if any(not isinstance(name, str) or not name.strip() for name in names) or len(set(names)) != len(names):
        raise ValueError("Missing or duplicate class names")
    return names


def decode(raw: np.ndarray, geometry: dict, names: list[str], confidence=.25, iou=.7) -> list[dict]:
    if raw.shape != tuple(SPEC["output"]["shape"]) or not np.isfinite(raw).all():
        raise ValueError("Unexpected or non-finite model output")
    if not 0 <= confidence <= 1 or not 0 <= iou <= 1:
        raise ValueError("Invalid thresholds")
    scores = raw[0, 4:]
    if len(names) != scores.shape[0] or np.any(scores < 0) or np.any(scores > 1):
        raise ValueError("Class scores/metadata disagree")
    winners = scores.argmax(axis=0)
    best = scores.max(axis=0)
    anchors = np.flatnonzero(best >= confidence)
    centers = raw[0, :2][:, anchors].T.astype(np.float64)
    dimensions = raw[0, 2:4][:, anchors].T.astype(np.float64)
    if np.any(dimensions < 0):
        raise ValueError("Negative box dimensions")
    boxes = np.concatenate((centers - dimensions / 2, centers + dimensions / 2), axis=1)
    # Suppression applies only within the predicted category.
    kept = []
    for category in np.unique(winners[anchors]):
        indices = np.flatnonzero(winners[anchors] == category)
        pending = indices[np.argsort(-best[anchors[indices]], kind="stable")]
        while pending.size:
            selected = int(pending[0]); kept.append(selected)
            rest = pending[1:]
            a = boxes[selected]; b = boxes[rest]
            intersection = np.maximum(0, np.minimum(a[2:], b[:, 2:]) - np.maximum(a[:2], b[:, :2])).prod(axis=1)
            area_a = np.maximum(0, a[2:] - a[:2]).prod()
            area_b = np.maximum(0, b[:, 2:] - b[:, :2]).prod(axis=1)
            union = area_a + area_b - intersection
            overlap = np.divide(intersection, union, out=np.zeros_like(intersection), where=union > 0)
            pending = rest[overlap <= iou]
    kept.sort(key=lambda k: (-float(best[anchors[k]]), int(anchors[k])))
    result = []
    offset = np.array([geometry["left"], geometry["top"], geometry["left"], geometry["top"]])
    maximum = np.array([geometry["width"], geometry["height"], geometry["width"], geometry["height"]])
    for k in kept:
        xyxy = np.rint(np.clip((boxes[k] - offset) / geometry["scale"], 0, maximum)).astype(int)
        if xyxy[2] <= xyxy[0] or xyxy[3] <= xyxy[1]:
            continue
        anchor = int(anchors[k]); category = int(winners[anchor])
        result.append({"class_id": category, "class_name": names[category], "confidence": float(best[anchor]),
                       "xyxy": xyxy.tolist(), "anchor": anchor})
    return result


def run(model: Path, fixture: Path, threads: list[int], repeats: int) -> dict:
    import onnx
    import onnxruntime as ort
    import PIL
    verify_model(model)
    graph = onnx.load(str(model), load_external_data=False)
    if any(value.data_location == onnx.TensorProto.EXTERNAL for value in graph.graph.initializer):
        raise ValueError("External weight files are not supported by this audit")
    onnx.checker.check_model(graph)
    report = {"status": "passed", "scope": "Host CPU smoke/contract audit; not Android, camera latency, accuracy, or distribution approval",
              "model": SPEC, "script_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
              "source_commit": os.environ.get("GITHUB_SHA"),
              "versions": {"onnx": onnx.__version__, "onnxruntime": ort.__version__, "numpy": np.__version__, "pillow": PIL.__version__},
              "host": {"architecture": platform.machine(), "cpu_count": os.cpu_count(),
                       "affinity_count": len(os.sched_getaffinity(0)) if hasattr(os, "sched_getaffinity") else None},
              "opsets": [{"domain": item.domain, "version": item.version} for item in graph.opset_import],
              "node_types": dict(Counter(node.op_type for node in graph.graph.node)),
              "used_node_domains": sorted({node.domain for node in graph.graph.node}),
              "initializer_types": dict(Counter(onnx.TensorProto.DataType.Name(value.data_type) for value in graph.graph.initializer)),
              "fixture_sha256": hashlib.sha256(fixture.read_bytes()).hexdigest(), "sessions": []}
    with Image.open(fixture) as source:
        portrait = source.convert("RGB")
    fixtures = [("black", Image.new("RGB", (640, 480), "black")),
                ("pad_gray", Image.new("RGB", (640, 480), (114, 114, 114))),
                ("white", Image.new("RGB", (640, 480), "white")), ("portrait", portrait)]
    for thread_count in threads:
        options = ort.SessionOptions(); options.intra_op_num_threads = thread_count; options.inter_op_num_threads = 1
        options.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
        started = time.perf_counter_ns()
        session = ort.InferenceSession(str(model), sess_options=options, providers=["CPUExecutionProvider"])
        setup_ms = (time.perf_counter_ns() - started) / 1e6
        for actual, expected in [(session.get_inputs(), SPEC["input"]), (session.get_outputs(), SPEC["output"])]:
            if len(actual) != 1 or actual[0].name != expected["name"] or actual[0].shape != expected["shape"] or actual[0].type != expected["type"]:
                raise ValueError("Actual session tensors disagree with pinned contract")
        metadata = session.get_modelmeta().custom_metadata_map
        names = parse_names(metadata["names"])
        if "AGPL-3.0" not in metadata.get("license", "") or metadata.get("end2end") != "False":
            raise ValueError("Unexpected embedded license or output processing")
        detail = {"threads": thread_count, "session_init_ms": setup_ms, "class_names": names,
                  "embedded_license": metadata["license"], "providers": session.get_providers(), "fixtures": []}
        for name, image in fixtures:
            at = time.perf_counter_ns(); tensor, geometry = prepare(image); preparation_ms = (time.perf_counter_ns() - at) / 1e6
            timings = []; first = None; raw = None
            for iteration in range(1 + 2 + repeats):
                at = time.perf_counter_ns(); raw = session.run(None, {SPEC["input"]["name"]: tensor})[0]
                elapsed = (time.perf_counter_ns() - at) / 1e6
                if first is None:
                    first = raw.copy(); first_ms = elapsed
                elif not np.array_equal(first, raw):
                    raise ValueError(f"Repeated identical input changed raw outputs: {name}")
                if iteration >= 3:
                    timings.append(elapsed)
            at = time.perf_counter_ns(); detections = decode(raw, geometry, names); decode_ms = (time.perf_counter_ns() - at) / 1e6
            detail["fixtures"].append({"name": name, "geometry": geometry, "input_sha256": hashlib.sha256(tensor.tobytes()).hexdigest(),
                                       "prepare_ms": preparation_ms, "first_case_run_ms": first_ms, "warmup_runs_after_first": 2,
                                       "run_ms": timings, "median_ms": statistics.median(timings), "max_ms": max(timings),
                                       "postprocess_ms": decode_ms, "max_class_score": float(raw[0, 4:].max()),
                                       "confidence_threshold": .25, "nms_iou_threshold": .7,
                                       "detections": len(detections),
                                       "count_at_035": len(decode(raw, geometry, names, confidence=.35)),
                                       "count_at_050": len(decode(raw, geometry, names, confidence=.5)),
                                       "top_detections": detections[:10]})
        report["sessions"].append(detail)
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", required=True, type=Path)
    parser.add_argument("--download", action="store_true")
    parser.add_argument("--fixture", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--threads", nargs="+", type=int, choices=[1, 2], default=[1, 2])
    parser.add_argument("--repeats", type=int, default=5)
    args = parser.parse_args()
    if not 1 <= args.repeats <= 100:
        parser.error("repeats must be in [1, 100]")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    try:
        if args.download:
            fetch_model(args.model)
        result = run(args.model, args.fixture, args.threads, args.repeats)
    except Exception as error:
        args.output.write_text(json.dumps({"status": "failed", "model": SPEC,
                                          "error_type": type(error).__name__, "error": str(error)[:1000]}, indent=2) + "\n")
        raise
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(f'CANDIDATE_AUDIT_PASS model_sha256={SPEC["sha256"]} sessions={len(result["sessions"])}; contract and repeated outputs only')
    for session in result["sessions"]:
        for case in session["fixtures"]:
            print(f'threads={session["threads"]} fixture={case["name"]} median_ms={case["median_ms"]:.2f} detections={case["detections"]}')


if __name__ == "__main__":
    main()
