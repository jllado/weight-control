const coachUrl = import.meta.env.VITE_CHATGPT_COACH_URL || 'https://chatgpt.com/gpts/mine';

export function buildCoachAdvicePrompt() {
    return 'What should I do now and for the rest of today?';
}

export function buildWorkoutAssessmentPrompt(date, sessionReference) {
    return `Assess my workout session on ${date} (sessionReference: ${sessionReference}) against my active coaching plan.`;
}

export function openCoach() {
    window.open(coachUrl, '_blank', 'noopener,noreferrer');
}
