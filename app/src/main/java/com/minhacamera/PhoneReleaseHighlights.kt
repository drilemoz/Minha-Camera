package com.minhacamera

/** Fonte única dos destaques visíveis em Sobre e Novidades. */
object PhoneReleaseHighlights {
    data class Entry(val version: String, val code: Int, val title: String, val detail: String)
    val latest = listOf(
        Entry("1.4.90", 100, "Ouvir e mover a câmera", "Escuta do áudio da câmera IP e controle de giro por ONVIF na rede local."),
        Entry("1.4.89", 99, "Troca de lente sem espera", "Quando o vídeo não informa as dimensões, a lente escolhida usa o recorte padrão de duas imagens 16:9 empilhadas."),
        Entry("1.4.88", 98, "Tela cheia ao girar", "A lente continua selecionada durante a rotação. Se faltarem dimensões do vídeo, a imagem inteira permanece visível."),
    )
    fun history(): String = latest.joinToString("\n\n") { "${it.version}+${it.code}\n• ${it.detail}" }
}
