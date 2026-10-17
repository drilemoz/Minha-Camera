package com.minhacamera

/** Fonte única dos destaques visíveis em Sobre e Novidades. */
object PhoneReleaseHighlights {
    data class Entry(val version: String, val code: Int, val title: String, val detail: String)
    val latest = listOf(
        Entry("1.4.74", 84, "FPS e lanterna remota", "MJPEG em segundo plano respeita a meta de até 30 FPS em condições normais, com proteção térmica; lanterna traseira remota por 30s, 2min ou durante REC; diagnóstico do encoder H.264 com motivos de rejeição."),
        Entry("1.4.73", 83, "Diagnóstico e transferência", "Investigação detalhada da captura em primeiro plano e no serviço com tela apagada; progresso e cancelamento seguros nos downloads remotos; corrigida a execução de testes no GitHub Actions."),
        Entry("1.4.72", 82, "Cabeçalho e estabilidade", "Codec e Diagnóstico realmente na barra do título; zoom sem cartão redundante; recuperação progressiva do MJPEG parado ao usar tela apagada, com diagnóstico. H.264 permanece experimental."),
    )
    fun history(): String = latest.joinToString("\n\n") { "${it.version}+${it.code}\n• ${it.detail}" }
}
