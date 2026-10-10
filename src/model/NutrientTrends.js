import dayjs from 'dayjs';

export function isNutrientCovered(item, field) {
    const nutrients = item?.nutrients;
    return item?.completed && nutrients?.[field.key] != null && nutrients.totalFoods > 0 && nutrients.foodsWithValues === nutrients.totalFoods && nutrients.mealsWithoutFoods === 0;
}

export function coveredNutrientDays(summaries, date, days, field) {
    const start = dayjs(date).subtract(days - 1, 'day');
    return summaries.filter(item => !dayjs(item.date).isBefore(start, 'day') && !dayjs(item.date).isAfter(date, 'day') && isNutrientCovered(item, field));
}

export function averageNutrientValue(rows, field) {
    return rows.length ? (rows.reduce((sum, item) => sum + item.nutrients[field.key], 0) / rows.length).toFixed(2) : '—';
}

export function nutrientChartData(summaries, from, to, field) {
    const rows = new Map(summaries.map(item => [dayjs(item.date).format('YYYY-MM-DD'), item]));
    const days = dayjs(to).diff(dayjs(from), 'day') + 1;
    const labels = Array.from({length: days}, (_, i) => dayjs(from).add(i, 'day'));
    const data = labels.map(day => {
        const row = rows.get(day.format('YYYY-MM-DD'));
        return isNutrientCovered(row, field) ? row.nutrients[field.key] : null;
    });
    const pointColors = labels.map(day => rows.get(day.format('YYYY-MM-DD'))?.nutrients?.estimatedFoods ? '#d69e2e' : '#2f855a');
    return {labels: labels.map(day => day.format('D MMM')), datasets: [{label: `${field.label} (${field.unit})`, data, spanGaps: false, borderColor: '#2f855a', backgroundColor: '#2f855a', pointBackgroundColor: pointColors, pointBorderColor: pointColors, pointRadius: 4, tension: 0.25}]};
}

export function nutrientChartRows(summaries, from, to, field) {
    const rows = new Map(summaries.map(item => [dayjs(item.date).format('YYYY-MM-DD'), item]));
    const days = dayjs(to).diff(dayjs(from), 'day') + 1;
    return Array.from({length: days}, (_, i) => {
        const day = dayjs(from).add(i, 'day');
        const item = rows.get(day.format('YYYY-MM-DD'));
        const nutrients = item?.nutrients;
        if (!item) return {date: day.format('YYYY-MM-DD'), description: 'no daily total recorded'};
        if (!item.completed) return {date: day.format('YYYY-MM-DD'), description: 'day not marked complete'};
        if (!isNutrientCovered(item, field)) return {date: day.format('YYYY-MM-DD'), description: `incomplete nutrient coverage (${nutrients?.foodsWithValues ?? 0}/${nutrients?.totalFoods ?? 0} foods; ${nutrients?.mealsWithoutFoods ?? 0} meals without foods)`};
        return {date: day.format('YYYY-MM-DD'), description: `${nutrients[field.key]} ${field.unit}${nutrients.estimatedFoods ? ' (includes estimates)' : ''}`};
    });
}

export function nutrientChartDescription(summaries, from, to, field) {
    return nutrientChartRows(summaries, from, to, field).map(row => `${row.date}: ${row.description}`).join('; ');
}
