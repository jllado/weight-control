"""Verify source-backed Fiber calculations before owner-scoped backfill."""

from decimal import Decimal
import unittest

from scripts.nutrients.fiber_enrichment import calculate_fiber, expand_group_decisions, prepare_manifest


def food(fdc_id, fiber):
    return {"fdcId": fdc_id, "description": "reviewed food", "sourceUrl": f"https://fdc.nal.usda.gov/food-details/{fdc_id}/nutrients",
            "foodNutrients": [{"nutrient": {"id": 1079, "unitName": "g"}, "amount": fiber}]}


class FiberEnrichmentTest(unittest.TestCase):
    def setUp(self):
        self.row = {"name": "Oats", "quantity": 40, "unit": "GRAM", "reference": {"quantity": 60}, "fiberGrams": None}
        self.sources = {169705: food(169705, 10.6), 168746: food(168746, 0), 170588: food(170588, 6.7)}

    def test_direct_composition_scales_reference_then_consumed_half_up(self):
        decision = {"method": "fdc", "fdcId": 169705, "sourceDescription": "reviewed food", "referenceGrams": 60,
                    "fiberSource": "Estimate: USDA FDC 169705; oats; 60 g reference.", "fiberEstimated": True}
        self.assertEqual({"referenceFiberGrams": Decimal("6.36"), "fiberGrams": Decimal("4.24"),
                          "fiberSource": decision["fiberSource"], "fiberEstimated": True},
                         calculate_fiber(self.row, decision, self.sources))

    def test_label_preserves_published_reference_portion_rounding(self):
        row = {"name": "Hacendado cereal bar", "quantity": 20, "unit": "GRAM", "reference": {"quantity": 20}, "fiberGrams": None}
        decision = {"method": "label", "referenceFiberGrams": "4.1",
                    "fiberSource": "Hacendado label; 4.1 g per 20 g bar; https://example.test/label", "fiberEstimated": False}
        self.assertEqual(Decimal("4.10"), calculate_fiber(row, decision, self.sources)["fiberGrams"])

    def test_recipe_sums_reviewed_ingredient_masses_before_rounding(self):
        row = {"name": "Oats and nuts", "quantity": 0.5, "unit": "SERVING", "reference": {"quantity": 1}, "fiberGrams": None}
        decision = {"method": "recipe", "components": [{"fdcId": 169705, "sourceDescription": "reviewed food", "grams": 40},
                                                        {"fdcId": 170588, "sourceDescription": "reviewed food", "grams": 15}],
                    "fiberSource": "Estimated recipe: oats 40 g and nuts 15 g; USDA FDC 169705,170588", "fiberEstimated": True}
        value = calculate_fiber(row, decision, self.sources)
        self.assertEqual(Decimal("5.25"), value["referenceFiberGrams"])
        self.assertEqual(Decimal("2.63"), value["fiberGrams"])

    def test_source_backed_zero_needs_no_guessed_serving_mass(self):
        row = {"name": "Draft beer", "quantity": 1, "unit": "SERVING", "reference": {"quantity": 1}, "fiberGrams": None}
        decision = {"method": "fdc", "fdcId": 168746, "sourceDescription": "reviewed food",
                    "fiberSource": "Estimate: USDA FDC 168746; regular beer Fiber 0 g/100 g", "fiberEstimated": True}
        self.assertEqual(Decimal("0.00"), calculate_fiber(row, decision, self.sources)["fiberGrams"])

    def test_nonzero_composition_requires_reviewed_edible_mass(self):
        decision = {"method": "fdc", "fdcId": 169705, "sourceDescription": "reviewed food",
                    "fiberSource": "USDA FDC 169705", "fiberEstimated": True}
        with self.assertRaisesRegex(ValueError, "referenceGrams"):
            calculate_fiber(self.row, decision, self.sources)

    def test_existing_fiber_cannot_be_overwritten(self):
        row = {**self.row, "fiberGrams": 3.1}
        decision = {"method": "fdc", "fdcId": 169705, "sourceDescription": "reviewed food", "referenceGrams": 60,
                    "fiberSource": "USDA FDC 169705", "fiberEstimated": True}
        with self.assertRaisesRegex(ValueError, "already has Fiber"):
            calculate_fiber(row, decision, self.sources)

    def test_reviewed_recipe_total_must_match_source_arithmetic(self):
        decision = {"method": "recipe", "components": [{"fdcId": 169705, "sourceDescription": "reviewed food", "grams": 40},
                                                        {"fdcId": 170588, "sourceDescription": "reviewed food", "grams": 15}],
                    "referenceFiberGrams": "6.00", "fiberSource": "Reviewed recipe", "fiberEstimated": True}
        with self.assertRaisesRegex(ValueError, "reviewed Fiber total"):
            calculate_fiber(self.row, decision, self.sources)

    def test_manifest_maps_a_reviewed_decision_to_one_snapshot(self):
        row = {**self.row, "kind": "meal_snapshot", "id": 42, "date": "2026-10-10"}
        decision = {"kind": "meal_snapshot", "id": 42, "method": "fdc", "fdcId": 169705,
                    "sourceDescription": "reviewed food", "referenceGrams": 60,
                    "fiberSource": "USDA FDC 169705; 60 g reference", "fiberEstimated": True}
        manifest = prepare_manifest([row], [decision], self.sources)
        self.assertEqual(1, len(manifest))
        self.assertEqual({"kind": "meal_snapshot", "id": 42}, manifest[0]["record"])
        self.assertEqual(Decimal("6.36"), manifest[0]["fiber"]["referenceFiberGrams"])

    def test_manifest_rejects_duplicate_or_unknown_snapshot_decisions(self):
        row = {**self.row, "kind": "meal_snapshot", "id": 42}
        decision = {"kind": "meal_snapshot", "id": 42, "method": "fdc", "fdcId": 169705,
                    "sourceDescription": "reviewed food", "referenceGrams": 60,
                    "fiberSource": "USDA FDC 169705", "fiberEstimated": True}
        with self.assertRaisesRegex(ValueError, "Duplicate Fiber decision"):
            prepare_manifest([row], [decision, decision], self.sources)
        with self.assertRaisesRegex(ValueError, "not in frozen inventory"):
            prepare_manifest([row], [{**decision, "id": 43}], self.sources)

    def test_group_expansion_checks_frozen_identity_and_exact_occurrences(self):
        row = {**self.row, "reference": {"quantity": 60, "calories": 160},
               "kind": "meal_snapshot", "id": 42, "calories": 160,
               "nutrientSource": "Saved oats estimate", "fdcIds": ["169705"]}
        group = {"name": "Oats", "unit": "GRAM", "referenceQuantity": 60,
                 "referenceCalories": 160, "legacySource": "Saved oats estimate", "fdcIds": ["169705"],
                 "occurrences": [{"kind": "meal_snapshot", "id": 42}], "method": "fdc", "fdcId": 169705,
                 "sourceDescription": "reviewed food", "referenceGrams": 60,
                 "fiberSource": "USDA FDC 169705; 60 g reference", "fiberEstimated": True}
        self.assertEqual(1, len(expand_group_decisions([row], [group])))
        with self.assertRaisesRegex(ValueError, "Frozen food reference calories changed"):
            expand_group_decisions([row], [{**group, "referenceCalories": 161}])
        with self.assertRaisesRegex(ValueError, "Frozen food identity changed"):
            expand_group_decisions([row], [{**group, "name": "Different oats"}])
        with self.assertRaisesRegex(ValueError, "do not cover frozen inventory"):
            expand_group_decisions([row, {**row, "id": 43}], [group])
