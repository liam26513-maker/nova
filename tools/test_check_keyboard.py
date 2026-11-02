"""Prove di tools/check_keyboard.py (si lanciano con: python -m unittest tools/test_check_keyboard.py)."""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import check_keyboard as ck  # noqa: E402

OK = '<manifest package="it.manlio.nova.keyboard"><uses-permission android:name="android.permission.VIBRATE"/></manifest>'


class ManifestTest(unittest.TestCase):
    def test_clean_manifest_passes(self):
        self.assertEqual([], ck.check_manifest(OK))

    def test_internet_is_refused(self):
        bad = OK.replace("VIBRATE", "INTERNET")
        self.assertEqual(1, len(ck.check_manifest(bad)))

    def test_network_state_and_wifi_state_are_refused(self):
        for perm in ("ACCESS_NETWORK_STATE", "ACCESS_WIFI_STATE"):
            self.assertEqual(1, len(ck.check_manifest(OK.replace("VIBRATE", perm))), perm)

    def test_wrong_package_is_refused(self):
        self.assertTrue(ck.check_manifest(OK.replace("it.manlio.nova.keyboard", "helium314.keyboard")))

    def test_missing_build_dir_fails(self):
        self.assertEqual(1, ck.main(["x", "/nonexistent-dir"]))


if __name__ == "__main__":
    unittest.main()
