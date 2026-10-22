"""Small, read-only check for the explicitly selected legacy Saanthvana project."""
import base64
from datetime import date, datetime, timezone
import json
import os
import sys
import time
from urllib import error, request

PROJECT = "kjnhtakqzboayqorrlue"
ENDPOINT = "https://" + PROJECT + ".supabase.co/rest/v1/rpc/sahara_health_check"
ANCHOR = date(2026, 10, 22)


def is_due(today, manual=False):
    elapsed = (today - ANCHOR).days
    return manual or (elapsed >= 0 and elapsed % 6 == 0)


def validate_key(key):
    if not key or key != key.strip() or any(c.isspace() for c in key):
        raise ValueError("Configure the project-specific publishable key secret first.")
    if key.startswith("sb_publishable_") and len(key) > len("sb_publishable_"):
        return key
    # Inspect legacy public JWT claims only to reject privileged/wrong-project keys.
    # The server still authenticates the key; this is not JWT signature verification.
    try:
        parts = key.split(".")
        if len(parts) != 3:
            raise ValueError()
        payload = parts[1] + "=" * (-len(parts[1]) % 4)
        claims = json.loads(base64.urlsafe_b64decode(payload))
        if claims.get("role") != "anon" or claims.get("ref") != PROJECT:
            raise ValueError()
    except (ValueError, TypeError, AttributeError, UnicodeError):
        raise ValueError("Use this project's publishable or legacy anon key, never a secret/service-role key.") from None
    return key


class NoRedirect(request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def check(key, opener=None, sleep=time.sleep):
    key = validate_key(key)
    opener = opener or request.build_opener(NoRedirect())
    for attempt in range(3):
        req = request.Request(ENDPOINT, headers={"apikey": key, "Accept": "application/json"}, method="GET")
        try:
            with opener.open(req, timeout=15) as response:
                body = response.read(33)
                if response.status != 200 or len(body) > 32 or body.strip() != b"true":
                    raise RuntimeError("Health check returned an unexpected result; no response body was logged.")
            return
        except error.HTTPError as exc:
            # No redirects, credential errors, or missing-RPC errors are retried.
            retry = exc.code == 429 or 500 <= exc.code <= 599
            exc.close()
            if not retry or attempt == 2:
                raise RuntimeError("Health endpoint failed (HTTP %d); inspect configuration in Supabase." % exc.code) from None
        except (error.URLError, TimeoutError, OSError):
            if attempt == 2:
                raise RuntimeError("Health endpoint could not be reached after three bounded attempts.") from None
        sleep(2 ** attempt)


def main():
    if sys.argv[1:] == ["due"]:
        today = datetime.now(timezone.utc).date()
        print("due=" + str(is_due(today, os.environ.get("MANUAL_RUN") == "true")).lower())
    elif sys.argv[1:] == ["check"]:
        try:
            check(os.environ.get("SAANTHVANA_SUPABASE_PUBLISHABLE_KEY", ""))
        except (ValueError, RuntimeError) as exc:
            print(str(exc), file=sys.stderr)
            return 1
        print("Saanthvana read-only database health check passed.")
    else:
        print("Usage: saanthvana_health_check.py due|check", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
