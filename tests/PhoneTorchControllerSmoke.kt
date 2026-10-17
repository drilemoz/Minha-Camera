package com.minhacamera

import android.hardware.Camera
import android.os.Handler
import android.os.Looper

fun main() {
    val main = Handler(Looper.getMainLooper())
    var recording = false
    var heat = PhoneHeatState.NORMAL
    val controller = PhoneTorchController(main, { recording }, { heat })
    val camera = Camera()
    controller.attach(camera, true)
    check(controller.supported && !controller.enabled)
    check(controller.setMode("30") && controller.enabled && camera.activeFlash == "torch")
    main.advance(29_000)
    check(controller.enabled)
    main.advance(1_000)
    check(!controller.enabled && controller.mode == "off" && camera.activeFlash == "off")
    check(controller.setMode("120") && controller.enabled)
    check(controller.setMode("off") && !controller.enabled)
    check(controller.setMode("record") && !controller.enabled)
    recording = true
    controller.onStateChanged()
    check(controller.enabled)
    recording = false
    controller.onStateChanged()
    check(!controller.enabled && controller.mode == "record")
    recording = true
    controller.onStateChanged()
    heat = PhoneHeatState.HOT
    controller.onStateChanged()
    check(!controller.enabled && controller.mode == "off")
    check(!controller.setMode("30"))
    heat = PhoneHeatState.NORMAL
    check(!controller.setMode("unlimited"))
    check(controller.setMode("30"))
    controller.detach()
    check(!controller.enabled && !controller.supported && camera.activeFlash == "off")
    controller.attach(Camera(), false)
    check(!controller.supported && !controller.setMode("30"))
    controller.attach(Camera(listOf("off")), true)
    check(!controller.supported && !controller.setMode("30"))
    println("OK: lanterna Camera1: disponibilidade, 30s/120s, REC, off, HOT, troca de câmera e modo inválido")
}
