package com.minhacamera

/** Fonte única dos destaques visíveis em Sobre e Novidades. */
object PhoneReleaseHighlights {
    data class Entry(val version: String, val code: Int, val title: String, val detail: String)
    val latest = listOf(
        Entry("1.4.78", 88, "Versionamento e CI corrigidos", "Versionamento 1.4.78+88 sincronizado e teste de CI desatualizado corrigido; mantida a captura JPEG via PixelCopy da 1.4.77. Reprodução RTSP Media3 e transcodificação MP4 ainda pendentes."),
        Entry("1.4.77", 87, "Captura JPEG RTSP", "Adicionada captura manual de quadros RTSP em JPEG via PixelCopy e MediaStore no Android 10+; gravação RTSP em MP4 e Media3 ainda pendentes."),
        Entry("1.4.76", 86, "Build APK 73 corrigido", "Corrigido registro antecipado de testProtocol no Gradle, que impedia compilar os APKs; novo teste CI verifica a ordem das tarefas. Mantidos FPS, lanterna remota e diagnóstico H.264."),
    )
    fun history(): String = latest.joinToString("\n\n") { "${it.version}+${it.code}\n• ${it.detail}" }
}
