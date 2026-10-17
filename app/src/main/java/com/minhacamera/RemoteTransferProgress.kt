package com.minhacamera

/** Barra indeterminada quando o servidor não informa tamanho; 100% apenas no fim. */
internal object RemoteTransferProgress {
    fun percent(bytes: Long, total: Long): Int? = if (total <= 0L) null
        else ((bytes.coerceAtLeast(0L) * 100.0 / total).toInt()).coerceIn(0, 99)
    private fun size(bytes: Long): String = if (bytes < 1024 * 1024)
        "${bytes / 1024} KB" else "%.1f MB".format(java.util.Locale.US, bytes / 1048576.0)
    fun label(bytes: Long, total: Long): String = if (total > 0)
        "${size(bytes)} de ${size(total)} · ${percent(bytes, total)}%"
        else "${size(bytes)} transferidos · tamanho desconhecido"
}
