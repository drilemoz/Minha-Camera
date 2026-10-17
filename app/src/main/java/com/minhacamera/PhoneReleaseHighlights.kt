package com.minhacamera

/** Fonte única dos destaques visíveis em Sobre e Novidades. */
object PhoneReleaseHighlights {
    data class Entry(val version: String, val code: Int, val title: String, val detail: String)
    val latest = listOf(
        Entry("1.4.72", 82, "Cabeçalho e estabilidade", "Codec e Diagnóstico realmente na barra do título; zoom sem cartão redundante; recuperação progressiva do MJPEG parado ao usar tela apagada, com diagnóstico. H.264 permanece experimental."),
        Entry("1.4.71", 81, "Versões e compatibilidade", "Sobre e Novidades com histórico atualizado; aviso não bloqueante quando celulares conectados utilizam versões diferentes."),
        Entry("1.4.70", 80, "Receptor reorganizado", "Build 68 corrigido; nome único, Ao vivo com duração, indicadores de bateria/temperatura e codec com submenu junto ao diagnóstico."),
    )
    fun history(): String = latest.joinToString("\n\n") { "${it.version}+${it.code}\n• ${it.detail}" }
}
