import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main"
SRC = MAIN / "java/com/minhacamera"

def source(name):
    return (SRC / name).read_text()

class PlaybackDiagnosticsTests(unittest.TestCase):
    def test_meter_keeps_real_attempt_limit_and_observed_frame(self):
        pure = source("PlaybackDiagnostics.kt")
        player = source("PlayerActivity.kt")
        self.assertIn("ATTEMPT_LIMIT_MS = 15_000L", pure)
        self.assertIn(".coerceAtMost(99)", pure)
        self.assertIn("if (firstFrame) return Progress(100", pure)
        self.assertIn("}, PlaybackTiming.ATTEMPT_LIMIT_MS)", player)
        self.assertIn("val timing = PlaybackTiming.progress(elapsed, attemptEstimateMs)", player)
        self.assertIn("if (stats.shown > 0)", player)
        self.assertIn("rememberFirstFrame(elapsed)", player)
        self.assertIn("PlaybackTiming.estimate(timingSamples(key))", player)

    def test_player_collects_actions_and_recording_errors(self):
        player = source("PlayerActivity.kt")
        for message in ("Câmera selecionada na lista", "Tela cheia ativada", "Exibição selecionada",
                        "PiP ativado", "Captura JPEG solicitada", "Gravação solicitada",
                        "VLC confirmou início da gravação", "VLC não confirmou a gravação em 8 s",
                        "Falha ao salvar gravação", "Encerramento não gerou gravação utilizável",
                        "Gravação salva", "Primeiros dados de mídia observados"):
            self.assertIn(message, player)
        self.assertIn('putExtra("selected_at_ms", SystemClock.elapsedRealtime())', source("MainActivity.kt"))
        self.assertIn('putString("last_player_crash", PlaybackJournal.mask(summary))', source("CameraApplication.kt"))

    def test_journal_uses_private_bounded_storage_and_fresh_export(self):
        player = source("PlayerActivity.kt")
        self.assertIn('getSharedPreferences("rtsp_session_journal", MODE_PRIVATE)', player)
        self.assertIn('journalPrefs.getString("previous_id", null) -> "previous"', player)
        self.assertIn("snapshotProvider = ::reportSnapshot", player)
        self.assertIn("snapshotProvider?.invoke() ?: body.text.toString()", source("CameraReportDialog.kt"))
        self.assertIn("PlaybackJournal.mask(input)", player)
        safe_path = player.split("private fun safeReportPath", 1)[1].split("private fun reportSnapshot", 1)[0]
        self.assertIn("RtspProbe.PATHS", safe_path)
        cause = player.split("private fun causeSummary", 1)[1].split("private fun maskCredentials", 1)[0]
        self.assertNotIn(".message", cause)
        self.assertIn("while (timeline.size > limit)", source("PlaybackDiagnostics.kt"))

    def test_diagnostic_access_and_timing_in_layout(self):
        root = ET.parse(MAIN / "res/layout/activity_player.xml").getroot()
        android = "{http://schemas.android.com/apk/res/android}"
        ids = {element.get(android + "id") for element in root.iter()}
        for item in ("loadingPercent", "loadingElapsed", "loadingEstimate", "loadingAttempt",
                     "btnLoadingDiagnostic", "btnFullscreenDiagnostic"):
            self.assertIn("@+id/" + item, ids)
        self.assertEqual(source("PlayerActivity.kt").count("setOnClickListener { showDiagnosticReport() }"), 3)

if __name__ == "__main__":
    unittest.main()
