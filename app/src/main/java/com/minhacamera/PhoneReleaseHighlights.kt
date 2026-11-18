package com.minhacamera

/** Fonte única dos destaques visíveis em Sobre e Novidades. */
object PhoneReleaseHighlights {
    data class Entry(val version: String, val code: Int, val title: String, val detail: String)
    val latest = listOf(
        Entry("1.4.86", 96, "Atualização mais leve", "O APK universal usa bibliotecas nativas compactadas para caber no limite do atualizador do GitHub."),
        Entry("1.4.85", 95, "Um APK por Release", "As versões públicas anexam somente o APK universal assinado, pronto para instalar."),
        Entry("1.4.84", 94, "Releases automáticas", "Builds assinados na main passaram a publicar os APKs automaticamente. O atualizador diferencia build de Release."),
    )
    fun history(): String = latest.joinToString("\n\n") { "${it.version}+${it.code}\n• ${it.detail}" }
}
