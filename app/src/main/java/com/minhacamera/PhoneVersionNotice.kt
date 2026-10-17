package com.minhacamera

/** Não inferir incompatibilidade pela versão: informar diferença sem bloquear uso. */
object PhoneVersionNotice {
    private val format = Regex("[0-9]{1,3}(\\.[0-9]{1,3}){2,3}")
    fun message(local: String?, remote: String?): String? {
        val mine = local?.trim().orEmpty()
        if (!format.matches(mine)) return null
        val other = remote?.trim().orEmpty()
        return when {
            other.isEmpty() -> "O outro celular não informa sua versão (pode ser antigo). A conexão continua normalmente."
            !format.matches(other) -> "O outro celular informou uma versão desconhecida. A conexão continua normalmente."
            mine == other -> null
            else -> "Versões diferentes: este celular $mine e o outro $other. Você pode continuar usando, mas alguns recursos podem variar."
        }
    }
}
