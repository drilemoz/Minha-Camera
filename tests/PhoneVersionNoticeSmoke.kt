package com.minhacamera

fun main() {
    check(PhoneVersionNotice.message("1.4.71", "1.4.71") == null)
    check(PhoneVersionNotice.message("1.4.71", "1.4.70")?.contains("Versões diferentes") == true)
    check(PhoneVersionNotice.message("1.4.71", "1.4.72")?.contains("Versões diferentes") == true)
    check(PhoneVersionNotice.message("1.4.71", null)?.contains("não informa") == true)
    check(PhoneVersionNotice.message("1.4.71", "oops")?.contains("desconhecida") == true)
    check(PhoneVersionNotice.message(null, "1.4.70") == null)
    check(PhoneVersionNotice.message("1.4.71", "1.4.70\r\nX-Fake:1")?.contains("desconhecida") == true)
    println("OK: 7 cenários de versão igual/diferente/ausente")
}
