import personalRecordService, {formatRecordValue} from './PersonalRecordService';

export async function loadWorkoutRecordContext(lines) {
    const exerciseIds = [...new Set(lines.filter(line => line.exerciseType === 'TRAINING').map(line => line.exerciseId))];
    const records = await Promise.all(exerciseIds.map(async exerciseId => [exerciseId, await personalRecordService.getCurrent({domain: 'WORKOUT', exerciseId})]));
    return Object.fromEntries(records);
}

export function recordsForLine(recordContext, line) {
    return (recordContext[line.exerciseId] || []).filter(record => record.subject.type !== 'EXERCISE_TOTAL');
}

export function metricRecords(recordContext, line, metric) {
    return recordsForLine(recordContext, line).filter(record => record.metric === metric || record.metric === `${metric}_MINIMUM`);
}

export function loadMetricRecords(recordContext, line) {
    return metricRecords(recordContext, line, 'WORKOUT_HEAVIEST_LOAD').filter(record => Number(record.value) > 0);
}

export function segmentMetricRecords(recordContext, line, segment, metric) {
    const load = Number(segment.weight || 0).toFixed(2);
    return metricRecords(recordContext, line, metric).filter(record => record.qualifier && Number(record.qualifier.loadKg).toFixed(2) === load);
}

export {formatRecordValue};
