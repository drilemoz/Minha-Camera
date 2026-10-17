package com.minhacamera

/** Cenários determinísticos da política de recuperação, sem hardware de câmera. */
fun main() {
    val expectedQuality = mapOf(1080 to 720, 768 to 720, 720 to 480, 480 to 360, 360 to 360)
    for ((quality, expected) in expectedQuality) {
        check(PhoneBackgroundRecovery.nextQuality(quality) == expected) { "Qualidade $quality" }
        check(PhoneBackgroundRecovery.nextQuality(quality) <= quality) { "Recuperação ampliou qualidade" }
    }
    val expectedFps = mapOf(30 to 15, 24 to 15, 15 to 10, 10 to 5, 5 to 5)
    for ((fps, expected) in expectedFps) {
        check(PhoneBackgroundRecovery.nextFps(fps) == expected) { "FPS $fps" }
        check(PhoneBackgroundRecovery.nextFps(fps) <= fps) { "Recuperação ampliou FPS" }
    }
    check((1..3).fold(1080) { q, _ -> PhoneBackgroundRecovery.nextQuality(q) } == 360)
    println("OK: recuperação com redução progressiva, sem escalar carga da câmera")
}
