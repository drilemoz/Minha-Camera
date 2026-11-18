package com.minhacamera

/** Fonte única dos destaques visíveis em Sobre e Novidades. */
object PhoneReleaseHighlights {
    data class Entry(val version: String, val code: Int, val title: String, val detail: String)
    val latest = listOf(
        Entry("1.4.88", 98, "Tela cheia ao girar", "A lente continua selecionada durante a rotação. Se faltarem dimensões do vídeo, a imagem inteira permanece visível."),
        Entry("1.4.87", 97, "Verificação de build corrigida", "A verificação de publicação não depende mais da ordem dos testes. O APK universal continua compacto."),
        Entry("1.4.86", 96, "Atualização mais leve", "O APK universal usa bibliotecas nativas compactadas para caber no limite do atualizador do GitHub."),
    )
    fun history(): String = latest.joinToString("\n\n") { "${it.version}+${it.code}\n• ${it.detail}" }
}
