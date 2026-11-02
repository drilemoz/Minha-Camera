"""Regressão Build APK 77: compilador Kotlin exige android.view.PixelCopy."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PLAYER = ROOT / 'app/src/main/java/com/minhacamera/PlayerActivity.kt'

class PlayerPixelCopy1479(unittest.TestCase):
    def test_correct_android_import(self):
        source = PLAYER.read_text(encoding='utf-8')
        self.assertIn('import android.view.PixelCopy', source)
        self.assertNotIn('import android.graphics.PixelCopy', source)

    def test_request_and_media_store_present(self):
        source = PLAYER.read_text(encoding='utf-8')
        self.assertIn('PixelCopy.request(surface, bitmap,', source)
        self.assertIn('PixelCopy.SUCCESS', source)
        self.assertIn('MediaStore.Images.Media.IS_PENDING', source)
        self.assertIn('if (!videoReady || Build.VERSION.SDK_INT < 29)', source)

if __name__ == '__main__': unittest.main()
