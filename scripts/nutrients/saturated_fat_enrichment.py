"""Prepare a reviewed saturated-fat migration from frozen owner rows and sources."""
from collections import Counter
from decimal import Decimal, ROUND_HALF_UP
from .salt_transaction import KINDS, SATURATED_FAT


def number(value):
    return Decimal(str(value))


def grams(value):
    return number(value).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def estimate_food(row, evidence):
    """Scale a reviewed label or comparable food to this occurrence's frozen reference."""
    sat = number(evidence["saturatedPer100"])
    fat = number(evidence["fatPer100"])
    if not 0 <= sat <= fat:
        raise ValueError("Saturated fat exceeds source total fat")
    reference_fat = row["reference_fat_grams"]
    basis = evidence["source"]
    if evidence.get("referenceMass") is not None:
        ref = sat * number(evidence["referenceMass"]) / 100
    elif reference_fat is not None and fat > 0:
        ref = number(reference_fat) * sat / fat
    elif reference_fat == 0:
        ref = Decimal(0)
    else:
        raise ValueError("A valid reference portion or comparable fat composition is required")
    if reference_fat is not None and ref > number(reference_fat):
        ref = number(reference_fat)
    ref = grams(ref)
    consumed = grams(ref * number(row["quantity"]) / number(row["reference_quantity"]))
    if row["fat_grams"] is not None and consumed > number(row["fat_grams"]):
        raise ValueError("Source scaling exceeds this occurrence's saved total fat")
    if not 0 < len(basis) <= 500:
        raise ValueError("Source exceeds the food source field")
    return dict(zip(SATURATED_FAT.fields, (float(consumed), float(ref), basis, evidence["estimated"])))


def prepare_manifest(inventory, decisions):
    records = []
    for kind, table in KINDS.items():
        for row in inventory[table]:
            item = {"kind": kind, "id": row["id"]}
            if any(row.get(key) is not None for key in SATURATED_FAT.columns):
                item["status"] = "alreadyPopulated"
            else:
                evidence = decisions[kind, row["id"]]
                if "unresolved" in evidence:
                    item.update(status="unresolved", reason=evidence["unresolved"])
                else:
                    value = estimate_food(row, evidence)
                    item.update(status="estimated" if value["saturatedFatEstimated"] else "researched", saturatedFat=value)
            records.append(item)
    itemized = {row["meal_id"] for row in inventory["meal_dishes"]}
    for meal in inventory["meals"]:
        item = {"kind": "legacy_meal", "id": meal["id"]}
        if meal["id"] in itemized:
            item["status"] = "itemized"
        elif meal.get("estimated_saturated_fat_grams") is not None:
            item["status"] = "alreadyPopulated"
        elif meal["fat_grams"] is None:
            item.update(status="unresolved", reason="No foods or recorded total fat")
        else:
            evidence = decisions["legacy_meal", meal["id"]]
            value = grams(evidence["saturatedFatGrams"])
            if not 0 <= value <= number(meal["fat_grams"]) or not 0 < len(evidence["source"]) <= 500:
                raise ValueError("Unitemized estimate requires a reviewed source and a value within the saved total fat")
            item.update(status="estimated", saturatedFat={"saturatedFatGrams": float(value),
                        "saturatedFatSource": evidence["source"], "saturatedFatEstimated": True})
        records.append(item)
    return {"counts": dict(Counter(row["status"] for row in records)), "records": records}
