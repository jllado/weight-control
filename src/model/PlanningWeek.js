import dayjs from 'dayjs';
import {appState} from '../state.js';

export const weekdays = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];
export const dayLabel = day => day.charAt(0) + day.slice(1).toLowerCase();
export const madridDate = (value = new Date()) => madridFormatter.format(value);
const madridFormatter = new Intl.DateTimeFormat('en-CA', {timeZone: 'Europe/Madrid', year: 'numeric', month: '2-digit', day: '2-digit'});
const iso = date => date.format('YYYY-MM-DD');

export function planningWeek(date, changes = appState().user.profile?.planningWeekChanges || []) {
    const day = dayjs(date);
    const key = iso(day);
    const next = changes.find(change => change.effectiveDate > key);
    const firstDay = changes.findLast(change => change.effectiveDate <= key)?.firstDay || 'SATURDAY';
    const start = day.subtract((day.day() - weekdays.indexOf(firstDay) + 7) % 7, 'day');
    const regularEnd = iso(start.add(6, 'day'));
    const transition = !!next && next.effectiveDate <= regularEnd;
    return {start: iso(start), end: transition ? iso(dayjs(next.effectiveDate).subtract(1, 'day')) : regularEnd, firstDay, transition};
}

export function planningDates({start, end}) {
    const first = dayjs(start);
    return Array.from({length: dayjs(end).diff(first, 'day') + 1}, (_, index) => iso(first.add(index, 'day')));
}

export function weekdaysFrom(firstDay) {
    const index = weekdays.indexOf(firstDay);
    return [...weekdays.slice(index), ...weekdays.slice(0, index)];
}

export function nextOccurrence(firstDay, today) {
    const day = dayjs(today);
    return iso(day.add((weekdays.indexOf(firstDay) - day.day() + 7) % 7 || 7, 'day'));
}

export function weekdayRange(start, end) {
    return `${dayjs(start).format('dddd')}–${dayjs(end).format('dddd')}`;
}

export function periodLabel(start, end) {
    const first = dayjs(start);
    const last = dayjs(end);
    return `${last.diff(first, 'day') < 6 ? 'Short transition' : 'Planning week'} · ${first.format('ddd D MMM YYYY')} – ${last.format('ddd D MMM YYYY')}`;
}
