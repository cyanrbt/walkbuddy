"""Evidence semantics and recorder cleanup; synthetic rows are not device results."""
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]


def module(name):
    spec = importlib.util.spec_from_file_location(name, ROOT/f'{name}.py')
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


report = module('report_preview_traces')
recorder = module('record_preview_trace')


def row(kind, name, ts=0, dur=0, utid=0, value=0, detail=''):
    return dict(kind=kind, name=name, ts=str(ts), dur=str(dur), utid=str(utid),
                tid='0', thread='', value=str(value), detail=detail)


def complete_rows():
    rows = [row('metric', 'sched_rows', value=100), row('thread', 'main', utid=1),
            row('thread', 'RenderThread', utid=2), row('slice', 'wb_main:dispatch', utid=1, dur=100_000_000),
            row('cpu', '0', utid=1, dur=20_000_000), row('cpu', '1', utid=2, dur=40_000_000),
            row('state', 'Running', utid=1, dur=20_000_000),
            row('state', 'S', ts=20_000_000, utid=1, dur=80_000_000),
            row('state', 'Running', utid=2, dur=40_000_000),
            row('slice', 'DrawFrame', utid=2, dur=60_000_000),
            row('slice', 'wb_main:dispatch', ts=7_000_000_000, dur=1_000_000, utid=1)]
    for index in range(4):
        at = 1_000_000_000*(index+1)
        rows += [row('slice', 'wb_test:forced_expiry', ts=at, dur=1_000_000, utid=1),
                 row('log', 'WalkBuddyTiming', ts=at+500_000,
                     detail='Preview stopped reason=GENERAL captureGapMs=1201 displayGapMs=30')]
    return rows


