import assert from 'node:assert/strict';
import {test} from 'node:test';
import {normalizeDish, scaleRecipe, foodPayload, nutrientSummary, dailyNutrientFields, trendNutrientFields} from '../../src/model/Dish.js';
import {dailyNutrientStatus, nutrientTargetStatus, coveredNutrientDays, averageNutrientLabel, nutrientChartData, nutrientChartDescription} from '../../src/model/NutrientTrends.js';

const salt = {key: 'saltGrams', label: 'Salt', unit: 'g', coverageKey: 'saltCoverage'};
const targets = {salt: {value: 5, source: 'WHO', referenceValue: 5}};
const summary = (value, covered = 1, total = 1, missingMeals = 0) => ({saltGrams: value, totalFoods: total, mealsWithoutFoods: missingMeals,
    saltCoverage: {foodsWithValues: covered, estimatedFoods: 0}});
const day = (date, value, covered = 1, total = 1) => ({date, completed: true, nutrients: summary(value, covered, total)});

test('salt uses strict daily maxima, personal limits, and unknown partial evidence', () => {
    for (const value of [0, 4.99]) assert.equal(dailyNutrientStatus(summary(value), salt, targets)?.className, 'target-met');
    for (const value of [5, 5.01]) {
        assert.equal(dailyNutrientStatus(summary(value), salt, targets)?.className, 'target-missed');
        assert.equal(dailyNutrientStatus(summary(value, 1, 2), salt, targets)?.className, 'target-missed');
        assert.equal(dailyNutrientStatus(summary(value), salt, targets, false)?.className, 'target-missed');
    }
    for (const partial of [summary(4, 1, 2), summary(0, 1, 2), summary(4, 1, 1, 1)]) {
        assert.equal(dailyNutrientStatus(partial, salt, targets)?.className, '');
    }
    assert.equal(dailyNutrientStatus(summary(4), salt, targets, false)?.className, '');
    assert.equal(dailyNutrientStatus(summary(null, 0), salt, targets), null);
    assert.equal(dailyNutrientStatus(summary(3), salt, {salt: {value: 3, source: 'PERSONAL'}})?.className, 'target-missed');
    assert.equal(dailyNutrientStatus(summary(2.99), salt, {salt: {value: 3, source: 'PERSONAL'}})?.className, 'target-met');
});

test('salt means exclude partial, unfinished and missing days and compare unrounded strict maxima', () => {
    const rows = [day('2026-10-20', 0), day('2026-10-21', 4.99), day('2026-10-22', 8, 1, 2),
        {...day('2026-10-23', 1), completed: false}, day('2026-10-24', null, 0)];
    const eligible = coveredNutrientDays(rows, '2026-10-24', 30, salt);
    assert.equal(eligible.length, 2);
    assert.equal(averageNutrientLabel(eligible, salt), '2.5 g/day');
    assert.equal(nutrientTargetStatus(eligible, salt, targets)?.className, 'target-met');
    assert.equal(nutrientTargetStatus([day('2026-10-20', 4.99), day('2026-10-21', 5.01)], salt, targets)?.className, 'target-missed');
    assert.equal(nutrientTargetStatus([day('2026-10-20', 4.99), day('2026-10-21', 5)], salt, targets)?.className, 'target-met');
    const legacy = {date: '2026-10-24', completed: true, nutrients: {saltGrams: 1, totalFoods: 1, foodsWithValues: 1, mealsWithoutFoods: 0}};
    assert.equal(coveredNutrientDays([legacy], '2026-10-24', 30, salt).length, 0);
});

test('salt chart lower bounds retain coverage, estimates, strict statuses and zero', () => {
    const rows = [day('2026-10-21', 0), day('2026-10-22', 5), day('2026-10-23', 4, 1, 2), day('2026-10-24', 5, 1, 2)];
    rows[2].nutrients.saltCoverage.estimatedFoods = 1;
    const chart = nutrientChartData(rows, '2026-10-21', '2026-10-24', salt, targets);
    assert.deepEqual(chart.datasets[0].data, [0, 5, null, null]);
    assert.deepEqual(chart.datasets[1].data, [null, null, 4, 5]);
    assert.deepEqual(chart.datasets[1].pointStyle, ['circle', 'circle', 'rectRot', 'circle']);
    assert.deepEqual(chart.datasets[0].pointBackgroundColor, ['#2f855a', '#c05621', '#3273b9', '#c05621']);
    assert.match(nutrientChartDescription(rows, '2026-10-21', '2026-10-24', salt, targets), /≥4 g partial lower bound \(1\/2 foods; includes estimates\)/);
});

test('salt survives independent catalog, recipe and meal snapshots with source and reference scaling', () => {
    const food = normalizeDish({name: 'Label food', quantity: 100, unit: 'GRAM', calories: 100,
        proteinGrams: 10, carbohydrateGrams: 10, fatGrams: 2, saltGrams: 1.25, saltSource: 'Product label', saltEstimated: false});
    const [portion] = scaleRecipe({servings: 2, ingredients: [food]}, 1);
    assert.equal(portion.saltGrams, 0.63);
    assert.equal(portion.reference.saltGrams, 1.25);
    assert.equal(food.saltGrams, 1.25);
    assert.equal(foodPayload(portion).saltSource, 'Product label');
    assert.equal(foodPayload(portion).nutrientSchemaVersion, 3);
    const zero = normalizeDish({...food, saltGrams: 0});
    const unknown = normalizeDish({name: 'Unknown food', quantity: 1, calories: 0});
    assert.equal(unknown.saltGrams, null);
    assert.equal(foodPayload(unknown).saltSource, null);
    assert.equal(foodPayload(unknown).saltEstimated, null);
    const total = nutrientSummary([{dishes: [portion, zero, unknown]}]);
    assert.equal(total.saltGrams, 0.63);
    assert.deepEqual(total.saltCoverage, {foodsWithValues: 2, estimatedFoods: 0});
    assert.equal(nutrientSummary([{dishes: [unknown]}]).saltGrams, null);
    assert.equal(nutrientSummary([{dishes: [zero]}]).saltGrams, 0);
    assert.ok(dailyNutrientFields.some(field => field.key === salt.key));
    assert.ok(trendNutrientFields.some(field => field.key === salt.key));
});
