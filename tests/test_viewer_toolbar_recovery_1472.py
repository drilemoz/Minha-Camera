"""A tela do receptor deve usar ActionBar real, sem duplicar os botões nos cartões."""
import unittest
from pathlib import Path
from xml.etree import ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
A='{http://schemas.android.com/apk/res/android}'
SRC=ROOT/'app/src/main/java/com/minhacamera'
MAIN=ET.parse(ROOT/'app/src/main/res/layout/activity_phone_viewer.xml').getroot()
BAR=ET.parse(ROOT/'app/src/main/res/layout/phone_viewer_actionbar.xml').getroot()
def ids(root): return {v.get(A+'id','').replace('@+id/','') for v in root.iter()}


class ViewerToolbarRecovery1472(unittest.TestCase):
    def test_codec_and_bug_are_in_actionbar_not_under_title(self):
        assert {'viewerActionbarDeviceName','viewerCodecTop','viewerDiagnosticTop'} <= ids(BAR)
        assert not {'viewerCodecTop','viewerDiagnosticTop','viewerRemoteZoom'} & ids(MAIN)
        source=(SRC/'PhoneCameraViewerActivity.kt').read_text()
        assert 'setCustomView(R.layout.phone_viewer_actionbar)' in source
        assert 'findViewById<TextView>(R.id.viewerActionbarDeviceName)?.text = title' in source
        assert 'scrollContent.smoothScrollTo(0, 0)' in source

    def test_zoom_keeps_pinching_and_compact_quick_actions_without_card(self):
        assert {'viewerZoomMinus','viewerZoomReset','viewerZoomPlus'} <= ids(MAIN)
        source=(SRC/'PhoneCameraViewerActivity.kt').read_text()
        assert 'remoteZoomLabel = findViewById(R.id.viewerZoomReset)' in source
        assert 'setOnLongClickListener' in source
        assert 'onScale(' in source or 'onScale(detector' in source

    def test_bg_watchdog_reduces_load_only_after_stall(self):
        service=(SRC/'PhoneCameraBackgroundService.kt').read_text()
        policy=(SRC/'PhoneBackgroundRecovery.kt').read_text()
        assert 'if (age > 12_000' in service
        assert 'PhoneBackgroundRecovery.nextQuality(requestedQuality)' in service
        assert 'PhoneBackgroundRecovery.nextFps(fpsLimit)' in service
        assert 'firstFramePublished = false' in service
        assert 'h264FailedThisSession = true' in service
        assert 'h264FailedThisSession) {' in service
        assert 'current > 720 -> 720' in policy
        assert 'current > 15 -> 15' in policy
        assert 'testBackgroundRecovery' in (ROOT/'build.gradle.kts').read_text()
