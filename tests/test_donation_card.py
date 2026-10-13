import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
A = '{http://schemas.android.com/apk/res/android}'


class DonationCardContract(unittest.TestCase):
    def test_settings_and_home_use_one_card_layout(self):
        settings = ET.parse(ROOT / 'app/src/main/res/layout/activity_settings.xml').getroot()
        content = next(e for e in settings.iter() if e.get(A + 'id') == '@+id/content')
        card = list(content)[0]
        self.assertEqual(card.tag, 'include')
        self.assertEqual(card.get('layout'), '@layout/item_donation_card')
        self.assertEqual(card.get(A + 'id'), '@+id/donationCard')
        home_source = (ROOT / 'app/src/main/java/com/joeld/minesweeper/HomeActivity.kt').read_text()
        self.assertIn('ItemDonationCardBinding.inflate', home_source)
        self.assertIn('binding.modeStrip.addView(createDonationCard())', home_source)
        self.assertLess(home_source.index('binding.modeStrip.addView(createDonationCard())'),
                        home_source.index('modes.forEach { mode ->'))

    def test_card_follows_game_mode_geometry_and_is_accessible(self):
        card = ET.parse(ROOT / 'app/src/main/res/layout/item_donation_card.xml').getroot()
        mode = ET.parse(ROOT / 'app/src/main/res/layout/item_mode_card.xml').getroot()
        for attr in ('layout_width', 'layout_height', 'layout_marginBottom', 'padding'):
            self.assertEqual(card.get(A + attr), mode.get(A + attr))
        self.assertEqual(card.get(A + 'clickable'), 'true')
        self.assertEqual(card.get(A + 'focusable'), 'true')
        self.assertEqual(card.get(A + 'contentDescription'), '@string/donation_card_description')
        action = next(e for e in card.iter() if e.get(A + 'id') == '@+id/donationAction')
        mode_action = next(e for e in mode.iter() if e.get(A + 'id') == '@+id/newGameButton')
        self.assertEqual(action.get(A + 'layout_height'), mode_action.get(A + 'layout_height'))
        icon = next(e for e in card.iter() if e.get(A + 'id') == '@+id/donationIcon')
        self.assertEqual(icon.tag, 'ImageView')
        self.assertEqual(icon.get(A + 'src'), '@drawable/ic_favorite_material')
        self.assertIsNone(icon.get(A + 'background'))

    def test_close_is_only_visible_on_home_and_settings_card_is_permanent(self):
        card = ET.parse(ROOT / 'app/src/main/res/layout/item_donation_card.xml').getroot()
        close = next(e for e in card.iter() if e.get(A + 'id') == '@+id/donationBannerClose')
        self.assertEqual(close.get(A + 'visibility'), 'gone')
        self.assertEqual(close.get(A + 'contentDescription'), '@string/donation_banner_dismiss')
        home_source = (ROOT / 'app/src/main/java/com/joeld/minesweeper/HomeActivity.kt').read_text()
        self.assertIn('donationBannerClose.visibility = View.VISIBLE', home_source)
        self.assertIn('repository.dismissDonationPrompt(todayLocalDate())', home_source)
        self.assertIn('repository.shouldShowDonationPrompt(todayLocalDate())', home_source)
        settings_source = (ROOT / 'app/src/main/java/com/joeld/minesweeper/SettingsActivity.kt').read_text()
        self.assertNotIn('donationBannerClose.visibility = View.VISIBLE', settings_source)
        self.assertIn('binding.donationCard.root.setOnClickListener', settings_source)

    def test_opens_exact_ko_fi_link_with_view_action(self):
        source = (ROOT / 'app/src/main/java/com/joeld/minesweeper/SettingsActivity.kt').read_text()
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
