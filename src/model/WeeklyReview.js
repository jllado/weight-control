const neutral = '#63717b';
const format = (value, places = 1) => value == null ? 'Not recorded' : Number(value).toLocaleString('en-GB', {maximumFractionDigits: places, minimumFractionDigits: places});
const delta = (current, previous, unit, places) => {
    if (current == null || previous == null) return null;
    const difference = Number((current - previous).toFixed(places));
    return {text: `${difference > 0 ? '+' : ''}${format(difference, places)} ${unit}`.trim(), color: neutral};
};
export function weeklyClassification(value, type, rules, goals = null, sleepScore = null) {
    if (value == null || !rules) return null;
    if (type === 'bodyFat') {
        const stages = rules.bodyFatStages;
        if (!stages?.length) return goals?.inShapeBodyFatLimit == null ? null : value <= goals.inShapeBodyFatLimit ? {label: 'Goal met', color: '#287d4b'} : {label: 'Above goal', color: '#b33b35'};
        return stages.find(stage => value > stage.threshold) || {label: 'Not classified', color: neutral};
    }
    if (type === 'sleep') return (sleepScore == null ? null : rules.sleepStages?.find(stage => sleepScore >= stage.threshold)) || (rules.sleepMinimumSeconds == null ? null : value >= rules.sleepMinimumSeconds ? {label: 'Goal met', color: '#287d4b'} : {label: 'Below goal', color: '#b33b35'});
    if (type === 'pressure') return rules.bloodPressureStages?.find(stage => stage.systolicAbove == null && stage.diastolicAbove == null || stage.systolicAbove != null && value.systolic > stage.systolicAbove || stage.diastolicAbove != null && value.diastolic > stage.diastolicAbove) || null;
    if (type === 'mood') return value >= rules.moodGoodMinimum ? {label: 'Good', color: '#287d4b'} : value >= rules.moodNeutralMinimum ? {label: 'Neutral', color: neutral} : {label: 'Low', color: '#b33b35'};
    return null;
}
export function weeklyMeasurementGroups(snapshot) {
    const periods = [snapshot.progress.currentPeriod, snapshot.progress.previousComparablePeriod, snapshot.progress.yearAgoComparablePeriod];
    const rules = snapshot.emailGoals?.rules;
    const row = (label, read, unit, places = 1, type = null, coverage = null) => {
        const values = periods.map(read);
        return {label, cells: periods.map((period, index) => ({value: format(values[index], places), unit: values[index] == null ? '' : unit,
            status: weeklyClassification(values[index], type, rules, snapshot.emailGoals), detail: coverage && values[index] != null ? coverage(period) : null,
            delta: index ? delta(values[0], values[index], unit, places) : null}))};
    };
    const body = [
        row('Average weight', p => p.weight?.weightKg, 'kg', 1, null, p => `${p.weight.measurementCount} readings`),
        row('Body fat', p => p.weight?.fatPercentage, '%', 2, 'bodyFat'),
        row('Fat mass', p => p.weight?.fatKg, 'kg'), row('Muscle mass', p => p.weight?.muscleKg, 'kg'), row('Muscle percentage', p => p.weight?.musclePercentage, '%'),
        {label: 'Average blood pressure', cells: periods.map(p => ({value: p.bloodPressure ? `${Math.round(p.bloodPressure.systolic)} / ${Math.round(p.bloodPressure.diastolic)}` : 'Not recorded', unit: p.bloodPressure ? 'mmHg' : '', status: weeklyClassification(p.bloodPressure, 'pressure', rules), detail: p.bloodPressure ? `${p.bloodPressure.measurementCount} readings` : null}))}
    ];
    const sleep = [row('Total sleep', p => p.sleep?.totalSleepSeconds == null ? null : p.sleep.totalSleepSeconds / 3600, 'h', 1, null, p => `${p.sleep.nightCount} nights`)];
    sleep[0].cells.forEach((cell, index) => { cell.status = weeklyClassification(periods[index].sleep?.totalSleepSeconds, 'sleep', rules, snapshot.emailGoals, periods[index].sleep?.statusScore); });
    for (const [label, key] of [['Deep sleep', 'deepSleepSeconds'], ['REM sleep', 'remSleepSeconds'], ['Light sleep', 'lightSleepSeconds'], ['Awake', 'awakeSeconds']]) sleep.push(row(label, p => p.sleep?.[key] == null ? null : p.sleep[key] / 3600, 'h'));
    sleep.push(row('Average heart rate', p => p.sleep?.averageHeartRate, 'bpm', 0), row('Average HRV', p => p.sleep?.averageHrv, 'ms', 0), row('Mood', p => p.moodAverage, '/ 5', 1, 'mood', p => `${p.moodDayCount} days`));
    const training = [row('Distance', p => p.workouts.distanceReadingCount ? p.workouts.totalDistanceKm : null, 'km'), row('Workout energy', p => p.workouts.calorieReadingCount ? p.workouts.totalCalories : null, 'kcal', 0), row('Strength volume', p => p.workouts.strengthSetCount ? p.workouts.strengthVolumeKg : null, 'kg')];
    const nutrition = [['Protein', 'averageProteinGrams', 'proteinDayCount'], ['Carbohydrates', 'averageCarbohydrateGrams', 'carbohydrateDayCount'], ['Fat', 'averageFatGrams', 'fatDayCount']].map(([label, key, count]) => row(label, p => p.calories[key], 'g/day', 1, null, p => `${p.calories[count]} days`));
    const calories = row('Calories', p => p.calories.averageCalories, 'kcal/day', 0, null, p => `${p.calories.entryCount} days`);
    return {body, sleep, training, nutrition, calories};
}
export function visualTableRows(rows) {
    return rows.map(row => ({label: row.label, key: row.key, cells: ['current', 'previous', 'yearAgo'].map((key, index) => {
        const text = row[key];
        const match = text.match(/^([≈≥]?\s*[\d,.]+)\s+(g|mg|µg|kcal|kg|min)(.*)$/);
        const missing = ['Not available', 'Not recorded', 'Unavailable'].includes(text);
        const status = row[index === 0 ? 'status' : `${key}Status`];
        const target = row[index === 0 ? 'target' : `${key}Target`];
        const change = index ? row[`${key}Delta`] : null;
        return {value: match ? match[1] : text, unit: match ? match[2] : row.label.endsWith('sessions') && /^\d+$/.test(text) ? Number(text) === 1 ? 'session' : 'sessions' : '', detail: match?.[3].replace(/^ · /, ''),
            status: missing && ['Not available', 'Unavailable', 'Target unavailable', 'Goal unavailable', 'Rule unavailable'].includes(status?.label) ? null : status,
            target: row.key === 'omega3' || ['Not available', 'Unavailable', 'Target unavailable', 'No saved target'].includes(target) ? null : target,
            delta: missing || change?.text === 'Not available' ? null : change};
    })}));
}
