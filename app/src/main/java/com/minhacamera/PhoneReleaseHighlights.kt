package com.minhacamera

/** Fonte única dos destaques visíveis em Sobre e Novidades. */
object PhoneReleaseHighlights {
    data class Entry(val version: String, val code: Int, val title: String, val detail: String)
    val latest = listOf(
        Entry("1.4.76", 86, "Build APK 73 corrigido", "Corrigido registro antecipado de testProtocol no Gradle, que impedia compilar os APKs; novo teste CI verifica a ordem das tarefas. Mantidos FPS, lanterna remota e diagnóstico H.264."),
        Entry("1.4.75", 85, "Build APK 72 corrigido", "A suíte Gradle da lanterna foi isolada para evitar a classe SystemClock duplicada e incluir PhoneTorchController no teste; mantém FPS e lanterna remota da 1.4.74."),
        Entry("1.4.74", 84, "FPS e lanterna remota", "MJPEG em segundo plano respeita a meta de até 30 FPS em condições normais, com proteção térmica; lanterna traseira remota por 30s, 2min ou durante REC; diagnóstico do encoder H.264 com motivos de rejeição."),
    )
    fun history(): String = latest.joinToString("\n\n") { "${it.version}+${it.code}\n• ${it.detail}" }
}
