import dayjs from 'dayjs';

export const weekdays = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
export const dayLabel = day => day.charAt(0) + day.slice(1).toLowerCase();
export const copyPlan = value => JSON.parse(JSON.stringify(value));

export default class WorkoutPlan {
    constructor(source) {
        Object.assign(this, source ? copyPlan(source) : {
            startDate: dayjs().format('YYYY-MM-DD'), reviewDate: dayjs().add(6, 'week').format('YYYY-MM-DD'), notes: '',
            days: weekdays.map(day => ({day, rest: null, note: '', lines: []}))
        });
    }
    toPayload() {
        return {
            startDate: this.startDate, reviewDate: this.reviewDate, notes: this.notes,
            days: this.days.map(day => ({day: day.day, rest: day.rest, note: day.note, lines: day.lines.map(line => ({exerciseId: line.exerciseId, segments: line.segments}))}))
        };
    }
}
