const coachUrl = import.meta.env.VITE_CHATGPT_COACH_URL || 'https://chatgpt.com/gpts/mine';

export function buildCoachAdvicePrompt() {
    return 'What should I do now and for the rest of today?';
}

export function buildWorkoutAssessmentPrompt(date) {
    return `Assess all my workout sessions on ${date} together as one training day against my active coaching plan.`;
}

export function buildMealRatingPrompt(meal) {
    return `Rate my ${meal.label()} on ${meal.date.toISOString().slice(0, 10)} out of 10. Check the meals from this Saturday through that date, my calorie targets and weekly-average cap, and my active coaching plan. Suggest one improvement and save the score after I confirm it.`;
}

export function openCoach() {
    window.open(coachUrl, '_blank', 'noopener,noreferrer');
}
