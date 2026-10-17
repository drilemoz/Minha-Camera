"""Regressão para compileProtocolTests no Build APK 72 (1.4.74)."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]

class TorchGradleIsolation1475(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.gradle = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")

    def test_no_duplicate_clock_stubs(self):
        text = self.gradle
        self.assertIn('exclude("torch-stubs/**", "PhoneTorchControllerSmoke.kt")', text)
        self.assertIn('fileTree("tests/torch-stubs")', text)
        self.assertIn('"app/src/main/java/com/minhacamera/PhoneTorchController.kt"', text)
        self.assertIn('"tests/PhoneTorchControllerSmoke.kt"', text)
        self.assertNotIn('fileTree("tests") { include("**/*.kt") }', text)

    def test_torch_runs_as_part_of_protocol_workflow(self):
        self.assertIn('tasks.register<JavaExec>("testTorch")', self.gradle)
        self.assertIn('mainClass.set("com.minhacamera.PhoneTorchControllerSmokeKt")', self.gradle)
        self.assertIn('tasks.named("testProtocol") { dependsOn("testTorch") }', self.gradle)
        self.assertTrue((ROOT / "tests/protocol-stubs/SystemClockStub.kt").is_file())
        self.assertTrue((ROOT / "tests/torch-stubs/HandlerStub.kt").is_file())

if __name__ == "__main__":
    unittest.main()
