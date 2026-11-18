package com.minhacamera

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.util.Locale

class UpdateApkProvider : FileProvider()

internal object UpdateInstall {
    fun start(activity: AppCompatActivity, file: File?) {
        if (file == null || !file.isFile) {
            notify(activity, "O APK já não está no cache. Baixe novamente.")
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}")))
                notify(activity, "Autorize esta fonte e volte para tocar em Instalar APK.")
                return
            }
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.update-apk", file)
            activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                clipData = ClipData.newRawUri("APK", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) { notify(activity, "Não foi possível abrir o instalador do Android.") }
    }

    fun releases(context: Context, release: GitHubUpdateRelease?) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse(release?.releaseUrl ?: GitHubUpdateCore.RELEASES_URL)))
        } catch (_: Exception) { notify(context, "Não foi possível abrir o navegador.") }
    }

    private fun notify(context: Context, text: String) =
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}

internal class HomeUpdatePrompt(private val activity: MainActivity, private val updates: GitHubUpdateManager) {
    private var snackbar: Snackbar? = null
    private var prompt: androidx.appcompat.app.AlertDialog? = null
    private val render: (UpdateState) -> Unit = render@{ update ->
        val release = update.release ?: return@render
        if (activity.isFinishing || activity.isDestroyed) return@render
        if (update.phase == UpdatePhase.READY && updates.claimPrompt("ready-${release.version}")) {
            snackbar?.dismiss()
            prompt = MaterialAlertDialogBuilder(activity)
                .setTitle("Atualização pronta")
                .setMessage("O Minha Câmera ${release.version} já foi baixado e verificado.")
                .setPositiveButton("Ver atualização") { _, _ -> open() }
                .setNegativeButton("Agora não", null)
                .show()
        } else if (update.phase in setOf(UpdatePhase.AVAILABLE, UpdatePhase.DOWNLOADING) &&
            updates.claimPrompt("available-${release.version}")) {
            snackbar = Snackbar.make(activity.findViewById<View>(android.R.id.content),
                "Minha Câmera ${release.version} disponível", Snackbar.LENGTH_LONG)
                .setAction("Ver") { open() }
            snackbar?.show()
        }
    }
    fun attach() = updates.observe(render)
    fun detach() { updates.remove(render); snackbar?.dismiss(); prompt?.dismiss() }
    private fun open() = activity.startActivity(Intent(activity, GitHubUpdatesActivity::class.java))
}

