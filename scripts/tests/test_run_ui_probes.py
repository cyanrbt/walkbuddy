"""Exercise the real shell runner with a fake adb; no emulator or Android settings are touched."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
EXPECTED = ["pip_recovery", "runtime", "preview", "overlay", "layout", "home", "recovery", "pip", "pip", "support"]
ADB = '''#!/usr/bin/env python3
import os, sys, tarfile
from pathlib import Path
args=sys.argv[1:]
if "instrument" in args:
    probe=args[args.index("-e")+1]
    with Path(os.environ["PROBE_FIXTURE_EVENTS"]).open("a") as out:out.write(probe+"\\n")
    failed=probe==os.environ.get("PROBE_FIXTURE_FAIL")
    print(probe.upper()+"_PROBE_"+("FAIL" if failed else "PASS"))
    if probe==os.environ.get("PROBE_FIXTURE_BOTH"):print(probe.upper()+"_PROBE_FAIL")
    if probe==os.environ.get("PROBE_FIXTURE_EXIT_ERROR"):sys.exit(7)
elif "exec-out" in args:
    with tarfile.open(fileobj=sys.stdout.buffer,mode="w|"):pass
'''


class UiProbeRunnerTest(unittest.TestCase):
    def run_fixture(self, **options):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            adb = directory / "adb"
            adb.write_text(ADB)
            adb.chmod(0o755)
            events = directory / "events.txt"
            env = dict(os.environ, PATH=str(directory)+os.pathsep+os.environ["PATH"],
                       PROBE_FIXTURE_EVENTS=str(events), WALKBUDDY_EMULATOR_SERIAL="emulator-fixture", **options)
            result = subprocess.run(["bash", str(ROOT/"scripts/run_ui_probes.sh")], cwd=directory,
                                    env=env, text=True, capture_output=True, timeout=20)
            calls = events.read_text().splitlines()
            records = {p.name: p.read_text() for p in (directory/"ui-evidence").glob("*.txt")}
            return result, calls, records

    def test_all_ten_successful_records_are_required_for_success(self):
        result, calls, records = self.run_fixture()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(EXPECTED, calls)
        self.assertIn("PIP_PROBE_PASS", records["pip-repeat.txt"])

    def test_last_support_probe_failure_still_fails_the_complete_run(self):
        result, calls, records = self.run_fixture(PROBE_FIXTURE_FAIL="support")
        self.assertEqual(1, result.returncode)
        self.assertEqual(EXPECTED, calls)
        self.assertIn("PIP_PROBE_PASS", records["pip-repeat.txt"])
        self.assertIn("SUPPORT_PROBE_FAIL", records["support.txt"])

    def test_first_probe_failure_does_not_hide_later_evidence_or_pass_the_run(self):
        result, calls, records = self.run_fixture(PROBE_FIXTURE_FAIL="pip_recovery")
        self.assertEqual(1, result.returncode)
        self.assertEqual(EXPECTED, calls)
        self.assertIn("PIP_RECOVERY_PROBE_FAIL", records["pip_recovery.txt"])
        self.assertIn("PIP_PROBE_PASS", records["pip-repeat.txt"])

    def test_nonzero_adb_exit_fails_even_if_a_pass_marker_is_printed(self):
        result, calls, records = self.run_fixture(PROBE_FIXTURE_EXIT_ERROR="preview")
        self.assertEqual(1, result.returncode)
        self.assertEqual(EXPECTED, calls)
        self.assertIn("PREVIEW_PROBE_PASS", records["preview.txt"])

    def test_a_failure_marker_is_not_masked_by_a_pass_marker(self):
        result, calls, records = self.run_fixture(PROBE_FIXTURE_BOTH="pip_recovery")
        self.assertEqual(1, result.returncode)
        self.assertEqual(EXPECTED, calls)
        self.assertIn("PIP_RECOVERY_PROBE_PASS", records["pip_recovery.txt"])
        self.assertIn("PIP_RECOVERY_PROBE_FAIL", records["pip_recovery.txt"])


if __name__ == "__main__":
    unittest.main()
