import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
A = '{http://schemas.android.com/apk/res/android}'

class DonationCardContract(unittest.TestCase):
    def test_card_is_first_settings_content_and_accessible(self):
        root = ET.parse(ROOT / 'app/src/main/res/layout/activity_settings.xml').getroot()
        content = next(e for e in root.iter() if e.get(A + 'id') == '@+id/content')
        card = list(content)[0]
        self.assertEqual(card.get(A + 'id'), '@+id/donationCard')
        self.assertEqual(card.get(A + 'clickable'), 'true')
        self.assertEqual(card.get(A + 'focusable'), 'true')
        self.assertEqual(card.get(A + 'contentDescription'), '@string/donation_card_description')

    def test_opens_exact_ko_fi_link_with_view_action(self):
        source = (ROOT / 'app/src/main/java/com/joeld/minesweeper/SettingsActivity.kt').read_text()
        self.assertIn('binding.donationCard.setOnClickListener', source)
        self.assertIn('Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/joeldankert"))', source)
        self.assertIn('ActivityNotFoundException', source)

    def test_donation_copy_exists_in_all_supported_locales(self):
        for locale in ('values', 'values-de', 'values-es', 'values-fr'):
            with self.subTest(locale=locale):
                root = ET.parse(ROOT / f'app/src/main/res/{locale}/strings.xml').getroot()
                names = {e.get('name') for e in root.findall('string')}
                self.assertTrue({'donation_title', 'donation_subtitle', 'donation_action', 'donation_card_description'} <= names)

if __name__ == '__main__':
    unittest.main()
