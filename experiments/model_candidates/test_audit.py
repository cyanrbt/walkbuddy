import tempfile
import json
import subprocess
import sys
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

from audit import decode, parse_names, prepare, verify_model


class CandidateAuditTest(unittest.TestCase):
    def setUp(self):
        self.names = [f"category_{i}" for i in range(24)]
        self.names[7] = "person"
        self.geometry = {"width": 640, "height": 640, "scale": 1, "left": 0, "top": 0}

    def tensor(self):
        return np.zeros((1, 28, 8400), dtype=np.float32)

    def box(self, raw, anchor, category, score, xywh=(320, 320, 100, 200)):
        raw[0, :4, anchor] = xywh
        raw[0, 4 + category, anchor] = score

    def test_rgb_nchw_normalization_and_padding(self):
        values, geometry = prepare(Image.new("RGB", (320, 640), (255, 0, 128)))
        self.assertEqual((1, 3, 640, 640), values.shape)
        self.assertEqual(np.float32, values.dtype)
        self.assertTrue(values.flags.c_contiguous)
        self.assertEqual((160, 0), (geometry["left"], geometry["top"]))
        np.testing.assert_array_equal(values[0, :, 320, 320], np.array([1, 0, 128 / 255], dtype=np.float32))
        np.testing.assert_array_equal(values[0, :, 0, 0], np.full(3, 114 / 255, dtype=np.float32))

    def test_odd_padding_is_recorded_without_recomputing_the_box_transform(self):
        _, geometry = prepare(Image.new("RGB", (100, 57)))
        self.assertEqual((640, 365), (geometry["resized_width"], geometry["resized_height"]))
        self.assertEqual(137, geometry["top"])
        raw = self.tensor()
        self.box(raw, 0, 7, .9, (320, 137 + 57 * 6.4 / 2, 640, 57 * 6.4))
        self.assertEqual([0, 0, 100, 57], decode(raw, geometry, self.names)[0]["xyxy"])

    def test_names_follow_embedded_ids_and_literal_input_is_never_executed(self):
        values = {i: name for i, name in enumerate(self.names)}
        self.assertEqual(self.names, parse_names(repr(values)))
        for bad in ["__import__('os').getcwd()", "[]", repr({**values, 24: "extra"}),
                    repr({str(k): v for k, v in values.items()}), repr({**values, 8: "person"})]:
            with self.subTest(bad=bad), self.assertRaises((ValueError, SyntaxError)):
                parse_names(bad)

    def test_nms_retains_independent_classes_and_stable_anchor_ties(self):
        raw = self.tensor()
        self.box(raw, 4, 7, .9)
        self.box(raw, 2, 7, .9)
        self.box(raw, 3, 12, .85)
        boxes = decode(raw, self.geometry, self.names)
        self.assertEqual([2, 3], [item["anchor"] for item in boxes])
        self.assertEqual(["person", "category_12"], [item["class_name"] for item in boxes])

    def test_confidence_boundary_and_clipped_source_coordinates(self):
        raw = self.tensor()
        self.box(raw, 0, 7, .25, (320, 320, 1000, 1000))
        self.box(raw, 1, 12, .249)
        self.assertEqual([0, 0, 640, 640], decode(raw, self.geometry, self.names)[0]["xyxy"])
        self.assertEqual(1, len(decode(raw, self.geometry, self.names)))

    def test_empty_outputs_are_valid_but_bad_tensors_and_scores_are_rejected(self):
        raw = self.tensor()
        self.assertEqual([], decode(raw, self.geometry, self.names))
        for bad in [np.zeros((1, 24, 8400), dtype=np.float32), raw + np.nan, raw - .1]:
            with self.subTest(shape=bad.shape), self.assertRaises(ValueError):
                decode(bad, self.geometry, self.names)

    def test_unknown_binary_is_rejected_before_runtime_creation(self):
        with tempfile.TemporaryDirectory() as directory:
            model = Path(directory) / "model.onnx"
            model.write_bytes(b"unverified")
            with self.assertRaises(ValueError):
                verify_model(model)

    def test_cli_failure_replaces_stale_report_and_exits_unsuccessfully(self):
        with tempfile.TemporaryDirectory() as directory:
            model = Path(directory) / "bad.onnx"
            report = Path(directory) / "report.json"
            model.write_bytes(b"unverified")
            report.write_text('{"status":"passed"}')
            result = subprocess.run([sys.executable, str(Path(__file__).with_name("audit.py")),
                                     "--model", str(model), "--fixture", str(model), "--output", str(report)],
                                    capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertEqual("failed", json.loads(report.read_text())["status"])


if __name__ == "__main__":
    unittest.main()
