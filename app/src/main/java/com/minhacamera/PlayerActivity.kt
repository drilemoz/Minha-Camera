package com.minhacamera

import android.content.ContentValues
import android.graphics.Bitmap
import android.view.PixelCopy
import android.os.Environment
import android.provider.MediaStore
import android.view.SurfaceView
import android.view.TextureView
import android.view.ViewGroup
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.Date
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.util.Rational
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.media3.common.VideoSize
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.MediaPlayer as VlcMediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/** RTSP em LibVLC por padrão: Media3 1.3.1 pode encerrar o processo em resposta sem CSeq. */
@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {
    private enum class Engine { MEDIA3, VLC }
    // Bloqueio explícito para impedir reentrada acidental no parser RTSP Media3 1.3.1.
    // Apenas reabilitar depois de atualizar/testar Media3 com respostas RTSP malformadas.
    private val media3RtspEnabled = false
    private var exo: ExoPlayer? = null
    private var vlcCore: LibVLC? = null
    private var vlcPlayer: VlcMediaPlayer? = null
    private lateinit var exoView: PlayerView
    private lateinit var vlcView: VLCVideoLayout
    private lateinit var loadingBox: View
    private lateinit var loadingDetail: TextView
    private lateinit var loadingTitle: TextView
    private lateinit var loadingBar: ProgressBar
    private lateinit var loadingPercent: TextView
    private lateinit var loadingElapsed: TextView
    private lateinit var loadingEstimate: TextView
    private lateinit var loadingAttempt: TextView
    private lateinit var errorBox: View
    private lateinit var errorText: TextView
    private lateinit var errorDetail: TextView
    private lateinit var engineText: TextView
    private lateinit var recordButton: MaterialButton
    private lateinit var recordTimer: TextView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var viewport: FrameLayout
    private lateinit var videoClip: FrameLayout
    private lateinit var videoContainer: FrameLayout
    private lateinit var fullscreenActions: View
    private lateinit var cropNotice: TextView
    private lateinit var lensBadge: TextView
    private var urls: List<String> = emptyList()
    private var index = 0
    private var engine = Engine.VLC
    private var rtspTransport = RtspTransport.TCP
    // Fallback conservador: URL com credenciais, depois URL limpa + opções RTSP.
    private var rtspOptionsOnly = false
    private var useWifiBinding = false // Começa com o comportamento anterior à reformulação.
    private var lastVlcStats = "Ainda sem amostras"
    private var lastVlcState = "Não iniciado"
    private var generation = 0
    private var active = false
    private var hevcSeen = false
    private var failedGeneration = -1
    private var videoReady = false
    private var fullscreenMode = VideoGeometry.Fullscreen.OFF
    private val fullscreen: Boolean get() = fullscreenMode != VideoGeometry.Fullscreen.OFF
    private var lastLandscape = false
    private var showFullscreenActions = true
    private var pipVisible = false
    private var displayMode = 0 // full frame, upper half, lower half; never zoom
    private var videoGeometry: VideoGeometry.Aspect? = null
    private val videoAspect: Double get() = videoGeometry?.value ?: 0.0
    private var geometryRetry: Runnable? = null
    private var vlcTexture: TextureView? = null
    private var vlcTextureListener: View.OnLayoutChangeListener? = null
    private var identifying = false
    private var identityThread: Thread? = null
    private var recordPending = false
    private var recordingSession: File? = null
    private var recordingPlayer: VlcMediaPlayer? = null
    private var recordingConfirmed = false
    private var stoppingRecording = false
    private var recordingStart = 0L
    private var finalizingCount = 0
    private val finalizingSessions = mutableSetOf<String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val failures = mutableListOf<String>()
    private val journal = PlaybackJournal()
    private var sessionStartedMs = 0L
    private var attemptStartedMs = 0L
    private var firstFrameInSessionMs: Long? = null
    private var attemptNumber = 0
    private var attemptTimingKey: String? = null
    private var attemptEstimateMs: Long? = null
    private var currentStage = R.string.player_loading_opening
    private var rebuffering = false
    private var sawMedia = false
    private var sawDecode = false
    private var lastVlcBufferEvent = -1
    private val journalId = UUID.randomUUID().toString()
    private var lastGeometry = ""
    private var lastCropFallback = false
    private val timingPrefs by lazy { getSharedPreferences("rtsp_first_frame", MODE_PRIVATE) }
    private val journalPrefs by lazy { getSharedPreferences("rtsp_session_journal", MODE_PRIVATE) }
    private var reportRefreshScheduled = false
    private val refreshDiagnostic = Runnable {
        reportRefreshScheduled = false
        diagnosticDialog?.takeIf { it.isShowing }?.update(reportSnapshot())
    }
    private var reportDialog: CameraReportDialog? = null
    private var diagnosticDialog: CameraReportDialog? = null
    private var rtspAnalysis = "Diagnóstico ainda não concluído"
    private var reportTextPending = ""
    private var recoveryRunning = false
    private var recoveryThread: Thread? = null
    private var pendingRecovered: Pair<String, String>? = null
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            val done = runCatching {
                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(reportTextPending.toByteArray(Charsets.UTF_8))
                } ?: error("Não foi possível abrir o arquivo")
            }.isSuccess
            Toast.makeText(this, if (done) "Relatório exportado" else "Falha ao exportar relatório", Toast.LENGTH_LONG).show()
        }
    }

    private fun selectRtspRoute() {
        if (useWifiBinding) NetUtil.bindToWifi(applicationContext)
        else NetUtil.useDefaultNetwork(applicationContext)
    }

    private fun toggleRtspRoute() {
        if (recordingSession != null || recordPending) {
            Toast.makeText(this, "Encerre a gravação antes de trocar a rede", Toast.LENGTH_LONG).show()
            return
        }
        val choices = arrayOf("Rota padrão do Android (anterior)", "Forçar Wi-Fi local")
        AlertDialog.Builder(this).setTitle("Rede do player RTSP")
            .setSingleChoiceItems(choices, if (useWifiBinding) 1 else 0) { dialog, which ->
                dialog.dismiss()
                val selected = which == 1
                if (selected == useWifiBinding) return@setSingleChoiceItems
                useWifiBinding = selected
                getSharedPreferences("rtsp_player", MODE_PRIVATE).edit()
                    .putBoolean("bind_wifi", useWifiBinding).apply()
                selectRtspRoute()
                event("Rede alterada: ${if (useWifiBinding) "Wi-Fi vinculado" else "rota padrão"}")
                resetAndPlay(Engine.VLC)
            }.setNegativeButton(R.string.cancelar, null).show()
    }

    private fun showSupportedMethods() {
        val primary = urls.getOrNull(index).orEmpty()
        val detail = "Fluxo atual: ${CameraStreamProtocol.description(primary)}\n\n" +
            "Reprodução via LibVLC:\n" +
            "• RTSP (TCP e UDP)\n• HTTP/HTTPS: MJPEG e vídeo direto\n" +
            "• HLS (.m3u8) e MPEG-DASH (.mpd)\n" +
            "• RTMP/RTMPS e RTP/UDP (experimentais)\n\n" +
            "Descoberta separada: ONVIF/WS-Discovery.\n" +
            "WebRTC/P2P proprietário: necessita sinalização, SDK ou protocolo documentado; não há acesso universal.\n\n" +
            "Para adicionar outro protocolo: Adicionar → Manual → cole a URL completa no campo IP/URL. " +
            "A compatibilidade real depende do formato e do firmware da câmera."
        AlertDialog.Builder(this).setTitle("Métodos de conexão")
            .setMessage(detail).setPositiveButton(android.R.string.ok, null).show()
    }

    private fun testExternalVlc() {
        val url = urls.getOrNull(index) ?: return
        val parsed = Uri.parse(url)
        val hasCredentials = !parsed.encodedUserInfo.isNullOrBlank()
        AlertDialog.Builder(this)
            .setTitle("Testar com VLC independente")
            .setMessage("Abrir a mesma URL (${CameraStreamProtocol.description(url)}) no aplicativo VLC para Android. " +
                "${if (hasCredentials) "O endereço contém login e será entregue ao aplicativo VLC após confirmar." else "Nenhuma senha está embutida no endereço."} " +
                "Se o vídeo abrir no VLC externo, isso aponta para uma falha de reprodução no Minha Câmera.")
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton("Abrir VLC") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .setPackage("org.videolan.vlc")
                    .setDataAndType(Uri.parse(url), "video/*")
                try {
                    startActivity(intent)
                    event("Teste no VLC externo solicitado pelo usuário")
                } catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(this, "Instale o VLC para Android para testar este endereço.", Toast.LENGTH_LONG).show()
                } catch (_: Exception) {
                    Toast.makeText(this, "Não foi possível abrir o VLC externo", Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    private data class VlcCounters(val bytes: Long, val demux: Long, val decoded: Long, val shown: Long, val lost: Long) {
        fun describe() = "bytes=$bytes; demux=$demux; decodificados=$decoded; exibidos=$shown; perdas=$lost"
    }

    private fun readVlcCounters(player: VlcMediaPlayer): VlcCounters? = try {
        val media = player.media
        try {
            media?.stats?.let {
                VlcCounters(it.readBytes.toLong(), it.demuxReadBytes.toLong(), it.decodedVideo.toLong(),
                    it.displayedPictures.toLong(), it.lostPictures.toLong())
            }
        } finally { media?.release() }
    } catch (_: Exception) { null }

    private fun vlcStatsSnapshot(player: VlcMediaPlayer): String =
        readVlcCounters(player)?.describe() ?: "Estatísticas nativas indisponíveis"

    private fun event(message: String, failed: Boolean = false) {
        journal.append(SystemClock.elapsedRealtime() - sessionStartedMs, message, failed)
        // Only bounded, sanitized events; no video frames, tokens, or file names are persisted.
        val key = when (journalId) {
            journalPrefs.getString("active_id", null) -> "latest"
            journalPrefs.getString("previous_id", null) -> "previous"
            else -> null
        }
        if (key != null) journalPrefs.edit().putString(key, journal.entries().joinToString("\n")).apply()
        if (diagnosticDialog?.isShowing == true && !reportRefreshScheduled) {
            reportRefreshScheduled = true
            mainHandler.postDelayed(refreshDiagnostic, 350L)
        }
    }

    private fun beginJournal(savedInstanceState: Bundle?) {
        val now = SystemClock.elapsedRealtime()
        val selected = intent.getLongExtra("selected_at_ms", -1L)
        sessionStartedMs = if (savedInstanceState == null && selected >= 0L && now - selected in 0..10_000L) selected else now
        journalPrefs.edit().putString("previous", journalPrefs.getString("latest", ""))
            .putString("previous_id", journalPrefs.getString("active_id", null))
            .putString("active_id", journalId).remove("latest").apply()
        event(if (sessionStartedMs == selected) "Câmera selecionada na lista" else "Visualizador retomado")
    }

    private fun configureAttemptTiming(url: String) {
        attemptStartedMs = SystemClock.elapsedRealtime()
        attemptNumber++
        sawMedia = false; sawDecode = false; lastVlcBufferEvent = -1
        rebuffering = false
        currentStage = R.string.player_loading_opening
        attemptTimingKey = null
        attemptEstimateMs = null
        val uri = Uri.parse(url)
        val host = uri.host ?: return
        // Different cameras/routes do not share estimates. Credentials and URL parameters are excluded.
        val fingerprint = "${uri.scheme}|$host|${uri.port}|${uri.encodedPath}|$index|$engine|$rtspTransport|$rtspOptionsOnly|$useWifiBinding"
        val key = MessageDigest.getInstance("SHA-256").digest(fingerprint.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        attemptTimingKey = key
        attemptEstimateMs = PlaybackTiming.estimate(timingSamples(key))
    }

    private fun timingSamples(key: String): List<Long> =
        timingPrefs.getString(key, "").orEmpty().substringAfter('|', "")
            .split(',').mapNotNull { it.toLongOrNull() }

    private fun rememberFirstFrame(elapsedMs: Long) {
        val key = attemptTimingKey ?: return
        val samples = PlaybackTiming.addSample(timingSamples(key), elapsedMs)
        if (elapsedMs !in 1..PlaybackTiming.ATTEMPT_LIMIT_MS || samples.isEmpty()) return
        val editor = timingPrefs.edit()
        val old = timingPrefs.all.filterKeys { it != key }.entries
            .sortedBy { (it.value as? String)?.substringBefore('|')?.toLongOrNull() ?: 0L }
        old.take((old.size - 39).coerceAtLeast(0)).forEach { editor.remove(it.key) }
        editor.putString(key, "${System.currentTimeMillis()}|${samples.joinToString(",")}").apply()
    }

    private fun exportReport(report: String) {
        reportTextPending = maskCredentials(report)
        exportLauncher.launch("Minha-Camera-RTSP-diagnostico.txt")
    }

    /** Não incluir tokens ou senhas embutidos no caminho do fluxo exportado. */
    private fun safeReportPath(url: String): String {
        if (!CameraStreamProtocol.isRtsp(url)) return "/[caminho protegido]"
        val path = Uri.parse(url).encodedPath.orEmpty().ifEmpty { "/" }
        // Custom paths may contain access tokens even without a query string or a key=value separator.
        val known = RtspProbe.PATHS.any { it.substringBefore('?') == path }
        return if (path == "/" || known) path else "/[caminho protegido]"
    }

    private fun reportSnapshot(): String {
        val safeAddresses = urls.mapIndexed { i, address ->
            val u = Uri.parse(address)
            "Fluxo ${i + 1}: ${CameraStreamProtocol.description(address)} · ${u.host ?: "?"}:${u.port.takeIf { it > 0 } ?: CameraStreamProtocol.defaultPort(address)}${safeReportPath(address)}"
        }
        val lines = mutableListOf(
            "Minha Câmera · Diagnóstico do visualizador IP",
            "Resumo: tentativa $attemptNumber · ${journal.failures} ocorrência(s) de erro/falha",
            "Primeira imagem nesta sessão: ${firstFrameInSessionMs?.let { "$it ms" } ?: "ainda não exibida"}",
            "Tela cheia: ${if (fullscreen) "sim" else "não"} · PiP: ${if (pipVisible) "sim" else "não"}",
            "Gravação: ${when {
                stoppingRecording -> "encerrando"
                recordingConfirmed -> "em andamento"
                recordingSession != null || recordPending -> "aguardando confirmação"
                finalizingCount > 0 -> "salvando"
                else -> "inativa"
            }}",
            "Android ${Build.VERSION.RELEASE} · SDK ${Build.VERSION.SDK_INT}",
            "App: ${packageManager.getPackageInfo(packageName, 0).versionName} · LibVLC 3.7.7",
            "Motor: ${engine.name} · protocolo: ${CameraStreamProtocol.description(urls.getOrNull(index).orEmpty())} · transporte: ${if (CameraStreamProtocol.isRtsp(urls.getOrNull(index).orEmpty())) rtspTransport.name else "padrão nativo"}",
            "Rede do player: ${if (useWifiBinding) "Wi-Fi vinculado" else "rota padrão Android"}",
            "Autenticação VLC atual: ${if (rtspOptionsOnly) "opções nativas (sem login na URL)" else "login embutido na URL"}",
            "Último estado do VLC: $lastVlcState",
            "Estatísticas VLC: $lastVlcStats",
            "Vídeo recebido: ${if (videoReady) "sim" else "não"}",
            "Exibição: ${displayLabels()[displayMode]} · proporção do vídeo: $videoAspect",
            "Endereços configurados: ${urls.size}"
        )
        lines.addAll(safeAddresses)
        lines.add("\nTentativas")
        lines.addAll(failures)
        lines.add("\nAnálise do protocolo")
        lines.add(rtspAnalysis)
        lines.add("\nEventos desta sessão")
        lines.addAll(journal.entries())
        val previousSession = journalPrefs.getString("previous", "").orEmpty()
        if (previousSession.isNotBlank()) {
            lines.add("\nSessão anterior neste aparelho")
            lines.add(previousSession)
        }
        getSharedPreferences("private_crash_diagnostics", MODE_PRIVATE)
            .getString("last_player_crash", null)?.let {
                lines.add("\nÚltima falha não tratada do visualizador (Java/Kotlin)")
                lines.add(it)
            }
        lines.add("\nObservação: uma porta TCP aberta não comprova serviço RTSP.")
        return maskCredentials(lines.joinToString("\n"))
    }

    private fun showReport(title: String, report: String): CameraReportDialog {
        return CameraReportDialog(this, title, maskCredentials(report), ::exportReport).show().also { reportDialog = it }
    }
    private fun showDiagnosticReport() {
        event("Painel de diagnóstico aberto")
        diagnosticDialog = CameraReportDialog(this, "Diagnóstico da câmera", reportSnapshot(),
            ::exportReport, subtitle = "Sessão atual e anterior · sem senhas ou tokens",
            snapshotProvider = ::reportSnapshot).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        // A reformulação forçava bindProcessToNetwork() ao abrir o player.
        // Restaurar a rota padrão anterior e permitir testar o bind via menu.
        useWifiBinding = getSharedPreferences("rtsp_player", MODE_PRIVATE)
            .getBoolean("bind_wifi", false)
        selectRtspRoute()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        toolbar = findViewById(R.id.playerToolbar)
        setSupportActionBar(toolbar)
        title = intent.getStringExtra("name") ?: getString(R.string.app_name)
        supportActionBar?.subtitle = getString(R.string.monitoramento_vivo)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        exoView = findViewById(R.id.playerView)
        vlcView = findViewById(R.id.vlcView)
        loadingBox = findViewById(R.id.loadingBox)
        loadingDetail = findViewById(R.id.loadingDetail)
        loadingTitle = findViewById(R.id.loadingTitle)
        loadingBar = findViewById(R.id.progress)
        loadingPercent = findViewById(R.id.loadingPercent)
        loadingElapsed = findViewById(R.id.loadingElapsed)
        loadingEstimate = findViewById(R.id.loadingEstimate)
        loadingAttempt = findViewById(R.id.loadingAttempt)
        errorBox = findViewById(R.id.errorBox)
        errorText = findViewById(R.id.errorText)
        errorDetail = findViewById(R.id.errorDetail)
        engineText = findViewById(R.id.engineText)
        recordButton = findViewById(R.id.btnRecord)
        recordTimer = findViewById(R.id.recordTimer)
        viewport = findViewById(R.id.videoViewport)
        videoClip = findViewById(R.id.videoClip)
        videoContainer = findViewById(R.id.videoContainer)
        fullscreenActions = findViewById(R.id.fullscreenActions)
        cropNotice = findViewById(R.id.cropNotice)
        lensBadge = findViewById(R.id.lensBadge)
        val main = intent.getStringExtra("url").orEmpty()
        val alternatives = intent.getStringArrayExtra("alts")?.toList().orEmpty()
        urls = (listOf(main) + alternatives).filter { CameraStreamProtocol.isSupported(it) }.distinct()
        displayMode = (savedInstanceState?.getInt("displayMode") ?: 0).coerceIn(0, 2)
        lastLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        fullscreenMode = savedInstanceState?.getString("fullscreenMode")?.let { saved ->
            VideoGeometry.Fullscreen.entries.firstOrNull { it.name == saved }
        } ?: when {
            savedInstanceState?.getBoolean("fullscreen") == true -> VideoGeometry.Fullscreen.MANUAL
            savedInstanceState != null -> VideoGeometry.Fullscreen.OFF
            else -> VideoGeometry.afterRotation(VideoGeometry.Fullscreen.OFF, lastLandscape)
        }
        findViewById<View>(R.id.btnRetry).setOnClickListener { event("Nova tentativa manual"); resetAndPlay(Engine.VLC) }
        findViewById<View>(R.id.btnCopyRtspDiagnostic).setOnClickListener { showDiagnosticReport() }
        findViewById<View>(R.id.btnRecoverRtsp).apply {
            visibility = if (urls.any { CameraStreamProtocol.isRtsp(it) }) View.VISIBLE else View.GONE
            setOnClickListener { recoverRtsp() }
        }
        beginJournal(savedInstanceState)
        event("Visualizador iniciado · ${urls.size} fluxo(s) cadastrado(s)")
        findViewById<View>(R.id.btnLoadingDiagnostic).setOnClickListener { showDiagnosticReport() }
        findViewById<View>(R.id.btnFullscreenDiagnostic).setOnClickListener { showDiagnosticReport() }
        recordButton.setOnClickListener { onRecordClick() }
        findViewById<View>(R.id.btnRecordings).setOnClickListener { event("Lista de gravações aberta"); startActivity(Intent(this, RecordingsActivity::class.java)) }
        findViewById<View>(R.id.btnDisplay).setOnClickListener { showDisplayOptions() }
        findViewById<View>(R.id.btnFullscreenImage).setOnClickListener { showDisplayOptions() }
        findViewById<View>(R.id.btnFullscreenExit).setOnClickListener { setFullscreen(false) }
        findViewById<View>(R.id.btnPip).setOnClickListener { enterPip() }
        findViewById<View>(R.id.btnFullscreen).setOnClickListener { setFullscreen(true) }
        viewport.setOnClickListener {
            if (fullscreen && !pipVisible) {
                showFullscreenActions = !showFullscreenActions
                updateViewerChrome()
            }
        }
        videoContainer.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right-left != oldRight-oldLeft || bottom-top != oldBottom-oldTop) syncVlcWindowSize()
        }
        viewport.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right-left != oldRight-oldLeft) resizeLoadingCard(right - left)
            if (right-left != oldRight-oldLeft || bottom-top != oldBottom-oldTop) applyDisplayMode()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (fullscreen) setFullscreen(false) else finish() }
        })
        configureControls()
        applyFullscreen()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("fullscreenMode", fullscreenMode.name)
        outState.putInt("displayMode", displayMode)
        super.onSaveInstanceState(outState)
    }
    override fun onPictureInPictureModeChanged(inPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(inPictureInPictureMode, newConfig)
        pipVisible = inPictureInPictureMode
        event(if (pipVisible) "PiP ativado" else "PiP encerrado")
        if (!pipVisible) handleScreenRotation(newConfig)
        applyFullscreen()
    }

    private fun enterPip() {
        event("PiP solicitado")
        if (!PipSupport.available(this)) {
            event("PiP indisponível no Android", failed = true)
            Toast.makeText(this, R.string.pip_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        if (!videoReady || !active) {
            event("PiP não aberto: sem vídeo disponível")
            Toast.makeText(this, R.string.pip_wait_for_video, Toast.LENGTH_SHORT).show()
            return
        }
        if (recordingSession != null || recordPending) {
            Toast.makeText(this, R.string.pip_stop_recording, Toast.LENGTH_LONG).show()
            return
        }
        val aspect = VideoGeometry.displayedAspect(videoAspect, displayMode != 0)
        val ratio = if (aspect != null && aspect in 0.43..2.30)
            Rational((aspect * 1000).toInt().coerceAtLeast(1), 1000) else Rational(16, 9)
        if (!PipSupport.enter(this, if (displayMode != 0 && aspect != null) videoClip else viewport, ratio)) {
            event("Android não abriu o PiP", failed = true)
            Toast.makeText(this, R.string.pip_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configureControls()
        if (!pipVisible && !isInPictureInPictureMode) handleScreenRotation(newConfig)
        applyFullscreen()
    }

    private fun handleScreenRotation(config: Configuration) {
        if (config.orientation != Configuration.ORIENTATION_LANDSCAPE &&
            config.orientation != Configuration.ORIENTATION_PORTRAIT) return
        val landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (landscape == lastLandscape) return
        lastLandscape = landscape
        fullscreenMode = VideoGeometry.afterRotation(fullscreenMode, landscape)
        showFullscreenActions = true
        event("Rotação: ${if (landscape) "horizontal" else "vertical"} · tela cheia=$fullscreen · modo de imagem=$displayMode")
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 2011, 1, "Capturar JPEG")
        menu.add(0, 2002, 2, R.string.usar_vlc)
        menu.add(0, 2003, 2, R.string.informacoes_tecnicas)
        menu.add(0, 2004, 3, R.string.identificar_camera)
        menu.add(0, 2005, 4, R.string.pip_window)
        if (urls.any { CameraStreamProtocol.isRtsp(it) })
            menu.add(0, 2007, 5, "Localizar fluxo RTSP")
        menu.add(0, 2010, 8, "Protocolos disponíveis")
        menu.add(0, 2008, 6, "Testar no VLC externo")
        menu.add(0, 2009, 7, "Testar outra rota de rede")
        menu.add(0, 2006, 0, "Diagnóstico").apply {
            setIcon(R.drawable.ic_ui_bug)
            icon?.mutate()?.setTint(getColor(R.color.live_accent))
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        2011 -> { captureJpeg(); true }
        2002 -> { event("Reinício manual via LibVLC"); resetAndPlay(Engine.VLC); true }
        2003 -> { showReport("Informações da transmissão", reportSnapshot()); true }
        2004 -> { identifyCamera(); true }
        2005 -> { enterPip(); true }
        2006 -> { showDiagnosticReport(); true }
        2007 -> { recoverRtsp(); true }
        2008 -> { testExternalVlc(); true }
        2009 -> { toggleRtspRoute(); true }
        2010 -> { showSupportedMethods(); true }
        else -> super.onOptionsItemSelected(item)
    }
    /** Copia pixels da superfície decodificada, sem capturar controles nem inventar quadros. */
    private fun captureJpeg() {
        event("Captura JPEG solicitada")
        if (!videoReady || Build.VERSION.SDK_INT < 29) {
            event("Captura JPEG indisponível: vídeo ausente ou Android abaixo de 10")
            Toast.makeText(this, "Aguarde um quadro de vídeo (captura requer Android 10+)", Toast.LENGTH_LONG).show()
            return
        }
        val root = if (engine == Engine.VLC) vlcView else exoView
        val texture = findVideoOutput(root, TextureView::class.java)
        if (texture != null && texture.isAvailable && texture.width > 0 && texture.height > 0) {
            // Read the decoded image, not the parent crop or screen overlays.
            val scale = minOf(1.0, 2048.0 / maxOf(texture.width, texture.height))
            val bitmap = try {
                texture.getBitmap((texture.width * scale).toInt().coerceAtLeast(1),
                    (texture.height * scale).toInt().coerceAtLeast(1))
            } catch (_: OutOfMemoryError) { null }
              catch (_: Exception) { null }
            if (bitmap != null) saveJpeg(bitmap)
            else {
                event("Falha ao copiar o quadro da textura", failed = true)
                Toast.makeText(this, R.string.player_capture_failed, Toast.LENGTH_SHORT).show()
            }
            return
        }

        val surface = findVideoOutput(root, SurfaceView::class.java)
        if (surface == null || surface.width <= 0 || surface.height <= 0) {
            event("Captura JPEG falhou: superfície indisponível", failed = true)
            Toast.makeText(this, R.string.player_capture_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val bitmap = try { Bitmap.createBitmap(surface.width, surface.height, Bitmap.Config.ARGB_8888) }
            catch (_: OutOfMemoryError) {
                event("Captura JPEG falhou: memória insuficiente", failed = true)
                Toast.makeText(this, "Memória insuficiente para captura", Toast.LENGTH_LONG).show()
                return
            }
        val capturedGeneration = generation
        try {
            PixelCopy.request(surface, bitmap, { result ->
                if (result == PixelCopy.SUCCESS && active && generation == capturedGeneration) saveJpeg(bitmap)
                else {
                    event("Captura JPEG não concluída · PixelCopy=$result", failed = true)
                    bitmap.recycle()
                    Toast.makeText(this, R.string.player_capture_failed, Toast.LENGTH_SHORT).show()
                }
            }, mainHandler)
        } catch (_: IllegalArgumentException) {
            event("Captura JPEG falhou: argumento inválido do PixelCopy", failed = true)
            bitmap.recycle()
            Toast.makeText(this, R.string.player_capture_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun <T : View> findVideoOutput(view: View, type: Class<T>): T? {
        if (view.visibility != View.VISIBLE) return null
        if (type.isInstance(view)) return type.cast(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findVideoOutput(view.getChildAt(i), type)?.let { return it }
        }
        return null
    }

    private fun saveJpeg(bitmap: Bitmap) {
        Thread {
            var saved = false
            var uri: Uri? = null
            try {
                val name = "Camera_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= 29) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Minha Câmera")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                val target = uri ?: error("Falha ao criar imagem")
                contentResolver.openOutputStream(target)?.use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) error("Falha JPEG")
                } ?: error("Falha ao abrir imagem")
                if (Build.VERSION.SDK_INT >= 29) contentResolver.update(target, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
                saved = true
            } catch (_: Exception) { uri?.let { runCatching { contentResolver.delete(it, null, null) } } }
            finally { bitmap.recycle() }
            runOnUiThread {
                event(if (saved) "JPEG salvo na galeria" else "Falha ao salvar JPEG na galeria", failed = !saved)
                if (!isDestroyed) Toast.makeText(this,
                    if (saved) "Imagem JPEG salva na galeria" else "Falha ao salvar JPEG", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    override fun onSupportNavigateUp(): Boolean {
        if (fullscreen) setFullscreen(false) else finish()
        return true
    }
    override fun onStart() {
        super.onStart()
        if (!active) { active = true; event("Visualizador em primeiro plano"); resetAndPlay(Engine.VLC) }
    }
    override fun onStop() {
        if (android.os.Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode) { super.onStop(); return }
        event("Visualizador saiu do primeiro plano")
        active = false; recordPending = false; generation++
        mainHandler.removeCallbacksAndMessages(null)
        reportRefreshScheduled = false
        releasePlayers()
        super.onStop()
    }
    override fun onDestroy() {
        identityThread?.interrupt(); identityThread = null
        recoveryThread?.interrupt(); recoveryThread = null
        super.onDestroy()
    }
    private fun displayLabels() = arrayOf(
        getString(R.string.imagem_inteira), getString(R.string.imagem_superior), getString(R.string.imagem_inferior)
    )

    private fun showDisplayOptions() {
        val choices = displayLabels()
        AlertDialog.Builder(this).setTitle(R.string.exibicao_video)
            .setSingleChoiceItems(choices, displayMode) { dialog, which ->
                displayMode = which
                dialog.dismiss()
                vlcPlayer?.let { readVlcVideoSize(it) }
                applyDisplayMode()
                event("Exibição selecionada: ${choices[which]} · proporção $videoAspect")
                if (which != 0) requestVlcVideoSize()
            }.show()
    }

    private fun setFullscreen(enabled: Boolean) {
        val target = if (enabled) VideoGeometry.Fullscreen.MANUAL else VideoGeometry.Fullscreen.OFF
        if (fullscreenMode == target) return
        fullscreenMode = target
        event(if (enabled) "Tela cheia ativada" else "Tela cheia encerrada")
        showFullscreenActions = true
        applyFullscreen()
    }

    private fun applyFullscreen() {
        toolbar.visibility = if (fullscreen || pipVisible) View.GONE else View.VISIBLE
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (fullscreen && !pipVisible) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else if (!pipVisible) controller.show(WindowInsetsCompat.Type.systemBars())
        updateViewerChrome()
        viewport.post {
            if (isDestroyed) return@post
            applyDisplayMode()
            syncVlcWindowSize()
            if (videoReady) requestVlcVideoSize()
        }
    }

    private fun updateViewerChrome() {
        findViewById<View>(R.id.playerControls).visibility = if (fullscreen || pipVisible) View.GONE else View.VISIBLE
        fullscreenActions.visibility = if (fullscreen && !pipVisible && showFullscreenActions) View.VISIBLE else View.GONE
        viewport.isClickable = fullscreen && !pipVisible
        engineText.visibility = if (fullscreen || pipVisible) View.GONE else View.VISIBLE
        recordTimer.visibility = if (!pipVisible && recordingConfirmed) View.VISIBLE else View.GONE
        val waitingForCrop = videoReady && displayMode > 0 && VideoGeometry.displayedAspect(videoAspect, true) == null
        lensBadge.visibility = if (videoReady && !pipVisible && displayMode > 0 && !waitingForCrop && (!fullscreen || showFullscreenActions))
            View.VISIBLE else View.GONE
        if (displayMode > 0) lensBadge.setText(
            if (displayMode == 1) R.string.player_lens_upper else R.string.player_lens_lower
        )
        cropNotice.visibility = if (waitingForCrop && !pipVisible) View.VISIBLE else View.GONE
        if (waitingForCrop && !lastCropFallback) event("Imagem inteira mantida: recorte aguardando dimensões da faixa")
        lastCropFallback = waitingForCrop
    }

    private fun configureControls() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val controls = findViewById<LinearLayout>(R.id.playerControls)
        controls.orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        for (id in listOf(R.id.controlsPrimary, R.id.controlsSecondary)) {
            findViewById<LinearLayout>(id).layoutParams = LinearLayout.LayoutParams(
                if (landscape) 0 else LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, if (landscape) 1f else 0f)
        }
        toolbar.layoutParams = toolbar.layoutParams.apply {
            height = ((if (landscape) 48 else 56) * resources.displayMetrics.density).toInt()
        }
        supportActionBar?.subtitle = if (landscape) null else getString(R.string.monitoramento_vivo)
    }
    private fun setVideoAspect(width: Int, height: Int, pixelRatio: Double = 1.0, rotated: Boolean = false,
        source: VideoGeometry.Source = VideoGeometry.Source.ACTIVE_TRACK): Boolean {
        val aspect = VideoGeometry.sourceAspect(width, height, pixelRatio, rotated) ?: return false
        val previous = videoGeometry
        if (previous != null && !previous.accepts(source)) return false
        val geometryChanged = previous == null || kotlin.math.abs(aspect - previous.value) > 0.0001
        if (geometryChanged || previous?.source != source) {
            videoGeometry = VideoGeometry.Aspect(aspect, source)
            event("Dimensões de vídeo: ${width}×$height · fonte=${source.name}")
            if (geometryChanged) applyDisplayMode()
        }
        return true
    }

    private fun useVlcTrack(track: IMedia.VideoTrack, source: VideoGeometry.Source): Boolean {
        val sar = if (track.sarNum > 0 && track.sarDen > 0) track.sarNum.toDouble() / track.sarDen else 1.0
        return setVideoAspect(track.width, track.height, sar, track.orientation >= 4, source)
    }

    private fun readVlcVideoSize(player: VlcMediaPlayer) {
        if (player !== vlcPlayer) return
        val activeTrack = runCatching { player.currentVideoTrack }.getOrNull()
        if (activeTrack != null && useVlcTrack(activeTrack, VideoGeometry.Source.ACTIVE_TRACK)) return
        if (!videoReady || videoGeometry?.source == VideoGeometry.Source.ACTIVE_TRACK) return
        if (videoGeometry?.source != VideoGeometry.Source.MEDIA_TRACK) {
            val fallback = runCatching {
                val media = player.media ?: return@runCatching null
                try {
                    val tracks = (0 until media.trackCount.coerceIn(0, 64))
                        .mapNotNull { media.getTrack(it) as? IMedia.VideoTrack }
                    val selected = player.videoTrack
                    if (selected >= 0) tracks.firstOrNull { it.id == selected } ?: tracks.singleOrNull()
                    else tracks.singleOrNull()
                } finally { media.release() }
            }.getOrNull()
            if (fallback != null && useVlcTrack(fallback, VideoGeometry.Source.MEDIA_TRACK)) return
        }
        if (videoGeometry != null || pipVisible || isInPictureInPictureMode) return
        // VideoHelper assigns positive layout params from the decoded format.
        // MATCH_PARENT and the window's own measured size are not source dimensions.
        val fitted = vlcTexture?.takeIf { it.isAvailable }?.layoutParams ?: return
        setVideoAspect(fitted.width, fitted.height, source = VideoGeometry.Source.RENDERED_LAYOUT)
    }

    private fun requestVlcVideoSize() {
        val player = vlcPlayer ?: return
        if (!active || geometryRetry != null || videoGeometry?.source == VideoGeometry.Source.ACTIVE_TRACK) return
        val current = generation
        val retry = object : Runnable {
            private var remaining = 12
            override fun run() {
                if (geometryRetry !== this) return
                if (active && generation == current && failedGeneration != current && vlcPlayer === player) {
                    readVlcVideoSize(player)
                    if (videoGeometry?.source != VideoGeometry.Source.ACTIVE_TRACK && remaining-- > 0) {
                        mainHandler.postDelayed(this, 250L)
                        return
                    }
                }
                geometryRetry = null
            }
        }
        geometryRetry = retry
        retry.run()
    }

    private fun syncVlcWindowSize() {
        if (vlcView.width > 0 && vlcView.height > 0)
            runCatching { vlcPlayer?.updateVideoSurfaces() }
    }

    private fun applyDisplayMode() {
        val frame = if (displayMode > 0)
            VideoGeometry.fitHalf(viewport.width, viewport.height, videoAspect, displayMode == 2)
        else VideoGeometry.fitFull(viewport.width, viewport.height, videoAspect)
        val clipParams = videoClip.layoutParams as FrameLayout.LayoutParams
        val clipWidth = frame?.width ?: FrameLayout.LayoutParams.MATCH_PARENT
        val clipHeight = frame?.clipHeight ?: FrameLayout.LayoutParams.MATCH_PARENT
        if (clipParams.width != clipWidth || clipParams.height != clipHeight || clipParams.gravity != Gravity.CENTER) {
            clipParams.width = clipWidth; clipParams.height = clipHeight; clipParams.gravity = Gravity.CENTER
            videoClip.layoutParams = clipParams
        }
        val params = videoContainer.layoutParams as FrameLayout.LayoutParams
        val width = frame?.width ?: FrameLayout.LayoutParams.MATCH_PARENT
        val height = frame?.videoHeight ?: FrameLayout.LayoutParams.MATCH_PARENT
        if (params.width != width || params.height != height || params.gravity != (Gravity.TOP or Gravity.CENTER_HORIZONTAL)) {
            params.width = width; params.height = height; params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            videoContainer.layoutParams = params
        }
        videoContainer.translationY = frame?.videoTop ?: 0f
        val geometry = if (frame != null) "${frame.width}×${frame.clipHeight} · modo $displayMode · tela cheia=$fullscreen · PiP=$pipVisible" else ""
        if (geometry.isNotBlank() && geometry != lastGeometry) event("Área visível: $geometry")
        lastGeometry = geometry
        exoView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        updateViewerChrome()
    }
    private fun identifyCamera() {
        if (identifying) return
        event("Identificação de serviços solicitada")
        val uri = Uri.parse(urls.getOrNull(index) ?: return)
        val host = uri.host ?: return
        val auth = uri.encodedUserInfo.orEmpty()
        val user = Uri.decode(auth.substringBefore(':', ""))
        val pass = Uri.decode(auth.substringAfter(':', ""))
        identifying = true
        val panel = showReport("Câmera e serviços", "Consultando serviços locais por ONVIF e RTSP…")
        identityThread = Thread {
            NetUtil.bindToWifi(applicationContext)
            val service = RtspServiceDetector.inspect(host, uri.port.takeIf { it in 1..65535 } ?: 554)
            val endpoints = runCatching { WsDiscovery.search(applicationContext)
                .filter { it.ip == host }.flatMap { it.xaddrs } }.getOrDefault(emptyList())
            val identity = runCatching { OnvifClient.identify(host, user, pass, endpoints) }
                .getOrElse { "Consulta ONVIF não concluída; serviço opcional." }
            val result = "SERVIÇO RTSP\n${service.detail}\n\nONVIF\n$identity\n\n" +
                "Descoberta WS-Discovery: ${if (endpoints.isNotEmpty()) "resposta encontrada" else "sem anúncio detectado"}\n" +
                "Porta aberta não garante vídeo. ONVIF ausente não prova ausência de funções proprietárias."
            runOnUiThread {
                identifying = false
                event("Identificação: ${service.kind}; ONVIF ${if (endpoints.isNotEmpty()) "anunciado" else "não anunciado"}")
                if (!isFinishing && !isDestroyed) panel.update(result)
            }
        }.also { it.name = "rtsp-services"; it.start() }
    }

    private fun recoverRtsp() {
        event("Recuperação de fluxo RTSP solicitada")
        if (urls.none { CameraStreamProtocol.isRtsp(it) }) {
            Toast.makeText(this, "Recuperação RTSP não se aplica a este endereço", Toast.LENGTH_LONG).show()
            return
        }
        if (recoveryRunning) {
            Toast.makeText(this, "Busca de fluxo em andamento", Toast.LENGTH_SHORT).show()
            return
        }
        val original = intent.getStringExtra("url").orEmpty()
        if (!original.startsWith("rtsp://", true)) return
        recoveryRunning = true
        val panel = showReport("Localizar fluxo RTSP", "Consultando portas e caminhos conhecidos na rede local…")
        recoveryThread = Thread {
            NetUtil.bindToWifi(applicationContext)
            val result = RtspRecovery.find(original) { progress ->
                runOnUiThread { if (!isFinishing && !isDestroyed) panel.update(progress) }
            }
            runOnUiThread {
                recoveryRunning = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                event("Recuperação: ${if (result.match != null) "DESCRIBE OK" else "sem resposta válida"}")
                panel.update(result.report + if (result.match != null)
                    "\nEncontrado: ${result.match.port}${result.match.path.substringBefore('?')} (${result.match.codec ?: "codec não informado"}).\nA reprodução ainda precisa ser confirmada."
                    else "")
                val matched = result.match ?: return@runOnUiThread
                AlertDialog.Builder(this)
                    .setTitle("Fluxo RTSP identificado")
                    .setMessage("A câmera respondeu ao DESCRIBE na porta ${matched.port}${matched.path.substringBefore('?')}. Testar o vídeo? O endereço só será atualizado se um quadro real for exibido.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Testar vídeo") { _, _ ->
                        pendingRecovered = original to matched.url
                        urls = listOf(matched.url, original).distinct()
                        resetAndPlay(Engine.VLC)
                    }.show()
            }
        }.also { it.name = "rtsp-recovery"; it.start() }
    }

    private fun resetAndPlay(startEngine: Engine) {
        attemptNumber = 0
        recordPending = false; index = 0; rtspTransport = RtspTransport.TCP; rtspOptionsOnly = false
        // Centraliza a política: nenhum botão/tentativa inicia Media3 RTSP nesta release.
        engine = if (media3RtspEnabled) startEngine else Engine.VLC
        failures.clear(); hevcSeen = false
        lastVlcStats = "Ainda sem amostras"
        lastVlcState = "Aguardando reprodução"
        rtspAnalysis = "Aguardando tentativa de conexão"
        if (active) playCurrent()
    }
    private fun releasePlayers() {
        geometryRetry?.let { mainHandler.removeCallbacks(it) }; geometryRetry = null
        vlcTextureListener?.let { vlcTexture?.removeOnLayoutChangeListener(it) }
        vlcTextureListener = null; vlcTexture = null
        exoView.player = null; exo?.let { runCatching { it.release() } }; exo = null
        vlcPlayer?.let { nativePlayer ->
            val session = recordingSession.takeIf { recordingPlayer === nativePlayer }
            if (session != null) {
                event("Gravação encerrada ao liberar o player")
                runCatching { nativePlayer.record(null) }.onFailure {
                    event("VLC falhou ao encerrar gravação na saída: ${causeSummary(it)}", failed = true)
                }
            }
            runCatching { nativePlayer.setEventListener(null) }
            // Synchronous stop closes native outputs before moving a completed file.
            runCatching { nativePlayer.stop() }
            runCatching { nativePlayer.detachViews() }; runCatching { nativePlayer.release() }
            if (session != null) finishRecording(session)
        }
        vlcPlayer = null; vlcCore?.let { runCatching { it.release() } }; vlcCore = null
    }
    private fun resizeLoadingCard(width: Int) {
        val density = resources.displayMetrics.density
        val cardWidth = minOf((360 * density).toInt(), width - (32 * density).toInt())
        if (cardWidth <= 0 || loadingBox.layoutParams.width == cardWidth) return
        loadingBox.layoutParams = loadingBox.layoutParams.apply { this.width = cardWidth }
    }

    private fun loading(detail: Int) {
        if (videoReady && !rebuffering) {
            rebuffering = true
            event("Rebufferização iniciada")
        }
        loadingDetail.setText(if (rebuffering) detail else currentStage)
        loadingTitle.setText(if (rebuffering) R.string.player_loading_resuming else R.string.player_loading_title)
        loadingBox.visibility = View.VISIBLE
        engineText.setText(R.string.status_conectando)
        renderLoadingPanel()
    }

    private fun renderLoadingPanel() {
        if (loadingBox.visibility != View.VISIBLE) return
        loadingBar.isIndeterminate = rebuffering
        for (view in listOf(loadingPercent, loadingElapsed, loadingEstimate, loadingAttempt))
            view.visibility = if (rebuffering) View.GONE else View.VISIBLE
        if (rebuffering) return
        val elapsed = (SystemClock.elapsedRealtime() - attemptStartedMs).coerceAtLeast(0L)
        val timing = PlaybackTiming.progress(elapsed, attemptEstimateMs)
        loadingBar.progress = timing.percentOfLimit
        loadingPercent.text = getString(R.string.player_loading_percent, timing.percentOfLimit)
        loadingElapsed.text = getString(R.string.player_loading_elapsed, elapsed / 1000.0)
        loadingEstimate.text = when {
            timing.remainingEstimateSeconds != null -> getString(R.string.player_loading_eta, timing.remainingEstimateSeconds)
            timing.overdue -> getString(R.string.player_loading_over_eta)
            else -> getString(R.string.player_loading_no_eta, PlaybackTiming.ATTEMPT_LIMIT_MS / 1000L)
        }
        val route = if (CameraStreamProtocol.isRtsp(urls.getOrNull(index).orEmpty())) rtspTransport.name
            else getString(R.string.player_route_default)
        loadingAttempt.text = getString(R.string.player_loading_attempt, attemptNumber, index + 1, urls.size, route)
    }

    private fun setConnectionStage(stage: Int) {
        if (videoReady || currentStage == stage) return
        currentStage = stage
        loadingDetail.setText(stage)
    }

    private fun tickConnectionPanel(current: Int) {
        if (!active || current != generation || videoReady || failedGeneration == current) return
        renderLoadingPanel()
        mainHandler.postDelayed({ tickConnectionPanel(current) }, 250L)
    }
    private fun videoAvailable() {
        val first = !videoReady
        val resumed = rebuffering
        rebuffering = false
        if (first) loadingBar.progress = PlaybackTiming.progress(0, null, firstFrame = true).percentOfLimit
        videoReady = true; loadingBox.visibility = View.GONE; errorBox.visibility = View.GONE
        if (resumed && !first) event("Rebufferização encerrada; vídeo retomado")
        engineText.setText(R.string.status_ao_vivo); updateRecordButton()
        if (first) {
            updateViewerChrome()
            requestVlcVideoSize()
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - attemptStartedMs
            if (firstFrameInSessionMs == null) firstFrameInSessionMs = now - sessionStartedMs
            rememberFirstFrame(elapsed)
            event("Primeiro quadro de vídeo exibido · $elapsed ms nesta tentativa")
            val recovered = pendingRecovered
            pendingRecovered = null
            if (index == 0 && recovered != null && urls.firstOrNull() == recovered.second) {
                val savedCameras = CameraStore(this).load()
                val position = savedCameras.indexOfFirst { it.url == recovered.first }
                if (position >= 0) {
                    val old = savedCameras[position]
                    savedCameras[position] = old.copy(url = recovered.second,
                        alts = (listOf(recovered.first) + old.alts).filter { it != recovered.second }.distinct())
                    CameraStore(this).save(savedCameras)
                    event("Novo endereço aplicado após primeiro quadro real")
                    Toast.makeText(this, "Vídeo confirmado; endereço atualizado", Toast.LENGTH_LONG).show()
                }
            }
        }
        if (first && recordPending) { recordPending = false; beginRecording() }
    }
    private fun playCurrent() {
        val url = urls.getOrNull(index)
        if (!media3RtspEnabled) engine = Engine.VLC
        val current = ++generation; failedGeneration = -1
        releasePlayers(); videoReady = false; videoGeometry = null; applyDisplayMode(); errorBox.visibility = View.GONE
        if (url == null) { event("Nenhum endereço de vídeo compatível disponível", failed = true); showFailure(); return }
        // Aplicar somente a escolha explícita do usuário a cada tentativa do player.
        selectRtspRoute()
        val u = Uri.parse(url)
        val isRtsp = CameraStreamProtocol.isRtsp(url)
        configureAttemptTiming(url)
        val attemptLabel = if (isRtsp)
            "${rtspTransport.name}, auth=${if (rtspOptionsOnly) "opções" else "URL"}"
        else "nativo"
        event("Tentativa $attemptNumber · fluxo ${index + 1}/${urls.size} [${CameraStreamProtocol.description(url)}]: ${u.host ?: "?"}:${u.port.takeIf { it in 1..65535 } ?: CameraStreamProtocol.defaultPort(url)}${safeReportPath(url)} ($attemptLabel)")
        if (attemptNumber > 1) currentStage = R.string.player_loading_retry
        loading(currentStage)
        tickConnectionPanel(current)
        exoView.visibility = if (engine == Engine.MEDIA3) View.VISIBLE else View.GONE
        vlcView.visibility = if (engine == Engine.VLC) View.VISIBLE else View.GONE
        updateRecordButton()
        mainHandler.postDelayed({
            if (active && generation == current && !videoReady) onAttemptFailed(
                if (engine == Engine.MEDIA3) "Media3" else "LibVLC", "Timeout de ${PlaybackTiming.ATTEMPT_LIMIT_MS / 1000} s aguardando a primeira imagem")
        }, PlaybackTiming.ATTEMPT_LIMIT_MS)
        if (engine == Engine.MEDIA3) startMedia3(url, current) else startVlc(url, current)
    }
    private fun startMedia3(url: String, current: Int) {
        try {
            val loadControl = DefaultLoadControl.Builder().setBufferDurationsMs(500, 2000, 250, 500).build()
            val source = RtspMediaSource.Factory().setForceUseRtpTcp(true).setTimeoutMs(10_000)
                .createMediaSource(MediaItem.fromUri(url))
            val next = ExoPlayer.Builder(this).setLoadControl(loadControl).build(); exo = next
            next.addListener(object : Player.Listener {
                override fun onVideoSizeChanged(size: VideoSize) {
                    if (active && generation == current) setVideoAspect(size.width, size.height,
                        size.pixelWidthHeightRatio.toDouble(), size.unappliedRotationDegrees % 180 != 0)
                }
                override fun onRenderedFirstFrame() { if (active && generation == current) videoAvailable() }
                override fun onPlaybackStateChanged(state: Int) {
                    if (!active || generation != current) return
                    if (state == Player.STATE_BUFFERING) loading(if (videoReady) R.string.buffer_imagem else R.string.aguarde_imagem)
                    if (state == Player.STATE_READY && videoReady) videoAvailable()
                    if (state == Player.STATE_ENDED) onAttemptFailed("Media3", "Transmissão encerrada")
                }
                override fun onPlayerError(error: PlaybackException) {
                    if (!active || generation != current) return
                    val detail = error.errorCodeName + "\n" + causeSummary(error)
                    if (detail.contains("sprop-vps", true) || detail.contains("hevc", true) || detail.contains("h265", true)) hevcSeen = true
                    onAttemptFailed("Media3", detail)
                }
            })
            exoView.player = next; next.setMediaSource(source); next.prepare(); next.playWhenReady = true
            applyDisplayMode()
        } catch (error: Exception) { onAttemptFailed("Media3", causeSummary(error)) }
          catch (error: LinkageError) { onAttemptFailed("Media3", "Componente de reprodução indisponível (${error.javaClass.simpleName})") }
    }
    private fun startVlc(url: String, current: Int) {
        try {
            // TCP é a primeira tentativa; UDP é a segunda, sem forçar rtsp-tcp.
            val coreOptions = arrayListOf("--network-caching=500", "--stats")
            val parsedRtsp = Uri.parse(url)
            val encodedInfo = if (CameraStreamProtocol.isRtsp(url)) parsedRtsp.encodedUserInfo else null
            // VLC live555 inherits rtsp-user/rtsp-pwd from the core if media-local
            // options are not honored by this Android build. Never export these values.
            if (rtspOptionsOnly && !encodedInfo.isNullOrBlank()) {
                val user = Uri.decode(encodedInfo.substringBefore(':'))
                val pass = Uri.decode(encodedInfo.substringAfter(':', ""))
                if (user.isNotBlank()) {
                    coreOptions.add("--rtsp-user=$user")
                    coreOptions.add("--rtsp-pwd=$pass")
                }
            }
            if (CameraStreamProtocol.isRtsp(url) && rtspTransport == RtspTransport.TCP)
                coreOptions.add("--rtsp-tcp")
            val core = LibVLC(this, coreOptions); vlcCore = core
            val next = VlcMediaPlayer(core); vlcPlayer = next
            lastVlcState = "Inicializando ${CameraStreamProtocol.description(url)}"
            next.setEventListener { event ->
                // Recording close events must be handled even while leaving the screen.
                val eventType = event.type
                val bufferingPercent = if (eventType == VlcMediaPlayer.Event.Buffering) event.buffering else null
                val eventRecording = if (eventType == VlcMediaPlayer.Event.RecordChanged) event.recording else false
                runOnUiThread {
                    if (eventType == VlcMediaPlayer.Event.RecordChanged && recordingPlayer === next) {
                        val session = recordingSession
                        if (session != null) {
                            if (eventRecording && !stoppingRecording && !recordingConfirmed) {
                                recordingConfirmed = true
                                event("VLC confirmou início da gravação")
                                updateRecordButton()
                                tickRecording(session)
                            } else if (!eventRecording) {
                                if (!stoppingRecording)
                                    event("VLC encerrou a gravação sem pedido de parada", failed = true)
                                else event("VLC confirmou encerramento da gravação")
                                finishRecording(session)
                            }
                        }
                    }
                    if (!active || generation != current || engine != Engine.VLC) return@runOnUiThread
                    when (eventType) {
                        VlcMediaPlayer.Event.Opening -> {
                            if (lastVlcState != "Abrindo fluxo") event("LibVLC iniciou a abertura")
                            lastVlcState = "Abrindo fluxo"
                        }
                        VlcMediaPlayer.Event.Playing -> {
                            if (lastVlcState != "Reprodução iniciada") event("LibVLC iniciou a reprodução")
                            lastVlcState = "Reprodução iniciada"
                            if (!sawMedia && !sawDecode) setConnectionStage(R.string.player_loading_wait_media)
                        }
                        VlcMediaPlayer.Event.Vout -> readVlcVideoSize(next)
                        VlcMediaPlayer.Event.Buffering -> {
                            val percent = bufferingPercent?.toInt()?.coerceIn(0, 100) ?: 0
                            val band = percent / 25
                            if (band != lastVlcBufferEvent) {
                                lastVlcBufferEvent = band
                                event("Buffer do LibVLC: $percent%")
                            }
                            if (percent < 100) loading(if (videoReady) R.string.buffer_imagem else currentStage)
                            else if (videoReady) videoAvailable()
                        }
                        VlcMediaPlayer.Event.EncounteredError -> {
                            lastVlcStats = vlcStatsSnapshot(next)
                            lastVlcState = "Erro nativo: ${CameraStreamProtocol.description(url)}"
                            onAttemptFailed("LibVLC", "Erro nativo. $lastVlcStats")
                        }
                        VlcMediaPlayer.Event.EndReached -> {
                            lastVlcStats = vlcStatsSnapshot(next)
                            lastVlcState = "Stream terminou"
                            onAttemptFailed("LibVLC", "Transmissão encerrada. $lastVlcStats")
                        }
                    }
                }
            }
            // TextureView participates in the normal View clip; SurfaceView could draw past it.
            // The clipped video box can be taller than it is wide even in landscape.
            // Use those actual bounds, not the device orientation, to size the VLC output.
            next.setUseOrientationFromBounds(true)
            findVideoOutput(vlcView, TextureView::class.java)?.let { texture ->
                texture.layoutParams = texture.layoutParams.apply { width = -1; height = -1 }
            }
            next.attachViews(vlcView, null, false, true)
            vlcTexture = findVideoOutput(vlcView, TextureView::class.java)
            vlcTextureListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                if (active && generation == current && videoReady && videoGeometry == null)
                    readVlcVideoSize(next)
            }.also { vlcTexture?.addOnLayoutChangeListener(it) }
            syncVlcWindowSize()
            // Live555 prefere as credenciais da URL quando há user-info. Alguns firmwares
            // falham nessa negociação: na segunda tentativa, remova APENAS o user-info
            // da URL passada ao player e informe o login via opções RTSP nativas.
            // A URL original permanece intacta no cadastro e nunca é exportada.
            val mediaUri = if (rtspOptionsOnly && !encodedInfo.isNullOrBlank()) {
                parsedRtsp.buildUpon().encodedAuthority(
                    parsedRtsp.encodedAuthority.orEmpty().substringAfterLast('@')
                ).build()
            } else parsedRtsp
            val media = Media(core, mediaUri)
            if (rtspOptionsOnly && !encodedInfo.isNullOrBlank()) {
                val user = Uri.decode(encodedInfo.substringBefore(':'))
                val pass = Uri.decode(encodedInfo.substringAfter(':', ""))
                if (user.isNotBlank()) {
                    media.addOption(":rtsp-user=$user")
                    media.addOption(":rtsp-pwd=$pass")
                }
            }
            if (CameraStreamProtocol.isRtsp(url) && rtspTransport == RtspTransport.TCP)
                media.addOption(":rtsp-tcp")
            media.addOption(":network-caching=500")
            next.setMedia(media); media.release(); next.play(); applyDisplayMode()
            // Playing means input is running; actual displayed pictures gate the loading screen.
            fun awaitPicture() {
                if (!active || generation != current || failedGeneration == current || videoReady || vlcPlayer !== next) return
                readVlcVideoSize(next)
                val stats = readVlcCounters(next)
                if (stats != null) {
                    if (!sawMedia && (stats.demux > 0 || stats.decoded > 0)) {
                        sawMedia = true
                        event("Primeiros dados de mídia observados · bytes=${stats.bytes}; demux=${stats.demux}")
                        setConnectionStage(R.string.player_loading_decoding)
                    }
                    if (!sawDecode && stats.decoded > 0) {
                        sawDecode = true
                        event("Primeiro quadro decodificado observado")
                        setConnectionStage(R.string.player_loading_wait_frame)
                    }
                    if (stats.shown > 0) {
                        lastVlcStats = stats.describe()
                        videoAvailable()
                        return
                    }
                }
                mainHandler.postDelayed({ awaitPicture() }, 250L)
            }
            mainHandler.postDelayed({ awaitPicture() }, 250L)
        } catch (error: Exception) { onAttemptFailed("LibVLC", causeSummary(error)) }
        catch (error: LinkageError) { onAttemptFailed("LibVLC", "Biblioteca nativa indisponível (${error.javaClass.simpleName})") }
    }
    private fun onRecordClick() {
        if (recordPending) {
            event("Preparação de gravação cancelada")
            recordPending = false; updateRecordButton(); return
        }
        if (recordingSession != null) { stopRecording(); return }
        if (!videoReady || finalizingCount > 0) return
        event("Gravação solicitada")
        AlertDialog.Builder(this).setTitle(R.string.gravar).setMessage(R.string.gravacao_local_hint)
            .setNegativeButton(R.string.cancelar) { _, _ -> event("Gravação cancelada antes de iniciar") }
            .setOnCancelListener { event("Confirmação de gravação fechada") }
            .setPositiveButton(R.string.iniciar_gravacao) { _, _ ->
                if (!active || !videoReady) return@setPositiveButton
                if (engine == Engine.MEDIA3) { recordPending = true; engine = Engine.VLC; playCurrent() } else beginRecording()
            }.show()
    }
    private fun beginRecording() {
        val player = vlcPlayer
        if (player == null || !active || !videoReady || recordingSession != null || finalizingCount > 0) {
            event("Gravação não iniciada: player ou vídeo indisponível, ou outro salvamento em andamento", failed = true)
            return
        }
        val started = runCatching { RecordingStore.newSession(this) }
        val session = started.getOrNull()
        if (session == null || session.usableSpace < 100L*1024*1024) {
            val reason = started.exceptionOrNull()?.let(::causeSummary)
                ?: if (session == null) "pasta de sessão indisponível" else "espaço insuficiente"
            event("Gravação não iniciada: $reason", failed = true)
            session?.delete(); Toast.makeText(this, R.string.gravacao_espaco, Toast.LENGTH_LONG).show(); updateRecordButton(); return
        }
        recordingSession = session; recordingPlayer = player; stoppingRecording = false
        recordingConfirmed = false; recordingStart = SystemClock.elapsedRealtime()
        event("Pedido de início enviado ao VLC")
        val nativeStart = runCatching { player.record(session.absolutePath) }
        if (!nativeStart.getOrDefault(false)) {
            val reason = nativeStart.exceptionOrNull()?.let(::causeSummary) ?: "operação recusada"
            event("VLC não iniciou a gravação: $reason", failed = true)
            recordingSession = null; recordingPlayer = null; session.delete()
            Toast.makeText(this, R.string.gravacao_falhou, Toast.LENGTH_LONG).show()
        } else mainHandler.postDelayed({
            if (recordingSession == session && !recordingConfirmed && !stoppingRecording) {
                event("VLC não confirmou a gravação em 8 s", failed = true)
                Toast.makeText(this, R.string.gravacao_sem_confirmacao, Toast.LENGTH_LONG).show(); stopRecording()
            }
        }, 8_000L)
        updateRecordButton()
    }
    private fun tickRecording(session: File) {
        if (!active || recordingSession != session || !recordingConfirmed || stoppingRecording) return
        val secs = (SystemClock.elapsedRealtime()-recordingStart)/1000
        recordTimer.text = String.format(Locale.ROOT, "● REC %02d:%02d", secs/60, secs%60)
        recordTimer.visibility = View.VISIBLE
        if (session.usableSpace < 16L*1024*1024) {
            event("Gravação interrompida por espaço insuficiente", failed = true)
            Toast.makeText(this, R.string.gravacao_espaco, Toast.LENGTH_LONG).show(); stopRecording(); return
        }
        mainHandler.postDelayed({ tickRecording(session) }, 1000L)
    }
    private fun stopRecording() {
        val session = recordingSession ?: return
        if (stoppingRecording) return
        stoppingRecording = true; updateRecordButton()
        event("Encerramento da gravação solicitado")
        val native = runCatching { recordingPlayer?.record(null) }
        if (native.getOrNull() != true)
            event("VLC não aceitou o pedido de parada: ${native.exceptionOrNull()?.let(::causeSummary) ?: "sem confirmação"}", failed = true)
        // If native closure is not confirmed, stop the native player before moving files.
        mainHandler.postDelayed({
            if (recordingSession == session && stoppingRecording) {
                event("VLC não confirmou a parada; player será liberado antes de salvar", failed = true)
                val current = generation
                releasePlayers()
                if (active && generation == current) playCurrent()
            }
        }, 1500L)
    }
    private fun finishRecording(session: File) {
        if (!finalizingSessions.add(session.absolutePath)) return
        if (recordingSession == session) {
            recordingSession = null; recordingPlayer = null; recordingConfirmed = false; stoppingRecording = false
        }
        finalizingCount++; recordTimer.visibility = View.GONE; updateRecordButton()
        val app = applicationContext
        RecordingStore.io.execute {
            val result = runCatching { RecordingFiles.finish(RecordingStore.root(app), session) }
            val saved = result.getOrNull().orEmpty()
            val bytes = saved.sumOf { it.length() }
            runOnUiThread {
                when {
                    result.isFailure -> event("Falha ao salvar gravação: ${result.exceptionOrNull()?.let(::causeSummary)}", failed = true)
                    saved.isEmpty() -> event("Encerramento não gerou gravação utilizável", failed = true)
                    else -> event("Gravação salva · ${saved.size} arquivo(s) · $bytes bytes")
                }
                finalizingCount--; if (!isDestroyed) updateRecordButton()
                RecordingStore.notifyChanged()
                val message = when {
                    result.isFailure -> R.string.gravacao_falhou
                    saved.isNotEmpty() -> R.string.gravacao_salva
                    else -> R.string.gravacao_vazia
                }
                Toast.makeText(app, message, Toast.LENGTH_LONG).show()
            }
        }
    }
    private fun updateRecordButton() {
        if (!::recordButton.isInitialized) return
        val text = when {
            stoppingRecording || finalizingCount > 0 -> R.string.salvando_gravacao
            recordingSession != null && recordingConfirmed -> R.string.parar_gravacao
            recordingSession != null || recordPending -> R.string.preparando_gravacao
            else -> R.string.gravar
        }
        recordButton.setText(text)
        recordButton.setIconResource(if (recordingSession != null || recordPending) R.drawable.ic_stop else R.drawable.ic_record)
        recordButton.isEnabled = active && !stoppingRecording && finalizingCount == 0 && (videoReady || recordPending || recordingSession != null)
    }
    private fun onAttemptFailed(from: String, detail: String) {
        if (!active || failedGeneration == generation) return
        failedGeneration = generation
        if (engine == Engine.VLC && !detail.contains("bytes=")) {
            vlcPlayer?.let { lastVlcStats = vlcStatsSnapshot(it) }
            event("Estado VLC: $lastVlcState · $lastVlcStats")
        }
        val currentUrl = urls.getOrNull(index).orEmpty()
        val isRtsp = engine == Engine.VLC && CameraStreamProtocol.isRtsp(currentUrl)
        val via = if (isRtsp) ", ${rtspTransport.name}, auth=${if (rtspOptionsOnly) "opções" else "URL"}" else ""
        failures.add("$from (fluxo ${index+1}$via): ${maskCredentials(detail)}")
        event("Falha $from fluxo ${index+1}$via: ${maskCredentials(detail)}", failed = true)
        // Antes de trocar TCP/UDP, tentar a segunda estratégia somente se há login.
        // Não insistir em autenticação alternativa para fluxos anônimos.
        val retryAuth = isRtsp && !rtspOptionsOnly &&
            !Uri.parse(currentUrl).encodedUserInfo.isNullOrBlank()
        val next = if (engine == Engine.VLC && !retryAuth)
            CameraStreamProtocol.nextAttempt(index, rtspTransport, urls) else null
        when {
            retryAuth -> { rtspOptionsOnly = true }
            next != null -> {
                index = next.urlIndex; rtspTransport = next.transport; rtspOptionsOnly = false
            }
            engine == Engine.MEDIA3 && media3RtspEnabled -> {
                engine = Engine.VLC; index = 0; rtspTransport = RtspTransport.TCP; rtspOptionsOnly = false
            }
            else -> { showFailure(); return }
        }
        val failed = generation
        vlcView.post { if (active && generation == failed && !isFinishing && !isDestroyed) playCurrent() }
    }
    private fun showFailure() {
        releasePlayers(); recordPending = false; videoReady = false; loadingBox.visibility = View.GONE
        updateViewerChrome()
        engineText.setText(R.string.player_connection_failed)
        if (urls.none { CameraStreamProtocol.isRtsp(it) })
            errorText.text = "Não foi possível abrir o vídeo. Confira a URL e o formato transmitido."
        else errorText.setText(if (hevcSeen) R.string.erro_h265 else R.string.erro_generico)
        val initial = failures.takeLast(8).joinToString("\n")
            .ifBlank { getString(R.string.erro_sem_fluxos) }
        rtspAnalysis = if (urls.any { CameraStreamProtocol.isRtsp(it) })
            getString(R.string.rtsp_diagnostic_running)
        else "Conexão não-RTSP: verifique URL, conteúdo do servidor e logs VLC."
        errorDetail.text = initial + "\n" + rtspAnalysis
        diagnosticDialog?.takeIf { it.isShowing }?.update(reportSnapshot())
        errorBox.visibility = View.VISIBLE; updateRecordButton()
        pendingRecovered = null
        event("Nenhum fluxo produziu imagem após as tentativas")
        if (urls.any { CameraStreamProtocol.isRtsp(it) })
            diagnoseRtspAsync(generation, initial)
    }

    /** Diagnóstico DESCRIBE independente do decoder; nunca mostra URL, usuário ou senha. */
    private fun diagnoseRtspAsync(checkedGeneration: Int, originalMessage: String) {
        val addresses = urls.filter { CameraStreamProtocol.isRtsp(it) }.take(2)
        Thread({
            val lines = addresses.mapIndexed { i, address ->
                val result = try {
                    val parsed = Uri.parse(address)
                    val host = parsed.host
                    val port = parsed.port.takeIf { it in 1..65535 } ?: 554
                    if (host.isNullOrBlank()) getString(R.string.rtsp_diagnostic_bad_url)
                    else {
                        val service = RtspServiceDetector.inspect(host, port)
                        if (service.kind == RtspServiceKind.CLOSED) {
                            getString(R.string.rtsp_diagnostic_port_closed, port)
                        } else if (service.kind == RtspServiceKind.HTTP || service.kind == RtspServiceKind.OTHER) {
                            "Porta $port: ${service.detail}. Confira o endereço RTSP."
                        } else {
                        val auth = parsed.encodedUserInfo.orEmpty()
                        val user = Uri.decode(auth.substringBefore(':'))
                        val password = Uri.decode(auth.substringAfter(':', ""))
                        val path = (parsed.encodedPath ?: "/") +
                            (parsed.encodedQuery?.let { "?$it" } ?: "")
                        val probe = RtspProbe.probe(host, port, user, password, path)
                        val resultDetail = when (probe.status) {
                            ProbeStatus.OK -> getString(R.string.rtsp_diagnostic_describe_ok,
                                probe.videoCodec ?: "codec não identificado") + " (${probe.detail})"
                            ProbeStatus.AUTH_FAIL -> getString(R.string.rtsp_diagnostic_auth) +
                                " (${probe.detail}). Confira usuário e senha de acesso NVR/RTSP nas configurações da câmera."
                            ProbeStatus.NOT_FOUND -> getString(R.string.rtsp_diagnostic_path)
                            ProbeStatus.ERROR -> "DESCRIBE: ${probe.detail}. " +
                                getString(R.string.rtsp_diagnostic_no_describe)
                        }
                        // DESCRIBE 200 não comprova que SETUP/PLAY aceitam a faixa.
                        // Sondagem curta, somente RTP/TCP e apenas quando há SDP com vídeo.
                        val sessionDetail = if (probe.status == ProbeStatus.OK) {
                            val result = RtspProbe.probeSetupPlay(host, port, user, password, path)
                            "; sequência TCP: ${result.stage} " +
                                (result.code?.toString() ?: "sem código") + "; ${result.detail}"
                        } else ""
                        val authPresent = if (auth.isNotBlank()) "sim" else "não"
                        // 'debug' inclui apenas esquema anunciado e códigos RTSP, nunca nonce/login/senha.
                        val safeStages = probe.debug.trim().replace('\n', ' ')
                        "${service.detail}; Credenciais cadastradas: $authPresent; " +
                            "$resultDetail" + sessionDetail + if (safeStages.isBlank()) "" else "; $safeStages"
                        }
                    }
                } catch (_: Exception) {
                    getString(R.string.rtsp_diagnostic_no_describe)
                }
                getString(R.string.rtsp_diagnostic_stream, i + 1, result)
            }
            runOnUiThread {
                if (active && generation == checkedGeneration &&
                    !isFinishing && !isDestroyed && errorBox.visibility == View.VISIBLE) {
                    rtspAnalysis = lines.joinToString("\n")
                    errorDetail.text = originalMessage + "\n\n" +
                        getString(R.string.rtsp_diagnostic_title) + "\n" + rtspAnalysis
                    event("Diagnóstico DESCRIBE concluído")
                    // A janela exportável pode ter sido aberta antes do término do DESCRIBE.
                    diagnosticDialog?.takeIf { it.isShowing }?.update(reportSnapshot())
                }
            }
        }, "rtsp-diagnostic").apply { isDaemon = true; start() }
    }
    private fun causeSummary(error: Throwable): String = generateSequence(error) { it.cause }.take(6)
        .joinToString(" → ") { it.javaClass.simpleName }
    private fun maskCredentials(input: String): String = PlaybackJournal.mask(input)
}
