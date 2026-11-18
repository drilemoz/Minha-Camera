"""Verifica a distribuição sem criar tags ou Releases de verdade."""
import hashlib
import json
import os
import re
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github/workflows/build-apk.yml"
SRC = ROOT / "app/src/main/java/com/minhacamera"
ABIS = ("universal", "armeabi-v7a", "arm64-v8a", "x86", "x86_64")
OFFICIAL = "drilemoz/Minha-Camera"

class AutoReleaseTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = self.folder = Path(self.tmp.name)
        tools = self.root / "bin"
        tools.mkdir()
        self.log = self.root / "fake-gh.log"
        self.response = self.root / "response.json"
        self.summary = self.root / "summary.md"
        gradle = (ROOT / "app/build.gradle.kts").read_text()
        folder = self.folder / "release"
        folder.mkdir()
        self.version = re.search(r'versionName\s*=\s*"([\d.]+)"', gradle).group(1)
        self.tag = "v" + self.version
        code = re.search(r'versionCode\s*=\s*(\d+)', gradle).group(1)
        cert = (ROOT / "docs/SIGNING_CERT_SHA256.txt").read_text().strip()
        entries, assets, sums = [], [], []
        for abi in ABIS:
            name = f"MinhaCamera-{self.version}-{abi}.apk"
            data = (abi + "-mock").encode()
            digest = hashlib.sha256(data).hexdigest()
            (folder / name).write_bytes(data)
            entries.append(dict(file=name, sha256=digest, size_bytes=len(data), certificate_sha256=cert))
            assets.append(dict(name=name, state="uploaded", size=len(data), digest="sha256:" + digest,
                               browser_download_url=f"https://github.com/{OFFICIAL}/releases/download/{self.tag}/{name}"))
            sums.append(f"{digest}  {name}")
        (folder / "apk-report.json").write_text(json.dumps(dict(identity=["com.minhacamera", code, self.version], apks=entries)))
        (folder / "SHA256SUMS").write_text("\n".join(sums) + "\n")
        self.published = dict(tag_name=self.tag, draft=False, assets=assets[:1],
                              zipball_url=f"https://api.github.com/repos/{OFFICIAL}/zipball/{self.tag}",
                              tarball_url=f"https://api.github.com/repos/{OFFICIAL}/tarball/{self.tag}")
        self.response.write_text(json.dumps(self.published))

        fakes = {
            "git": '''#!/usr/bin/env bash
case " $* " in
  *" --heads "*) printf '%s\trefs/heads/main\n' "$MOCK_HEAD"; exit 0 ;;
  *" --tags "*) if [[ ${MOCK_STATE} == tag-only ]]; then exit 0; fi; exit 2 ;;
  *) exit 7 ;;
esac
''',
            "gh": '''#!/usr/bin/env bash
printf '%s\n' "$*" >> "$MOCK_LOG"
case "$1 $2" in
  "release view")
    case "$MOCK_STATE" in existing) echo false; exit 0 ;; draft) echo true; exit 0 ;; esac
    exit 1 ;;
  "release create") exit 0 ;;
  api*) cat "$MOCK_RESPONSE" ;;
  *) exit 8 ;;
esac
''',
            "sleep": "#!/usr/bin/env bash\nexit 0\n",
        }
        for name, contents in fakes.items():
            executable = tools / name
            executable.write_text(contents)
            executable.chmod(0o755)
        self.env = dict(os.environ, PATH=f"{tools}:{os.environ['PATH']}", GITHUB_REPOSITORY=OFFICIAL,
                        GITHUB_REF="refs/heads/main", GITHUB_SHA="a"*40, MOCK_HEAD="a"*40,
                        MOCK_LOG=str(self.log), MOCK_RESPONSE=str(self.response), GITHUB_STEP_SUMMARY=str(self.summary))

    def run_release(self, state="new", **env):
        return subprocess.run(["bash", "scripts/publish_github_release.sh", str(self.folder / "release")], cwd=ROOT,
                              env=dict(self.env, MOCK_STATE=state, **env), capture_output=True, text=True)

    def test_push_and_manual_routes_share_a_verified_publish_job(self):
        workflow = WORKFLOW.read_text()
        publish = workflow.split("\n  publish:\n", 1)[1]
        conditions = re.findall(r"(?m)^ *if: (.*github\.event_name == 'push'.*)$", workflow)
        self.assertEqual(len(conditions), 2)
        self.assertEqual(conditions[0], conditions[1])
        for expression in ["refs/heads/main", OFFICIAL, "inputs.publish_release"]:
            self.assertIn(expression, conditions[0])
        self.assertIn("needs: build", publish)
        self.assertIn("default: true", workflow)

    def test_new_release_uploads_only_direct_universal_apk_and_confirms_api_response(self):
        result = self.run_release()
        self.assertEqual(result.returncode, 0, result.stderr)
        log = self.log.read_text()
        self.assertIn(f"release create {self.tag}", log)
        self.assertIn(f"--target {self.env['GITHUB_SHA']}", log)
        create = next(line for line in log.splitlines() if line.startswith("release create "))
        self.assertEqual(create.count(".apk"), 1)
        self.assertIn(f"MinhaCamera-{self.version}-universal.apk", create)
        self.assertNotIn("SHA256SUMS", create)
        self.assertNotIn(".zip", create)
        for abi in ABIS[1:]:
            self.assertNotIn(f"MinhaCamera-{self.version}-{abi}.apk", create)
        self.assertIn(f"api repos/{OFFICIAL}/releases/tags/{self.tag}", log)
        self.assertIn("APK universal", self.summary.read_text())

    def test_existing_release_is_verified_and_never_overwritten(self):
        result = self.run_release(state="existing")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn("release create", self.log.read_text())
        self.assertIn("Nenhum APK foi substituído", self.summary.read_text())

    def test_superseded_build_skips_publication(self):
        result = self.run_release(MOCK_HEAD="b"*40)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("revisão anterior", result.stdout)
        self.assertFalse(self.log.exists())

    def test_missing_digest_does_not_report_a_successful_update(self):
        self.published["assets"][0]["digest"] = None
        self.response.write_text(json.dumps(self.published))
        result = self.run_release()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("SHA-256 indisponível", result.stderr)
        self.assertFalse(self.summary.exists())

    def test_mismatched_digest_does_not_report_a_successful_update(self):
        self.published["assets"][0]["digest"] = "sha256:" + "f" * 64
        self.response.write_text(json.dumps(self.published))
        result = self.run_release()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("SHA-256 divergente", result.stderr)
        self.assertFalse(self.summary.exists())

    def test_extra_uploaded_zip_is_rejected_but_generated_source_links_are_not_assets(self):
        self.published["assets"].append(dict(name="extra.zip"))
        self.response.write_text(json.dumps(self.published))
        result = self.run_release()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("exclusivamente o APK universal", result.stderr)
        self.assertFalse(self.summary.exists())

    def test_draft_or_tag_only_are_not_replaced(self):
        for state in ("draft", "tag-only"):
            with self.subTest(state=state):
                result = self.run_release(state=state)
                self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("release create", self.log.read_text())

    def test_app_does_not_report_current_when_release_is_missing(self):
        manager = (SRC / "GitHubUpdateManager.kt").read_text()
        client = (SRC / "GitHubUpdateClient.kt").read_text()
        self.assertIn("catalog.latestPublished == null -> publish(UpdateState(UpdatePhase.UNPUBLISHED", manager)
        self.assertIn("UpdatePhase.AWAITING_APK", manager)
        self.assertIn("POLL_MS = 2 * 60_000L", manager)
        self.assertIn('setRequestProperty("If-None-Match", etag)', client)
        self.assertIn("HTTP_NOT_MODIFIED", client)

if __name__ == "__main__":
    unittest.main()
