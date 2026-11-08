/* global BigInt */
import dayjs from 'dayjs';

export function nutrientValue(item, field) {
    return field.kind === 'macro' ? item?.[field.key] : item?.nutrients?.[field.key];
}

export function nutrientCoverage(item, field) {
    const nutrients = item?.nutrients;
    return nutrients?.[field.coverageKey] ?? (['epaDhaMilligrams', 'alaMilligrams'].includes(field.key) ? null : {
        foodsWithValues: nutrients?.foodsWithValues ?? 0, estimatedFoods: nutrients?.estimatedFoods ?? 0
    });
}

export function isNutrientCovered(item, field) {
    if (!item?.completed || nutrientValue(item, field) == null) return false;
    if (field.kind === 'macro') return item.macrosComplete;
    const nutrients = item.nutrients;
    return nutrients.totalFoods > 0 && nutrientCoverage(item, field)?.foodsWithValues === nutrients.totalFoods
        && nutrients.mealsWithoutFoods === 0;
}

export function coveredNutrientDays(summaries, date, days, field) {
    const start = dayjs(date).subtract(days - 1, 'day');
    return summaries.filter(item => !dayjs(item.date).isBefore(start, 'day') && !dayjs(item.date).isAfter(date, 'day') && isNutrientCovered(item, field));
}

export function partialNutrientDays(summaries, date, days, field) {
    const start = dayjs(date).subtract(days - 1, 'day');
    return summaries.filter(item => !dayjs(item.date).isBefore(start, 'day') && !dayjs(item.date).isAfter(date, 'day')
        && item.completed && nutrientValue(item, field) != null && !isNutrientCovered(item, field));
}

const cents = value => BigInt(Math.round(Number(value) * 100));
const sumCents = (rows, field) => rows.reduce((sum, row) => sum + cents(nutrientValue(row, field)), 0n);

function roundedCentsLabel(numerator, denominator) {
    const rounded = (numerator * 2n + denominator) / (denominator * 2n);
    const fraction = (rounded % 100n).toString().padStart(2, '0').replace(/0+$/, '');
    return `${rounded / 100n}${fraction ? `.${fraction}` : ''}`;
}

export function averageNutrientLabel(rows, field) {
    return rows.length ? `${roundedCentsLabel(sumCents(rows, field), BigInt(rows.length))} ${field.unit}/day` : '—';
}

export function nutrientChangeLabel(currentRows, previousRows, field) {
    if (!currentRows.length || !previousRows.length) return 'Prior 30d: no data';
    const currentCount = BigInt(currentRows.length), previousCount = BigInt(previousRows.length);
    const difference = sumCents(currentRows, field) * previousCount - sumCents(previousRows, field) * currentCount;
    const label = roundedCentsLabel(difference < 0n ? -difference : difference, currentCount * previousCount);
    return `${label === '0' ? '' : difference < 0n ? '−' : '+'}${label}`;
}

export function nutrientMinimumStatus(rows, field, target) {
    const met = sumCents(rows, field) >= BigInt(rows.length) * cents(target);
    return {label: met ? 'At or above current minimum' : 'Below current minimum', className: met ? 'target-met' : 'target-missed'};
}

export function omega3TrendStatus(rows, field, targets) {
    return combinedOmegaStatus(omegaFields.map(component => {
        const covered = rows.filter(row => isNutrientCovered(row, component));
        const target = targets[component.targetKey]?.value;
        return !covered.length || target == null ? null : nutrientMinimumStatus(covered, component, target).className;
    }));
}

const omegaFields = [
    {key: 'epaDhaMilligrams', coverageKey: 'epaDhaCoverage', targetKey: 'epaDha'},
    {key: 'alaMilligrams', coverageKey: 'alaCoverage', targetKey: 'ala'}
];

function combinedOmegaStatus(statuses) {
    if (statuses.includes('target-missed')) return {className: 'target-missed', label: 'Below goal'};
    if (statuses.every(status => status === 'target-met')) return {className: 'target-met', label: 'Goal met'};
    return {className: '', label: 'Assessment unavailable'};
}

export function nutrientTargetStatus(rows, field, targets) {
    if (field.key === 'omega3Milligrams') return omega3TrendStatus(rows, field, targets);
    const key = {vitaminDMicrograms: 'vitaminD', magnesiumMilligrams: 'magnesium', fiberGrams: 'fiber'}[field.key];
    const target = targets[key];
    if (target?.value == null || !rows.length) return null;
    if (key === 'vitaminD' && target.source !== 'PERSONAL') return {className: '', label: 'Reference only'};
    return nutrientMinimumStatus(rows, field, target.value);
}

