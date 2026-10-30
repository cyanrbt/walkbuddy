import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import numpy as np

from openimages_reference import gt_max_iou, stairs_from_outputs, verify_file


class OpenImagesReferenceTest(unittest.TestCase):
    def outputs(self):
        # This is the observed archive contract, opposite to the card's field description.
        return {"detection_class_names": np.array([[b"/m/01lynh"], [b"/m/other"]]),
                "detection_class_entities": np.array([[b"Stairs"], [b"Other"]]),
                "detection_class_labels": np.array([[93], [7]], dtype=np.int64),
                "detection_scores": np.array([[.4], [.9]], dtype=np.float32),
                "detection_boxes": np.array([[.1, .2, .6, .8], [0, 0, 1, 1]], dtype=np.float32)}

    def test_actual_string_fields_and_numeric_id_identify_stairs(self):
        predictions = stairs_from_outputs(self.outputs())
        self.assertEqual(1, len(predictions))
        self.assertEqual((93, "Stairs"), (predictions[0]["label"], predictions[0]["name"]))
        self.assertAlmostEqual(.4, predictions[0]["score"])

    def test_card_order_is_rejected_instead_of_silently_reporting_zero_stairs(self):
        outputs = self.outputs()
        outputs["detection_class_names"], outputs["detection_class_entities"] = outputs["detection_class_entities"], outputs["detection_class_names"]
        with self.assertRaisesRegex(ValueError, "MID/display"):
            stairs_from_outputs(outputs)

    def test_changed_label_or_display_is_rejected(self):
        for field, value in [("detection_class_labels", 92), ("detection_class_entities", b"Other")]:
            outputs = self.outputs()
            outputs[field][0, 0] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                stairs_from_outputs(outputs)

    def test_bad_shapes_scores_and_normalized_boxes_are_rejected(self):
        base = self.outputs()
        cases = []
        for field, value in [("detection_boxes", np.zeros((1, 4))),
                             ("detection_scores", np.array([[np.nan], [.5]])),
                             ("detection_scores", np.array([[1.1], [.5]])),
                             ("detection_boxes", np.array([[0, 0, -1, 1], [0, 0, 1, 1]]))]:
            outputs = copy.deepcopy(base); outputs[field] = value; cases.append(outputs)
        for outputs in cases:
            with self.assertRaises(ValueError):
                stairs_from_outputs(outputs)

    def test_iou_uses_yxyx_and_applies_confidence_without_one_to_one_assignment(self):
        gt = [{"YMin": "0", "XMin": "0", "YMax": "1", "XMax": "1"}] * 2
        prediction = {"score": .35, "box_yxyx": [0, 0, .5, 1]}
        self.assertEqual([.5, .5], gt_max_iou([prediction], gt))
        self.assertEqual([0, 0], gt_max_iou([prediction], gt, confidence=.3501))
        self.assertEqual([0, 0], gt_max_iou([], gt))

    def test_unknown_file_is_rejected_before_tensorflow_loading(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "saved_model.pb"
            path.write_bytes(b"unverified")
            with self.assertRaises(ValueError):
                verify_file(path, {"bytes": 10, "sha256": "0" * 64})

    def test_cli_failure_overwrites_stale_success_report(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "report.json"
            output.write_text('{"status":"passed"}')
            result = subprocess.run([sys.executable, str(Path(__file__).with_name("openimages_reference.py")),
                                     "--cache", directory, "--portrait", str(output), "--output", str(output)],
                                    capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertEqual("failed", json.loads(output.read_text())["status"])


if __name__ == "__main__":
    unittest.main()
