import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'


class LauncherIconContract(unittest.TestCase):
    def test_manifest_uses_launcher_resources_and_preserves_app_name(self):
        manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
        app = manifest.find('application')
        self.assertEqual(app.get(ANDROID + 'label'), '@string/app_name')
        self.assertEqual(app.get(ANDROID + 'icon'), '@mipmap/ic_launcher')
        self.assertEqual(app.get(ANDROID + 'roundIcon'), '@mipmap/ic_launcher_round')

    def test_adaptive_and_legacy_icons_use_bomb_foreground(self):
        adaptive = ET.parse(ROOT / 'app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml').getroot()
        self.assertEqual(adaptive.tag, 'adaptive-icon')
        self.assertEqual(adaptive.find('foreground').get(ANDROID + 'drawable'), '@drawable/ic_launcher_foreground')
        self.assertEqual(adaptive.find('background').get(ANDROID + 'drawable'), '@drawable/ic_launcher_background')
        legacy = ET.parse(ROOT / 'app/src/main/res/mipmap-anydpi/ic_launcher.xml').getroot()
        self.assertEqual(legacy.tag, 'layer-list')
        self.assertTrue(any(item.get(ANDROID + 'drawable') == '@drawable/ic_launcher_foreground' for item in legacy.findall('item')))
        foreground = ET.parse(ROOT / 'app/src/main/res/drawable/ic_launcher_foreground.xml').getroot()
        self.assertEqual(foreground.tag, 'vector')
        paths = list(foreground.iter('path'))
        self.assertEqual(len(paths), 2)
        self.assertIn('M14.499 3.996', paths[0].get(ANDROID + 'pathData'))

    def test_bomb_source_and_license_are_attributed(self):
        notice = (ROOT / 'THIRD_PARTY_NOTICES.md').read_text()
        self.assertIn('tabler-icons', notice)
        self.assertIn('icons/filled/bomb.svg', notice)
        self.assertIn('MIT License', notice)

if __name__ == '__main__':
    unittest.main()
