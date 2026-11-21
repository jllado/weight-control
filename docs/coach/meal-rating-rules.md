# Meal ratings

Saving a meal in the app or through Coach stores its integer score and explanation in one transaction. Editing a meal replaces both. A copied meal gets a new calculation for the destination date. A change to a different meal, planning setting or profile leaves existing scores and explanations intact. Earlier rated and unrated meals keep their original values until edited.

## Calculation

Start at **5**, add the four adjustments below and limit the result to **1–10**.

| Evidence | Positive | Neutral | Negative |
| --- | --- | --- | --- |
| Protein in this meal | +1 at or above the reference density | At least half and below the reference, or unknown | −1 below half the reference |
| Fiber in this meal | +1 at or above the reference density | At least half and below the reference, or insufficient coverage | −1 below half the reference with complete coverage |
| Recorded calories on this date through this meal | +2 at or below the effective dated target | Above target through 110% | −2 above 110% |
| Average daily calories recorded from the planning-week start through this meal | +1 at or below the weekly average maximum | Above the maximum through 110% | −1 above 110% |

Each composition reference uses the personal daily gram goal divided by the effective calories for this date. If a goal is absent or the calorie target is unknown or zero, use the scoring convention of 5 g of protein or 1.4 g of fiber per 100 kcal. Compare exact densities before rounding. Zero meal calories make both density adjustments neutral. For partially recorded fiber, the known subtotal can establish the positive threshold. An incomplete amount below it is neutral.

The dated calorie plan takes precedence over the weekday profile default, including a saved zero. The local planning-week history determines its start for the meal date. Personal gram goals, weekday defaults and the weekly average maximum use the latest profile settings known effective by the meal date. Profile and nutrient-settings saves record a dated revision. At upgrade, each existing account's initial snapshot starts on the Madrid date of its persisted profile's last update. For new accounts, the initial settings apply from account creation. When older profile evidence is unavailable, a saved calorie plan can still supply the dated target. An unknown daily target or weekly cap contributes zero points. The explanation records the targets and reference densities used.

Both calorie comparisons include this meal once. Include all earlier dates in the same planning week and only the same-date meals through the rated meal. Order known meal times from earliest to latest, followed by untimed meals. Break ties, including between untimed meals, by database insertion order. A copied day is scored with the whole batch available. The weekly denominator counts recorded dates, including the partial meal date and recorded zero-calorie dates. Dates with no entries are unknown.

For example, a 500 kcal meal with 25 g protein and 7 g fiber meets both standard density references. If it takes the day to 2,100 kcal against a 2,000 kcal target and the recorded weekly average is within the maximum, its score is 8 (5 + 1 + 1 + 0 + 1).

Coach reads the saved score and explanation through the existing meal reads. The rules operation returns the same thresholds. Legacy rating-only requests return the stored result, subject to their existing validation, confirmation and ownership requirements.
