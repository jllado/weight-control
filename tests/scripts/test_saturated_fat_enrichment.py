from unittest import TestCase
from scripts.nutrients.saturated_fat_enrichment import estimate_food, prepare_manifest


class SaturatedFatEnrichmentTest(TestCase):
    def setUp(self):
        self.row = dict(id=12, quantity=40, reference_quantity=100, reference_fat_grams=20, fat_grams=8)
        self.evidence = dict(source="Comparable cooked patty", saturatedPer100=8, fatPer100=25, estimated=True)

    def test_scales_comparable_fat_mix_to_each_reference(self):
        value = estimate_food(self.row, self.evidence)
        self.assertEqual((6.4, 2.56), (value["referenceSaturatedFatGrams"], value["saturatedFatGrams"]))
        self.assertTrue(value["saturatedFatEstimated"])
        self.assertEqual(self.evidence["source"], value["saturatedFatSource"])

    def test_label_reference_scales_and_preserves_source(self):
        evidence = {**self.evidence, "referenceMass": 50, "estimated": False}
        value = estimate_food(self.row, evidence)
        self.assertEqual((4, 1.6, False), (value["referenceSaturatedFatGrams"], value["saturatedFatGrams"], value["saturatedFatEstimated"]))

    def test_known_zero_is_preserved(self):
        inventory = dict(catalog_foods=[{**self.row, "saturated_fat_grams": 0}], meal_dishes=[], recipe_ingredients=[], meals=[])
        self.assertEqual({"alreadyPopulated": 1}, prepare_manifest(inventory, {})["counts"])

    def test_impossible_source_fails_review(self):
        with self.assertRaisesRegex(ValueError, "exceeds"):
            estimate_food(self.row, {**self.evidence, "saturatedPer100": 26})


    def test_older_meal_keeps_calorie_only_unknown_and_uses_reviewed_estimate_for_known_fat(self):
        inventory = dict(catalog_foods=[], recipe_ingredients=[], meal_dishes=[],
                         meals=[dict(id=1, fat_grams=12), dict(id=2, fat_grams=None)])
        decisions = {("legacy_meal", 1): dict(saturatedFatGrams=4.2, source="Meal-specific estimate")}
        manifest = prepare_manifest(inventory, decisions)
        self.assertEqual({"estimated": 1, "unresolved": 1}, manifest["counts"])
        self.assertEqual(4.2, manifest["records"][0]["saturatedFat"]["saturatedFatGrams"])
