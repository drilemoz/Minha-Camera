import hashlib
import importlib.util
import json
import re
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main"
SRC = APP / "java/com/minhacamera"
NS = "{http://schemas.android.com/apk/res/android}"

def source(filename):
    return (SRC / filename).read_text()

spec = importlib.util.spec_from_file_location("prepare_github_release", ROOT / "scripts/prepare_github_release.py")
prepare_release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare_release)

class UpdateContractTest(unittest.TestCase):
    def test_source_is_fixed_and_connected_to_foreground(self):
        self.assertIn('const val REPOSITORY = "drilemoz/Minha-Camera"', source("GitHubUpdateCore.kt"))
        self.assertIn("registerActivityLifecycleCallbacks(updates)", source("CameraApplication.kt"))
        manager = source("GitHubUpdateManager.kt")
        for text in ["override fun onActivityStarted", "override fun onActivityStopped", "main.removeCallbacks(periodic)",
                     "15 * 60_000L", "REENTRY_COOLDOWN_MS = 60_000L", 'getBoolean("auto_download", true)']:
            self.assertIn(text, manager)
        self.assertIn("GitHubUpdatesActivity::class.java", source("SettingsActivity.kt"))
        self.assertNotIn("EditText", source("GitHubUpdatesActivity.kt"))

    def test_download_and_fileprovider_are_constrained(self):
        client = source("GitHubUpdateClient.kt")
        for text in ["NET_CAPABILITY_VALIDATED", "network.openConnection", "destination.protocol != \"https\"",
                     "matchingContentRange", "digest(partial, checkpoint)", "verifyApk(partial, release)",
                     "candidate.packageName", "longVersionCode", "verifyInstalledSigner", "BuildConfig.UPDATE_SIGNER_SHA256"]:
            self.assertIn(text, client)
        self.assertNotIn("bindProcessToNetwork", client)
        manifest = ET.parse(APP / "AndroidManifest.xml").getroot()
        provider = next(p for p in manifest.findall("./application/provider") if p.get(NS + "name") == ".UpdateApkProvider")
        self.assertEqual(provider.get(NS + "exported"), "false")
        self.assertEqual(provider.get(NS + "grantUriPermissions"), "true")
        paths = ET.parse(APP / "res/xml/github_update_paths.xml").getroot()
        self.assertEqual([(e.tag, e.attrib["path"]) for e in paths], [("cache-path", "github-updates/ready/")])
        self.assertIn("canRequestPackageInstalls()", source("GitHubUpdatesActivity.kt"))

    def test_publication_requires_verified_job_and_explicit_input(self):
        workflow = (ROOT / ".github/workflows/build-apk.yml").read_text()
        publish = workflow.split("\n  publish:\n", 1)[1]
        for text in ["inputs.publish_release", "needs: build", "contents: write",
                     "scripts/prepare_github_release.py", "sha256sum --check", 'gh release create "$tag"']:
            self.assertIn(text, publish)
        self.assertNotIn("--clobber", publish)

    def test_preparation_accepts_only_matching_report_hash_identity_and_signer(self):
        with tempfile.TemporaryDirectory() as temporary:
            folder = Path(temporary)
            gradle = (ROOT / "app/build.gradle.kts").read_text()
            version = re.search(r'versionName\s*=\s*"([^"]+)"', gradle).group(1)
            code = re.search(r'versionCode\s*=\s*(\d+)', gradle).group(1)
            cert = (ROOT / "docs/SIGNING_CERT_SHA256.txt").read_text().strip()
            entries = []
            for abi in prepare_release.ABIS:
                apk = folder / f"MinhaCamera-{version}-{abi}.apk"
                data = (abi + version).encode()
                apk.write_bytes(data)
                entries.append(dict(file=apk.name, size_bytes=len(data),
                                    sha256=hashlib.sha256(data).hexdigest(), certificate_sha256=cert))
            report = dict(identity=["com.minhacamera", code, version], apks=entries)
            (folder / "apk-report.json").write_text(json.dumps(report))
            prepare_release.prepare(folder)
            notes = (folder / "notes.md").read_text()
            self.assertTrue(notes.strip())
            self.assertNotIn("# Minha Câmera", notes)
            apk.write_bytes(b"modified")
            with self.assertRaises(AssertionError):
                prepare_release.prepare(folder)

if __name__ == "__main__":
    unittest.main()
