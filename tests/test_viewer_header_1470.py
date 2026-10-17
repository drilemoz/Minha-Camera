"""Regressoes do Build APK 68 e cabecalho do receptor 1.4.70."""
from pathlib import Path
from xml.etree import ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
KT=(ROOT/'app/src/main/java/com/minhacamera/PhoneCameraViewerActivity.kt').read_text()
TREE=ET.parse(ROOT/'app/src/main/res/layout/activity_phone_viewer.xml').getroot()
NS='{http://schemas.android.com/apk/res/android}'
def get(id_):
    return next((v for v in TREE.iter() if v.attrib.get(NS+'id')=='@+id/'+id_),None)

def test_gravity_import_from_build68():
    assert 'import android.view.Gravity' in KT
    assert 'Gravity.CENTER_VERTICAL' in KT and 'Gravity.CENTER' in KT

def test_device_name_only_actionbar():
    assert get('viewerCameraName') is None
    assert 'title = intent.getStringExtra("name")' in KT

def test_single_live_label_and_clock_inline():
    # A linha superior do painel mostra 'Ao vivo' apenas uma vez.
    row=next(v for v in TREE.iter() if get('viewerTransmissionClock') in list(v))
    assert any(v.attrib.get(NS+'text')=='@string/ui_live' for v in row)
    assert get('viewerBatteryPill') in list(row)
    assert get('viewerTemperature') in list(row)
    assert '"AO VIVO %02d' not in KT

def test_codec_in_place_without_popup():
    assert get('viewerCodecSubmenu') is not None
    assert get('viewerCodecMjpegOption') is not None
    assert get('viewerCodecH264Option') is not None
    assert 'PhoneViewerChoiceDialog.show(this, getString(R.string.phone_codec_title)' not in KT
    assert 'changeRemoteCodec("mjpeg")' in KT
    assert 'changeRemoteCodec("h264")' in KT
    assert get('viewerCodecAction') is None
    assert get('viewerDiagnosticsAction') is None
    assert get('viewerDiagnosticTop') is not None
