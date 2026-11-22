# Meal ratings

Saving or editing a meal in the app or Coach stores its score and explanation together. A copy is assessed for its destination date. Read the saved explanation for the inputs, estimates, skipped criteria and one suggested improvement. An earlier meal entered, moved, edited or deleted refreshes affected automatic scores on that date. Later intake does not count toward earlier meals.

## Food and portion criteria

| Criterion | Possible points | Calculation |
| --- | ---: | --- |
| Non-starchy vegetables | 2 | Give proportional credit up to half of estimated edible plate volume. |
| Protein foods and starches | ½ each | Give proportional credit up to one quarter of the plate for each group. |
| Protein and fiber | 1 each | Compare the recorded grams with the dated daily goal multiplied by the meal’s share of that day’s calorie target. Credit rises proportionally to the goal. |
| Salt and saturated fat | 1 each | At or below the meal allowance, give full credit. Taper to zero at twice the allowance. Scale the salt limit to the meal’s calorie share. The saturated-fat allowance in grams is meal kcal × configured energy percentage ÷ 900. |
| Main-meal size | 1 | Give full credit through the configured upper share of the dated calorie target, then taper to zero at the entire daily target. |

Settings → Meal preferences controls main meals (initially lunch and dinner), the upper calorie share (65%), timing, and saturated fat (10% of energy). Plate and size criteria apply to configured main meals. Estimate volume using the logged quantities and identified foods, including itemized recipe ingredients. A plate with identifiable foods and no vegetables earns zero vegetable credit. Skip uncertain plate shares and nutrients without sufficient evidence.

Divide the points earned by the points available for that meal and multiply by ten. After the daily-budget and timing adjustments, round once to an integer from 1 to 10. With no assessable food or portion criterion, save an explanation and leave the rating empty.

## Remaining daily budgets

Every meal type uses the dated calorie budget and the carbohydrate maximum shown in Nutrition. Sum earlier meals on the same calendar date, ordering equal start times by record ID. For each budget, subtract up to two points from the normalized score in proportion to the fraction of this meal above the remaining amount. If earlier meals already exhausted the budget, only this meal’s own intake counts as new excess. Meeting the remaining amount exactly incurs no deduction. An unknown meal order skips both adjustments. Missing carbohydrate evidence skips the carbohydrate adjustment independently. The saved explanation lists the amount already eaten, target, remaining allowance, meal amount, new excess and deduction.

## Timing

Use the previous recorded eating occasion’s end and this meal’s start. The initial minimum gap is 90 minutes. Dinner and evening snacks also use the interval from finish to usual bedtime, initially at least 60 minutes. Usual bedtime is the median local bedtime from the preceding seven nights with records, treating times across midnight as adjacent. A snack counts as evening after dinner or from four hours before usual bedtime through that evening. Meeting a threshold exactly meets the goal. Deduct one point if either or both goals were missed. When evidence is missing, explain the gap.

## Saved scores

The release migrates earlier backend-generated scores once after saturated-fat enrichment. It keeps original Coach and manual ratings and leaves previously unrated meals unrated. The authenticated owner can preview the migration with POST /api/meals/rerate?apply=false. Applying it with apply=true selects the same legacy automatic scores under an owner lock. A second application has no eligible scores. The Coach rules Action describes the current calculation.

To upgrade the previous automatic ratings for one owner and date, run the deployed backend with `java -jar /app/app.jar --spring.main.web-application-type=none --spring.flyway.enabled=false --app.meal-rating.rerate.mode=dry-run --app.meal-rating.rerate.owner-email=EMAIL --app.meal-rating.rerate.date=YYYY-MM-DD`. Review the meal times and scores, then repeat with `mode=apply`. The command preserves manual ratings, food records, meal timestamps and other dates.
