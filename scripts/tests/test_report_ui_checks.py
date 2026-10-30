import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from report_ui_checks import PROBES, markdown, summarize


class UiCheckReportTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)

    def write(self, name, text):
        path = self.directory / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)

    def passing_probes(self):
        for name in PROBES:
            self.write(name + ".txt", name.split("-")[0].upper() + "_PROBE_PASS\n")

    def anr(self, package):
        # Format replayed from the downloaded 12959501925 lifecycle.txt.
        self.write("lifecycle.txt", "10-30 06:38:19.415 559 2188 E ActivityManager: ANR in " + package + " (Activity)\n"
                   "10-30 06:38:19.415 559 2188 E ActivityManager: PID: 1195\n"
                   "10-30 06:38:19.415 559 2188 E ActivityManager: Reason: Input dispatching timed out (Application does not have a focused window).\n")

    def test_success_requires_both_step_results_and_all_nine_records(self):
        self.passing_probes()
        report = summarize(self.directory, "success", "success")
        self.assertEqual("passed", report["status"])
        self.assertEqual(9, len(report["probes"]))
        (self.directory / "pip-repeat.txt").unlink()
        self.assertEqual("evidence_incomplete", summarize(self.directory, "success", "success")["status"])

    def test_probe_failure_is_not_overridden_by_pass_marker(self):
        self.passing_probes()
        self.write("pip.txt", "PIP_PROBE_PASS\nPIP_PROBE_FAIL: stale results\n")
        report = summarize(self.directory, "success", "failure")
        self.assertEqual("checks_not_passed", report["status"])
        self.assertEqual("failed", next(p for p in report["probes"] if p["name"] == "pip")["status"])

    def test_launcher_anr_alone_is_an_observation_not_a_proven_focus_block(self):
        self.anr("com.google.android.apps.nexuslauncher")
        self.write("pip_recovery.txt", "PIP_RECOVERY_PROBE_FAIL: Timed out: touch target ready\n")
        report = summarize(self.directory, "success", "failure")
        self.assertEqual(["external_anr_observed"], report["observations"])
        self.assertEqual([], report["focused_external_anr_dialogs"])
        self.assertIn("Application does not have a focused window", report["anrs"][0]["reason"])
        self.assertNotIn("弹窗占有输入焦点", markdown(report))

    def test_failure_time_focus_identifies_the_external_anr_dialog(self):
        self.anr("com.google.android.apps.nexuslauncher")
        # Window title is specified by AOSP AppNotRespondingDialog.
        self.write("cache/ui-probe/pip-recovery-failure-window.txt",
                   "  mCurrentFocus=Window{abc u0 Application Not Responding: com.google.android.apps.nexuslauncher}\n")
        report = summarize(self.directory, "success", "failure")
        self.assertIn("external_anr_dialog_has_focus", report["observations"])
        self.assertEqual("com.google.android.apps.nexuslauncher", report["focused_external_anr_dialogs"][0]["package"])
        self.assertIn("弹窗占有输入焦点", markdown(report))

    def test_background_system_anr_does_not_turn_a_passing_check_into_a_failure(self):
        self.passing_probes()
        self.anr("com.google.android.as")
        self.write("cache/ui-probe/pip-failure-window.txt",
                   "mCurrentFocus=Window{abc u0 com.cyanrbt.walkbuddy/.PreviewActivity}\n")
        report = summarize(self.directory, "success", "success")
        self.assertEqual("passed", report["status"])
        self.assertEqual(["external_anr_observed"], report["observations"])

    def test_application_anr_is_never_attributed_to_an_external_package(self):
        self.anr("com.cyanrbt.walkbuddy")
        self.write("cache/ui-probe/pip-failure-window.txt",
                   "mCurrentFocus=Window{abc u0 Application Not Responding: com.cyanrbt.walkbuddy}\n")
        report = summarize(self.directory, "success", "failure")
        self.assertEqual(["application_anr_observed"], report["observations"])
        self.assertEqual([], report["focused_external_anr_dialogs"])

    def test_build_failure_or_skipped_checks_are_reported_separately(self):
        report = summarize(self.directory, "failure", "skipped")
        self.assertEqual("build_not_passed", report["status"])
        self.assertIn("模拟器测试 APK 编译 | 失败", markdown(report))
        self.assertIn("模拟器运行 | 未运行", markdown(report))
        self.assertEqual("checks_not_passed", summarize(self.directory, "success", "skipped")["status"])

    def test_diagnostic_modes_do_not_claim_nine_standard_probes_were_run(self):
        report = summarize(self.directory, "success", "success", mode="thread")
        self.assertEqual([], report["probes"])
        self.assertEqual("passed", report["status"])


if __name__ == "__main__":
    unittest.main()
