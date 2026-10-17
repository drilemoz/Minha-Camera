"""Evita UnknownTaskException na avaliação do Gradle (Build APK 73)."""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

class GradleRegistration1476(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")

    def test_named_tasks_have_prior_registration(self):
        registrations = {
            task: match.start()
            for match in re.finditer(r'tasks\.register(?:<[^>]+>)?\(\s*"([^"]+)"', self.text)
            for task in [match.group(1)]
        }
        for match in re.finditer(r'tasks\.named\(\s*"([^"]+)"', self.text):
            task = match.group(1)
            with self.subTest(task=task):
                self.assertIn(task, registrations, f"{task} não registrado neste script")
                self.assertLess(registrations[task], match.start(), f"{task}: tasks.named antes de tasks.register")

    def test_torch_kept_in_protocol_pipeline(self):
        self.assertIn('tasks.named("testProtocol") { dependsOn("testTorch") }', self.text)
        self.assertIn('tasks.register<JavaExec>("testTorch")', self.text)
        self.assertIn('tasks.register<JavaExec>("testProtocol")', self.text)
        self.assertIn('dependsOn(compileProtocolTests)', self.text)

if __name__ == "__main__":
    unittest.main()