function dailyMinimumStatus(summary, field, target, completed) {
    if (summary[field.key] == null || target == null) return null;
    const status = nutrientMinimumStatus([{nutrients: summary}], field, target);
    const coverage = nutrientCoverage({nutrients: summary}, field);
    const partial = !completed || summary.totalFoods === 0 || summary.mealsWithoutFoods > 0 || coverage?.foodsWithValues !== summary.totalFoods;
    return partial && status.className === 'target-missed'
        ? {className: '', label: 'Partial recorded total; the minimum is not yet established.'} : status;
}

export function dailyNutrientStatus(summary, field, targets, completed = true) {
    if (summary[field.key] == null || !targets) return null;
    if (field.key === 'omega3Milligrams') {
        return combinedOmegaStatus(omegaFields.map(component => dailyMinimumStatus(summary, component, targets[component.targetKey]?.value, completed)?.className));
    }
    const key = {vitaminDMicrograms: 'vitaminD', magnesiumMilligrams: 'magnesium', fiberGrams: 'fiber'}[field.key];
    if (key === 'vitaminD' && targets.vitaminD?.value != null && targets.vitaminD.source !== 'PERSONAL') return {className: '', label: 'Reference only'};
    return dailyMinimumStatus(summary, field, targets[key]?.value, completed);
}

export function macroTargetStatus(rows, field, fatPercentage) {
    if (rows.some(row => row.macroTargets?.[field.targetKey]?.value == null)) return {label: 'Current target unavailable', className: ''};
    if (field.targetKey !== 'protein' && fatPercentage == null) return {label: 'Current target unavailable', className: ''};
    const actual = rows.reduce((sum, row) => sum + cents(row[field.key]), 0n);
    const protein = cents(rows[0].macroTargets.protein.value);
    const days = BigInt(rows.length);
    let met;
    if (field.targetKey === 'protein') met = actual >= days * protein;
    else {
        const budget = rows.reduce((sum, row) => sum + BigInt(row.macroTargets.calorieBudget), 0n);
        const fatPercent = cents(fatPercentage);
        met = field.targetKey === 'fat' ? actual * 900n <= budget * fatPercent
            : actual * 400n <= budget * (10000n - fatPercent) - days * protein * 400n;
    }
    const minimum = field.targetKey === 'protein';
    return {label: met ? minimum ? 'At or above current minimum' : 'Within current ceiling'
        : minimum ? 'Below current minimum' : 'Above current ceiling', className: met ? 'target-met' : 'target-missed'};
}

export function meanMacroTargetLabel(rows, field, fatPercentage) {
    if (rows.some(row => row.macroTargets?.[field.targetKey]?.value == null)) return null;
    if (field.targetKey === 'protein') return roundedCentsLabel(cents(rows[0].macroTargets.protein.value), 1n);
    if (fatPercentage == null) return null;
    const days = BigInt(rows.length);
    const budget = rows.reduce((sum, row) => sum + BigInt(row.macroTargets.calorieBudget), 0n);
    const fatPercent = cents(fatPercentage);
    const protein = cents(rows[0].macroTargets.protein.value);
    const numerator = field.targetKey === 'fat' ? budget * fatPercent
        : budget * (10000n - fatPercent) - days * protein * 400n;
    return roundedCentsLabel(numerator, days * (field.targetKey === 'fat' ? 900n : 400n));
}

