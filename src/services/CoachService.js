const coachUrl = import.meta.env.VITE_CHATGPT_COACH_URL || 'https://chatgpt.com/gpts/mine';

export function buildCoachAdvicePrompt() {
    return 'What should I do now and for the rest of today?';
}

export function buildWorkoutAssessmentPrompt(date) {
    return `Assess all my workout sessions on ${date} together as one training day against my active coaching plan.`;
}

export function buildMealRatingPrompt(meal) {
    return `Rate my ${meal.label()} on ${meal.date.toISOString().slice(0, 10)} against my active coaching plan and suggest one improvement.`;
}

export function openCoach() {
    window.open(coachUrl, '_blank', 'noopener,noreferrer');
}
