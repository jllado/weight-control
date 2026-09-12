import dayjs from 'dayjs';

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
        this.startTime = source.startTime ?? null;
        this.durationMinutes = source.durationMinutes ?? null;
        this.warmUpMinutes = source.warmUpMinutes ?? null;
        this.trainingMinutes = source.trainingMinutes ?? null;
        this.stretchingMinutes = source.stretchingMinutes ?? null;
        this.assessment = source.assessment || null;
        this.lines = (source.lines || []).map(line => ({
            exerciseId: line.exerciseId,
            exerciseName: line.exerciseName,
            exerciseDescription: line.exerciseDescription,
            trackingMode: line.trackingMode,
            exerciseType: line.exerciseType || 'TRAINING',
            position: line.position,
            calories: line.calories,
            averageHeartRate: line.averageHeartRate,
            sets: line.sets || [],
            intervals: line.intervals || []
        }));
    }

    summary() {
        return this.lines.map(line => line.exerciseName).join(', ');
    }

    toObject() {
        return {
            id: this.id,
            workoutDate: this.workoutDate,
            note: this.note,
            startTime: this.startTime,
            durationMinutes: this.durationMinutes,
            warmUpMinutes: this.warmUpMinutes,
            trainingMinutes: this.trainingMinutes,
            stretchingMinutes: this.stretchingMinutes,
            lines: this.lines
        };
    }
}
