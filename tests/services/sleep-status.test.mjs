import assert from 'node:assert/strict';
import {test} from 'node:test';
import Sleep, {getSleepStatus} from '../../src/model/Sleep.js';

function night(start, end, total = 7 * 3600, startOffset = 0, endOffset = startOffset) {
    return new Sleep({date: end.slice(0, 10), bedtimeStart: start, bedtimeEnd: end,
        bedtimeStartOffsetSeconds: startOffset, bedtimeEndOffsetSeconds: endOffset, totalSleepDuration: total});
}

test('rating explains the failed efficiency criterion when both duration goals are met', () => {
    const status = getSleepStatus([night('2026-11-01T23:00Z', '2026-11-02T07:00Z', 6.5 * 3600)]);
    assert.equal(status.score, 3);
    assert.equal(status.name, 'GOOD');
    assert.deepEqual(status.criteria.map(({key, met}) => [key, met]), [
        ['timeInBed', true], ['totalSleep', true], ['efficiency', false], ['midpointDeviation', true]
    ]);
    assert.equal(status.criteria[2].value, 81.25);
});

test('recorded local sleep consistency survives travel and viewing timezone changes', () => {
    const sleeps = [night('2026-10-10T23:00:00-05:00', '2026-10-11T07:00:00-05:00', 7 * 3600, -18000),
        night('2026-10-11T23:00:00+02:00', '2026-10-12T07:00:00+02:00', 7 * 3600, 7200)];
    const original = process.env.TZ;
    try {
        for (const zone of ['UTC', 'Europe/Madrid', 'America/Cancun']) {
            process.env.TZ = zone;
            assert.equal(getSleepStatus(sleeps).score, 4, zone);
            assert.equal(getSleepStatus(sleeps).criteria[3].value, 0, zone);
        }
    } finally { process.env.TZ = original; }
});

test('both recorded endpoint clocks define midpoint while DST durations remain elapsed time', () => {
    const sleeps = [
        night('2026-03-28T23:00:00+01:00', '2026-03-29T07:00:00+02:00', 6.5 * 3600, 3600, 7200),
        night('2026-10-24T23:00:00+02:00', '2026-10-25T07:00:00+01:00', 8 * 3600, 7200, 3600)
    ];
    assert.deepEqual(sleeps.map(s => s.timeInBedSeconds()), [25200, 32400]);
    assert.equal(getSleepStatus(sleeps).score, 4);
    assert.equal(getSleepStatus(sleeps).criteria[3].value, 0);
});

test('legacy endpoint offsets use dated Madrid clocks independently of device timezone', () => {
    const sleeps = [night('2026-03-27T22:00Z', '2026-03-28T06:00Z', 7 * 3600, undefined),
        night('2026-03-29T21:00Z', '2026-03-30T05:00Z', 7 * 3600, undefined)];
    sleeps.forEach(s => { s.bedtimeStartOffsetSeconds = undefined; s.bedtimeEndOffsetSeconds = undefined; });
    const original = process.env.TZ;
    try {
        process.env.TZ = 'UTC';
        assert.equal(getSleepStatus(sleeps).criteria[3].value, 0);
    } finally { process.env.TZ = original; }
});

test('duration and efficiency boundaries are inclusive, consistency is strictly below sixty minutes', () => {
    for (const [bed, total, score] of [[25199, 21600, 3], [25200, 21600, 4], [25200, 21599, 3],
        [28800, 24480, 4], [28800, 24479, 3]]) {
        const end = new Date('2026-11-02T07:00Z');
        const sleep = night(new Date(end.getTime() - bed * 1000).toISOString(), end.toISOString(), total);
        assert.equal(getSleepStatus([sleep]).score, score, `${bed}/${total}`);
    }
    const sleeps = [night('2026-11-01T22:00Z', '2026-11-02T06:00Z'), night('2026-11-02T00:00Z', '2026-11-02T08:00Z')];
    assert.equal(getSleepStatus(sleeps).score, 3);
    assert.equal(getSleepStatus(sleeps).criteria[3].value, 60);
    assert.equal(getSleepStatus(sleeps).criteria[3].met, false);
});
