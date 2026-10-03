import dayjs from 'dayjs';

export function saunaSummary(rounds) {
    return `${rounds.length} sauna round${rounds.length === 1 ? '' : 's'} · ${rounds.reduce((sum, minutes) => sum + minutes, 0)} min sauna`;
}

export default class Workout {

    constructor(source) {
        if (source === undefined) {
            return;
        }
        this.id = source.id;
        this.sessionReference = source.sessionReference;
        this.workoutDate = new Date(source.workoutDate);
        this.workoutDateFormat = source.workoutDateFormat || dayjs(this.workoutDate).format('DD/MM/YYYY');
        this.note = source.note;
        this.plannedSessionName = source.plannedSessionName ?? null;
        this.plannedTargets = source.plannedTargets ?? null;
        this.saunaSession = source.saunaSession ?? false;
        this.saunaRoundsMinutes = source.saunaRoundsMinutes ?? [];
        this.plannedSaunaRoundsMinutes = source.plannedSaunaRoundsMinutes ?? null;
        this.startTime = source.startTime ?? null;
        this.endTime = source.endTime ?? null;
        this.durationMinutes = source.durationMinutes ?? null;
        this.warmUpMinutes = source.warmUpMinutes ?? null;
        this.trainingMinutes = source.trainingMinutes ?? null;
        this.stretchingMinutes = source.stretchingMinutes ?? null;
        this.cardioMinutes = source.cardioMinutes ?? null;
        this.lines = (source.lines || []).map(line => ({
            exerciseId: line.exerciseId,
            exerciseName: line.exerciseName,
            exerciseDescription: line.exerciseDescription,
            trackingMode: line.trackingMode,
            cardioMetric: line.cardioMetric,
            stretchingUnit: line.stretchingUnit ?? 'SECONDS',
            exerciseType: line.exerciseType || 'TRAINING',
            supersetGroupId: line.supersetGroupId ?? null,
            position: line.position,
            calories: line.calories,
            averageHeartRate: line.averageHeartRate,
            sets: line.sets || [],
            intervals: line.intervals || []
        }));
    }

    summary() {
        const lines = [...this.lines].sort((left, right) => left.position - right.position);
        return this.plannedSessionName || lines.find(line => line.exerciseType === 'TRAINING')?.exerciseName || lines[0]?.exerciseName || (this.saunaSession ? 'Sauna' : 'Workout');
    }

    toObject() {
        return {
            id: this.id,
            workoutDate: this.workoutDate,
            note: this.note,
            plannedSessionName: this.plannedSessionName,
            plannedTargets: this.plannedTargets,
            saunaSession: this.saunaSession,
            saunaRoundsMinutes: this.saunaRoundsMinutes,
            plannedSaunaRoundsMinutes: this.plannedSaunaRoundsMinutes,
            startTime: this.startTime,
            endTime: this.endTime,
            durationMinutes: this.durationMinutes,
            warmUpMinutes: this.warmUpMinutes,
            trainingMinutes: this.trainingMinutes,
            stretchingMinutes: this.stretchingMinutes,
            cardioMinutes: this.cardioMinutes,
            lines: this.lines
        };
    }
}
