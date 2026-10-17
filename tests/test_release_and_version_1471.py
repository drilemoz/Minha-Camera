"""Releases visíveis e aviso de versão no protocolo autenticado (1.4.71)."""
from pathlib import Path
import re
import unittest
ROOT=Path(__file__).resolve().parents[1]
P=ROOT/'app/src/main/java/com/minhacamera'
def src(n): return (P/n).read_text(encoding='utf-8')
class ReleaseAndCompatibility1471(unittest.TestCase):
    def test_about_and_welcome_use_common_latest_highlights(self):
        history=src('PhoneReleaseHighlights.kt')
        version=src('WelcomeActivity.kt')
        about=src('AboutActivity.kt')
        self.assertIn('PhoneReleaseHighlights.history()', about)
        self.assertIn('PhoneReleaseHighlights.latest.first()', version)
        self.assertIn('Entry("1.4.75", 85',history)
        self.assertIn('Entry("1.4.76", 86',history)
        self.assertIn('Entry("1.4.74", 84',history)
        self.assertNotIn('1.4.55+65', about)
        self.assertNotIn('Alternativa de autenticação VLC', version)
    def test_release_and_resources_synced(self):
        gradle=(ROOT/'app/build.gradle.kts').read_text()
        resources=(ROOT/'app/src/main/res/values/strings.xml').read_text()
        self.assertIn('versionName = "1.4.76"',gradle)
        self.assertIn('versionCode = 86',gradle)
        self.assertIn('Versão 1.4.76 (código 86)',resources)
        self.assertIn('Novidades 1.4.76',resources)
    def test_both_transmitters_announce_only_in_authenticated_routes(self):
        server=src('PhoneCameraServer.kt')
        self.assertEqual(server.count('X-App-Version:'),2)
        self.assertIn('"app_version"',server.replace('\\"','"'))
        self.assertIn('GET $control/status',server)
        self.assertIn('GET $mcrpRoot/media',server)
        self.assertNotIn('appVersion',src('PhoneCameraUrls.kt'))
        self.assertEqual(src('PhoneCameraActivity.kt').count('appVersion ='),2)
        self.assertEqual(src('PhoneCameraBackgroundService.kt').count('appVersion ='),1)
    def test_remote_viewer_and_gallery_warn_without_disconnecting(self):
        viewer=src('PhoneCameraViewerActivity.kt')
        gallery=src('RemoteMultimediaActivity.kt')
        for text in [viewer,gallery]:
            self.assertIn('PhoneVersionNotice.message(local, remote)',text)
            self.assertIn('Snackbar.make',text)
            self.assertIn('X-App-Version',text)
            self.assertIn('lastVersionNoticeKey',text)
        self.assertIn('if (state.authenticated)',viewer)
        self.assertIn('result.onSuccess { response ->',gallery)
        self.assertNotIn('stop(); start()',src('PhoneVersionNotice.kt'))
    def test_unknown_version_and_protocol_kept(self):
        helper=src('PhoneVersionNotice.kt')
        server=src('PhoneCameraServer.kt')
        self.assertIn('other.isEmpty()',helper)
        self.assertIn('mine == other -> null',helper)
        self.assertIn('"version":1',server.replace('\\"','"'))
if __name__=='__main__': unittest.main()
