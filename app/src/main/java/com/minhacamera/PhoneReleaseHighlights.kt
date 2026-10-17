package com.minhacamera

/** Fonte única dos destaques visíveis em Sobre e Novidades. */
object PhoneReleaseHighlights {
    data class Entry(val version: String, val code: Int, val title: String, val detail: String)
    val latest = listOf(
        Entry("1.4.73", 83, "Diagnóstico e transferência", "Investigação detalhada da captura em primeiro plano e no serviço com tela apagada; progresso e cancelamento seguros nos downloads remotos; corrigida a execução de testes no GitHub Actions."),
        Entry("1.4.72", 82, "Cabeçalho e estabilidade", "Codec e Diagnóstico realmente na barra do título; zoom sem cartão redundante; recuperação progressiva do MJPEG parado ao usar tela apagada, com diagnóstico. H.264 permanece experimental."),
        Entry("1.4.71", 81, "Versões e compatibilidade", "Sobre e Novidades com histórico atualizado; aviso não bloqueante quando celulares conectados utilizam versões diferentes."),
    )
    fun history(): String = latest.joinToString("\n\n") { "${it.version}+${it.code}\n• ${it.detail}" }
}
