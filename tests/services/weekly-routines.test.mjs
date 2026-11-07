import assert from 'node:assert/strict';
import {test} from 'node:test';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import dayjs from 'dayjs';

const source = readFileSync(new URL('../../src/model/Routine.js', import.meta.url), 'utf8')
    .replace(/^import .*;?\n/gm, '').replace(/^dayjs\.extend\(.*\)\n/gm, '')
    .replace('export default class Routine', 'class Routine').replaceAll('export const ', 'const ');
const context = {dayjs, Intl};
vm.runInNewContext(`${source}\nglobalThis.Routine = Routine;`, context);
const date = value => new Date(`${value}T12:00:00+01:00`);
const routine = (times, start = '2026-10-03', cadence = 'WEEKLY') => new context.Routine({
    id: 1, name: 'Check weight', cadence, start_date: date(start), times: times.map(date),
    current_strike: 9, best_strike: 9, last_time_date: times.length ? date(times.at(-1)) : null, types: ['WEIGHT']
});

test('weekly completion respects Saturday boundaries and actual evidence cutoff', () => {
    const value = routine(['2026-11-04']);
    assert.equal(value.isDone(date('2026-11-03')), false);
    assert.equal(value.isDone(date('2026-11-04')), true);
    assert.equal(value.isDone(date('2026-11-06')), true);
    assert.equal(value.isDone(date('2026-11-07')), false);
    value.times = [new Date('2026-10-25T23:30:00Z')];
    assert.equal(value.isDone(date('2026-10-25')), false);
    assert.equal(value.isDone(date('2026-10-26')), true);
});

test('weekly rolling statistics count ended Fridays in 31 days and deduplicate evidence', () => {
    const value = routine(['2026-10-03', '2026-10-04', '2026-10-17', '2026-10-31', '2026-11-07']);
    assert.equal(value.status(date('2026-11-07')), 60);
    assert.equal(value.fails(date('2026-11-07')), 2);
    assert.equal(routine([], '2026-11-07').status(date('2026-11-07')), null);
    assert.equal(routine([], '2026-11-07').fails(date('2026-11-07')), 0);
});

test('weekly pending opportunity preserves preceding streak and historical best ignores later evidence', () => {
    const value = routine(['2026-10-17', '2026-10-24', '2026-10-25', '2026-10-31']);
    assert.equal(value.strike(date('2026-11-07')), 3);
    assert.equal(value.strike(date('2026-11-14')), 0);
    assert.equal(value.bestStreak(date('2026-10-23')), 1);
    assert.equal(value.bestStreak(date('2026-11-07')), 3);
});

test('weekly monthly analytics assign completion to the week-ending Friday', () => {
    const value = routine(['2026-04-30'], '2026-04-25');
    assert.equal(value.month_percentage(date('2026-04-01'), date('2026-05-03')), undefined);
    assert.equal(value.month_percentage(date('2026-05-01'), date('2026-05-03')), 100);
});

test('omitted cadence retains existing daily completion and statistics', () => {
    const value = routine(['2026-11-05'], '2026-10-01', undefined);
    const source = value.toObject();
    delete source.cadence;
    const daily = new context.Routine(source);
    assert.equal(daily.cadence, 'DAILY');
    assert.equal(daily.isDone(date('2026-11-05')), true);
    assert.equal(daily.isDone(date('2026-11-06')), false);
    assert.equal(daily.status(date('2026-11-07')), 3.23);
});

test('weekly evidence never changes daily aggregate percentages', () => {
    const summarySource = readFileSync(new URL('../../src/services/MeasuresSummaryService.js', import.meta.url), 'utf8')
        .replace(/^import .*;?\n/gm, '').replace('export const ', 'const ').replace('export default ', 'const summary = ');
    const scope = {dayjs};
    vm.runInNewContext(`${summarySource}\nglobalThis.summary = summary;`, scope);
    const daily = {cadence: 'DAILY', month_percentage: () => 80};
    const weekly = {cadence: 'WEEKLY', month_percentage: () => 0};
    assert.equal(scope.summary.get_month_average_routines_percentage_for(dayjs('2026-10-01'), [daily, weekly]), 80);
    assert.equal(scope.summary.get_month_average_routines_percentage_for(dayjs('2026-10-01'), [weekly]), undefined);
});
