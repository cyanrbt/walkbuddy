import hashlib
from pathlib import Path
import shutil
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from prepare_ui_storage import prepare


class HostedStorageTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.workspace, self.sdk, self.gradle = (self.base/name for name in ('repo', 'sdk', 'gradle'))
        self.environment = {'GITHUB_ACTIONS': 'true', 'RUNNER_ENVIRONMENT': 'github-hosted'}
        self.app = self.write('repo/app/build/outputs/apk/debug/app-debug.apk', b'application fixture')
        self.test = self.write('repo/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk', b'instrumentation fixture')
        for name in ('sdk/platform-tools/adb', 'sdk/emulator/emulator', 'sdk/system-images/current/userdata.img',
                     'sdk/platforms/current/android.jar', 'sdk/build-tools/current/aapt', 'repo/app/src/main/source.java', 'signing.key'):
            self.write(name, b'retain fixture')
        for name in ('sdk/ndk/current/strip', 'sdk/cmake/current/cmake', 'gradle/caches/dependency.jar', 'gradle/wrapper/dists/gradle.zip'):
            self.write(name, b'disposable cache fixture')

    def write(self, name, data):
        path = self.base/name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        return path

    def run_prepare(self, environment=None):
        return prepare(self.workspace, self.sdk, self.gradle, self.base,
                       self.environment if environment is None else environment)

    def test_only_build_time_caches_are_removed_and_apk_bytes_are_preserved(self):
        before = {path: path.read_bytes() for path in self.base.rglob('*') if path.is_file()
                  and not any(part in path.parts for part in ('ndk', 'cmake', 'caches', 'dists'))}
        report = self.run_prepare()
        self.assertEqual(4, len(report['removed']))
        self.assertTrue(report['compiled_apks_unchanged'])
        for path, content in before.items():
            self.assertEqual(content, path.read_bytes())
        self.assertEqual(hashlib.sha256(self.app.read_bytes()).hexdigest(),
                         report['apk_sha256']['app/build/outputs/apk/debug/app-debug.apk'])

    def test_self_hosted_or_local_execution_is_rejected_before_deletion(self):
        for environment in ({}, {'GITHUB_ACTIONS': 'true', 'RUNNER_ENVIRONMENT': 'self-hosted'}):
            with self.assertRaisesRegex(ValueError, 'GitHub-hosted'):
                self.run_prepare(environment)
            self.assertTrue((self.sdk/'ndk/current/strip').is_file())
            self.assertTrue((self.gradle/'caches/dependency.jar').is_file())

    def test_missing_compiled_test_apk_prevents_cleanup(self):
        self.test.unlink()
        with self.assertRaisesRegex(ValueError, 'Both compiled APKs'):
            self.run_prepare()
        self.assertTrue((self.sdk/'cmake/current/cmake').is_file())

    def test_wrong_sdk_prevents_deletion_of_gradle_caches(self):
        (self.sdk/'platform-tools/adb').unlink()
        with self.assertRaisesRegex(ValueError, 'platform-tools/adb'):
            self.run_prepare()
        self.assertTrue((self.gradle/'caches/dependency.jar').is_file())

    def test_linked_cache_targets_outside_the_allowlist_are_not_deleted(self):
        external = self.write('outside/dists/keep', b'outside fixture')
        shutil.rmtree(self.sdk/'ndk')
        (self.sdk/'ndk').symlink_to(self.base/'outside', target_is_directory=True)
        shutil.rmtree(self.gradle/'wrapper')
        (self.gradle/'wrapper').symlink_to(self.base/'outside', target_is_directory=True)
        report = self.run_prepare()
        self.assertEqual(2, len(report['skipped']))
        self.assertEqual(b'outside fixture', external.read_bytes())


if __name__ == '__main__':
    unittest.main()
