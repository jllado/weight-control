import assert from 'node:assert/strict';
import {test} from 'node:test';
import dayjs from 'dayjs';
import {macroTargetStatus, meanMacroTargetLabel, averageNutrientLabel, nutrientChangeLabel, nutrientMinimumStatus, omega3TrendStatus, dailyNutrientStatus,
    coveredNutrientDays, partialNutrientDays, nutrientChartData, nutrientChartDescription} from '../../src/model/NutrientTrends.js';
import {dailyNutrientFields, trendNutrientFields} from '../../src/model/Dish.js';

const protein = {key: 'proteinGrams', targetKey: 'protein'};
const carbohydrate = {key: 'carbohydrateGrams', targetKey: 'carbohydrate'};
const fat = {key: 'fatGrams', targetKey: 'fat'};

function row(budget, proteinIntake, carbohydrateIntake, fatIntake, carbohydrateTarget, fatTarget) {
    return {
        proteinGrams: proteinIntake, carbohydrateGrams: carbohydrateIntake, fatGrams: fatIntake,
        macroTargets: {calorieBudget: budget, protein: {value: 115}, carbohydrate: {value: carbohydrateTarget}, fat: {value: fatTarget}}
    };
}

test('macro ceilings compare unrounded dated allocations over exactly eligible days', () => {
    const rows = [row(2000, 115, 235, 66.67, 235, 66.67), row(2001, 115, 235.18, 66.7, 235.18, 66.70)];
    assert.equal(macroTargetStatus([rows[0]], fat, 30).label, 'Above current ceiling');
    assert.equal(macroTargetStatus([rows[1]], carbohydrate, 30).label, 'Above current ceiling');
    assert.equal(macroTargetStatus(rows, protein, 30).label, 'At or above current minimum');
    assert.equal(macroTargetStatus(rows, fat, 30).label, 'Above current ceiling');
    assert.equal(meanMacroTargetLabel(rows, carbohydrate, 30), '235.09');
    assert.equal(meanMacroTargetLabel(rows, fat, 30), '66.68');
    assert.equal(meanMacroTargetLabel([row(2589, 115, 338.08, 86.3, 338.08, 86.3)], carbohydrate, 30), '338.08');
});

test('a missing effective budget makes the corresponding ceiling neutral', () => {
    const rows = [row(2000, 115, 235, 66.67, 235, 66.67), row(0, 115, 0, 0, null, null)];
    assert.equal(macroTargetStatus(rows, carbohydrate, 30).label, 'Current target unavailable');
    assert.equal(macroTargetStatus(rows, fat, 30).className, '');
});

const vitaminD = {key: 'vitaminDMicrograms', unit: 'µg'};

test('nutrient means, changes and minimum status preserve cents until final rounding', () => {
    const rows = [{nutrients: {vitaminDMicrograms: 0.01}}, {nutrients: {vitaminDMicrograms: 0.02}}];
    assert.equal(averageNutrientLabel(rows, vitaminD), '0.02 µg/day');
    assert.equal(nutrientChangeLabel(rows, [rows[0]], vitaminD), '+0.01');
    assert.equal(nutrientChangeLabel(rows, [], vitaminD), 'Prior 30d: no data');
    const equality = [{nutrients: {vitaminDMicrograms: 0.1}}, {nutrients: {vitaminDMicrograms: 0.7}}];
    assert.equal(nutrientMinimumStatus(equality, vitaminD, 0.4).label, 'At or above current minimum');
});

const omega3 = {key: 'omega3Milligrams', unit: 'mg', coverageKey: 'omega3Coverage'};
const fiber = {key: 'fiberGrams', unit: 'g', coverageKey: 'fiberCoverage'};
const nutrientDay = (date, key, value, covered, totalFoods = 1, estimatedFoods = 0) => ({date, completed: true,
    nutrients: {[key]: value, totalFoods, mealsWithoutFoods: 0,
        [key === 'fiberGrams' ? 'fiberCoverage' : 'omega3Coverage']: {foodsWithValues: covered, estimatedFoods}}});

