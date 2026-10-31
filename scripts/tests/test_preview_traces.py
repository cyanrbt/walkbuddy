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

    def test_recovery_success_cannot_hide_failed_or_missing_surface_setup(self):
        capture = {**self.capture, 'scenario': 'surface_reinstall_recovery', 'surface_prefix_pass': True,
                   'reinstall': [{'passed': True}, {'passed': True}]}
        self.assertTrue(report.summarize(complete_rows(), capture)['valid'])
        for changed in ({'surface_prefix_pass': False}, {'reinstall': []},
                        {'reinstall': [{'passed': True}, {'passed': False}]}):
            with self.subTest(changed=changed):
                self.assertFalse(report.summarize(complete_rows(), {**capture, **changed})['valid'])

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

    def test_missing_trace_preserves_the_recorded_adb_cause_in_the_summary(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            (directory/'capture.json').write_text(json.dumps({'errors': ['adb pull: remote file unavailable']}))
            with self.assertRaisesRegex(ValueError, 'remote file unavailable'):
                report.analyze(directory, '/does-not-exist')


class FakeAdb:
    def __init__(self, fail_probe=False, timeout_probe=False, failed_start=False, fail_first_pull=False, corrupt_pull=False):
        self.calls = []
        self.alive = True
        self.stopping_checks = None
        self.fail_probe = fail_probe
        self.timeout_probe = timeout_probe
        self.failed_start = failed_start
        self.fail_first_pull = fail_first_pull
        self.corrupt_pull = corrupt_pull
        self.pulls = 0

    def run(self, *args, **kwargs):
        self.calls.append(args)
        if 'perfetto' in args:
            return SimpleNamespace(returncode=1 if self.failed_start else 0, stdout='123\n',
                                   stderr='data source startup failed' if self.failed_start else 'started\n')
        if 'stat' in args:
            return SimpleNamespace(returncode=0, stdout=str(len(b'flushed trace'))+'\n', stderr='')
        if 'sha256sum' in args:
            return SimpleNamespace(returncode=0, stdout=hashlib.sha256(b'flushed trace').hexdigest()+'  trace\n', stderr='')
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
            self.pulls += 1
            if self.fail_first_pull and self.pulls == 1:
                Path(args[-1]).write_bytes(b'partial')
                return SimpleNamespace(returncode=1, stdout='sync transfer interrupted', stderr='60%')
            Path(args[-1]).write_bytes(b'flushed trAce' if self.corrupt_pull else b'flushed trace')
        return SimpleNamespace(returncode=0, stdout='', stderr='')


class SurfacePrefixAdb(FakeAdb):
    output = ('SURFACE_COPY_PARITY_PASS cases=32 pixels=6144\n'
              'SURFACE_PRODUCERS_PARITY_PASS camera_cases=2 egl_cases=2 pixels=768\n'
              'PREVIEW_PROBE_PASS cases=32\n')

    def __init__(self, prefix_output=None, prefix_exit=0, prefix_timeout=False, failed_install=None, **kwargs):
        super().__init__(**kwargs)
        self.prefix_output = self.output if prefix_output is None else prefix_output
        self.prefix_exit = prefix_exit
        self.prefix_timeout = prefix_timeout
        self.failed_install = failed_install
        self.installs = 0

    def run(self, *args, **kwargs):
        if 'surface_copy' in args:
            self.calls.append(args)
            if self.prefix_timeout:
                raise subprocess.TimeoutExpired('adb surface prefix', 45, output=b'partial surface\n')
            return SimpleNamespace(returncode=self.prefix_exit, stdout=self.prefix_output, stderr='')
        if args[0] == 'install':
            self.calls.append(args)
            self.installs += 1
            failed = self.installs == self.failed_install
            return SimpleNamespace(returncode=1 if failed else 0,
                                   stdout='Failure [INSTALL_FAILED_TEST_ONLY]' if failed else 'Performing Streamed Install\nSuccess\n', stderr='')
        return super().run(*args, **kwargs)


class TraceRecorderTest(unittest.TestCase):
    def test_surface_producers_and_both_reinstalls_are_inside_the_same_trace_before_recovery(self):
        with tempfile.TemporaryDirectory() as temp:
            adb = SurfacePrefixAdb()
            directory = Path(temp)
            config = 'duration_ms: 240000\n'
            result = recorder.capture(adb, directory, config, 1, surface_prefix=True)
            self.assertEqual([], result['errors'])
            self.assertTrue(result['surface_prefix_pass'])
            self.assertTrue(result['probe_pass'])
            self.assertEqual('surface_reinstall_recovery', result['scenario'])
            calls = adb.calls
            start = next(i for i, c in enumerate(calls) if '--background-wait' in c)
            prefix = next(i for i, c in enumerate(calls) if 'surface_copy' in c)
            installs = [i for i, c in enumerate(calls) if c[0] == 'install']
            recovery = next(i for i, c in enumerate(calls) if 'pip_recovery' in c)
            stop = next(i for i, c in enumerate(calls) if 'kill' in c)
            self.assertEqual(sorted([start, prefix, *installs, recovery, stop]),
                             [start, prefix, *installs, recovery, stop])
            self.assertEqual(2, len(installs))
            self.assertNotEqual(calls[installs[0]][-1], calls[installs[1]][-1])
            self.assertTrue(all(i['passed'] for i in result['reinstall']))
            self.assertEqual(config, (directory/'config.pbtxt').read_text())
            self.assertEqual(hashlib.sha256(config.encode()).hexdigest(), result['config_sha256'])
            self.assertEqual(adb.output, (directory/'surface-prefix.txt').read_text())

    def test_surface_prefix_requires_all_markers_no_failure_and_successful_exit(self):
        for output, exit_code in ((SurfacePrefixAdb.output, 1), ('PREVIEW_PROBE_PASS cases=32', 0),
                                  (SurfacePrefixAdb.output+'PREVIEW_PROBE_FAIL: late error', 0)):
            with self.subTest(output=output, exit_code=exit_code), tempfile.TemporaryDirectory() as temp:
                adb = SurfacePrefixAdb(prefix_output=output, prefix_exit=exit_code)
                result = recorder.capture(adb, Path(temp), '', 1, surface_prefix=True)
                self.assertFalse(result['surface_prefix_pass'])
                self.assertFalse(result['probe_pass'])
                self.assertTrue(result['errors'])
                self.assertTrue(result['flushed'])
                self.assertTrue((Path(temp)/'trace.pftrace').is_file())
                self.assertFalse(any('pip_recovery' in c or c[0] == 'install' for c in adb.calls))

    def test_prefix_timeout_keeps_partial_output_and_stops_owned_trace_without_recovery(self):
        with tempfile.TemporaryDirectory() as temp:
            adb = SurfacePrefixAdb(prefix_timeout=True)
            directory = Path(temp)
            result = recorder.capture(adb, directory, '', 1, surface_prefix=True)
            self.assertTrue(result['flushed'])
            self.assertFalse(result['probe_pass'])
            self.assertIn('surface-prefix:', (directory/'timeout.txt').read_text())
            self.assertEqual('partial surface\n', (directory/'partial-surface-prefix.txt').read_text())
            self.assertIn(('shell', 'am', 'force-stop', 'com.cyanrbt.walkbuddy'), adb.calls)
            self.assertFalse(any('pip_recovery' in c for c in adb.calls))

    def test_failed_reinstall_stops_before_recovery_and_retains_install_failure(self):
        for failed in (1, 2):
            with self.subTest(failed=failed), tempfile.TemporaryDirectory() as temp:
                adb = SurfacePrefixAdb(failed_install=failed)
                directory = Path(temp)
                result = recorder.capture(adb, directory, '', 1, surface_prefix=True)
                self.assertTrue(result['surface_prefix_pass'])
                self.assertFalse(result['probe_pass'])
                self.assertEqual(failed, len(result['reinstall']))
                self.assertFalse(result['reinstall'][-1]['passed'])
                self.assertTrue(result['flushed'])
                self.assertIn('INSTALL_FAILED_TEST_ONLY', (directory/f"reinstall-{'app' if failed == 1 else 'tests'}.txt").read_text())
                self.assertFalse(any('pip_recovery' in c for c in adb.calls))

    def test_recovery_failure_after_successful_prefix_is_not_retried_or_relabelled(self):
        with tempfile.TemporaryDirectory() as temp:
            adb = SurfacePrefixAdb(fail_probe=True)
            result = recorder.capture(adb, Path(temp), '', 1, surface_prefix=True)
            self.assertTrue(result['surface_prefix_pass'])
            self.assertFalse(result['probe_pass'])
            self.assertTrue(result['errors'])
            self.assertTrue(result['flushed'])
            self.assertEqual(1, sum('pip_recovery' in c for c in adb.calls))

    def test_only_first_of_three_trials_gets_surface_prefix(self):
        fake = SimpleNamespace(run=lambda *args, **kw: SimpleNamespace(stdout='1\n'))
        with tempfile.TemporaryDirectory() as temp, \
                patch.object(recorder, 'Adb', return_value=fake), \
                patch('sys.argv', ['record_preview_trace.py', temp, '--serial', 'emulator-fixture']), \
                patch.object(recorder, 'capture', return_value={'errors': [], 'flushed': True, 'probe_pass': True}) as capture:
            self.assertEqual(0, recorder.main())
            self.assertEqual([True, False, False], [c.kwargs['surface_prefix'] for c in capture.call_args_list])

    def test_interrupted_copy_retries_same_trace_without_repeating_probe(self):
        with tempfile.TemporaryDirectory() as temp:
            adb = FakeAdb(fail_first_pull=True)
            result = recorder.capture(adb, Path(temp), '', 1)
            self.assertEqual([], result['errors'])
            self.assertEqual([False, True], [a['verified'] for a in result['transfer_attempts']])
            self.assertEqual(1, sum('instrument' in call for call in adb.calls))
            self.assertEqual(b'flushed trace', (Path(temp)/'trace.pftrace').read_bytes())
            self.assertIn('sync transfer interrupted', (Path(temp)/'pull-1.txt').read_text())

    def test_success_exit_with_same_length_wrong_hash_never_accepts_a_trace(self):
        with tempfile.TemporaryDirectory() as temp:
            result = recorder.capture(FakeAdb(corrupt_pull=True), Path(temp), '', 1)
            self.assertTrue(result['errors'])
            self.assertFalse((Path(temp)/'trace.pftrace').exists())
            self.assertEqual(2, len(json.loads((Path(temp)/'transfer.json').read_text())['attempts']))

    def test_offline_transport_reconnects_before_copying_the_same_stopped_trace(self):
        class OfflineAdb(FakeAdb):
            offline = False
            def run(self, *args, **kwargs):
                if 'wait-for-device' in args:
                    self.calls.append(args);self.offline = False
                    return SimpleNamespace(returncode=0, stdout='', stderr='')
                if 'pull' in args and self.offline:
                    self.calls.append(args)
                    return SimpleNamespace(returncode=1, stdout='', stderr='device offline')
                result = super().run(*args, **kwargs)
                if 'pull' in args and result.returncode:
                    self.offline = True
                return result
        with tempfile.TemporaryDirectory() as temp:
            adb = OfflineAdb(fail_first_pull=True)
            directory = Path(temp)
            result = recorder.capture(adb, directory, '', 1)
            self.assertEqual([], result['errors'])
            self.assertEqual([False, True], [a['verified'] for a in result['transfer_attempts']])
            self.assertEqual(0, result['transfer_attempts'][1]['reconnect_exit'])
            self.assertEqual(1, sum('instrument' in call for call in adb.calls))
            sources = [call[1] for call in adb.calls if call[0] == 'pull']
            self.assertEqual(2, len(sources));self.assertEqual(sources[0], sources[1])
            self.assertEqual(b'flushed trace', (directory/'trace.pftrace').read_bytes())

    def test_reconnection_timeout_keeps_the_copy_failed_and_does_not_repeat_the_probe(self):
        class UnavailableAdb(FakeAdb):
            def run(self, *args, **kwargs):
                if 'wait-for-device' in args:
                    self.calls.append(args)
                    raise subprocess.TimeoutExpired('adb wait-for-device', 10)
                return super().run(*args, **kwargs)
        with tempfile.TemporaryDirectory() as temp:
            adb = UnavailableAdb(fail_first_pull=True)
            directory = Path(temp)
            result = recorder.capture(adb, directory, '', 1)
            self.assertTrue(result['errors'])
            self.assertFalse((directory/'trace.pftrace').exists())
            self.assertEqual(1, sum('instrument' in call for call in adb.calls))
            self.assertEqual(1, sum(call[0] == 'pull' for call in adb.calls))
            attempts = json.loads((directory/'transfer.json').read_text())['attempts']
            self.assertEqual(2, len(attempts));self.assertFalse(attempts[1]['verified'])

    def test_failed_start_preserves_stderr_and_cleans_up_its_owned_pid(self):
        with tempfile.TemporaryDirectory() as temp:
            adb = FakeAdb(failed_start=True)
            result = recorder.capture(adb, Path(temp), '', 1)
            self.assertFalse(result['probe_pass'])
            self.assertTrue(result['flushed'])
            self.assertFalse(any('instrument' in call for call in adb.calls))
            self.assertTrue(any('data source startup failed' in error for error in result['errors']))

    def test_failing_probe_still_stops_flushes_pulls_and_retains_failure(self):
        with tempfile.TemporaryDirectory() as temp:
            adb = FakeAdb(fail_probe=True)
            result = recorder.capture(adb, Path(temp), 'duration_ms: 120000', 1)
            self.assertTrue(result['flushed'])
            self.assertFalse(result['probe_pass'])
            self.assertTrue(result['errors'])
            self.assertEqual(3, sum('test' in call for call in adb.calls))
            self.assertEqual(hashlib.sha256(b'flushed trace').hexdigest(), result['trace_sha256'])

    def test_copy_failure_retains_info_stage_logs_without_accepting_the_trace(self):
        class StageLogAdb(FakeAdb):
            def run(self, *args, **kwargs):
                if args and args[0] == 'logcat':
                    # Android logcat's WARN filter drops the INFO stage events.
                    info = 'BrightnessStage event=CALLBACK id=1 status=0\n'
                    output = info if 'WalkBuddyTiming:I' in args else ''
                    return SimpleNamespace(returncode=0, stdout=output, stderr='')
                return super().run(*args, **kwargs)
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            result = recorder.capture(StageLogAdb(corrupt_pull=True), directory, '', 1)
            self.assertTrue(result['errors'])
            self.assertFalse((directory/'trace.pftrace').exists())
            self.assertIn('BrightnessStage event=CALLBACK', (directory/'logcat.txt').read_text())

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
