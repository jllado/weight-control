import dayjs from 'dayjs';
import {weekdayRange} from './PlanningWeek';

export function buildReflectionPrompt(date) {
    return `Draft the daily reflection for ${date} from evidence through that date. Separately review every current Coach warning using today's evidence, show each rolling window, current metric and goal, then ask me to confirm the exact reflection and warningReview before saving them together.`;
}

export function buildWeeklyReflectionPrompt({periodStart, fridayDate, transition, snapshot}) {
    const periodEnd = snapshot.periodEnd || fridayDate;
    const outcomeStart = snapshot.followUpStart || periodEnd;
    const outcomeEnd = snapshot.followUpEnd || dayjs(periodEnd).add(2, 'day').format('YYYY-MM-DD');
    const range = weekdayRange(periodStart, periodEnd);
    return `Review the saved ${range} ${transition ? 'short transition ' : ''}summary ending ${periodEnd} (${periodStart} to ${periodEnd}) and draft its weekly reflection using the immutable snapshot. For progress weight and blood-pressure averages, use the label “${range} average” with the snapshot's full period dates and reading count. Describe selected outcomes as readings with their measured dates in the ${weekdayRange(outcomeStart, outcomeEnd)} window ${outcomeStart} to ${outcomeEnd}. Do not call an average a single weekly measurement or copy ambiguous wording from the saved reflection. Keep the overall summary and all six required sections concise: one finding and one action per section, no repeated snapshot data, and one to three next-week actions. Separately review every current Coach warning using today's evidence and show each rolling window, current metric and goal. Then ask me to confirm the exact reflection and warningReview before saving them together. Make no writes before my confirmation.`;
}