test('total omega-3 colors direct targets and only one-sided combined subtype shortfalls', () => {
    const rows = [nutrientDay('2026-10-23', 'omega3Milligrams', 1849.99, 1),
        nutrientDay('2026-10-24', 'omega3Milligrams', 1850.01, 1)];
    const componentTargets = {omega3: {value: null}, epaDha: {value: 250}, ala: {value: 1600}};
    assert.equal(omega3TrendStatus([rows[0]], omega3, componentTargets).className, 'target-missed');
    assert.match(omega3TrendStatus([rows[0]], omega3, componentTargets).label, /does not establish either subtype goal/i);
    assert.equal(omega3TrendStatus(rows, omega3, componentTargets).className, '');
    assert.match(omega3TrendStatus(rows, omega3, componentTargets).label, /does not establish either subtype goal/i);
    assert.equal(omega3TrendStatus([rows[0]], omega3, {...componentTargets, ala: {value: null}}).className, '');
    assert.equal(omega3TrendStatus([rows[0]], omega3, {...componentTargets, omega3: {value: 1800}}).className, 'target-met');
    assert.match(omega3TrendStatus([rows[0]], omega3, {...componentTargets, omega3: {value: 1900}}).label, /personal total omega-3 minimum/i);
});

test('daily and trend field choices use stored total omega-3 without changing food fields', () => {
    assert.deepEqual(dailyNutrientFields.map(field => field.label), ['Vitamin D', 'Magnesium', 'Omega-3', 'Fiber']);
    assert.deepEqual(trendNutrientFields.map(field => field.label), ['Vitamin D', 'Omega-3', 'Magnesium', 'Fiber']);
});

test('legacy total omega-3 uses 26 and 25 fully covered days and excludes a partial selected day', () => {
    const current = Array.from({length: 26}, (_, i) => nutrientDay(dayjs('2026-09-25').add(i, 'day').format('YYYY-MM-DD'), 'omega3Milligrams', 1320.3, 1));
    const prior = Array.from({length: 25}, (_, i) => nutrientDay(dayjs('2026-08-26').add(i, 'day').format('YYYY-MM-DD'), 'omega3Milligrams', 1940.28, 1));
    current[0].nutrients.omega3Milligrams = 0;
    current[1].nutrients.omega3Milligrams = 2640.6;
    const partial = nutrientDay('2026-10-24', 'omega3Milligrams', 499, 10, 17);
    const rows = [...prior, ...current, partial];
    const currentCovered = coveredNutrientDays(rows, '2026-10-24', 30, omega3);
    const priorCovered = coveredNutrientDays(rows, '2026-09-24', 30, omega3);
    assert.equal(currentCovered.length, 26);
    assert.equal(priorCovered.length, 25);
    assert.equal(averageNutrientLabel(currentCovered, omega3), '1320.3 mg/day');
    assert.equal(nutrientChangeLabel(currentCovered, priorCovered, omega3), '−619.98');
    assert.equal(currentCovered.includes(partial), false);
    assert.equal(currentCovered.some(day => day.nutrients.omega3Milligrams === 0), true);
});

test('partial fiber days retain recorded lower bounds and never become a comparable mean', () => {
    const rows = [nutrientDay('2026-10-22', 'fiberGrams', 0, 1, 2),
        nutrientDay('2026-10-24', 'fiberGrams', 6.2, 2, 17, 2),
        nutrientDay('2026-09-24', 'fiberGrams', 9, 1, 2)];
    const current = partialNutrientDays(rows, '2026-10-24', 30, fiber);
    assert.equal(current.length, 2);
    assert.equal(averageNutrientLabel(current, fiber), '3.1 g/day');
    assert.equal(coveredNutrientDays(rows, '2026-10-24', 30, fiber).length, 0);
    assert.equal(partialNutrientDays(rows, '2026-09-24', 30, fiber).length, 1);
    assert.equal(partialNutrientDays([nutrientDay('2026-10-24', 'fiberGrams', null, 0, 2)], '2026-10-24', 30, fiber).length, 0);
    const full = nutrientDay('2026-10-23', 'fiberGrams', 8, 2, 2);
    assert.equal(averageNutrientLabel(coveredNutrientDays([...rows, full], '2026-10-24', 30, fiber), fiber), '8 g/day');
});

