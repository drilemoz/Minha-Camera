#!/usr/bin/env python3
"""Ground a GitHub Release in the same commit and stable signer used by the build."""
import argparse
import hashlib
import json
import re
from pathlib import Path

from release_apk_policy import require_updater_apk_size

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
    # Não iniciar upload de um APK que as instalações existentes vão ignorar.
    universal = folder / f"MinhaCamera-{version}-universal.apk"
    require_updater_apk_size(universal.stat().st_size)
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

def verify_published(folder: Path, release_path: Path, existing: bool = False):
    report = json.loads((folder / "apk-report.json").read_text())
    version = report["identity"][2]
    release = json.loads(release_path.read_text())
    tag = f"v{version}"
    assert release["tag_name"] == tag and release["draft"] is False, "Release não publicada"
    prefix = f"https://github.com/drilemoz/Minha-Camera/releases/download/{tag}/"
    name = f"MinhaCamera-{version}-universal.apk"
    assets = release["assets"]
    # GitHub generates source-code links separately. Only the directly attached APK counts here.
    assert len(assets) == 1 and assets[0]["name"] == name, "A Release deve anexar exclusivamente o APK universal"
    asset = assets[0]
    expected = next(entry for entry in report["apks"] if entry["file"] == name)
    assert asset["state"] == "uploaded" and asset["browser_download_url"] == prefix + name, name
    require_updater_apk_size(asset["size"])
    digest = asset.get("digest") or ""
    assert re.fullmatch(r"sha256:[0-9a-f]{64}", digest), f"SHA-256 indisponível: {name}"
    if not existing:
        assert asset["size"] == expected["size_bytes"], f"Tamanho divergente: {name}"
        assert digest == "sha256:" + expected["sha256"], f"SHA-256 divergente: {name}"

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("folder", type=Path)
    parser.add_argument("--verify-published", type=Path)
    parser.add_argument("--existing", action="store_true", help="Exige assets válidos, sem comparar novo build com anterior.")
    args = parser.parse_args()
    if args.verify_published:
        verify_published(args.folder, args.verify_published, args.existing)
    else:
        prepare(args.folder)
