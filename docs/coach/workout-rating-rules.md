# Workout day ratings

Each app or Coach workout create or edit saves one assessment for the owner's recorded date. Its two integer scores range from 1 to 10. The assessment includes every recorded session on that date. Moving a session updates both dates. Deleting a session refreshes its day, and deleting the final session removes the assessment. Replaying a successful creation with the same recording key preserves the original result. Other dates and saved reflections retain their existing values.

## Goal alignment

Use the saved weekly plan version applicable for the entire workout date in Europe/Madrid. The dated plan resolver handles archived, ambiguous and unavailable versions. When a dated rest day has no completed training segments it scores 10. A rest day with completed training scores 3. Warm-ups, stretching and sauna can all align with a rest day.

On a planned training day, match each expected session once by its saved snapshot key. For manual recordings, a unique one-to-one match requires the same exercises and sauna flag. Ambiguous or unrecorded sessions receive a neutral 5. An unrelated extra session cannot satisfy another session’s targets. If the dated plan is unavailable, assess each recording that carries its own saved targets. With no targets, alignment is 5 and the saved explanation identifies the evidence gap.

Match dated plan exercises by their saved catalog identity, exercise type and tracking mode. Renaming a catalog exercise preserves its match. A recording-only target with no saved catalog identity uses its saved name, type and tracking mode. If the name changed, the recording alone cannot establish the original identity. Compare each planned set to the recorded set in the same position. For each positive target in repetitions, seconds, breaths, kilograms, speed, cadence, distance, incline or resistance, calculate recorded quantity divided by target, capped at 1. The lowest ratio is the set’s credit. A missing value, missing set or skipped set gives zero credit for the missing work. Compare sauna rounds in order by recorded versus planned minutes, also capped at 1. Each set and round has equal weight. Score the session as 1 + 9 × mean credit. A session with no quantitative targets scores 5. Average the session scores and round to the nearest integer, with halves rounded up.

For example, two 8-rep targets at 20 kg with one completed exactly and one explicitly skipped score 5.5 before rounding and 6 for a single-session day. A second fully completed planned session raises the day average to 7.75 and its score to 8. Extra sets do not increase either session’s score beyond its targets.

The goal snapshot keeps the saved coaching goal when its start date and last update apply by the end of the workout date. Otherwise it describes the target source. Free-text goals and health advice do not change the calculation.

## Estimated training demand

Count only completed TRAINING segments. Every rep-based or timed strength set contributes one unit. Cardio contributes one unit per 300 recorded interval seconds, including fractional units. Warm-ups, stretching, sauna, skipped sets, breaks and total elapsed or phase times contribute zero. Zero units score 1. Otherwise the base score is 2 + floor(units / 3), capped at 9.

Within the previous 90 calendar days, select the three most recent owned workout dates containing a completed matching exercise ID on the same loading basis. Unweighted and positively weighted sets have separate comparisons. Sum each exercise’s repetitions or seconds across all sessions for each date. Multiply each weighted set’s quantity by its recorded kilograms before summing. Compute current/prior ratios for the shared exercise and loading pairs. Average the ratios equally within each day, then average the comparable days equally. At or below 75% subtract one point from the base. At or above 125% add one. Other ratios, missing measurements on a comparable basis and absent history produce no adjustment. Compare the exact average before rounding for display. When rounding would print a threshold crossed by a small amount, the explanation says “just under” or “just over.” Clamp the final score to 1–10.

For example, six strength sets give a base of 4. If the recorded comparable volume is 125% of the earlier baseline, the result is 5. Twelve minutes of sauna alone always give demand 1 regardless of elapsed session time.

WorkoutRatingRules supplies the live Coach rules. WorkoutAssessmentService applies them inside the workout mutation transaction. The existing Coach assessment context and diary return the same saved scores and explanation.
