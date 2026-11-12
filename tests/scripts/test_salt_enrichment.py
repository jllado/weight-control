"""Verify reviewed salt conversions and complete historical inventory accounting."""
from decimal import Decimal
import unittest

from scripts.nutrients.salt_enrichment import calculate_salt, prepare_manifest


def row(kind="meal_snapshot", identity=1, **changes):
    return {"kind": kind, "id": identity, "quantity": 50, "reference": {"quantity": 100},
            "saltGrams": None, "referenceSaltGrams": None, "saltSource": None, "saltEstimated": None, **changes}


def decision(**changes):
    return {"kind": "meal_snapshot", "id": 1, "method": "reference", "value": "400", "unit": "sodium_mg",
            "saltSource": "Reviewed manufacturer label; 400 mg sodium per 100 g", "saltEstimated": False, **changes}


class SaltEnrichmentTest(unittest.TestCase):
    def test_units_convert_once_and_scale_each_saved_portion(self):
        for unit, value in (("sodium_mg", "400"), ("sodium_g", "0.4"), ("salt_g", "1")):
            with self.subTest(unit=unit):
                actual = calculate_salt(row(), decision(unit=unit, value=value), {})
                self.assertEqual(Decimal("1.00"), actual["referenceSaltGrams"])
                self.assertEqual(Decimal("0.50"), actual["saltGrams"])

    def test_fdc_sodium_requires_reviewed_identity_and_edible_mass(self):
        source = {"description": "Bread", "foodNutrients": [{"nutrient": {"id": 1093, "unitName": "mg"}, "amount": 500}]}
        choice = decision(method="fdc", fdcId="1", sourceDescription="Bread", referenceGrams=40, saltEstimated=True)
        actual = calculate_salt(row(), choice, {"1": source})
        self.assertEqual(Decimal("0.50"), actual["referenceSaltGrams"])
        self.assertEqual(Decimal("0.25"), actual["saltGrams"])
        with self.assertRaisesRegex(ValueError, "identity"):
            calculate_salt(row(), {**choice, "sourceDescription": "Different food"}, {"1": source})
        with self.assertRaisesRegex(ValueError, "referenceGrams"):
            calculate_salt(row(), {k:v for k,v in choice.items() if k != "referenceGrams"}, {"1": source})

    def test_recipe_sums_before_rounding_and_preserves_known_zero(self):
        choice = decision(method="recipe", components=[{"value": 100, "unit": "sodium_mg", "grams": 25},
                                                       {"value": "0.2", "unit": "salt_g", "grams": 40}], saltEstimated=True)
        self.assertEqual(Decimal("0.14"), calculate_salt(row(), choice, {})["referenceSaltGrams"])
        self.assertEqual(Decimal("0.00"), calculate_salt(row(), decision(value=0), {})["saltGrams"])

    def test_preserves_existing_values_and_accounts_for_archived_and_unresolved(self):
        inventory = [row(), row("catalog_food", 2, deleted=True), row("recipe_ingredient", 3),
                     row("catalog_food", 4, saltGrams=0, referenceSaltGrams=0, saltSource="Label", saltEstimated=False)]
        choices = [decision(), decision(kind="catalog_food", id=2),
                   {"kind": "recipe_ingredient", "id": 3, "unresolved": "Recipe lacks a defensible salt or sodium basis"}]
        result = prepare_manifest(inventory, choices, {})
        self.assertEqual({"researched": 2, "estimated": 0, "alreadyPopulated": 1, "unresolved": 1}, result["counts"])
        self.assertEqual(4, len(result["records"]))
        self.assertTrue(inventory[1]["deleted"])
        self.assertIsNone(inventory[0]["saltGrams"])

    def test_manifest_rejects_missing_duplicate_unknown_and_overwriting_decisions(self):
        with self.assertRaisesRegex(ValueError, "account"):
            prepare_manifest([row()], [], {})
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            prepare_manifest([row()], [decision(), decision()], {})
        with self.assertRaisesRegex(ValueError, "inventory"):
            prepare_manifest([row()], [decision(id=2)], {})
        with self.assertRaisesRegex(ValueError, "populated"):
            prepare_manifest([row(saltGrams=1)], [decision()], {})
        self.assertEqual([], prepare_manifest([], [], {})["records"])

    def test_invalid_units_and_incomplete_provenance_are_rejected(self):
        for changes in ({"unit": "mg"}, {"value": -1}, {"saltSource": ""}, {"saltEstimated": None}):
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                calculate_salt(row(), decision(**changes), {})
