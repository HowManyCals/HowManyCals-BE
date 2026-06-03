import unittest

from preprocess_food_sources import build_packaged_canonical_name, normalize_key


class PreprocessFoodSourcesTest(unittest.TestCase):

    def test_normalize_key_removes_bom_and_spaces(self):
        self.assertEqual(normalize_key("\ufeff우리밀 한입 스콘 초코칩"), "우리밀한입스콘초코칩")

    def test_build_packaged_canonical_name_removes_brand_like_english_tokens(self):
        canonical, confidence, aliases = build_packaged_canonical_name(
            product_name="Yeschef BIG TTEOKBOKKI 국물떡볶이",
            representative_name="떡",
            manufacturer_name="(주)아이케이푸드",
        )

        self.assertEqual(canonical, "국물떡볶이")
        self.assertIn(confidence, {"MEDIUM", "HIGH"})
        self.assertIn("국물떡볶이", aliases)

    def test_build_packaged_canonical_name_keeps_core_flavor_tokens(self):
        canonical, confidence, aliases = build_packaged_canonical_name(
            product_name="우리밀 한입 스콘 초코칩",
            representative_name="스콘",
            manufacturer_name="(주)페어베이커리",
        )

        self.assertIn("스콘", canonical)
        self.assertIn("초코칩", canonical)
        self.assertEqual(confidence, "HIGH")
        self.assertTrue(any("초코칩" in alias and "스콘" in alias for alias in aliases))


if __name__ == "__main__":
    unittest.main()

