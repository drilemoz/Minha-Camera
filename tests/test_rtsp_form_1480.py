from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
SRC = ROOT / 'app/src/main/java/com/minhacamera'
ANDROID = '{http://schemas.android.com/apk/res/android}'
APP = '{http://schemas.android.com/apk/res-auto}'

def source(filename):
    return (SRC / filename).read_text(encoding='utf-8')

def layout(name):
    return ET.parse(RES / 'layout' / (name + '.xml')).getroot()

def by_id(root, name):
    return next(n for n in root.iter() if n.get(ANDROID + 'id') == '@+id/' + name)

class RtspFormTests(unittest.TestCase):
    def test_forms_share_login_and_path_and_are_scrollable(self):
        for name in ('dialog_scan_camera', 'dialog_manual_camera'):
            form = layout(name)
            self.assertEqual(form.tag, 'ScrollView')
            includes = [n.get('layout') for n in form.iter('include')]
            self.assertEqual(includes, ['@layout/view_camera_login', '@layout/view_rtsp_path_choice'])
        login = layout('view_camera_login')
        self.assertEqual(by_id(login, 'etPass').get(ANDROID + 'inputType'), 'textPassword')
        self.assertTrue(any(n.get(APP + 'endIconMode') == 'password_toggle' for n in login.iter()))
        for marker in ('etUser', 'btnCopyPass', 'btnPastePass'):
            self.assertIsNotNone(by_id(login, marker))

    def test_custom_path_is_editable_only_when_revealed(self):
        path = layout('view_rtsp_path_choice')
        self.assertEqual(by_id(path, 'rtspCustomPathBox').get(ANDROID + 'visibility'), 'gone')
        self.assertEqual(by_id(path, 'rtspCustomPath').get(ANDROID + 'inputType'), 'textUri|textNoSuggestions')
        for node in path.iter():
            self.assertNotIn('AutoComplete', node.tag)
            self.assertNotEqual(node.get(APP + 'endIconMode'), 'dropdown_menu')
        helper = source('CameraFormUi.kt')
        self.assertIn('hideSoftInputFromWindow', helper)
        self.assertIn('SOFT_INPUT_STATE_ALWAYS_HIDDEN', helper)
        self.assertIn('SOFT_INPUT_ADJUST_RESIZE', helper)
        self.assertIn('if (manual) normalizePathChoice(customField.text?.toString().orEmpty()) else selected', helper)

    def test_presets_preserve_the_previous_paths_and_query_parameters(self):
        presets = re.findall(r'Preset\("[^"]+", \d+, "([^"]+)"\)', source('CameraFormUi.kt'))
        self.assertEqual(presets, [
            '/stream1', '/stream2', '/onvif1', '/onvif2', '/live/ch00_0', '/live/ch00_1',
            '/Streaming/Channels/101', '/Streaming/Channels/102',
            '/h264Preview_01_main', '/h264Preview_01_sub',
            '/cam/realmonitor?channel=1&subtype=0', '/cam/realmonitor?channel=1&subtype=1',
        ])
        self.assertIn('0 -> select("")', source('CameraFormUi.kt'))
        self.assertIn('select(pathSuggestions[index].path)', source('CameraFormUi.kt'))

    def test_modes_feed_existing_save_branches(self):
        scan, main = source('ScanActivity.kt'), source('MainActivity.kt')
        self.assertEqual(scan.count('CameraFormUi.setupPathSelector(view)'), 2)
        self.assertIn('testAndSave(f, name, user, pass, path())', scan)
        self.assertIn('user, pass, path()', scan)
        self.assertIn('CameraFormUi.setupPathSelector(view, parsed.path, editing = true)', main)
        self.assertIn('CameraFormUi.buildRtspUrl(target, port, user, pass, path())', main)
        self.assertIn('if (url == c.url) c.alts else emptyList()', main)
        for activity in (main, scan):
            self.assertIn('CameraFormUi.adaptToAddress(view, etIp)', activity)
            self.assertIn('CameraFormUi.attachRtspCredentials', activity)

if __name__ == '__main__':
    unittest.main()
