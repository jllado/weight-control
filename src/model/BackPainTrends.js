import dayjs from 'dayjs';
import {BACK_PAIN_AREAS, datedBackPainRecords, backPainDailyAverages} from './BackPainDaily';

function periodAverage(days) {
    return {days: days.length, average: days.length ? days.reduce((sum, day) => sum + day.average, 0) / days.length : null};
}

export function buildBackPainTrends(episodes, date) {
    const end = dayjs(date).format('YYYY-MM-DD');
    const start = dayjs(end).subtract(29, 'day').format('YYYY-MM-DD');
    const previousEnd = dayjs(end).subtract(30, 'day').format('YYYY-MM-DD');
    const previousStart = dayjs(end).subtract(59, 'day').format('YYYY-MM-DD');
    const records = datedBackPainRecords(episodes).filter(record => record.date >= previousStart && record.date <= end);
    const activeAreas = new Set(records.filter(record => record.date >= start && record.severity !== 'NONE').map(record => `${record.region}_${record.side}`));
    const areas = [{value: 'ALL', label: 'Back'}, ...BACK_PAIN_AREAS.filter(area => activeAreas.has(area.value))];
    const rows = areas.map(area => {
        const days = [...backPainDailyAverages(records, area.value).values()];
        const current = periodAverage(days.filter(day => day.date >= start));
        const previous = periodAverage(days.filter(day => day.date <= previousEnd));
        return {...area, current, previous, delta: current.average !== null && previous.average !== null ? current.average - previous.average : null};
    });
    return {start, end, previousStart, previousEnd, rows};
}

export function backPainTrendNumber(value) {
    return value === null ? '—' : String(Number(value.toFixed(2)));
}

export function backPainTrendDelta(value) {
    const label = backPainTrendNumber(value);
    return value > 0 && label !== '0' ? `+${label}` : label;
}
