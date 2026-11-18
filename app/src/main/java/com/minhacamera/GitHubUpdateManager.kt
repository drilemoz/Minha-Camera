package com.minhacamera

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.util.concurrent.Executors

internal enum class UpdatePhase {
    IDLE, CHECKING, CURRENT, UNPUBLISHED, AWAITING_APK, AVAILABLE, DOWNLOADING, VERIFYING, PAUSED, READY, ERROR
}

internal data class UpdateState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val release: GitHubUpdateRelease? = null,
    val bytes: Long = 0L,
    val rate: Long = 0L,
    val message: String? = null,
    val file: File? = null
)

/** One app-wide session. Polling does not require the camera list to remain open. */
internal class GitHubUpdateManager(app: Application) : Application.ActivityLifecycleCallbacks {
    private enum class Command { NONE, PAUSE, STOP, RESTART }
    private class InterruptedTransfer : Exception()

    private val prefs = app.getSharedPreferences("github_updater", Application.MODE_PRIVATE)
    private val client = GitHubUpdateClient(app)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val listeners = linkedSetOf<(UpdateState) -> Unit>()
    private val promptSeen = mutableSetOf<String>()
    private var lastCheck: Long? = null
    private var aliveScreens = 0
    private val periodic = object : Runnable {
        override fun run() {
            if (aliveScreens <= 0) return
            check()
            main.postDelayed(this, POLL_MS)
        }
    }
    @Volatile private var command = Command.NONE
    var state = UpdateState()
        private set
    val autoDownload: Boolean get() = prefs.getBoolean("auto_download", true)

    fun observe(listener: (UpdateState) -> Unit) {
        listeners.add(listener)
        listener(state)
    }

    fun remove(listener: (UpdateState) -> Unit) { listeners.remove(listener) }

    fun claimPrompt(key: String): Boolean = promptSeen.add(key)

    fun setAutoDownload(enabled: Boolean) {
        prefs.edit().putBoolean("auto_download", enabled).apply()
        // Enabling does not override a deliberate pause or stop.
        state.release?.takeIf { state.phase == UpdatePhase.AVAILABLE }?.let { maybeDownload(it) }
    }

    private fun publish(next: UpdateState) {
        state = next
        listeners.toList().forEach { it(next) }
    }

    fun check(manual: Boolean = false) {
        if (state.phase in busy || (!manual && state.phase == UpdatePhase.PAUSED)) return
        val now = SystemClock.elapsedRealtime()
        if (!manual && lastCheck?.let { now - it < REENTRY_COOLDOWN_MS } == true) return
        lastCheck = now
        val previous = state
        publish(previous.copy(phase = UpdatePhase.CHECKING, message = null))
        worker.execute {
            val result = runCatching { client.check(BuildConfig.VERSION_NAME) }
            main.post {
                result.fold(
                    onSuccess = { catalog ->
                        val release = catalog.release
                        when {
                            catalog.latestPublished == null -> publish(UpdateState(UpdatePhase.UNPUBLISHED, message =
                                "O app recebe APKs de GitHub Releases. Um build no Actions sozinho não é uma atualização."))
                            catalog.isAwaitingApk(BuildConfig.VERSION_NAME) -> publish(UpdateState(UpdatePhase.AWAITING_APK,
                                message = "A versão ${catalog.latestPublished} foi publicada, mas ainda não tem um APK universal compatível."))
                            release == null -> publish(UpdateState(UpdatePhase.CURRENT, message =
                                "Última versão publicada no GitHub: ${catalog.latestPublished}"))
                            previous.phase == UpdatePhase.READY && previous.release != null &&
                                previous.release.version == release.version &&
                                previous.release.sha256 == release.sha256 && previous.file?.isFile == true ->
                                publish(previous.copy(release = release))
                            prefs.getString("paused_version", null) == release.version -> publish(
                                UpdateState(UpdatePhase.PAUSED, release, client.partial(release).length())
                            )
                            else -> {
                                publish(UpdateState(UpdatePhase.AVAILABLE, release))
                                maybeDownload(release)
                            }
                        }
                    },
                    onFailure = { error ->
                        if (previous.phase in setOf(UpdatePhase.READY, UpdatePhase.PAUSED))
                            publish(previous.copy(message = updateError(error)))
                        else publish(UpdateState(UpdatePhase.ERROR, previous.release, message = updateError(error)))
                    }
                )
            }
        }
    }

