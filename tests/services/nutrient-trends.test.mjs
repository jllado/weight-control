import assert from 'node:assert/strict';
import {test} from 'node:test';
import {macroTargetStatus, meanMacroTargetLabel, averageNutrientLabel, nutrientChangeLabel, nutrientMinimumStatus} from '../../src/model/NutrientTrends.js';

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
