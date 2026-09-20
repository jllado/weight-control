import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("verify_deployment", Path(__file__).resolve().parents[2] / "scripts/verify-deployment.py")
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)
TREE = "a" * 40


class ReleaseVerificationTest(unittest.TestCase):
    def setUp(self):
        self.responses = {
            "/": (200, f'<html><head><meta content="{TREE}" name="release-tree"></head></html>'.encode()),
            "/api/version": (200, json.dumps({"sourceTree": TREE}).encode()),
        }
        self.responses.update({path: (status, b" ".join(markers)) for path, status, markers in verifier.READINESS})
        self.fetch = patch.object(verifier, "fetch", side_effect=lambda url: self.responses[url.removeprefix("https://app.test")])
        self.fetch.start()
        self.addCleanup(self.fetch.stop)

    def test_matching_release(self):
        self.assertEqual(0, verifier.verify("https://app.test", TREE, timeout=0))

    def test_stale_frontend_backend_and_missing_metadata_fail(self):
        for path, body in [
            ("/", b'<meta name="release-tree" content="old">'),
            ("/", b"<html>Login</html>"),
            ("/api/version", b'{"sourceTree":"old"}'),
            ("/api/version", b'{}'),
            ("/api/version", b'[]'),
            ("/api/version", b'<html>Not JSON</html>'),
        ]:
            with self.subTest(path=path, body=body):
                previous = self.responses[path]
                self.responses[path] = (200, body)
                self.assertEqual(1, verifier.verify("https://app.test", TREE, timeout=0))
                self.responses[path] = previous

    def test_failed_readiness_fails(self):
        path = verifier.READINESS[0][0]
        self.responses[path] = (500, b"")
        self.assertEqual(1, verifier.verify("https://app.test", TREE, timeout=0))

    def test_retries_until_ready(self):
        with patch.object(verifier, "check", side_effect=[OSError("not ready"), None]) as check, patch.object(verifier.time, "sleep"):
            self.assertEqual(0, verifier.verify("https://app.test", TREE))
            self.assertEqual(2, check.call_count)

    def test_development_is_not_a_release(self):
        with self.assertRaises(ValueError):
            verifier.verify("https://app.test", "development", timeout=0)

    def test_http_fetch_is_get_and_disables_cache(self):
        self.fetch.stop()
        with patch.object(verifier.urllib.request, "urlopen") as open_url:
            response = open_url.return_value.__enter__.return_value
            response.status = 200
            response.read.return_value = b"body"
            self.assertEqual((200, b"body"), verifier.fetch("https://app.test/"))
            request = open_url.call_args.args[0]
            self.assertEqual("GET", request.get_method())
            self.assertEqual("no-cache, no-store", request.get_header("Cache-control"))


if __name__ == "__main__":
    unittest.main()
