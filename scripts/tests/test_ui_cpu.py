"""Prevent a requested four-core experiment from silently recording a two-core guest."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location('cpu', Path(__file__).resolve().parents[1]/'record_ui_cpu.py')
cpu = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cpu)


class CpuProfileTest(unittest.TestCase):
    def test_quota_limits_affinity_and_two_core_baseline_is_retained(self):
        limited = cpu.host_profile(4, {2, 3, 4, 5}, '200000 100000')
        self.assertEqual(2, limited['configured_cores'])
        self.assertEqual([2, 3, 4, 5], limited['allowed_cpu_ids'])
        self.assertEqual(2, cpu.host_profile(2, {0, 1, 2, 3}, 'max 100000')['configured_cores'])
        self.assertEqual(4, cpu.host_profile(4, {0, 1, 2, 3}, 'max 100000')['configured_cores'])

    def test_invalid_capacity_is_not_treated_as_unlimited(self):
        for requested, allowed, quota in ((8, {0, 1}, None), (4, set(), None),
                                           (4, {0, 1}, '200000 0'), (4, {0, 1}, 'invalid')):
            with self.subTest(quota=quota), self.assertRaises(ValueError):
                cpu.host_profile(requested, allowed, quota)

    def test_guest_mismatch_fails_but_retains_the_actual_topology(self):
        with tempfile.TemporaryDirectory() as temp, \
                patch.object(cpu.subprocess, 'check_output', side_effect=['1\n', '0-1\n']) as adb, \
                patch('sys.argv', ['record_ui_cpu.py', 'guest', '--cores', '4', '--report', temp+'/cpu.json']), \
                contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(1, cpu.main())
            result = json.loads(Path(temp, 'cpu.json').read_text())
            self.assertFalse(result['valid'])
            self.assertEqual([0, 1], result['online_cpu_ids'])
            self.assertEqual(4, result['expected_cores'])
            self.assertEqual(2, adb.call_count)

    def test_four_core_guest_must_have_four_actual_online_cpus(self):
        with patch.object(cpu.subprocess, 'check_output', side_effect=['1\n', '0-3\n']):
            result = cpu.guest_profile('emulator-fixture', 4)
        self.assertTrue(result['valid'])
        self.assertEqual([0, 1, 2, 3], result['online_cpu_ids'])

    def test_physical_device_does_not_reach_topology_collection(self):
        with patch.object(cpu.subprocess, 'check_output', return_value='0\n') as adb:
            with self.assertRaisesRegex(ValueError, 'disposable emulator'):
                cpu.guest_profile('physical-fixture', 4)
            self.assertEqual(1, adb.call_count)
            self.assertEqual(['getprop', 'ro.kernel.qemu'], adb.call_args.args[0][-2:])

    def test_malformed_or_unbounded_guest_ranges_are_rejected(self):
        for online in ('3-0', '0-999999', '0-3;echo bad', ''):
            with self.subTest(online=online), \
                    patch.object(cpu.subprocess, 'check_output', side_effect=['1', online]), \
                    self.assertRaises(ValueError):
                cpu.guest_profile('emulator-fixture', 4)


if __name__ == '__main__':
    unittest.main()
