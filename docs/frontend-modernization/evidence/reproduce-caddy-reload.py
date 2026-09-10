import os
from pathlib import Path
import subprocess
import tempfile
import time
import uuid

name = 'weight-control-baseline-caddy-' + uuid.uuid4().hex[:10]
old = ':8090 {\n    respond "old"\n}\n'
new = ':8090 {\n    respond "new"\n}\n'

def docker(*args, **kwargs):
    return subprocess.run(['docker', *args], capture_output=True, text=True, check=True, **kwargs).stdout

with tempfile.TemporaryDirectory() as directory:
    config = Path(directory) / 'Caddyfile'
    config.write_text(old)
    docker('run', '--detach', '--rm', '--name', name, '-v', str(config) + ':/etc/caddy/Caddyfile:ro', 'caddy:2.10')
    try:
        deadline = time.monotonic() + 10
        while True:
            probe = subprocess.run(['docker', 'exec', name, 'wget', '-qO-', 'http://127.0.0.1:8090'], capture_output=True, text=True)
            if probe.returncode == 0:
                assert probe.stdout == 'old'
                break
            if time.monotonic() >= deadline:
                raise AssertionError('Caddy did not start: ' + probe.stderr)
            time.sleep(.1)
        replacement = Path(directory) / 'replacement'
        replacement.write_text(new)
        os.replace(replacement, config)
        assert config.read_text() == new
        assert docker('exec', name, 'cat', '/etc/caddy/Caddyfile') == old
        docker('exec', name, 'caddy', 'reload', '--config', '/etc/caddy/Caddyfile')
        assert docker('exec', name, 'wget', '-qO-', 'http://127.0.0.1:8090') == 'old'
        docker('exec', '-i', name, 'caddy', 'reload', '--config', '-', '--adapter', 'caddyfile', input=new)
        assert docker('exec', name, 'wget', '-qO-', 'http://127.0.0.1:8090') == 'new'
    finally:
        docker('rm', '--force', name)
print('Reproduced stale file bind mount; stdin reload served the new configuration; test container cleaned up.')
