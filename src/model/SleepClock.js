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
