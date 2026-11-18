"""Limite de distribuição compatível com o atualizador a partir da 1.4.81."""

from zipfile import ZIP_DEFLATED

MAX_UPDATER_APK_BYTES = 200 * 1024 * 1024


def require_updater_apk_size(size):
    if not isinstance(size, int) or not 0 < size <= MAX_UPDATER_APK_BYTES:
        raise ValueError(
            f"Tamanho do APK universal fora dos limites do atualizador: "
            f"{size} bytes; máximo {MAX_UPDATER_APK_BYTES} bytes (200 MiB)."
        )


def check_native_compression(entries, apk):
    for entry in entries:
        if entry.filename.startswith("lib/") and entry.filename.endswith(".so"):
            if entry.compress_type != ZIP_DEFLATED:
                raise ValueError(f"Biblioteca nativa sem compressão: {apk}, {entry.filename}")
