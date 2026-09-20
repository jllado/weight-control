#!/usr/bin/env python3
"""Read-only release identity and readiness checks; no browser, login, or notifications."""
import argparse
from html.parser import HTMLParser
import json
import re
import sys
import time
import urllib.error
import urllib.request


READINESS = [
    ("/api/auth/me", 403, []),
    ("/service-worker.js", 200, [b"push-service-worker.js"]),
    ("/push-service-worker.js", 200, [b"addEventListener('push'", b"addEventListener('notificationclick'"]),
]


class ReleaseHTML(HTMLParser):
    def __init__(self):
        super().__init__()
        self.trees = []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "meta" and attrs.get("name") == "release-tree":
            self.trees.append(attrs.get("content"))


def fetch(url):
    request = urllib.request.Request(url, headers={"Cache-Control": "no-cache, no-store"})
    try:
        with urllib.request.urlopen(request, timeout=5) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()


def check(base_url, expected_tree):
    status, body = fetch(base_url + "/")
    page = ReleaseHTML()
    page.feed(body.decode("utf-8"))
    if status != 200 or page.trees != [expected_tree]:
        raise ValueError(f"Frontend identity mismatch: expected {expected_tree}, observed {page.trees!r}, HTTP {status}")
    status, body = fetch(base_url + "/api/version")
    version = json.loads(body)
    if status != 200 or not isinstance(version, dict) or version.get("sourceTree") != expected_tree:
        raise ValueError(f"Backend identity mismatch: expected {expected_tree}, HTTP {status}")
    for path, expected_status, markers in READINESS:
        status, body = fetch(base_url + path)
        if status != expected_status or any(marker not in body for marker in markers):
            raise ValueError(f"Readiness failed: {path}, HTTP {status}")



def verify(base_url, expected_tree, timeout=120):
    if not re.fullmatch(r"[0-9a-f]{40}", expected_tree):
        raise ValueError("Expected source tree must be a full Git tree hash, not a development marker.")
    deadline = time.monotonic() + timeout
    while True:
        try:
            check(base_url.rstrip("/"), expected_tree)
            print(f"Verified frontend and backend sourceTree={expected_tree}; project readiness checks passed.")
            return 0
        except (OSError, ValueError) as error:
            print(str(error), file=sys.stderr)
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            print("Release verification timed out.", file=sys.stderr)
            return 1
        time.sleep(min(5, remaining))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("base_url")
    parser.add_argument("expected_tree")
    parser.add_argument("--timeout", type=float, default=120)
    args = parser.parse_args()
    sys.exit(verify(args.base_url, args.expected_tree, args.timeout))
