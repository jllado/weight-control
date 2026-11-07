import assert from 'node:assert/strict';
import {test} from 'node:test';
import Sleep from '../../src/model/Sleep.js';
import {sleepClockMinutes, sleepEditorValue, sleepInstantFromEditor, sleepTimestamp} from '../../src/model/SleepClock.js';

process.env.TZ = 'Europe/Madrid';

const travelNight = {
    id: 1, date: '2025-10-11',
    bedtimeStart: '2025-10-11T04:10:01Z', bedtimeEnd: '2025-10-11T14:08:01Z',
    bedtimeStartOffsetSeconds: -18000, bedtimeEndOffsetSeconds: -18000,
    totalSleepDuration: 28020, deepSleepDuration: 4980, remSleepDuration: 6450,
    lightSleepDuration: 16590, awakeTime: 7860, averageHeartRate: 74.01, averageHrv: 13
};

test('travel sleep displays its recorded local clocks without changing elapsed time', () => {
    const sleep = new Sleep(travelNight);
    assert.equal(sleep.bedtimeWindowFormat(), '10/10 23:10 - 11/10 09:08');
    assert.equal(sleep.timeInBedSeconds(), 35880);
    assert.equal(sleep.bedtimeStart.toISOString(), travelNight.bedtimeStart.replace('Z', '.000Z'));
});

test('cloning sleep retains the recorded offsets', () => {
    const sleep = new Sleep(new Sleep(travelNight).toObject());
    assert.equal(sleep.bedtimeWindowFormat(), '10/10 23:10 - 11/10 09:08');
});

test('separate endpoint offsets preserve elapsed time on a daylight-saving night', () => {
    const sleep = new Sleep({...travelNight, date: '2025-10-26',
        bedtimeStart: '2025-10-25T23:00:00+02:00', bedtimeEnd: '2025-10-26T07:00:00+01:00',
        bedtimeStartOffsetSeconds: 7200, bedtimeEndOffsetSeconds: 3600});
    assert.equal(sleep.bedtimeWindowFormat(), '25/10 23:00 - 26/10 07:00');
    assert.equal(sleep.timeInBedSeconds(), 9 * 3600);
});

for (const [start, end, startOffset, endOffset, hours] of [
    ['2025-10-10T23:10:00-05:00', '2025-10-11T09:10:00-05:00', -18000, -18000, 10],
    ['2025-10-25T23:00:00+02:00', '2025-10-26T07:00:00+01:00', 7200, 3600, 9],
    ['2025-03-29T23:00:00+01:00', '2025-03-30T07:00:00+02:00', 3600, 7200, 7],
    ['2025-10-10T23:00:00Z', '2025-10-11T07:00:00Z', 0, 0, 8]
]) {
    test(`editing retains local clocks and elapsed time for ${start}`, () => {
        const startEditor = sleepEditorValue(new Date(start), startOffset);
        const endEditor = sleepEditorValue(new Date(end), endOffset);
        assert.equal(startEditor.slice(11, 13), '23');
        const startInstant = sleepInstantFromEditor(startEditor, startOffset);
        const endInstant = sleepInstantFromEditor(endEditor, endOffset);
        assert.equal(startInstant.getTime(), new Date(start).getTime());
        assert.equal(endInstant.getTime(), new Date(end).getTime());
        assert.equal((endInstant - startInstant) / 3600000, hours);
        assert.equal(sleepTimestamp(startInstant, startOffset), start.replace('Z', '+00:00'));
        assert.equal(sleepClockMinutes(startInstant, startOffset), 23 * 60 + Number(startEditor.slice(14, 16)));
    });
}

test('clock averages do not depend on the viewing device time zone', () => {
    const original = process.env.TZ;
    try {
        for (const zone of ['Europe/Madrid', 'America/Cancun', 'UTC']) {
            process.env.TZ = zone;
            const sleep = new Sleep(travelNight);
            assert.equal(sleep.bedtimeWindowFormat(), '10/10 23:10 - 11/10 09:08');
            assert.equal(sleepClockMinutes(sleep.bedtimeStart, sleep.bedtimeStartOffsetSeconds), 1390);
            assert.equal(sleepClockMinutes(sleep.bedtimeEnd, sleep.bedtimeEndOffsetSeconds), 548);
        }
    } finally { process.env.TZ = original; }
});


test('editing a travel clock in the viewing device spring gap retains the instant', () => {
    const original = new Date('2025-03-30T02:30:00-05:00');
    const edited = sleepInstantFromEditor(sleepEditorValue(original, -18000), -18000);
    assert.equal(edited.getTime(), original.getTime());
});
