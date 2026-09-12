const {test, expect} = require('@playwright/test');
const {readFileSync} = require('node:fs');
const vm = require('node:vm');

test('workout charts show daily totals and one rating per assessed date', () => {
    const source = readFileSync(require.resolve('../../src/model/CoachMetrics.js'), 'utf8').replace("import dayjs from 'dayjs';", '').replaceAll('export function', 'function');
    const charts = {dayjs: require('dayjs')};
    vm.runInNewContext(source, charts);
    // The API combines all sessions on each date before returning chart data.
    const days = [
        {date: '2026-08-20', goalAlignmentScore: 8, estimatedTrainingDemandScore: 6, totals: {totalDurationSeconds: 1020}},
        {date: '2026-08-21', goalAlignmentScore: null, estimatedTrainingDemandScore: null, totals: {totalDurationSeconds: 300}},
        {date: '2026-08-22', goalAlignmentScore: 9, estimatedTrainingDemandScore: 4, totals: {totalDurationSeconds: 120}}
    ];
    const detail = charts.buildWorkoutDetailCharts(days).duration.data;
    expect([...detail.labels]).toEqual(['20/08/2026', '21/08/2026', '22/08/2026']);
    expect([...detail.datasets[0].data]).toEqual([17, 5, 2]);
    const assessment = charts.buildWorkoutAssessmentChart(days).data;
    expect([...assessment.labels]).toEqual(['20/08/2026', '22/08/2026']);
    expect([...assessment.datasets[0].data]).toEqual([8, 9]);
    expect([...assessment.datasets[1].data]).toEqual([6, 4]);
    const weekly = charts.buildWeeklyWorkoutCharts([{startDate: '2026-08-15', endDate: '2026-08-21', totals: {workoutCount: 2}}]).sessions;
    expect(weekly.options.plugins.title.text).toBe('Training days per week');
    expect([...weekly.data.datasets[0].data]).toEqual([2]);
    expect(charts.buildWorkoutAssessmentChart([]).data.labels).toHaveLength(0);
});
