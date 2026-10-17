package android.os
object SystemClock {
    var nowMs = 5000L
    @JvmStatic fun elapsedRealtime(): Long = nowMs
}
class Looper {
    companion object {
        private val main = Looper()
        @JvmStatic fun getMainLooper(): Looper = main
        @JvmStatic fun myLooper(): Looper = main
    }
}
class Handler(val looper: Looper) {
    private data class Queued(val runnable: Runnable, val at: Long)
    private val queue = mutableListOf<Queued>()
    fun post(runnable: Runnable): Boolean { runnable.run(); return true }
    fun postDelayed(runnable: Runnable, delay: Long): Boolean { queue.add(Queued(runnable, SystemClock.nowMs + delay)); return true }
    fun removeCallbacks(runnable: Runnable) { queue.removeAll { it.runnable == runnable } }
    fun advance(millis: Long) {
        val end = SystemClock.nowMs + millis
        while (true) {
            val q = queue.filter { it.at <= end }.minByOrNull { it.at } ?: break
            queue.remove(q)
            SystemClock.nowMs = q.at
            q.runnable.run()
        }
        SystemClock.nowMs = end
    }
}
