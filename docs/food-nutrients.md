# Food nutrients

Foods, recipe ingredients and logged food snapshots carry vitamin D (µg), total omega-3 (mg), magnesium (mg), a source note and an estimate flag. New food writes require all values; zero is valid only when supplied or supported, never a substitute for missing data. Source notes describe product composition, published food data, or explicit estimates. Total omega-3 includes the available omega-3 components, not EPA/DHA alone; avoid double-counting overlapping totals and components.

Portion references preserve all three values. Quantity changes scale with half-up two-decimal rounding; disabling scaling or correcting units resets the reference without implicit conversion. Copies and recipe expansion remain independent snapshots. Meal and day totals sum foods with all three values and expose recorded/total/estimated food counts plus meals without food rows. Coverage concerns logged foods only, not all actual consumption. Foodless meals and unmigrated foods are unknown. No targets or deficiency conclusions are introduced.

## Historical migration

The Flyway migration adds nullable columns so historical unknown values are not silently replaced by zero. Prepare a reviewed owner-scoped mapping for every catalog food (including deleted snapshots), recipe ingredient and historical meal food. Use exact product data where available; otherwise use suitable USDA FoodData Central composition and label portion, recipe and generic-product estimates. Resolve unidentified foods before applying; calorie-only meals remain unknown.

Keep the inventory, reviewed values, expected old fields and inverse SQL in ignored `tmp/nutrients/`. Create a temporary `nutrient_backfill` table with the columns listed in `scripts/sql/food-nutrients.sql`, load the mapping, and execute that script in the same MariaDB batch session without `--force`. It locks matched rows, validates owner/name/portion/calorie fields and empty destination fields, then fills reference and scaled values transactionally. Failure must disconnect and roll back; never ignore SQL errors. Repeated application rejects already populated values.

Before execution, compare mapping counts with the fresh inventory and record unresolved rows. After execution, verify zero unmapped food snapshots for the owner, unchanged existing fields and derived totals. Do not overwrite existing nutrient corrections or commit private food inventories. No live external API is called during migration or food saves.

## Coach and delivery

FOODS, DISHES, meal reads/writes and NUTRITION context expose nutrient values and coverage through existing Actions. Coach reuses saved values, researches product/USDA data, or labels inferred amounts before the existing exact meal confirmation. New food writes missing the new values fail validation, so deploy the app, complete the historical backfill and publish the private GPT schema/instructions as a coordinated delivery. Preserve 30 Actions, reflection JSON, account isolation and catalog/consumption separation. Live GPT publication remains separate from application verification.

## Delivery evidence — October 4, 2026

Application commit `f10e3a7c11fb89baf6346554a908594fb6d3a08d` is deployed with source tree `0bf9d20a14f2aecc8f0457fba201ad1669cae3b5`; the complete release gate and independent command-based production identity/readiness verification passed. The historical backfill populated 628 snapshots, clarified four catalog names and retained four retired aliases, and verified 632 snapshots with zero missing nutrient values and preserved preexisting nutrition and portion fields; do not rerun it. Private inventories and migration evidence remain outside source control.

Published the matching private GPT schema and 7,995-character instructions; the editor reported **GPT Updated**. Reloaded configuration matched source exactly: schema SHA-256 `8c8ab73a46f8a686a4c4fa2dcb8e442df8eed1a4a1ffd5bdb3ee3c97333e585a`, instruction SHA-256 `63ab75f804f9642b04ad57f31a7dbf50cb7c211c9194f4ae50d68f2da5c42685`. All 30 Actions remained available without parser errors; API Key authentication, Only me visibility and the recommended model were preserved.

A fresh published-GPT conversation returned saved food and recipe nutrients, sources and estimate flags, correctly calculated half a saved reference portion, and distinguished logged-food coverage from complete intake without inferring deficiency. Production acceptance used read-only retrieval and a hypothetical proposal; confirmed write behavior retains the existing automated coverage. Application identity, GPT publication and conversational acceptance are separate evidence; the later documentation commits do not change the deployed application identity.
