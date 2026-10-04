# Food nutrients

Foods, recipe ingredients and logged food snapshots carry vitamin D (µg), total omega-3 (mg), magnesium (mg), a source note and an estimate flag. New food writes require all values; zero is valid only when supplied or supported, never a substitute for missing data. Source notes describe product composition, published food data, or explicit estimates. Total omega-3 includes the available omega-3 components, not EPA/DHA alone; avoid double-counting overlapping totals and components.

Portion references preserve all three values. Quantity changes scale with half-up two-decimal rounding; disabling scaling or correcting units resets the reference without implicit conversion. Copies and recipe expansion remain independent snapshots. Meal and day totals sum foods with all three values and expose recorded/total/estimated food counts plus meals without food rows. Coverage concerns logged foods only, not all actual consumption. Foodless meals and unmigrated foods are unknown. No targets or deficiency conclusions are introduced.

## Historical migration

The Flyway migration adds nullable columns so historical unknown values are not silently replaced by zero. Prepare a reviewed owner-scoped mapping for every catalog food (including deleted snapshots), recipe ingredient and historical meal food. Use exact product data where available; otherwise use suitable USDA FoodData Central composition and label portion, recipe and generic-product estimates. Resolve unidentified foods before applying; calorie-only meals remain unknown.

Keep the inventory, reviewed values, expected old fields and inverse SQL in ignored `tmp/nutrients/`. Create a temporary `nutrient_backfill` table with the columns listed in `scripts/sql/food-nutrients.sql`, load the mapping, and execute that script in the same MariaDB batch session without `--force`. It locks matched rows, validates owner/name/portion/calorie fields and empty destination fields, then fills reference and scaled values transactionally. Failure must disconnect and roll back; never ignore SQL errors. Repeated application rejects already populated values.

Before execution, compare mapping counts with the fresh inventory and record unresolved rows. After execution, verify zero unmapped food snapshots for the owner, unchanged existing fields and derived totals. Do not overwrite existing nutrient corrections or commit private food inventories. No live external API is called during migration or food saves.

## Coach and delivery

FOODS, DISHES, meal reads/writes and NUTRITION context expose nutrient values and coverage through existing Actions. Coach reuses saved values, researches product/USDA data, or labels inferred amounts before the existing exact meal confirmation. New food writes missing the new values fail validation, so deploy the app, complete the historical backfill and publish the private GPT schema/instructions as a coordinated delivery. Preserve 30 Actions, reflection JSON, account isolation and catalog/consumption separation. Live GPT publication remains separate from application verification.
