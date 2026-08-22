const coachUrl = process.env.VUE_APP_CHATGPT_COACH_URL || 'https://chatgpt.com/gpts/mine';

export function buildCoachAdvicePrompt() {
    return 'What should I do now and for the rest of today?';
}

export function buildWorkoutAssessmentPrompt(date) {
    return `Assess my workout on ${date} against my active coaching plan.`;
}

export function openCoach() {
    window.open(coachUrl, '_blank', 'noopener,noreferrer');
}
