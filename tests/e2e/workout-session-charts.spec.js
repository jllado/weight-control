const {test, expect} = require('@playwright/test');
const {readFileSync} = require('node:fs');
const vm = require('node:vm');

test('workout charts preserve separate points for sessions sharing dates and times', () => {
    const source = readFileSync(require.resolve('../../src/model/CoachMetrics.js'), 'utf8').replace("import dayjs from 'dayjs';", '').replaceAll('export function', 'function');
    const charts = {dayjs: require('dayjs')};
    vm.runInNewContext(source, charts);
    const sessions = [
        {date: '2026-08-20', startTime: '08:00', sessionReference: 'morning', goalAlignmentScore: 8, estimatedTrainingDemandScore: 6, totals: {totalDurationSeconds: 600}},
        {date: '2026-08-20', startTime: '08:00', sessionReference: 'same-time', goalAlignmentScore: 7, estimatedTrainingDemandScore: 5, totals: {totalDurationSeconds: 300}},
        {date: '2026-08-20', startTime: null, sessionReference: 'untimed', goalAlignmentScore: 9, estimatedTrainingDemandScore: 4, totals: {totalDurationSeconds: 120}}
    ];
    const detail = charts.buildWorkoutDetailCharts(sessions).duration.data;
    expect(detail.labels).toHaveLength(3);
    expect(new Set(detail.labels).size).toBe(3);
    expect([...detail.datasets[0].data]).toEqual([10, 5, 2]);
    const assessment = charts.buildWorkoutAssessmentChart(sessions).data;
    expect(new Set(assessment.labels).size).toBe(3);
    expect([...assessment.datasets[0].data]).toEqual([8, 7, 9]);
    expect([...assessment.datasets[1].data]).toEqual([6, 5, 4]);
    const partlyRated = sessions.map((session, index) => ({...session, goalAlignmentScore: index === 1 ? 7 : null}));
    const partial = charts.buildWorkoutAssessmentChart(partlyRated).data;
    expect([...partial.labels]).toEqual([detail.labels[1]]);
    expect([...partial.datasets[0].data]).toEqual([7]);
});
