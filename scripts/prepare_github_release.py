#!/usr/bin/env python3
"""Ground a GitHub Release in the same commit and stable signer used by the build."""
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ABIS = ("universal", "armeabi-v7a", "arm64-v8a", "x86", "x86_64")

def prepare(folder: Path, root: Path = ROOT):
    report = json.loads((folder / "apk-report.json").read_text())
    gradle = (root / "app/build.gradle.kts").read_text()
    version = re.search(r'versionName\s*=\s*"(\d+\.\d+\.\d+)"', gradle).group(1)
    code = re.search(r'versionCode\s*=\s*(\d+)', gradle).group(1)
    cert = (root / "docs/SIGNING_CERT_SHA256.txt").read_text().strip().lower().replace(":", "")
    assert report["identity"] == ["com.minhacamera", code, version]
    expected = {f"MinhaCamera-{version}-{abi}.apk" for abi in ABIS}
    records = report["apks"]
    assert len(records) == 5 and {r["file"] for r in records} == expected
    assert {f.name for f in folder.glob("*.apk")} == expected
    for entry in records:
        apk = folder / entry["file"]
        assert apk.is_file() and apk.stat().st_size == entry["size_bytes"]
        assert entry["certificate_sha256"].lower().replace(":", "") == cert
        with apk.open("rb") as stream:
            assert hashlib.file_digest(stream, "sha256").hexdigest() == entry["sha256"]
    notes = (root / "docs/RELEASE_NOTES.md").read_text()
    title = re.escape(f"# Minha Câmera {version} (versionCode {code})")
    block = re.search(rf"(?m)^{title}\n(?P<body>[\s\S]*?)(?=^# |\Z)", notes)
    assert block and block["body"].strip(), "Notas da versão não encontradas"
    (folder / "notes.md").write_text(block["body"].strip() + "\n")

if __name__ == "__main__":
    prepare(Path(sys.argv[1]))
