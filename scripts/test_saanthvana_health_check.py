import base64
from datetime import date
import io
import json
import unittest
from urllib import error

import saanthvana_health_check as health


def legacy(role="anon", project=health.PROJECT):
    payload = base64.urlsafe_b64encode(json.dumps({"role": role, "ref": project}).encode()).decode().rstrip("=")
    return "header." + payload + ".signature"


class Response(io.BytesIO):
    status = 200


class FakeOpener:
    def __init__(self, outcomes):
        self.outcomes = iter(outcomes)
        self.requests = []

    def open(self, req, timeout):
        self.requests.append((req, timeout))
        result = next(self.outcomes)
        if isinstance(result, Exception):
            raise result
        return Response(result)


class HealthTests(unittest.TestCase):
    def test_elapsed_cadence_crosses_month_and_year(self):
        for day in (date(2026, 10, 22), date(2026, 10, 28), date(2026, 11, 3), date(2027, 1, 2)):
            self.assertTrue(health.is_due(day))
        for day in (date(2026, 10, 21), date(2026, 10, 23), date(2026, 11, 1)):
            self.assertFalse(health.is_due(day))
        self.assertTrue(health.is_due(date(2026, 10, 23), manual=True))

    def test_public_keys_only(self):
        for key in ("sb_publishable_synthetic", legacy()):
            self.assertEqual(health.validate_key(key), key)
        for key in ("", "sb_secret_synthetic", legacy("service_role"), legacy(project="cghlkqeqiokjqdionrkc"), "a.invalid.c", " key "):
            with self.assertRaises(ValueError):
                health.validate_key(key)

    def test_fixed_get_without_bearer(self):
        opener = FakeOpener([b"true"])
        health.check("sb_publishable_synthetic", opener)
        req, timeout = opener.requests[0]
        self.assertEqual(req.full_url, health.ENDPOINT)
        self.assertEqual(req.get_method(), "GET")
        self.assertIsNone(req.data)
        self.assertIsNone(req.get_header("Authorization"))
        self.assertEqual(timeout, 15)

    def test_strict_boolean(self):
        for body in (b"false", b'"true"', b"[true]", b"true" + b"x" * 100, b"true" + b" " * 100):
            with self.assertRaises(RuntimeError):
                health.check(legacy(), FakeOpener([body]))

    def test_bounded_retry(self):
        opener = FakeOpener([error.URLError("network"), error.URLError("network"), b"true"])
        delays = []
        health.check(legacy(), opener, delays.append)
        self.assertEqual(delays, [1, 2])
        self.assertEqual(len(opener.requests), 3)

    def test_no_retry_or_redirect_for_configuration_errors(self):
        for status in (302, 401, 403, 404):
            opener = FakeOpener([error.HTTPError(health.ENDPOINT, status, "failed", {}, io.BytesIO(b"private body"))])
            with self.assertRaises(RuntimeError):
                health.check(legacy(), opener, lambda _: self.fail("must not retry"))
            self.assertEqual(len(opener.requests), 1)
        self.assertIsNone(health.NoRedirect().redirect_request(None, None, 302, "", {}, "https://other.invalid"))


if __name__ == "__main__":
    unittest.main()
