import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc.js';

dayjs.extend(utc);

export function sleepClock(date, offsetSeconds) {
    return offsetSeconds == null ? dayjs(date) : dayjs.utc(new Date(date).getTime() + offsetSeconds * 1000);
}

export function sleepClockMinutes(date, offsetSeconds) {
    const clock = sleepClock(date, offsetSeconds);
    return clock.hour() * 60 + clock.minute();
}

// Compare recorded wall clocks, while duration calculations continue to use instants.
export function sleepMidpointMinutes(start, end, startOffset, endOffset) {
    const midpoint = new Date((localClockTimestamp(start, startOffset) + localClockTimestamp(end, endOffset)) / 2);
    return midpoint.getUTCHours() * 60 + midpoint.getUTCMinutes() + midpoint.getUTCSeconds() / 60 + midpoint.getUTCMilliseconds() / 60000;
}

const madridClock = new Intl.DateTimeFormat('en-GB', {timeZone: 'Europe/Madrid',
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23'});

function localClockTimestamp(date, offsetSeconds) {
    if (offsetSeconds != null) return date.getTime() + offsetSeconds * 1000;
    const parts = Object.fromEntries(madridClock.formatToParts(date).map(part => [part.type, part.value]));
    return Date.UTC(Number(parts.year), Number(parts.month) - 1, Number(parts.day), Number(parts.hour), Number(parts.minute), Number(parts.second), date.getUTCMilliseconds());
}

export function sleepEditorValue(date, offsetSeconds) {
    return date === null ? null : sleepClock(date, offsetSeconds).format('YYYY-MM-DDTHH:mm:ss.SSS');
}

export function sleepInstantFromEditor(value, offsetSeconds) {
    return new Date(offsetSeconds == null ? value : `${value}${formatOffset(offsetSeconds)}`);
}

export function normalizeSleepClock(value) {
    if (value === null || value === '') return value;
    const clock = new Date(`${value}Z`);
    clock.setUTCSeconds(0, 0);
    clock.setUTCMinutes(Math.round(clock.getUTCMinutes() / 5) * 5);
    return clock.toISOString().slice(0, 16);
}

export function sleepTimestamp(date, offsetSeconds) {
    return offsetSeconds == null ? dayjs(date).format('YYYY-MM-DDTHH:mm:ssZ')
        : `${sleepClock(date, offsetSeconds).format('YYYY-MM-DDTHH:mm:ss')}${formatOffset(offsetSeconds)}`;
}

function formatOffset(seconds) {
    const minutes = Math.abs(seconds) / 60;
    return `${seconds < 0 ? '-' : '+'}${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;
}
