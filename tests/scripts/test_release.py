"""Exercise release safeguards in disposable repositories with stub tools; never deploy."""
import os
from pathlib import Path
import shutil
import signal
import subprocess
import tempfile
import time
import unittest

SOURCE = Path(__file__).resolve().parents[2]
SKILL = Path('.agents/skills/release-plan/scripts')


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name) / 'repo'
        self.root.mkdir()
        self.git('init', '-b', 'master')
        self.git('config', 'user.email', 'test@example.com')
        self.git('config', 'user.name', 'Test')
        for directory in ['scripts', str(SKILL), 'backend', 'bin', 'tests/scripts']:
            (self.root / directory).mkdir(parents=True, exist_ok=True)
        shutil.copytree(SOURCE / 'scripts/lib', self.root / 'scripts/lib')
        shutil.copy2(SOURCE / 'scripts/check.sh', self.root / 'scripts/check.sh')
        for name in ['build-release-artifacts.sh', 'deploy-production.sh', 'verify-production.sh']:
            shutil.copy2(SOURCE / SKILL / name, self.root / SKILL / name)
        (self.root / '.gitignore').write_text('tmp/\ndist/\nbackend/build/\n.env\n')
        (self.root / '.env').write_text('\n'.join(f'{key}=test-value' for key in [
            'VUE_APP_GOOGLE_CLIENT_ID', 'VUE_APP_CHATGPT_COACH_URL', 'CHATGPT_ACTION_TOKEN',
            'CHATGPT_FILE_SIGNING_SECRET', 'APP_VAPID_PUBLIC_KEY', 'APP_VAPID_PRIVATE_KEY',
            'APP_PUSH_RELEASE_TOKEN', 'MAILGUN_SMTP_PASSWORD']) + '\n')
        self.script('bin/yarn', '''
case "$1" in
  build) mkdir -p dist; printf frontend > dist/index.html ;;
esac
''')
        self.script('backend/gradlew', '''
case "$1" in
  bootJar) mkdir -p build/libs; printf backend > build/libs/app.jar ;;
esac
''')
        self.script('scripts/deploy.sh', 'touch tmp/deployed\n')
        self.script('bin/curl', '''
case "${!#}" in
  */api/auth/me) printf 403 ;;
  */api/push/release-notification) printf 204 ;;
  */push-service-worker.js) printf "addEventListener('push' addEventListener('notificationclick'" ;;
  */service-worker.js) printf push-service-worker.js ;;
  *) printf 200 ;;
esac
''')
        self.git('add', '.')
        self.git('commit', '-qm', 'Fixture')
        self.environment = dict(os.environ, PATH=str(self.root / 'bin') + ':' + os.environ['PATH'])
        self.build = [str(self.root / SKILL / 'build-release-artifacts.sh'), str(self.root)]
        self.deploy = [str(self.root / SKILL / 'deploy-production.sh'), 'HEAD', str(self.root)]

    def git(self, *args):
        return subprocess.check_output(['git', '-C', str(self.root), *args], stderr=subprocess.STDOUT, text=True).strip()

    def script(self, name, body):
        path = self.root / name
        path.write_text('#!/usr/bin/env bash\nset -euo pipefail\n' + body)
        path.chmod(0o755)

    def commit(self):
        self.git('add', '.')
        self.git('commit', '-qm', 'Update fixture')

    def run_command(self, args):
        return subprocess.run(args, cwd=self.root, env=self.environment, capture_output=True, text=True, timeout=20)

    def start(self, args):
        process = subprocess.Popen(args, cwd=self.root, env=self.environment, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        self.addCleanup(self.stop, process)
        return process

    def stop(self, process):
        if process.poll() is None:
            process.terminate()
            process.communicate(timeout=10)

    def wait_file(self, name):
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            if (self.root / name).exists():
                return
            time.sleep(0.02)
        self.fail(f'Process did not create {name}')

    def ready(self):
        return (self.root / 'tmp/release-artifacts/tree').exists()

    def test_success_builds_and_verifies_with_timings(self):
        result = self.run_command(self.build)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertTrue(self.ready())
        rows = next((self.root / 'tmp/checks').glob('*/timings.tsv')).read_text().splitlines()
        self.assertEqual(8, len(rows))
        self.assertTrue(all(row.split('\t')[4] == '0' for row in rows[1:]))
        self.assertNotIn('test-value', '\n'.join(rows))
        result = self.run_command(self.deploy)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertTrue((self.root / 'tmp/deployed').exists())

    def test_failure_invalidates_previous_artifacts_and_preserves_exit_status(self):
        self.assertEqual(0, self.run_command(self.build).returncode)
        self.script('bin/yarn', 'exit 23\n')
        self.commit()
        result = self.run_command(self.build)
        self.assertEqual(23, result.returncode)
        self.assertFalse(self.ready())
        self.assertNotEqual(0, self.run_command(self.deploy).returncode)
        self.assertFalse((self.root / 'tmp/deployed').exists())
        self.assertEqual(0, self.run_command([str(self.root / 'scripts/check.sh'), 'backend', 'test']).returncode)

    def test_source_change_even_if_committed_rejects_artifacts(self):
        self.script('bin/yarn', '''
if [[ "$1" == build ]]; then
  mkdir -p dist; echo frontend > dist/index.html
  echo changed > changed.txt; git add changed.txt; git commit -qm changed
fi
''')
        self.commit()
        result = self.run_command(self.build)
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(self.ready())

    def test_uncommitted_source_change_rejects_artifacts(self):
        self.script('bin/yarn', 'echo changed > tracked.txt\n')
        (self.root / 'tracked.txt').write_text('original')
        self.commit()
        self.assertNotEqual(0, self.run_command(self.build).returncode)
        self.assertFalse(self.ready())

    def test_checksum_mismatch_prevents_deployment(self):
        self.assertEqual(0, self.run_command(self.build).returncode)
        (self.root / 'dist/index.html').write_text('tampered')
        self.assertNotEqual(0, self.run_command(self.deploy).returncode)
        self.assertFalse((self.root / 'tmp/deployed').exists())

    def test_deployment_failure_keeps_valid_artifacts_and_releases_locks(self):
        self.script('scripts/deploy.sh', 'exit 29\n')
        self.commit()
        self.assertEqual(0, self.run_command(self.build).returncode)
        self.assertEqual(29, self.run_command(self.deploy).returncode)
        self.assertTrue(self.ready())
        self.assertEqual(29, self.run_command(self.deploy).returncode)
        self.assertEqual(0, self.run_command([str(self.root / 'scripts/check.sh'), 'frontend', 'lint']).returncode)

    def test_master_tree_mismatch_prevents_deployment(self):
        self.assertEqual(0, self.run_command(self.build).returncode)
        (self.root / 'new.txt').write_text('changed')
        self.commit()
        self.assertNotEqual(0, self.run_command(self.deploy).returncode)
        self.assertFalse((self.root / 'tmp/deployed').exists())

    def test_lock_spans_command_cleanup_and_releases_after_interruption(self):
        self.script('backend/gradlew', '''
trap 'touch ../tmp/cleaning; sleep 0.5; touch ../tmp/cleaned; exit 0' TERM
mkdir -p ../tmp; touch ../tmp/started
while :; do sleep 0.1; done
''')
        unrelated = subprocess.Popen(['sleep', '20'])
        self.addCleanup(self.stop, unrelated)
        runner = self.start([str(self.root / 'scripts/check.sh'), 'backend', 'test'])
        self.wait_file('tmp/started')
        self.assertEqual(75, self.run_command(self.build).returncode)
        self.assertEqual(75, self.run_command([str(self.root / 'scripts/check.sh'), 'frontend', 'lint']).returncode)
        runner.send_signal(signal.SIGTERM)
        self.wait_file('tmp/cleaning')
        self.assertEqual(75, self.run_command([str(self.root / 'scripts/check.sh'), 'frontend', 'lint']).returncode)
        output, _ = runner.communicate(timeout=10)
        self.assertEqual(143, runner.returncode, output)
        self.assertTrue((self.root / 'tmp/cleaned').exists())
        self.assertIsNone(unrelated.poll())
        self.assertEqual(0, self.run_command([str(self.root / 'scripts/check.sh'), 'frontend', 'lint']).returncode)

    def test_interrupted_gate_never_publishes_manifest(self):
        self.script('bin/yarn', 'mkdir -p tmp; touch tmp/started; sleep 20\n')
        self.commit()
        runner = self.start(self.build)
        self.wait_file('tmp/started')
        self.assertEqual(75, self.run_command(self.build).returncode)
        runner.terminate()
        runner.communicate(timeout=10)
        self.assertEqual(143, runner.returncode)
        self.assertFalse(self.ready())

    def test_interruption_during_publication_removes_readiness(self):
        self.script('bin/mv', '/usr/bin/mv "$@"; kill -TERM "$PPID"\n')
        self.commit()
        result = self.run_command(self.build)
        self.assertEqual(143, result.returncode)
        self.assertFalse(self.ready())

    def test_normal_exit_waits_for_background_cleanup(self):
        self.script('backend/gradlew', 'mkdir -p ../tmp; (sleep 0.5; touch ../tmp/cleaned) &\n')
        result = self.run_command([str(self.root / 'scripts/check.sh'), 'backend', 'test'])
        self.assertEqual(0, result.returncode)
        self.assertTrue((self.root / 'tmp/cleaned').exists())

    def parallel_fixture(self, failing=False):
        self.script('bin/yarn', """
case "$1" in
  install)
    mkdir -p tmp; touch tmp/frontend-started
    while [[ ! -e tmp/backend-started ]]; do sleep 0.02; done
    sleep 0.2
    EXIT_FRONTEND
    ;;
  lint) touch tmp/linted ;;
  test:e2e) touch tmp/browser-tested ;;
  build)
    test -e tmp/linted; test -e tmp/browser-tested
    mkdir -p dist; echo frontend > dist/index.html ;;
esac
""".replace('EXIT_FRONTEND', 'exit 23' if failing else 'touch tmp/frontend-finished'))
        self.script('backend/gradlew', """
case "$1" in
  test)
    mkdir -p ../tmp; touch ../tmp/backend-started
    while [[ ! -e ../tmp/frontend-started ]]; do sleep 0.02; done
    sleep 0.8; touch ../tmp/backend-finished ;;
  bootJar)
    test -e ../tmp/backend-finished
    mkdir -p build/libs; echo backend > build/libs/app.jar ;;
esac
""")
        self.commit()
        return [*self.build, 'parallel-pipelines']

    def test_parallel_success_preserves_pipeline_order_and_requires_both(self):
        result = self.run_command(self.parallel_fixture())
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertTrue(self.ready())
        self.assertTrue((self.root / 'tmp/backend-finished').exists())
        rows = '\n'.join(p.read_text() for p in (self.root / 'tmp/checks').glob('*/timings.tsv'))
        for stage in ['frontend-install', 'frontend-lint', 'browser-tests',
                      'frontend-production-build', 'backend-tests', 'backend-production-build']:
            self.assertIn(stage, rows)

    def test_parallel_failure_drains_peer_and_invalidates_previous_readiness(self):
        self.assertEqual(0, self.run_command(self.build).returncode)
        runner = self.start(self.parallel_fixture(failing=True))
        self.wait_file('tmp/frontend-started')
        self.wait_file('tmp/backend-started')
        self.assertEqual(75, self.run_command(self.build).returncode)
        output, _ = runner.communicate(timeout=10)
        self.assertEqual(23, runner.returncode, output)
        self.assertTrue((self.root / 'tmp/backend-finished').exists())
        self.assertFalse((self.root / 'tmp/linted').exists())
        self.assertFalse(self.ready())
        self.assertNotEqual(0, self.run_command(self.deploy).returncode)
        self.assertFalse((self.root / 'tmp/deployed').exists())

    def test_parallel_interrupt_drains_active_stages_and_keeps_lock(self):
        runner = self.start(self.parallel_fixture())
        unrelated = subprocess.Popen(['sleep', '20'])
        self.addCleanup(self.stop, unrelated)
        self.wait_file('tmp/backend-started')
        self.wait_file('tmp/frontend-started')
        runner.terminate()
        self.assertEqual(75, self.run_command(self.build).returncode)
        output, _ = runner.communicate(timeout=10)
        self.assertEqual(143, runner.returncode, output)
        self.assertTrue((self.root / 'tmp/backend-finished').exists())
        self.assertFalse((self.root / 'backend/build/libs/app.jar').exists())
        self.assertFalse(self.ready())
        self.assertIsNone(unrelated.poll())
        self.assertEqual(0, self.run_command([str(self.root / 'scripts/check.sh'), 'frontend', 'lint']).returncode)

    def test_backend_failure_drains_frontend_descendants_before_unlocking(self):
        self.script('bin/yarn', '''
mkdir -p tmp
if [[ "$1" == install ]]; then
  touch tmp/frontend-started
  (sleep 0.8; touch tmp/frontend-cleaned) &
else
  touch tmp/unexpected-stage
fi
''')
        self.script('backend/gradlew', '''
while [[ ! -e ../tmp/frontend-started ]]; do sleep 0.02; done
exit 29
''')
        self.commit()
        result = self.run_command([*self.build, 'parallel-pipelines'])
        self.assertEqual(29, result.returncode, result.stdout + result.stderr)
        self.assertTrue((self.root / 'tmp/frontend-cleaned').exists())
        self.assertFalse((self.root / 'tmp/unexpected-stage').exists())
        self.assertFalse(self.ready())

    def test_experiment_modes_reject_source_mutation(self):
        self.script('bin/yarn', '''
if [[ "$1" == build ]]; then
  mkdir -p dist; echo frontend > dist/index.html
  echo changed >> tracked.txt; git add tracked.txt; git commit -qm changed
fi
''')
        self.commit()
        for mode in ['parallel-pipelines', 'parallel-browser']:
            with self.subTest(mode=mode):
                result = self.run_command([*self.build, mode])
                self.assertNotEqual(0, result.returncode)
                self.assertFalse(self.ready())

    def test_browser_experiment_only_changes_browser_command(self):
        self.script('bin/yarn', '''
mkdir -p tmp
printf '%s\\n' "$*" >> tmp/yarn-commands
if [[ "$1" == build ]]; then mkdir -p dist; echo frontend > dist/index.html; fi
''')
        self.commit()
        result = self.run_command([*self.build, 'parallel-browser'])
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        commands = (self.root / 'tmp/yarn-commands').read_text().splitlines()
        self.assertEqual(['install --frozen-lockfile', 'lint',
                          'test:e2e --config playwright.experiment.config.js', 'build'], commands)

    def test_unknown_mode_does_not_start_validation(self):
        self.assertEqual(2, self.run_command([*self.build, 'combined']).returncode)
        self.assertFalse((self.root / 'tmp/checks').exists())

    def test_deployment_lock_is_shared_across_worktrees(self):
        self.script('scripts/deploy.sh', 'touch tmp/deployment-started; sleep 20\n')
        self.commit()
        self.assertEqual(0, self.run_command(self.build).returncode)
        other = self.root.parent / 'other'
        self.git('worktree', 'add', '-b', 'other', str(other))
        runner = self.start(self.deploy)
        self.wait_file('tmp/deployment-started')
        command = ['bash', '-c', 'source "$1/scripts/lib/checks.sh"; check_acquire_lock deployment "$2"', '_', str(self.root), str(other)]
        self.assertEqual(75, self.run_command(command).returncode)
        runner.terminate()
        runner.communicate(timeout=10)
        self.assertEqual(143, runner.returncode)
        self.assertEqual(0, self.run_command(command).returncode)


if __name__ == '__main__':
    unittest.main()
