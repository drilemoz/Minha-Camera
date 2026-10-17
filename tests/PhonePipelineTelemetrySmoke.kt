package com.minhacamera

fun main() {
    val p = PhonePipelineTelemetry("test")
    p.reset(1000)
    p.callback(1020)
    p.accepted(1030)
    p.worker(1040)
    p.published(1050)
    check(p.publishAge(1150) == 100L)
    val first = p.snapshot(1150)
    check("callbacks=1" in first && "publicados=1" in first && "publicacaoHa=100ms" in first)
    p.failed("CameraAccessException")
    check("falhas=1" in p.snapshot(1200))
    println("OK: etapas e idades de captura, worker, publicação e falha")
    val t = RemoteTransferProgress
    check(t.percent(50, 100) == 50)
    check(t.percent(0, -1) == null)
    check(t.percent(120, 100) == 99)
    check("50%" in t.label(50, 100))
    check("tamanho desconhecido" in t.label(10, -1))
    println("OK: progresso remoto com tamanho desconhecido, limites e transferência parcial")
}
