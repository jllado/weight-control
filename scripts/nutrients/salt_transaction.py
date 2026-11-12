"""Generate a frozen-owner salt-only transaction; exact replay is a no-op."""
from decimal import Decimal

SALT = ("salt_grams", "reference_salt_grams", "salt_source", "salt_estimated")
KINDS = {"catalog_food": "catalog_foods", "meal_snapshot": "meal_dishes", "recipe_ingredient": "recipe_ingredients"}
SCOPES = {
    "catalog_foods": ("catalog_foods a", "a.user_id"),
    "meal_dishes": ("meal_dishes a JOIN meals parent ON parent.id=a.meal_id", "parent.user_id"),
    "recipe_ingredients": ("recipe_ingredients a JOIN dish_recipes parent ON parent.id=a.recipe_id", "parent.user_id"),
    "meals": ("meals a", "a.user_id"),
    "dish_recipes": ("dish_recipes a", "a.user_id"),
}


def literal(value):
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, (int, float, Decimal)):
        return str(value)
    return "CONVERT(0x" + str(value).encode("utf-8").hex() + " USING utf8mb4)"


def build_transaction(inventory, schema, manifest, owner_id, *, commit=False):
    """Compare every frozen field before changing only selected empty salt fields.

    inventory contains all five owner-scoped tables, including archived foods and
    every meal date. schema lists their real columns. No external lookup runs here.
    """
    expected = {(kind, row["id"]) for kind, table in KINDS.items() for row in inventory[table]}
    records = {(row["kind"], row["id"]): row for row in manifest["records"]}
    if len(records) != len(manifest["records"]) or set(records) != expected:
        raise ValueError("Manifest must account for the complete frozen inventory")
    selected = [row for row in manifest["records"] if row["status"] in ("researched", "estimated")]
    sql = ["-- Reviewed salt-only enrichment; disconnect on error, never use --force.",
           "-- SERIALIZABLE owner transaction; exact replay is accepted, mixed/stale state rejected."]
    for table in SCOPES:
        sql += [f"DROP TEMPORARY TABLE IF EXISTS salt_old_{table};", f"CREATE TEMPORARY TABLE salt_old_{table} LIKE {table};"]
        rows = inventory[table]
        if rows:
            columns = schema[table]
            values = ["(" + ",".join(literal(row[column]) for column in columns) + ")" for row in rows]
            sql.append(f"INSERT INTO salt_old_{table} ({','.join(columns)}) VALUES\n" + ",\n".join(values) + ";")
    sql += ["DROP TEMPORARY TABLE IF EXISTS salt_review, salt_assertion;",
            "CREATE TEMPORARY TABLE salt_review(kind VARCHAR(30),id BIGINT,value DECIMAL(10,2),reference_value DECIMAL(10,2),source VARCHAR(500),estimated BOOLEAN,PRIMARY KEY(kind,id));",
            "CREATE TEMPORARY TABLE salt_assertion(valid BOOLEAN NOT NULL CHECK(valid=TRUE));"]
    if selected:
        values = []
        for row in selected:
            value = row["salt"]
            values.append("(" + ",".join(literal(item) for item in (row["kind"], row["id"], Decimal(str(value["saltGrams"])),
                           Decimal(str(value["referenceSaltGrams"])), value["saltSource"], value["saltEstimated"])) + ")")
        sql.append("INSERT INTO salt_review VALUES\n" + ",\n".join(values) + ";")
    sql += ["SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;", "START TRANSACTION;",
            f"SELECT id FROM users WHERE id={owner_id} FOR UPDATE;",
            f"INSERT INTO salt_assertion SELECT COUNT(*)=1 FROM users WHERE id={owner_id};"]
    # Lock parent collections first, then every food collection, including empty ranges.
    for table in ("meals", "dish_recipes", "catalog_foods", "meal_dishes", "recipe_ingredients"):
        scope, owner = SCOPES[table]
        sql.append(f"SELECT a.id FROM {scope} WHERE {owner}={owner_id} FOR UPDATE;")
    unchanged_checks = []
    for table, (scope, owner) in SCOPES.items():
        count = len(inventory[table])
        sql.append(f"INSERT INTO salt_assertion SELECT COUNT(*)={count} FROM {scope} WHERE {owner}={owner_id};")
        fields = [column for column in schema[table] if column not in SALT]
        comparison = " AND ".join(f"BINARY a.{column}<=>BINARY old.{column}" for column in fields)
        unchanged_checks.append(f"INSERT INTO salt_assertion SELECT COUNT(*)={count} FROM {scope} JOIN salt_old_{table} old ON old.id=a.id WHERE {owner}={owner_id} AND {comparison};")
    sql += unchanged_checks
    state_branches = []
    untouched_checks = []
    for kind, table in KINDS.items():
        scope, owner = SCOPES[table]
        count = sum(row["kind"] == kind for row in selected)
        unchanged = " AND ".join(f"BINARY a.{field}<=>BINARY old.{field}" for field in SALT)
        untouched_checks.append(f"INSERT INTO salt_assertion SELECT COUNT(*)={len(inventory[table])-count} FROM {scope} JOIN salt_old_{table} old ON old.id=a.id LEFT JOIN salt_review r ON r.kind='{kind}' AND r.id=a.id WHERE {owner}={owner_id} AND r.id IS NULL AND {unchanged};")
        empty = " AND ".join(f"a.{field} IS NULL" for field in SALT)
        populated = " AND ".join(f"BINARY a.{field}<=>BINARY r.{new}" for field, new in zip(SALT, ("value", "reference_value", "source", "estimated")))
        state_branches.append(f"SELECT ({empty}) is_old,({populated}) is_new FROM {scope} JOIN salt_review r ON r.kind='{kind}' AND r.id=a.id WHERE {owner}={owner_id}")
    state = " UNION ALL ".join(state_branches)
    sql += untouched_checks
    sql.append(f"INSERT INTO salt_assertion SELECT COUNT(*)={len(selected)} AND (COALESCE(SUM(is_old),0)={len(selected)} OR COALESCE(SUM(is_new),0)={len(selected)}) FROM ({state}) states;")
    for kind, table in KINDS.items():
        scope, owner = SCOPES[table]
        assignments = ",".join(f"a.{field}=r.{new}" for field, new in zip(SALT, ("value", "reference_value", "source", "estimated")))
        empty = " AND ".join(f"a.{field} IS NULL" for field in SALT)
        sql += [f"UPDATE {scope} JOIN salt_review r ON r.kind='{kind}' AND r.id=a.id SET {assignments} WHERE {owner}={owner_id} AND {empty};",
                f"SELECT 'salt_updated_{kind}',ROW_COUNT();"]
    sql += unchanged_checks + untouched_checks
    sql += [f"INSERT INTO salt_assertion SELECT COUNT(*)={len(selected)} AND COALESCE(SUM(is_new),0)={len(selected)} FROM ({state}) states;",
            "COMMIT;" if commit else "ROLLBACK;",
            f"SELECT 'salt_{'committed' if commit else 'dry_run_rolled_back'}',{len(selected)};"]
    return "\n".join(sql) + "\n"
