package com.minhacamera

/** Fonte única dos destaques visíveis em Sobre e Novidades. */
object PhoneReleaseHighlights {
    data class Entry(val version: String, val code: Int, val title: String, val detail: String)
    val latest = listOf(
        Entry("1.4.81", 91, "Atualizações pelo GitHub", "Busca automática, progresso, notas de versão, pausar e retomar. A instalação passa pela confirmação do Android."),
        Entry("1.4.80", 90, "Formulário RTSP mais simples", "Acesso à câmera em uma seção, caminho automático ou sugestões sem abrir teclado e campo manual separado. O player não mudou."),
        Entry("1.4.79", 89, "Captura JPEG: compilação corrigida", "Build APK 77: import PixelCopy corrigido para android.view.PixelCopy e teste preventivo adicionado; captura JPEG permanece disponível no player RTSP. Testes Android pendentes."),
    )
    fun history(): String = latest.joinToString("\n\n") { "${it.version}+${it.code}\n• ${it.detail}" }
}