test('fiber charts keep partial points separate from full lines and describe coverage', () => {
    const rows = [nutrientDay('2026-10-21', 'fiberGrams', 8, 2, 2),
        nutrientDay('2026-10-23', 'fiberGrams', 0, 1, 1),
        nutrientDay('2026-10-24', 'fiberGrams', 6.2, 2, 17, 2)];
    const chart = nutrientChartData(rows, '2026-10-21', '2026-10-24', fiber);
    assert.deepEqual(chart.datasets[0].data, [8, null, 0, null]);
    assert.equal(chart.datasets[0].spanGaps, false);
    assert.deepEqual(chart.datasets[1].data, [null, null, null, 6.2]);
    assert.equal(chart.datasets[1].showLine, false);
    assert.match(chart.datasets[1].pointLabels[3], /≥6\.2 g.*2\/17 foods.*includes estimates/);
    assert.match(nutrientChartDescription(rows, '2026-10-21', '2026-10-24', fiber), /2026-10-24: ≥6\.2 g.*2\/17 foods.*includes estimates/);
    assert.equal(nutrientChartData(rows, '2026-10-21', '2026-10-24', omega3).datasets.length, 1);
});

test('partial fiber chart evidence names meals without foods even at full listed-food coverage', () => {
    const row = nutrientDay('2026-10-24', 'fiberGrams', 6.2, 2, 2);
    row.nutrients.mealsWithoutFoods = 1;
    assert.equal(partialNutrientDays([row], '2026-10-24', 30, fiber).length, 1);
    const chart = nutrientChartData([row], '2026-10-24', '2026-10-24', fiber);
    assert.match(chart.datasets[1].pointLabels[0], /2\/2 foods; 1 meal\(s\) without foods/);
    assert.match(nutrientChartDescription([row], '2026-10-24', '2026-10-24', fiber), /2\/2 foods; 1 meal\(s\) without foods/);
});


test('daily colors compare recorded totals without treating missing foods as zero', () => {
    const targets = {fiber: {value: 25}, omega3: {value: null}, epaDha: {value: 250}, ala: {value: 1600}};
    const total = (value, covered = 1, mealsWithoutFoods = 0) => ({fiberGrams: value, totalFoods: 1,
        mealsWithoutFoods, fiberCoverage: {foodsWithValues: covered}});
    assert.equal(dailyNutrientStatus(total(24.99), fiber, targets).className, 'target-missed');
    for (const value of [25, 25.01]) assert.equal(dailyNutrientStatus(total(value), fiber, targets).className, 'target-met');
    for (const summary of [total(24.5, 0), total(24.5, 1, 1)]) {
        const status = dailyNutrientStatus(summary, fiber, targets);
        assert.equal(status.className, 'target-missed');
        assert.match(status.label, /minimum is not yet established/);
    }
    assert.equal(dailyNutrientStatus(total(25, 0), fiber, targets).className, 'target-met');
    assert.equal(dailyNutrientStatus(total(null, 0), fiber, targets), null);
    assert.equal(dailyNutrientStatus(total(0), fiber, targets).className, 'target-missed');
    assert.equal(dailyNutrientStatus(total(25), fiber, null), null);
    assert.equal(dailyNutrientStatus(total(25), fiber, {}), null);
    const omega = nutrientDay('2026-10-25', 'omega3Milligrams', 571.13, 1, 2).nutrients;
    assert.match(dailyNutrientStatus(omega, omega3, targets).label, /minimum is not yet established/);
    omega.omega3Milligrams = 1850;
    assert.equal(dailyNutrientStatus(omega, omega3, targets).className, '');
    assert.equal(dailyNutrientStatus(omega, omega3, {...targets, omega3: {value: 1850}}).className, 'target-met');
    assert.equal(dailyNutrientStatus(omega, omega3, {...targets, ala: {value: null}}).className, '');
});
