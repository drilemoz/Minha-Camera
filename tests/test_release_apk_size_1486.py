"""Regressão do limite do atualizador e da compactação nativa."""
import io
from pathlib import Path
import re
import unittest
from zipfile import ZIP_DEFLATED, ZIP_STORED, ZipFile

from scripts.release_apk_policy import (
    MAX_UPDATER_APK_BYTES,
    check_native_compression,
    require_updater_apk_size,
)

ROOT = Path(__file__).resolve().parents[1]


class ReleaseApkSizeTest(unittest.TestCase):
    def test_release_limit_matches_legacy_kotlin_updater(self):
        source = (ROOT / "app/src/main/java/com/minhacamera/GitHubUpdateCore.kt").read_text()
        match = re.search(r"MAX_APK_BYTES\s*=\s*(\d+)L\s*\*\s*1024L\s*\*\s*1024L", source)
        self.assertIsNotNone(match)
        self.assertEqual(MAX_UPDATER_APK_BYTES, int(match.group(1)) * 1024 * 1024)
        self.assertEqual(MAX_UPDATER_APK_BYTES, 209_715_200)
        require_updater_apk_size(MAX_UPDATER_APK_BYTES)
        for size in (0, 218_391_269, MAX_UPDATER_APK_BYTES + 1):
            with self.subTest(size=size), self.assertRaises(ValueError):
                require_updater_apk_size(size)

    def test_gradle_requests_compressed_jni_packaging(self):
        gradle = (ROOT / "app/build.gradle.kts").read_text()
        self.assertRegex(gradle, r"packaging\s*\{\s*jniLibs\s*\{\s*useLegacyPackaging\s*=\s*true")

    def test_zip_fixture_rejects_stored_jni_but_not_other_resources(self):
        for method in (ZIP_DEFLATED, ZIP_STORED):
            with self.subTest(method=method):
                data = io.BytesIO()
                with ZipFile(data, "w") as apk:
                    apk.writestr("lib/arm64-v8a/libvlc.so", b"so-test", compress_type=method)
                    apk.writestr("resources.arsc", b"res-test", compress_type=ZIP_STORED)
                with ZipFile(data) as apk:
                    if method == ZIP_STORED:
                        with self.assertRaisesRegex(ValueError, "Biblioteca nativa sem compressão"):
                            check_native_compression(apk.infolist(), "fixture.apk")
                    else:
                        check_native_compression(apk.infolist(), "fixture.apk")


if __name__ == "__main__":
    unittest.main()
