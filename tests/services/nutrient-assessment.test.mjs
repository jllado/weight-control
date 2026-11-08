import assert from 'node:assert/strict';
import {test} from 'node:test';
import {omega3TrendStatus, dailyNutrientStatus, nutrientTargetStatus, nutrientChartData, nutrientChartDescription} from '../../src/model/NutrientTrends.js';

const omega = {key: 'omega3Milligrams', coverageKey: 'omega3Coverage'};
const vitamin = {key: 'vitaminDMicrograms', coverageKey: 'vitaminDCoverage'};
const targets = {omega3: {value: 1}, epaDha: {value: 250}, ala: {value: 1600}, vitaminD: {value: 15, source: 'EFSA_AI'}};
const summary = (epa = 250, ala = 1600) => ({omega3Milligrams: 3000, epaDhaMilligrams: epa, alaMilligrams: ala,
    vitaminDMicrograms: 5, totalFoods: 2, mealsWithoutFoods: 0,
    omega3Coverage: {foodsWithValues: 2}, epaDhaCoverage: {foodsWithValues: 2}, alaCoverage: {foodsWithValues: 2}, vitaminDCoverage: {foodsWithValues: 2}});
const day = nutrients => ({completed: true, nutrients});

test('omega assessment requires both independent targets despite a large total or legacy target', () => {
    assert.equal(omega3TrendStatus([day(summary())], omega, targets).className, 'target-met');
    for (const value of [summary(249.99, 2000), summary(300, 1599.99)]) {
        assert.equal(omega3TrendStatus([day(value)], omega, targets).className, 'target-missed');
    }
    assert.equal(omega3TrendStatus([day(summary(null, 1600))], omega, targets).className, '');
    assert.equal(omega3TrendStatus([day(summary())], omega, {...targets, ala: {value: null}}).className, '');
    assert.equal(omega3TrendStatus([day(summary(200, null))], omega, targets).className, 'target-missed');
});

test('omega means use each subtype fully covered completed days independently', () => {
    const partial = summary(0, 1600);
    partial.epaDhaCoverage.foodsWithValues = 1;
    assert.equal(omega3TrendStatus([day(summary(250, 1600)), day(partial)], omega, targets).className, 'target-met');
    assert.equal(omega3TrendStatus([day(partial)], omega, targets).className, '');
    assert.equal(omega3TrendStatus([{...day(summary()), completed: false}], omega, targets).className, '');
});

test('partial daily omega can prove minima reached but not a shortfall', () => {
    assert.equal(dailyNutrientStatus(summary(200, 1600), omega, targets, false).className, '');
    assert.equal(dailyNutrientStatus(summary(), omega, targets, false).className, 'target-met');
    const partial = summary(200, 1600);
    partial.epaDhaCoverage.foodsWithValues = 1;
    assert.equal(dailyNutrientStatus(partial, omega, targets).className, '');
    partial.epaDhaMilligrams = 250;
    assert.equal(dailyNutrientStatus(partial, omega, targets).className, 'target-met');
    partial.alaMilligrams = 1500;
    assert.equal(dailyNutrientStatus(partial, omega, targets).className, 'target-missed');
    partial.mealsWithoutFoods = 1;
    assert.equal(dailyNutrientStatus(partial, omega, targets).className, '');
});

test('vitamin D default reference stays neutral while personal intake goals compare normally', () => {
    const recorded = summary();
    assert.equal(dailyNutrientStatus(recorded, vitamin, targets).className, '');
    assert.equal(dailyNutrientStatus(recorded, vitamin, targets).label, 'Reference only');
    assert.equal(nutrientTargetStatus([day(recorded)], vitamin, targets).className, '');
    const personal = {...targets, vitaminD: {value: 15, source: 'PERSONAL'}};
    assert.equal(dailyNutrientStatus(recorded, vitamin, personal).className, 'target-missed');
    recorded.vitaminDCoverage.foodsWithValues = 1;
    assert.equal(dailyNutrientStatus(recorded, vitamin, personal).className, '');
    recorded.vitaminDMicrograms = 15;
    assert.equal(dailyNutrientStatus(recorded, vitamin, personal).className, 'target-met');
});

test('chart points distinguish assessment from estimated provenance using the same daily rules', () => {
    const field = {...omega, unit: 'mg', label: 'Omega-3'};
    const rows = [{date: '2026-11-06', ...day(summary(200, 1600))}];
    rows[0].nutrients.omega3Coverage.estimatedFoods = 2;
    const chart = nutrientChartData(rows, '2026-11-06', '2026-11-06', field, targets);
    assert.equal(chart.datasets[0].pointBackgroundColor[0], '#c05621');
    assert.equal(chart.datasets[0].pointStyle[0], 'rectRot');
    assert.match(chart.datasets[0].pointLabels[0], /Below goal/);
    assert.match(nutrientChartDescription(rows, '2026-11-06', '2026-11-06', field, targets), /Below goal/);
    const vitaminChart = nutrientChartData(rows, '2026-11-06', '2026-11-06', {...vitamin, unit: 'µg'}, targets);
    assert.equal(vitaminChart.datasets[0].pointBackgroundColor[0], '#3273b9');
    assert.match(vitaminChart.datasets[0].pointLabels[0], /Reference only/);
});

test('daily omega assessment stays independent when the recorded total is unknown', () => {
    const withoutTotal = (epa, ala) => ({...summary(epa, ala), omega3Milligrams: null, omega3Coverage: {foodsWithValues: 0}});
    assert.equal(dailyNutrientStatus(withoutTotal(250, 1600), omega, targets).label, 'Goal met');
    assert.equal(dailyNutrientStatus(withoutTotal(249.99, 1600), omega, targets).label, 'Below goal');
    assert.equal(dailyNutrientStatus(withoutTotal(250, 1599.99), omega, targets).label, 'Below goal');
    assert.equal(dailyNutrientStatus(withoutTotal(250, null), omega, targets).label, 'Assessment unavailable');
    assert.equal(dailyNutrientStatus(withoutTotal(250, 1600), omega, {...targets, ala: {value: null}}).label, 'Assessment unavailable');
    assert.equal(dailyNutrientStatus(withoutTotal(250, 1600), omega, targets, false).label, 'Goal met');
    assert.equal(dailyNutrientStatus(withoutTotal(249.99, 1600), omega, targets, false).label, 'Assessment unavailable');
    const partial = withoutTotal(200, 1600);
    partial.epaDhaCoverage.foodsWithValues = 1;
    assert.equal(dailyNutrientStatus(partial, omega, targets).label, 'Assessment unavailable');
    partial.epaDhaMilligrams = 250;
    assert.equal(dailyNutrientStatus(partial, omega, targets).label, 'Goal met');
    assert.equal(partial.omega3Milligrams, null);
});
