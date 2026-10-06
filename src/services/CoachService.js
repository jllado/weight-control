const coachUrl = import.meta.env.VITE_CHATGPT_COACH_URL || 'https://chatgpt.com/gpts/mine';

export function buildCoachAdvicePrompt() {
    return 'What should I do now and for the rest of today?';
}

export function buildWorkoutAssessmentPrompt(date) {
    return `Assess all my workout sessions on ${date} together as one training day against my active coaching plan.`;
}

export function buildMealRatingPrompt(meal) {
    return `Rate my ${meal.label()} on ${meal.date.toISOString().slice(0, 10)} out of 10. Check the meals from this Saturday through that date, my calorie targets and weekly-average cap, and my active coaching plan. Suggest one improvement, present the proposed score for my confirmation, then save that rating to the meal after I confirm the exact score.`;
}

export function buildAllMealRatingPrompt(date) {
    return `Rate all my meals recorded on ${date}, including meals already rated. Check meals from this Saturday through ${date}, my calorie targets and weekly-average cap, and my active coaching plan. Propose one integer score out of 10 and one improvement for each meal, in the returned meal order. Show the complete meal-by-meal list, then ask whether I want to save all the proposed ratings. Make no writes until I explicitly confirm the full list. Then save each meal's rating only. If any write fails, read back every meal, report saved and unsaved ratings accurately, and stop without rolling back or claiming full success.`;
}

export function openCoach() {
    window.open(coachUrl, '_blank', 'noopener,noreferrer');
}
