"""Opt-in coordinator: drain active stages on failure or interruption under the outer lock."""
from pathlib import Path
import signal
import subprocess
import sys
import time


def main():
    root = Path(sys.argv[1])
    cancel = Path(sys.argv[2]) / 'cancel-pipelines'
    interrupted = 0
    processes = []

    def interrupt(signum, _frame):
        nonlocal interrupted
        interrupted = 128 + signum
        cancel.touch()

    for signum in (signal.SIGINT, signal.SIGTERM, signal.SIGHUP):
        signal.signal(signum, interrupt)

    status = 0
    try:
        for pipeline in ('frontend', 'backend'):
            if interrupted:
                break
            # Isolate active stages from the outer gate's termination signal.
            # Their normal exit includes Gradle's worker and database shutdown.
            processes.append(subprocess.Popen(
                ['bash', str(root / 'scripts/lib/release-pipelines.sh'),
                 str(root), pipeline, str(cancel), sys.argv[3]], start_new_session=True))
        pending = list(processes)
        while pending:
            for process in pending[:]:
                result = process.poll()
                if result is not None:
                    pending.remove(process)
                    if result and (not status or status == 125):
                        status = result if result > 0 else 128 - result
                        cancel.touch()
            time.sleep(0.05)
    finally:
        # Also drain on coordinator exceptions; never release the outer lock
        # while a normally running pipeline can still write artifacts.
        cancel.touch()
        for process in processes:
            process.wait()
    return interrupted or status


if __name__ == '__main__':
    sys.exit(main())
