export function buildReflectionPrompt(date) {
    return `Generate or update and save the reflection for ${date} using the latest context.`;
}

export function buildReflectionAdvicePrompt(reflection, contextDate, currentTime) {
    return `Give me practical wellness advice for the current moment.

First, call getReflectionContext for ${contextDate} to review my latest available health data. Use that data together with my latest saved reflection below. Do not create, update, or save a reflection.

Current local date and time: ${currentTime}

Take the current hour into account. Respond with two short sections: "Now", with one realistic action I can take immediately, and "Rest of today", with a concise plan adjusted to the time remaining. Base the advice only on the retrieved data and reflection. Treat missing information as unknown. Do not diagnose conditions or recommend treatment or medication changes.

Latest saved reflection:
Date: ${reflection.reflectionDate}
Title: ${reflection.title}
Summary: ${reflection.summary}
Positive signal: ${reflection.positiveSignals[0]}
Watchout: ${reflection.watchouts[0]}
Next action: ${reflection.nextActions[0]}`;
}