    private fun maybeDownload(release: GitHubUpdateRelease) {
        if (aliveScreens > 0 && autoDownload && release.sha256 != null &&
            prefs.getString("stopped_version", null) != release.version) download()
    }

    fun pause() {
        if (state.phase != UpdatePhase.DOWNLOADING || command != Command.NONE) return
        command = Command.PAUSE
        publish(state.copy(message = "Pausando…"))
    }

    fun stop() {
        val release = state.release ?: return
        if (state.phase == UpdatePhase.CHECKING) return
        prefs.edit().putString("stopped_version", release.version).remove("paused_version").apply()
        if (state.phase in busy) {
            command = Command.STOP
            publish(state.copy(message = "Parando…"))
        } else {
            worker.execute { client.partial(release).delete() }
            publish(UpdateState(UpdatePhase.AVAILABLE, release, message = "Download parado. Você pode baixar novamente."))
        }
    }

    fun restart() {
        if (state.phase in setOf(UpdatePhase.DOWNLOADING, UpdatePhase.VERIFYING)) {
            command = Command.RESTART
            publish(state.copy(message = "Reiniciando…"))
        } else download(resume = false)
    }

    fun download(resume: Boolean = true) {
        val release = state.release ?: return
        if (state.phase in busy || release.sha256 == null) return
        prefs.edit().remove("stopped_version").remove("paused_version").apply()
        command = Command.NONE
        publish(UpdateState(UpdatePhase.DOWNLOADING, release, if (resume) state.bytes else 0L))
        worker.execute {
            val result = runCatching {
                client.download(release, resume,
                    checkpoint = { if (command != Command.NONE) throw InterruptedTransfer() },
                    progress = { bytes, speed ->
                        main.post {
                            if (state.phase == UpdatePhase.DOWNLOADING && command == Command.NONE)
                                publish(state.copy(bytes = bytes, rate = speed))
                        }
                    },
                    verifying = { main.post {
                        if (command == Command.NONE) publish(UpdateState(UpdatePhase.VERIFYING, release, release.size))
                    } }
                )
            }
            main.post {
                val requested = command
                command = Command.NONE
                when (requested) {
                    Command.PAUSE -> {
                        prefs.edit().putString("paused_version", release.version).apply()
                        publish(UpdateState(UpdatePhase.PAUSED, release, client.partial(release).length()))
                    }
                    Command.STOP, Command.RESTART -> {
                        worker.execute {
                            client.partial(release).delete()
                            result.getOrNull()?.delete()
                        }
                        publish(UpdateState(UpdatePhase.AVAILABLE, release,
                            message = if (requested == Command.STOP) "Download parado. Você pode baixar novamente." else null))
                        if (requested == Command.RESTART) download(resume = false)
                    }
                    Command.NONE -> result.fold(
                        onSuccess = { publish(UpdateState(UpdatePhase.READY, release, release.size, file = it)) },
                        onFailure = { error ->
                            if (error is UpdateProblem) worker.execute { client.partial(release).delete() }
                            publish(UpdateState(UpdatePhase.ERROR, release, message = updateError(error)))
                        }
                    )
                }
            }
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (++aliveScreens == 1) {
            check()
            state.release?.takeIf { state.phase == UpdatePhase.AVAILABLE }?.let { maybeDownload(it) }
            main.removeCallbacks(periodic)
            main.postDelayed(periodic, POLL_MS)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        if (--aliveScreens == 0) main.removeCallbacks(periodic)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    private companion object {
        const val POLL_MS = 2 * 60_000L
        const val REENTRY_COOLDOWN_MS = 60_000L
        val busy = setOf(UpdatePhase.CHECKING, UpdatePhase.DOWNLOADING, UpdatePhase.VERIFYING)
    }
}
