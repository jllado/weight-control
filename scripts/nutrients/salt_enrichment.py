"""Calculate reviewed salt values and account for every frozen food snapshot.

This module performs no network or database operations. Research is reviewed first;
stored salt grams are never converted a second time. Private manifests stay ignored.
"""
from collections import Counter
from decimal import Decimal, ROUND_HALF_UP

CENT = Decimal("0.01")
FACTORS = {"salt_g": Decimal(1), "sodium_g": Decimal("2.5"), "sodium_mg": Decimal("0.0025")}
FIELDS = ("saltGrams", "referenceSaltGrams", "saltSource", "saltEstimated")


def decimal(value):
    return Decimal(str(value))


def converted(value, unit):
    if unit not in FACTORS:
        raise ValueError("Use explicit salt_g, sodium_g or sodium_mg units")
    amount = decimal(value)
    if not amount.is_finite() or amount < 0:
        raise ValueError("Salt or sodium must be finite and nonnegative")
    return amount * FACTORS[unit]


def fdc_salt(source, description):
    if source["description"] != description:
        raise ValueError("Reviewed FDC identity changed")
    nutrient = next((item for item in source["foodNutrients"] if item["nutrient"]["id"] == 1093), None)
    if nutrient is None or nutrient["nutrient"]["unitName"].lower() != "mg":
        raise ValueError("Reviewed FDC sodium in milligrams is required")
    return converted(nutrient["amount"], "sodium_mg")


def calculate_salt(row, decision, sources):
    if any(row.get(key) is not None for key in FIELDS):
        raise ValueError("Salt is already populated; preserve the saved fields")
    provenance = decision["saltSource"].strip()
    if not provenance or len(provenance) > 500 or type(decision["saltEstimated"]) is not bool:
        raise ValueError("Salt needs a 1–500 character source and explicit estimate flag")
    method = decision["method"]
    if method == "reference":
        reference = converted(decision["value"], decision["unit"])
    elif method == "fdc":
        per100 = fdc_salt(sources[str(decision["fdcId"])], decision["sourceDescription"])
        if "referenceGrams" not in decision:
            raise ValueError("FDC salt needs reviewed referenceGrams")
        reference = per100 * decimal(decision["referenceGrams"]) / 100
    elif method == "recipe":
        reference = Decimal(0)
        for part in decision["components"]:
            per100 = fdc_salt(sources[str(part["fdcId"])], part["sourceDescription"]) if "fdcId" in part else converted(part["value"], part["unit"])
            reference += per100 * decimal(part["grams"]) / 100
    else:
        raise ValueError("Unknown salt evidence method")
    if not reference.is_finite() or reference < 0:
        raise ValueError("Reviewed reference salt must be finite and nonnegative")
    reference = reference.quantize(CENT, rounding=ROUND_HALF_UP)
    consumed = (reference * decimal(row["quantity"]) / decimal(row["reference"]["quantity"])).quantize(CENT, rounding=ROUND_HALF_UP)
    return {"referenceSaltGrams": reference, "saltGrams": consumed,
            "saltSource": provenance, "saltEstimated": decision["saltEstimated"]}


def prepare_manifest(inventory, decisions, sources):
    """Require one reviewed decision for each empty row; retain existing evidence."""
    rows = {(row["kind"], row["id"]): row for row in inventory}
    if len(rows) != len(inventory):
        raise ValueError("Duplicate frozen inventory row")
    choices = {}
    for decision in decisions:
        key = decision["kind"], decision["id"]
        if key in choices:
            raise ValueError("Duplicate salt decision")
        if key not in rows:
            raise ValueError("Salt decision is outside the inventory")
        if any(rows[key].get(field) is not None for field in FIELDS):
            raise ValueError("Salt is already populated")
        choices[key] = decision
    records = []
    counts = Counter({key: 0 for key in ("researched", "estimated", "alreadyPopulated", "unresolved")})
    for key, row in rows.items():
        result = {"kind": key[0], "id": key[1]}
        if any(row.get(field) is not None for field in FIELDS):
            result.update(status="alreadyPopulated", salt={field: row.get(field) for field in FIELDS})
        elif key not in choices:
            raise ValueError("Decisions must account for every empty inventory row")
        elif "unresolved" in choices[key]:
            reason = choices[key]["unresolved"].strip()
            if not reason:
                raise ValueError("Unresolved salt needs a concrete reason")
            result.update(status="unresolved", reason=reason)
        else:
            value = calculate_salt(row, choices[key], sources)
            result.update(status="estimated" if value["saltEstimated"] else "researched", salt=value)
        counts[result["status"]] += 1
        records.append(result)
    return {"counts": dict(counts), "records": records}
