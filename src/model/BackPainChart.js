import dayjs from 'dayjs';
import {BACK_PAIN_SEVERITIES, getBackPainSeverityRank} from './BackPainEpisode';

const backPainDateFormatter = new Intl.DateTimeFormat('en-CA', {timeZone: 'Europe/Madrid', year: 'numeric', month: '2-digit', day: '2-digit'});

function observationLabel(point) {
    return `${dayjs(point.date).format('DD/MM/YYYY')}: ${point.average.toFixed(2)}/4, ${point.count} check-in${point.count === 1 ? '' : 's'}`;
}

export function buildBackPainChart(episodes, {chartType, today, area, period}) {
    const records = episodes.map(episode => ({...episode, date: backPainDateFormatter.format(episode.date)})).filter(episode => episode.date <= today);
    const firstDate = records.reduce((first, episode) => episode.date < first ? episode.date : first, today);
    const start = chartType === 'all' ? firstDate : dayjs(today).subtract(chartType === 'monthly' ? 3 : 1, chartType === 'monthly' ? 'month' : 'year').format('YYYY-MM-DD');
    const totals = new Map();
    for (const record of records) {
        if (period !== 'ALL' && record.period !== period) continue;
        if (area !== 'ALL' && record.severity !== 'NONE' && `${record.region}_${record.side}` !== area) continue;
        const total = totals.get(record.date) || {sum: 0, count: 0};
        total.sum += getBackPainSeverityRank(record.severity);
        total.count++;
        totals.set(record.date, total);
    }
    const point = date => {
        const total = totals.get(date);
        return total ? {date, average: total.sum / total.count, count: total.count} : null;
    };
    const rows = [];
    for (let day = dayjs(start); !day.isAfter(today, 'day'); day = day.add(1, 'day')) {
        const date = day.format('YYYY-MM-DD');
        const previousDate = day.subtract(1, 'year').format('YYYY-MM-DD');
        const previous = chartType !== 'all' && date.slice(5) === previousDate.slice(5) ? point(previousDate) : null;
        rows.push({date, current: point(date), previous});
    }
    const hasPrevious = rows.some(row => row.previous !== null);
    const series = [{key: 'current', label: 'Current', color: '#1a36c1', dash: []}];
    if (hasPrevious) series.push({key: 'previous', label: 'Year ago', color: 'gray', dash: [6, 4]});
    const datasets = series.map(({key, label, color, dash}) => ({
        label, borderColor: color, backgroundColor: color, borderDash: dash, fill: false, spanGaps: false, tension: 0,
        pointRadius: 3, pointHoverRadius: 5,
        data: rows.map(row => row[key]?.average ?? null),
        pointLabels: rows.map(row => row[key] ? observationLabel(row[key]) : null)
    }));
    const description = [`Daily average severity, 0 No pain, 1 Mild, 2 Moderate, 3 Severe, 4 Extreme. ${dayjs(start).format('DD/MM/YYYY')} to ${dayjs(today).format('DD/MM/YYYY')}. Missing days are gaps.`,
        ...datasets.map(dataset => `${dataset.label}: ${dataset.pointLabels.filter(Boolean).join('; ') || 'No matching check-ins.'}`)].join(' ');
    return {
        rows, hasPrevious, hasData: rows.some(row => row.current !== null || row.previous !== null), description,
        data: {labels: rows.map(row => dayjs(row.date).format('DD/MM/YYYY')), datasets},
        options: {
            responsive: true, maintainAspectRatio: false, animation: false,
            plugins: {
                title: {display: true, text: 'Daily average back pain /4'},
                tooltip: {callbacks: {title: () => '', label: context => `${context.dataset.label}: ${context.dataset.pointLabels[context.dataIndex]}`}}
            },
            scales: {
                x: {ticks: {autoSkip: true, maxTicksLimit: 8}},
                y: {min: 0, max: 4, ticks: {stepSize: 1, callback: value => BACK_PAIN_SEVERITIES[value].label}}
            }
        }
    };
}
