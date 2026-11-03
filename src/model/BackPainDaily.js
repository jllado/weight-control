import {BACK_REGIONS, BACK_SIDES, getBackPainSeverityRank} from './BackPainEpisode';

const backPainDateFormatter = new Intl.DateTimeFormat('en-CA', {timeZone: 'Europe/Madrid', year: 'numeric', month: '2-digit', day: '2-digit'});

export const BACK_PAIN_AREAS = BACK_REGIONS.flatMap(region => BACK_SIDES.map(side => ({label: `${region.label} ${side.label.toLowerCase()}`, value: `${region.value}_${side.value}`})));

export function datedBackPainRecords(episodes) {
    return episodes.map(episode => ({...episode, date: backPainDateFormatter.format(episode.date)}));
}

export function backPainDailyAverages(records, area = 'ALL', period = 'ALL') {
    const totals = new Map();
    for (const record of records) {
        if (period !== 'ALL' && record.period !== period) continue;
        if (area !== 'ALL' && record.severity !== 'NONE' && `${record.region}_${record.side}` !== area) continue;
        const total = totals.get(record.date) || {sum: 0, count: 0};
        total.sum += getBackPainSeverityRank(record.severity);
        total.count++;
        totals.set(record.date, total);
    }
    return new Map([...totals].map(([date, {sum, count}]) => [date, {date, average: sum / count, count}]));
}
