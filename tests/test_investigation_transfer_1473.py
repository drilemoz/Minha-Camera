import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/java/com/minhacamera'
class InvestigationTransfer1473(unittest.TestCase):
    def test_ci_runs_real_unittest(self):
        import subprocess,sys
        p=subprocess.run([sys.executable,'-m','unittest','discover','-s','tests','-p','test_viewer_toolbar_recovery_1472.py','-q'],cwd=ROOT,capture_output=True,text=True)
        self.assertEqual(p.returncode,0,p.stderr)
        self.assertRegex(p.stderr,r'Ran [1-9]')
    def test_pipeline_covers_two_modes(self):
        activity=(SRC/'PhoneCameraActivity.kt').read_text()
        service=(SRC/'PhoneCameraBackgroundService.kt').read_text()
        for t in (activity,service):
            for mark in ['pipelineProbe.callback','pipelineProbe.worker','pipelineProbe.published','captureFreeze']:
                self.assertIn(mark,t)
    def test_no_frame_allocation_in_probe(self):
        s=(SRC/'PhonePipelineTelemetry.kt').read_text()
        self.assertNotIn('ByteArray(',s)
        self.assertIn('publishAge(',s)
    def test_progress_and_cancel_cleanup(self):
        s=(SRC/'RemoteMultimediaActivity.kt').read_text()
        for mark in ['activeDownload.compareAndSet','ProgressBar(','contentLengthLong','output.write(buffer, 0, n)','canceled.set(true)','contentResolver.delete(target','legacyFile?.delete()','copied != announcedBytes']:
            self.assertIn(mark,s)
        self.assertIn('percent(bytes: Long, total: Long)',(SRC/'RemoteTransferProgress.kt').read_text())
    def test_versions(self):
        self.assertIn('versionCode = 86',(ROOT/'app/build.gradle.kts').read_text())
        self.assertIn('versionName = "1.4.76"',(ROOT/'app/build.gradle.kts').read_text())
if __name__=='__main__': unittest.main()
