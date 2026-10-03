export function buildReflectionPrompt(date) {
    return `Generate or update and save the reflection for ${date} using the latest context.`;
}

export function buildWeeklyReflectionPrompt(fridayDate) {
    return `Review the saved Saturday–Friday summary ending ${fridayDate}, draft its weekly reflection, then ask me to confirm the exact content before saving it.`;
}
