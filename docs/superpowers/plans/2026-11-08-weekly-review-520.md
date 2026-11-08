# Weekly review and app-wide nutrition feedback

## Approved design and intent
Implement the approved three-tab mockup: Overview, Measurements, Reflection & actions. Keep the existing app's light compact PrimeVue style. Use existing classification/status meanings and colors (body EXCELLENT, blood pressure NORMAL, sleep scale, nutrient Goal met/Above goal/Below goal); no new generic On track label. Apply meaningful colors across measurement tabs, not only Overview. Keep absolute classifications separate from signed comparison changes. No specific food prescription in a metric table and no verbose vitamin-D explanation/cards.

Approved local mockup: /tmp/weekly-review-preview/.superpowers/brainstorm/4046876-1794122429/content/weekly-existing-statuses-v5.html. Screenshots /tmp/weekly-review-v5-body.png, /tmp/weekly-review-v4-nutrition.png (v4 nutrition food advice is superseded). Original user screenshots in this conversation govern intent. Demo goals/data must never become production defaults.

## Review interface
Overview leads with daily routine completion and three low-completion routines; searchable full list in Measurements, weekly routines separate. Group Measurements into Body & pressure, Sleep & mood, Training & back pain, Nutrition, Routines, Other metrics. Distinguish Saturday-Friday averages from dated Friday-Sunday outcomes. Compare this week, last week and 52 weeks ago with explicit units.
Workout activity: independently counted strength/cardio/stretching sessions including stretching in mixed/sauna sessions; actual active cardio minutes; compare with applicable dated saved plan, keep missing plan evidence neutral. Prior-period changes distinct from goal status.
Reflection/actions: first three saved next-week priorities; remaining recommendations expandable; preserve dedup, source identity, activation state, wording, all reflection/record/warning content and existing workflow.
Nutrition: seven compact rows including vitamin D and omega-3. Omega references may be disclosed on demand; no main-view subtype breakdown or food suggestion. Use responsive tables and keyboard-accessible navigation/disclosures.

## Shared nutrition rules (whole app)
Apply consistent assessments to Home dashboard/daily totals/trends, charts, settings, weekly report, future summary email and Coach.
Vitamin D: retain recorded estimates and applicable 15µg reference; default reference comparison neutral Reference only. Explicit personal goals retain normal comparison. Food intake is not a deficiency diagnosis. Distinguish daily, weekly average and 30-day average without verbose methodology.
Omega-3: keep independent total as measurement, assess saved EPA+DHA and ALA targets independently. Both established minima met => Goal met; established shortfall => Below goal; missing evidence or target => neutral unavailable unless a supported shortfall is already established. Preserve complete-day average/coverage rules; partial daily subtotals can establish met but never shortfall. No summing targets into a total goal, deriving subtypes from total, or automatically rewarding increasing total. Preserve custom settings/legacy total target as data, disclose direct legacy comparison separately instead of treating it as proof of subtype adequacy.
Keep food, recipe, meal source/reference/scaling/estimate contracts intact. No historical meal rewrite, automatic target reset, or diagnostic feedback. Coach suggestions may consider all food sources; the fish routine is independent of nutrient adequacy.

## Contracts and compatibility
Reuse shared assessment/presentation helpers rather than duplicating rules per screen/email/Coach. Additive presentation DTOs and next weekly snapshot schema capture rule versions, dated goals, nutrient evidence/coverage and workout targets for future saves. Old reports use stored evidence only, never today's goals. No resave/regeneration of old reflections/emails.
Extend existing Coach context/schema/instructions without increasing 30 Actions; preserve confirmation/privacy/ownership and limits. Deploy before publishing and independently verify private GPT saved/live configuration.

## New weekly routine
Create exactly one owned manual WEEKLY routine: Have fish for dinner once a week. Category WEIGHT (existing nutrition grouping), no reminders, personal-record notifications disabled. Begins creation week, Saturday-Friday Europe/Madrid. Check for equivalent existing routine to avoid duplicates; read back configuration. Do not mark completed, infer historic dinners or add automated meal detection.

## Acceptance and delivery
Verify overview/detail navigation, all data/topics accessible, target colors and existing labels, period selection, sparse/legacy snapshots, historical targets unaffected by profile edits, routine search, recommendation activation/persistence and desktop/mobile keyboard layouts.
Tests: omega both met/either short/unknown/partial/large total with subtype shortfall; vitamin default vs personal; boundaries/rounding; mixed workouts/plan missing; snapshots/Coach contract; new weekly routine exact configuration and duplicate avoidance.
Use scripts/check.sh sequentially for focused checks and full release artifact gate (shared contracts). Independent read-only QA against original request, revised mockup and user corrections; retain through release verification. Write repo design/implementation docs and applicable lessons. Validate, commit, push, integrate/deploy with repository release-plan; command-based identity/readiness verification, private GPT publication/readback and appropriate read-only/reversible acceptance. Create user-requested routine as retained intended data, not a temporary test.
This development invocation authorizes source changes, integration, push, deployment, required Coach publication and weekly routine creation without further permission. Scope is this newly created task only.
