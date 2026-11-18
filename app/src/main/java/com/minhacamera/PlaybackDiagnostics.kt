package com.minhacamera

import kotlin.math.ceil

/** Estimates image wait separately from the measured time budget of a connection attempt. */
internal object PlaybackTiming {
    const val ATTEMPT_LIMIT_MS = 15_000L

    data class Progress(val percentOfLimit: Int, val remainingEstimateSeconds: Int?, val overdue: Boolean)

    fun progress(elapsedMs: Long, estimateMs: Long?, firstFrame: Boolean = false): Progress {
        val elapsed = elapsedMs.coerceAtLeast(0L)
        val estimate = estimateMs?.takeIf { it in 1..ATTEMPT_LIMIT_MS }
        if (firstFrame) return Progress(100, 0, false)
        val percent = (elapsed.coerceAtMost(ATTEMPT_LIMIT_MS) * 100 / ATTEMPT_LIMIT_MS).toInt().coerceAtMost(99)
        val remaining = estimate?.takeIf { it > elapsed }?.let { ceil((it - elapsed) / 1000.0).toInt() }
        return Progress(percent, remaining, estimate != null && elapsed >= estimate)
    }

    fun estimate(samples: List<Long>): Long? {
        val valid = samples.filter { it in 1..ATTEMPT_LIMIT_MS }.sorted()
        if (valid.isEmpty()) return null
        val index = valid.size / 2
        return if (valid.size % 2 == 1) valid[index] else (valid[index - 1] + valid[index]) / 2
    }

    fun addSample(samples: List<Long>, elapsedMs: Long): List<Long> =
        if (elapsedMs !in 1..ATTEMPT_LIMIT_MS) samples.takeLast(5) else (samples + elapsedMs).takeLast(5)
}

internal class PlaybackJournal(private val limit: Int = 160) {
    private val timeline = ArrayDeque<String>()
    var failures = 0
        private set

    fun append(elapsedMs: Long, message: String, failed: Boolean = false) {
        if (failed) failures++
        val kind = if (failed) " · ERRO" else ""
        timeline.addLast("+${elapsedMs.coerceAtLeast(0)} ms$kind · ${mask(message).replace('\n', ' ').replace('\r', ' ').take(360)}")
        while (timeline.size > limit) timeline.removeFirst()
    }

    fun entries(): List<String> = timeline.toList()

    companion object {
        private val url = Regex("""(?i)\b(?:rtsp|rtsps|http|https|rtmp|rtmps|rtp|udp)://[^\s"'<>]+""")
        private val secret = Regex("""(?i)\b(Authorization:|WWW-Authenticate:|Password:|(?:token|password|passwd|pwd|secret|auth|api[_-]?key|key)=)[^\r\n]+""")

        fun mask(message: String): String =
            secret.replace(url.replace(message, "[endereço protegido]")) { "${it.groupValues[1]} [oculto]" }
    }
}
