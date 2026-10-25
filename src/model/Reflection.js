import dayjs from 'dayjs';

export function buildReflectionPrompt(date) {
    return `Draft the daily reflection for ${date} from evidence through that date. Separately review every current Coach warning using today's evidence, show each direction and resolution criteria, then ask me to confirm the exact reflection and warningReview before saving them together.`;
}

export function buildWeeklyReflectionPrompt(fridayDate) {
    const friday = dayjs(`${fridayDate}T00:00:00`);
    const periodStart = friday.subtract(6, 'day').format('YYYY-MM-DD');
    const outcomeEnd = friday.add(2, 'day').format('YYYY-MM-DD');
    return `Review the saved Saturday–Friday summary ending ${fridayDate} (${periodStart} to ${fridayDate}) and draft its weekly reflection using the immutable snapshot. For progress weight and blood-pressure averages, use the label “Saturday–Friday average” with the snapshot's full period dates and reading count; describe selected outcomes as readings with their measured dates in the Friday–Sunday window ${fridayDate} to ${outcomeEnd}. Do not call an average a single weekly measurement or copy ambiguous wording from the saved reflection. Keep the overall summary and all six required sections concise: one finding and one action per section, no repeated snapshot data, and one to three next-week actions. Then ask me to confirm the exact content before saving it. Make no writes before my confirmation.`;
}
