"""Calculate reviewed reference and consumed Fiber for private backfill manifests."""

from decimal import Decimal, ROUND_HALF_UP


HUNDRED = Decimal("100")
CENT = Decimal("0.01")


def decimal(value):
    return Decimal(str(value))


def fdc_fiber(source, description):
    if source["description"] != description:
        raise ValueError("FDC description changed after review")
    nutrient = next(item for item in source["foodNutrients"] if item["nutrient"]["id"] == 1079)
    if nutrient["nutrient"]["unitName"] != "g":
        raise ValueError("FDC dietary Fiber is not in grams")
    return decimal(nutrient["amount"])


def calculate_fiber(row, decision, sources):
    """Return Fiber fields for one empty food snapshot and its reviewed reference."""
    if row["fiberGrams"] is not None or row["reference"].get("fiberGrams") is not None:
        raise ValueError("Food already has Fiber")
    provenance = decision["fiberSource"].strip()
    if not provenance or len(provenance) > 500:
        raise ValueError("Fiber source must be 1–500 characters")
    if type(decision["fiberEstimated"]) is not bool:
        raise ValueError("Fiber estimate flag is required")

    if decision["method"] == "label":
        reference = decimal(decision["referenceFiberGrams"])
    elif decision["method"] == "fdc":
        amount = fdc_fiber(sources[decision["fdcId"]], decision["sourceDescription"])
        if amount == 0:
            reference = Decimal(0)
        else:
            if "referenceGrams" not in decision:
                raise ValueError("Nonzero FDC Fiber needs reviewed referenceGrams")
            reference = amount * decimal(decision["referenceGrams"]) / HUNDRED
    elif decision["method"] == "recipe":
        reference = sum((fdc_fiber(sources[part["fdcId"]], part["sourceDescription"])
                         * decimal(part["grams"]) / HUNDRED for part in decision["components"]), Decimal(0))
    else:
        raise ValueError("Unknown Fiber evidence method")

    if reference < 0:
        raise ValueError("Fiber cannot be negative")
    reference = reference.quantize(CENT, rounding=ROUND_HALF_UP)
    if decision["method"] != "label" and "referenceFiberGrams" in decision \
            and reference != decimal(decision["referenceFiberGrams"]).quantize(CENT, rounding=ROUND_HALF_UP):
        raise ValueError("Calculated Fiber differs from reviewed Fiber total")
    consumed = (reference * decimal(row["quantity"]) / decimal(row["reference"]["quantity"])).quantize(CENT, rounding=ROUND_HALF_UP)
    return {"referenceFiberGrams": reference, "fiberGrams": consumed,
            "fiberSource": provenance, "fiberEstimated": decision["fiberEstimated"]}


def record_key(row):
    if row["kind"] == "recipe_ingredient":
        return row["kind"], row["recipeId"], row["position"]
    return row["kind"], row["id"]


def expand_group_decisions(inventory, groups):
    """Expand reviewed group evidence only when every frozen row still matches."""
    rows = {record_key(row): row for row in inventory}
    if len(rows) != len(inventory):
        raise ValueError("Duplicate frozen Fiber record")
    decisions = []
    selected = set()
    for group in groups:
        identity = group.get("identity", group)
        for occurrence in group["occurrences"]:
            key = record_key(occurrence)
            if key not in rows or key in selected:
                raise ValueError("Fiber group occurrence is absent or duplicated")
            row = rows[key]
            expected = (identity["name"].casefold(), identity["unit"], decimal(identity["referenceQuantity"]),
                        identity.get("legacySource", identity.get("nutrientSource")),
                        [str(item) for item in identity["fdcIds"]])
            actual = (row["name"].casefold(), row["unit"], decimal(row["reference"]["quantity"]),
                      row["nutrientSource"],
                      [str(item) for item in row["fdcIds"]])
            if actual != expected or any(row[field] != occurrence[field] for field in ("mealId", "date", "recipeId", "position")
                                         if field in occurrence and occurrence[field] is not None):
                raise ValueError("Frozen food identity changed")
            if not group.get("calorieIndependent", False) and decimal(row["reference"]["calories"]) != decimal(identity["referenceCalories"]):
                raise ValueError("Frozen food reference calories changed")
            selected.add(key)
            decisions.append({**{name: occurrence[name] for name in ("kind", "id", "recipeId", "position")
                                 if name in occurrence}, **{name: group[name] for name in
                                 ("method", "fdcId", "sourceDescription", "referenceGrams", "components",
                                  "referenceFiberGrams", "fiberSource", "fiberEstimated") if name in group}})
    if selected != set(rows):
        raise ValueError("Fiber groups do not cover frozen inventory")
    return decisions


def prepare_manifest(inventory, decisions, sources):
    """Attach each reviewed decision to exactly one frozen owner record."""
    rows = {record_key(row): row for row in inventory}
    if len(rows) != len(inventory):
        raise ValueError("Duplicate frozen Fiber record")
    selected = set()
    manifest = []
    for decision in decisions:
        key = record_key(decision)
        if key in selected:
            raise ValueError("Duplicate Fiber decision")
        if key not in rows:
            raise ValueError("Fiber decision is not in frozen inventory")
        selected.add(key)
        identity = {name: decision[name] for name in ("kind", "recipeId", "position") if name in decision} if decision["kind"] == "recipe_ingredient" else {"kind": decision["kind"], "id": decision["id"]}
        manifest.append({"record": identity, "fiber": calculate_fiber(rows[key], decision, sources)})
    return manifest