export function nutrientChartData(summaries, from, to, field, targets = null) {
    const rows = new Map(summaries.map(item => [dayjs(item.date).format('YYYY-MM-DD'), item]));
    const days = dayjs(to).diff(dayjs(from), 'day') + 1;
    const labels = Array.from({length: days}, (_, i) => dayjs(from).add(i, 'day'));
    const data = labels.map(day => {
        const row = rows.get(day.format('YYYY-MM-DD'));
        return isNutrientCovered(row, field) ? nutrientValue(row, field) : null;
    });
    const pointColors = labels.map(day => {
        const row = rows.get(day.format('YYYY-MM-DD'));
        const status = row?.nutrients && dailyNutrientStatus(row.nutrients, field, targets);
        return status?.className === 'target-met' ? '#2f855a' : status?.className === 'target-missed' ? '#c05621' : '#3273b9';
    });
    const pointStyle = labels.map(day => nutrientCoverage(rows.get(day.format('YYYY-MM-DD')), field)?.estimatedFoods ? 'rectRot' : 'circle');
    const datasets = [{label: `${field.label} (${field.unit})`, data, spanGaps: false, borderColor: '#3273b9', backgroundColor: '#3273b9', pointBackgroundColor: pointColors, pointBorderColor: pointColors, pointStyle, pointRadius: 4, tension: 0.25,
        pointLabels: labels.map(day => {
            const row = rows.get(day.format('YYYY-MM-DD'));
            return row && isNutrientCovered(row, field) ? `${nutrientValue(row, field)} ${field.unit}${nutrientCoverage(row, field)?.estimatedFoods ? ' (includes estimates)' : ''}${chartStatusLabel(row, field, targets)}` : null;
        })}];
    if (field.key === 'fiberGrams') {
        const partial = labels.map(day => {
            const row = rows.get(day.format('YYYY-MM-DD'));
            return row?.completed && nutrientValue(row, field) != null && !isNutrientCovered(row, field) ? nutrientValue(row, field) : null;
        });
        datasets.push({label: 'Partial recorded lower bound', data: partial, showLine: false, spanGaps: false,
            pointBackgroundColor: '#fff', pointBorderColor: pointColors, pointBorderWidth: 2, pointRadius: 5,
            pointLabels: labels.map((day, index) => {
                const row = rows.get(day.format('YYYY-MM-DD'));
                return partial[index] == null ? null : `≥${partial[index]} ${field.unit} (${nutrientCoverage(row, field).foodsWithValues}/${row.nutrients.totalFoods} foods${row.nutrients.mealsWithoutFoods ? `; ${row.nutrients.mealsWithoutFoods} meal(s) without foods` : ''}${nutrientCoverage(row, field).estimatedFoods ? '; includes estimates' : ''})`;
            })});
    }
    return {labels: labels.map(day => day.format('D MMM')), datasets};
}

function chartStatusLabel(row, field, targets) {
    const status = dailyNutrientStatus(row.nutrients, field, targets);
    return status ? ` · ${status.label}` : '';
}

export function nutrientChartRows(summaries, from, to, field, targets = null) {
    const rows = new Map(summaries.map(item => [dayjs(item.date).format('YYYY-MM-DD'), item]));
    const days = dayjs(to).diff(dayjs(from), 'day') + 1;
    return Array.from({length: days}, (_, i) => {
        const day = dayjs(from).add(i, 'day');
        const item = rows.get(day.format('YYYY-MM-DD'));
        if (!item) return {date: day.format('YYYY-MM-DD'), description: 'no daily total recorded'};
        if (!item.completed) return {date: day.format('YYYY-MM-DD'), description: 'day not marked complete'};
        if (field.key === 'fiberGrams' && nutrientValue(item, field) != null && !isNutrientCovered(item, field)) {
            const coverage = nutrientCoverage(item, field);
            return {date: day.format('YYYY-MM-DD'), description: `≥${nutrientValue(item, field)} ${field.unit} partial lower bound (${coverage.foodsWithValues}/${item.nutrients.totalFoods} foods${item.nutrients.mealsWithoutFoods ? `; ${item.nutrients.mealsWithoutFoods} meal(s) without foods` : ''}${coverage.estimatedFoods ? '; includes estimates' : ''})`};
        }
        if (!isNutrientCovered(item, field)) {
            const nutrients = item.nutrients;
            const coverage = nutrientCoverage(item, field);
            return {date: day.format('YYYY-MM-DD'), description: field.kind === 'macro' ? 'incomplete macros'
                : `incomplete nutrient coverage (${coverage?.foodsWithValues ?? 0}/${nutrients?.totalFoods ?? 0} foods; ${nutrients?.mealsWithoutFoods ?? 0} meals without foods)`};
        }
        return {date: day.format('YYYY-MM-DD'), description: `${nutrientValue(item, field)} ${field.unit}${nutrientCoverage(item, field)?.estimatedFoods ? ' (includes estimates)' : ''}${chartStatusLabel(item, field, targets)}`};
    });
}

export function nutrientChartDescription(summaries, from, to, field, targets = null) {
    return nutrientChartRows(summaries, from, to, field, targets).map(row => `${row.date}: ${row.description}`).join('; ');
}