class TraceReportTest(unittest.TestCase):
    def setUp(self):
        self.capture = {'flushed': True, 'probe_pass': True, 'errors': []}

    def test_forced_faults_do_not_count_as_unforced_deadlines(self):
        result = report.summarize(complete_rows(), self.capture)
        self.assertTrue(result['valid'], result['errors'])
        self.assertEqual({'forced_expiry': 4}, result['stop_counts'])

    def test_unforced_deadline_and_lifecycle_are_separate(self):
        rows = complete_rows() + [
            row('log', 'WalkBuddyTiming', ts=5_000_000_000,
                detail='Preview stopped reason=GENERAL captureGapMs=1246 displayGapMs=1246'),
            row('log', 'WalkBuddyTiming', ts=6_000_000_000,
                detail='Preview stopped reason=LOCKED captureGapMs=2000 displayGapMs=2000')]
        result = report.summarize(rows, self.capture)
        self.assertEqual(1, result['stop_counts']['unforced_over_deadline'])
        self.assertEqual(1, result['stop_counts']['lifecycle'])

    def test_overlap_clips_states_and_cpu_to_dispatch_interval(self):
        result = report.summarize(complete_rows(), self.capture)
        observation = result['longest_dispatches'][0]
        self.assertEqual(100, observation['wall_ms'])
        self.assertEqual(20, observation['main_cpu_ms'])
        self.assertEqual({'Running': 20, 'S': 80}, observation['main_state_ms'])
        self.assertEqual(40, observation['render_cpu_overlap_ms'])
        self.assertEqual([{'name': 'DrawFrame', 'overlap_ms': 60}], observation['render_slices'])
        self.assertEqual(0, report.overlap(0, 10, 10, 20))
        self.assertEqual(5, report.overlap(0, 10, 5, 20))

    def test_missing_scheduler_threads_and_markers_fail_evidence(self):
        result = report.summarize([], self.capture)
        self.assertFalse(result['valid'])
        self.assertIn('No scheduler events decoded', result['errors'])
        self.assertIn('No application RenderThread CPU scheduling decoded', result['errors'])

    def test_signal_delivery_without_confirmed_flush_is_not_complete(self):
        result = report.summarize(complete_rows(), {**self.capture, 'flushed': False})
        self.assertFalse(result['valid'])

    def test_backlog_stop_outside_observed_trial_does_not_count(self):
        rows = complete_rows()+[row('log', 'WalkBuddyTiming', ts=9_000_000_000,
                                   detail='Preview stopped reason=GENERAL captureGapMs=2000 displayGapMs=2000')]
        result = report.summarize(rows, self.capture)
        self.assertEqual({'forced_expiry': 4}, result['stop_counts'])
        self.assertEqual(1, len(result['outside_observer_stops']))

    def test_info_severity_ring_overwrite_still_fails_full_trial_evidence(self):
        rows = complete_rows()+[row('stat', 'traced_buf_bytes_overwritten', value=20512768, detail='info/trace')]
        result = report.summarize(rows, self.capture)
        self.assertFalse(result['valid'])
        self.assertTrue(any('traced_buf_bytes_overwritten=20512768' in error for error in result['errors']))

    def test_data_loss_is_a_failure_even_with_four_passing_forced_stops(self):
        rows = complete_rows()+[row('stat', 'ftrace_cpu_overrun', value=1, detail='data_loss/trace')]
        result = report.summarize(rows, self.capture)
        self.assertFalse(result['valid'])
        self.assertEqual(4, result['stop_counts']['forced_expiry'])

    def test_unfinished_dispatch_is_not_a_valid_scheduling_measurement(self):
        rows = complete_rows()+[row('slice', 'wb_main:dispatch', dur=-1, utid=1)]
        self.assertFalse(report.summarize(rows, self.capture)['valid'])

    def test_trace_hash_is_checked_before_running_a_processor(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            (directory/'trace.pftrace').write_bytes(b'incomplete download')
            (directory/'capture.json').write_text(json.dumps({'trace_sha256': hashlib.sha256(b'complete').hexdigest()}))
            with self.assertRaisesRegex(ValueError, 'SHA-256'):
                report.analyze(directory, '/does-not-exist')


class FakeAdb:
    def __init__(self, fail_probe=False, timeout_probe=False):
        self.calls = []
        self.alive = True
        self.stopping_checks = None
        self.fail_probe = fail_probe
        self.timeout_probe = timeout_probe

    def run(self, *args, **kwargs):
        self.calls.append(args)
        if 'perfetto' in args:
            return SimpleNamespace(returncode=0, stdout='123\n', stderr='started\n')
        if 'instrument' in args:
            if self.timeout_probe:
                raise subprocess.TimeoutExpired('adb instrument', 110, output=b'partial probe\n')
            return SimpleNamespace(returncode=0, stdout='PIP_RECOVERY_PROBE_'+('FAIL' if self.fail_probe else 'PASS'), stderr='')
        if 'kill' in args:
            self.stopping_checks = 2
        if 'test' in args:
            if self.stopping_checks is not None:
                self.stopping_checks -= 1
                if self.stopping_checks <= 0:
                    self.alive = False
            return SimpleNamespace(returncode=0 if self.alive else 1)
        if 'pull' in args:
            assert not self.alive, 'Trace was pulled before process exit'
            Path(args[-1]).write_bytes(b'flushed trace')
        return SimpleNamespace(returncode=0, stdout='', stderr='')


class TraceRecorderTest(unittest.TestCase):
    def test_failing_probe_still_stops_flushes_pulls_and_retains_failure(self):
        with tempfile.TemporaryDirectory() as temp:
            adb = FakeAdb(fail_probe=True)
            result = recorder.capture(adb, Path(temp), 'duration_ms: 120000', 1)
            self.assertTrue(result['flushed'])
            self.assertFalse(result['probe_pass'])
            self.assertTrue(result['errors'])
            self.assertEqual(3, sum('test' in call for call in adb.calls))
            self.assertEqual(hashlib.sha256(b'flushed trace').hexdigest(), result['trace_sha256'])

    def test_timeout_stops_remote_instrumentation_and_preserves_partial_output(self):
        with tempfile.TemporaryDirectory() as temp:
            adb = FakeAdb(timeout_probe=True)
            result = recorder.capture(adb, Path(temp), '', 1)
            self.assertIn(('shell', 'am', 'force-stop', 'com.cyanrbt.walkbuddy'), adb.calls)
            self.assertEqual('partial probe\n', (Path(temp)/'partial-instrumentation.txt').read_text())
            self.assertTrue(result['flushed'])
            self.assertFalse(result['probe_pass'])

    def test_invalid_pid_never_reaches_adb(self):
        adb = FakeAdb()
        with self.assertRaises(ValueError):
            recorder.stop_trace(adb, '123; killall perfetto')
        self.assertEqual([], adb.calls)

    def test_emulator_guard_runs_before_any_mutating_command(self):
        fake = SimpleNamespace(run=lambda *args, **kw: SimpleNamespace(stdout='0\n'))
        with patch.object(recorder, 'Adb', return_value=fake), \
                patch('sys.argv', ['record_preview_trace.py', '/tmp/unused-fixture', '--serial', 'physical-fixture']), \
                patch.object(recorder, 'capture') as capture:
            with self.assertRaisesRegex(SystemExit, 'restricted'):
                recorder.main()
            capture.assert_not_called()


if __name__ == '__main__':
    unittest.main()
