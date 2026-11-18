import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main"
PLAYER = MAIN / "java/com/minhacamera/PlayerActivity.kt"
ANDROID = "{http://schemas.android.com/apk/res/android}"

class LensFullscreenTests(unittest.TestCase):
    def setUp(self):
        self.player = PLAYER.read_text()
        self.layout = ET.parse(MAIN / "res/layout/activity_player.xml").getroot()

    def function(self, start, end):
        return self.player.split(start, 1)[1].split(end, 1)[0]

    def test_clip_participates_in_view_hierarchy(self):
        self.assertIn("next.attachViews(vlcView, null, false, true)", self.player)
        clip = next(v for v in self.layout.iter() if v.get(ANDROID + "id") == "@+id/videoClip")
        self.assertEqual(clip.get(ANDROID + "clipChildren"), "true")
        self.assertTrue(any(v.get(ANDROID + "id") == "@+id/vlcView" for v in clip.iter()))
        apply = self.function("    private fun applyDisplayMode()", "    private fun identifyCamera()")
        self.assertIn("VideoGeometry.fitHalf", apply)
        self.assertIn("VideoGeometry.fitFull", apply)
        self.assertIn("videoContainer.translationY = frame?.videoTop ?: 0f", apply)

    def test_same_lens_and_player_when_entering_fullscreen(self):
        self.assertIn("R.id.btnFullscreenImage).setOnClickListener { showDisplayOptions() }", self.player)
        self.assertIn("R.id.btnFullscreenExit).setOnClickListener { setFullscreen(false) }", self.player)
        fullscreen = self.function("    private fun setFullscreen(", "    private fun configureControls()")
        self.assertNotRegex(fullscreen, r"\bdisplayMode\s*=(?!=)")
        self.assertNotIn("resetAndPlay", fullscreen)
        chooser = self.function("    private fun showDisplayOptions()", "    private fun setFullscreen(")
        self.assertIn("displayMode = which", chooser)
        self.assertNotIn("resetAndPlay", chooser)
        self.assertIn('if (fullscreen || pipVisible) View.GONE else View.VISIBLE', fullscreen)

    def test_unknown_dimensions_keep_live_picture_and_show_compact_notice(self):
        status = next(v for v in self.layout.iter() if v.get(ANDROID + "id") == "@+id/cropNotice")
        self.assertEqual(status.get(ANDROID + "background"), "@drawable/player_badge")
        self.assertEqual(status.get(ANDROID + "layout_height"), "wrap_content")
        self.assertIn("cropNotice.visibility = if (waitingForCrop && !pipVisible)", self.player)
        self.assertIn("Imagem inteira mantida: recorte aguardando dimensões", self.player)
        apply = self.function("    private fun applyDisplayMode()", "    private fun identifyCamera()")
        self.assertIn("frame?.videoTop ?: 0f", apply)
        self.assertNotIn("vlcView.visibility =", apply)

    def test_rotation_updates_geometry_without_reconnecting_or_changing_lens(self):
        rotation = self.function("    override fun onConfigurationChanged", "    override fun onCreateOptionsMenu")
        self.assertIn("VideoGeometry.afterRotation(fullscreenMode, landscape)", rotation)
        self.assertIn("if (landscape == lastLandscape) return", rotation)
        self.assertIn("applyFullscreen()", rotation)
        self.assertIn("!pipVisible && !isInPictureInPictureMode", rotation)
        self.assertIn('putString("fullscreenMode"', self.player)
        self.assertNotRegex(rotation, r"\bdisplayMode\s*=(?!=)")
        for marker in ("resetAndPlay", "playCurrent", "releasePlayers", "detachViews"):
            self.assertNotIn(marker, rotation)
        self.assertIn("next.setUseOrientationFromBounds(true)", self.player)
        self.assertIn("vlcPlayer?.updateVideoSurfaces()", self.player)

    def test_vlc_resolves_missing_active_track_without_guessing_from_screen(self):
        geometry = self.function("    private fun readVlcVideoSize(", "    private fun applyDisplayMode()")
        self.assertIn("player.currentVideoTrack", geometry)
        self.assertIn("media.getTrack(it) as? IMedia.VideoTrack", geometry)
        self.assertIn("tracks.singleOrNull()", geometry)
        self.assertIn("finally { media.release() }", geometry)
        self.assertIn("source = VideoGeometry.Source.RENDERED_LAYOUT", geometry)
        self.assertIn("?.layoutParams", geometry)
        self.assertNotIn("viewport.width", geometry)
        self.assertIn("generation == current", geometry)
        self.assertIn("videoGeometry = null", self.player)
        self.assertIn("removeOnLayoutChangeListener", self.player)

    def test_capture_and_pip_use_correct_output(self):
        self.assertIn("texture.getBitmap", self.player)
        self.assertIn("PixelCopy.request(surface, bitmap,", self.player)
        self.assertIn("VideoGeometry.displayedAspect(videoAspect, displayMode != 0)", self.player)
        self.assertIn("if (displayMode != 0 && aspect != null) videoClip else viewport", self.player)
        start_recording = self.function("    private fun beginRecording()", "    private fun tickRecording(")
        self.assertNotIn("displayMode", start_recording)

if __name__ == "__main__":
    unittest.main()