class GitHubUpdatesActivity : AppCompatActivity() {
    private val manager get() = (application as CameraApplication).updates
    private lateinit var status: TextView
    private lateinit var detail: TextView
    private lateinit var notes: TextView
    private lateinit var progress: ProgressBar
    private lateinit var check: MaterialButton
    private lateinit var primary: MaterialButton
    private lateinit var stop: MaterialButton
    private lateinit var restart: MaterialButton
    private lateinit var toggle: MaterialSwitch
    private val observer: (UpdateState) -> Unit = { render(it) }
    private var shownNotesVersion: String? = null
    private var previousNotes: MaterialButton? = null

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.app_updates)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.app_background))
            setPadding(dp(18), dp(14), dp(18), dp(24))
        }
        setContentView(ScrollView(this).apply { setFillViewport(true); addView(content) })
        val panel = card(content)
        addText(panel, "Atualizações do Minha Câmera", 21)
        addText(panel, "Versão instalada: ${BuildConfig.VERSION_NAME}", 13)
        addText(panel, "Fonte oficial: GitHub · ${GitHubUpdateCore.REPOSITORY}", 13)
        toggle = MaterialSwitch(this).apply {
            text = "Baixar automaticamente"
            setMinimumHeight(dp(48))
            isChecked = manager.autoDownload
            setOnCheckedChangeListener { _, enabled -> manager.setAutoDownload(enabled) }
        }
        panel.addView(toggle)
        addText(panel, "Verifica ao abrir e a cada 2 min com o app em uso.", 13)
        check = button(panel, "Verificar agora") { manager.check(manual = true) }

        val transfer = card(content)
        status = addText(transfer, "", 18)
        status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        detail = addText(transfer, "", 14)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        transfer.addView(progress)
        primary = button(transfer, "", accent = true) {
            val state = manager.state
            when (state.phase) {
                UpdatePhase.READY -> if (state.file?.isFile == true) UpdateInstall.start(this, state.file) else manager.download(resume = false)
                UpdatePhase.DOWNLOADING -> manager.pause()
                else -> manager.download()
            }
        }
        stop = button(transfer, "Parar") {
            MaterialAlertDialogBuilder(this).setTitle("Parar o download?")
                .setMessage("A parte baixada será descartada. Esta versão não será baixada novamente sem você pedir.")
                .setNegativeButton(R.string.cancelar, null)
                .setPositiveButton("Parar") { _, _ -> manager.stop() }.show()
        }
        restart = button(transfer, "Reiniciar do zero") { manager.restart() }

        val releaseNotes = card(content)
        addText(releaseNotes, "O que mudou", 18)
        notes = addText(releaseNotes, "", 14)
        previousNotes = button(releaseNotes, "Ver versões puladas") {
            val history = manager.state.release?.previousNotes.orEmpty().joinToString("\n\n") {
                "${it.version}\n${GitHubUpdateCore.displayNotes(it.notes)}"
            }
            MaterialAlertDialogBuilder(this).setTitle("Versões puladas")
                .setMessage(history).setPositiveButton("Fechar", null).show()
        }
        button(releaseNotes, "Abrir no GitHub") { UpdateInstall.releases(this, manager.state.release) }
        addText(content, "O Android pede confirmação para instalar. Na primeira vez, pode pedir permissão para esta fonte.", 13)
    }

    override fun onStart() { super.onStart(); manager.observe(observer) }
    override fun onStop() { manager.remove(observer); super.onStop() }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun render(state: UpdateState) {
        val release = state.release
        val phase = state.phase
        val transferring = phase == UpdatePhase.DOWNLOADING || phase == UpdatePhase.VERIFYING
        val pending = phase == UpdatePhase.PAUSED
        val busy = transferring || phase == UpdatePhase.CHECKING
        val canDownload = release?.sha256 != null
        status.text = when (phase) {
            UpdatePhase.IDLE -> "Aguardando verificação"
            UpdatePhase.CHECKING -> "Verificando no GitHub…"
            UpdatePhase.CURRENT -> "Nenhuma versão mais nova publicada"
            UpdatePhase.UNPUBLISHED -> "Nenhuma Release publicada"
            UpdatePhase.AWAITING_APK -> "Nova Release ainda sem APK compatível"
            UpdatePhase.AVAILABLE -> "Versão ${release?.version} disponível"
            UpdatePhase.DOWNLOADING -> "Baixando ${release?.version}"
            UpdatePhase.VERIFYING -> "Verificando o APK…"
            UpdatePhase.PAUSED -> "Download pausado"
            UpdatePhase.READY -> "Versão ${release?.version} pronta para instalar"
            UpdatePhase.ERROR -> "Não foi possível concluir"
        }
        val total = release?.size ?: 0L
        val percent = if (total > 0L) (state.bytes * 100 / total).toInt().coerceIn(0,100) else 0
        detail.text = state.message ?: when {
            transferring || pending -> "$percent% · ${mib(state.bytes)} / ${mib(total)}" +
                (if (state.rate > 0) " · ${mib(state.rate)}/s" else "")
            phase == UpdatePhase.READY -> "APK verificado. Confirme a instalação no Android."
            release != null && !canDownload -> "A Release não informa SHA-256. Consulte a página oficial."
            release != null -> "APK universal · ${mib(total)}"
            else -> ""
        }
        detail.visibility = if (detail.text.isEmpty()) View.GONE else View.VISIBLE
        progress.visibility = if (busy || pending) View.VISIBLE else View.GONE
        progress.isIndeterminate = phase == UpdatePhase.CHECKING || phase == UpdatePhase.VERIFYING
        if (!progress.isIndeterminate) progress.progress = percent

        check.isEnabled = !busy
        val primaryLabel = when {
            phase == UpdatePhase.READY -> "Instalar APK"
            phase == UpdatePhase.DOWNLOADING -> "Pausar"
            pending -> "Retomar"
            canDownload && !busy -> "Baixar APK"
            else -> ""
        }
        primary.text = primaryLabel
        primary.visibility = if (primaryLabel.isEmpty()) View.GONE else View.VISIBLE
        primary.isEnabled = state.message == null || !busy
        stop.visibility = if (transferring || pending) View.VISIBLE else View.GONE
        restart.visibility = if (transferring || pending) View.VISIBLE else View.GONE
        stop.isEnabled = !busy || state.message == null
        restart.isEnabled = !busy || state.message == null
        if (shownNotesVersion != release?.version || notes.text.isEmpty()) {
            shownNotesVersion = release?.version
            notes.text = release?.notes?.let(GitHubUpdateCore::displayNotes)?.ifBlank { null }
                ?: "Nenhuma nota de atualização disponível."
        }
        previousNotes?.visibility = if (release?.previousNotes.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private fun card(parent: LinearLayout) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_ui_card)
        setPadding(dp(16), dp(13), dp(16), dp(12))
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    private fun addText(parent: LinearLayout, value: String, size: Int) = TextView(this).apply {
        text = value; textSize = size.toFloat()
        setTextColor(getColor(if (size >= 18) R.color.app_text else R.color.ui_muted))
        setPadding(0, dp(4), 0, dp(5))
        parent.addView(this)
    }

    private fun button(parent: LinearLayout, label: String, accent: Boolean = false, action: () -> Unit) =
        MaterialButton(this, null, if (accent) com.google.android.material.R.attr.materialButtonStyle
            else com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = label
            setAllCaps(false)
            setOnClickListener { action() }
            parent.addView(this, LinearLayout.LayoutParams(-1, -2))
        }

    private fun mib(bytes: Long) = String.format(Locale.getDefault(), "%.1f MiB", bytes / 1048576.0)
}
