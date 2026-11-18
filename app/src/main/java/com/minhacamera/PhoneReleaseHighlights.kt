package com.minhacamera

/** Fonte única dos destaques visíveis em Sobre e Novidades. */
object PhoneReleaseHighlights {
    data class Entry(val version: String, val code: Int, val title: String, val detail: String)
    val latest = listOf(
        Entry("1.4.83", 93, "Diagnóstico e espera da câmera", "Painel de abertura com relógio e previsão. Diagnóstico do player IP reúne conexão, visualização, captura e gravação."),
        Entry("1.4.82", 92, "Uma lente em tela cheia", "Escolha a lente em Imagem e mantenha a seleção em tela cheia. A gravação mantém o fluxo completo."),
        Entry("1.4.81", 91, "Atualizações pelo GitHub", "Busca automática, progresso, notas de versão, pausar e retomar. A instalação passa pela confirmação do Android."),
    )
    fun history(): String = latest.joinToString("\n\n") { "${it.version}+${it.code}\n• ${it.detail}" }
}
