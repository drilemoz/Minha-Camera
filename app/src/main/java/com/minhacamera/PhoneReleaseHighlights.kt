package com.minhacamera

/** Fonte única dos destaques visíveis em Sobre e Novidades. */
object PhoneReleaseHighlights {
    data class Entry(val version: String, val code: Int, val title: String, val detail: String)
    val latest = listOf(
        Entry("1.4.79", 89, "Captura JPEG: compilação corrigida", "Build APK 77: import PixelCopy corrigido para android.view.PixelCopy e teste preventivo adicionado; captura JPEG permanece disponível no player RTSP. Testes Android pendentes."),
        Entry("1.4.78", 88, "Versionamento e CI corrigidos", "Versionamento 1.4.78+88 sincronizado e teste de CI desatualizado corrigido; mantida captura JPEG. Media3 e transcodificação MP4 pendentes."),
        Entry("1.4.77", 87, "Captura JPEG RTSP", "Adicionada captura manual RTSP em JPEG via PixelCopy e MediaStore no Android 10+; gravação RTSP em MP4 e Media3 pendentes."),
    )
    fun history(): String = latest.joinToString("\n\n") { "${it.version}+${it.code}\n• ${it.detail}" }
}
