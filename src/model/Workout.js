import dayjs from 'dayjs';

export default class Workout {

    constructor(source) {
        if (source === undefined) {
            return;
        }
        this.id = source.id;
        this.workoutDate = new Date(source.workoutDate);
        this.workoutDateFormat = source.workoutDateFormat || dayjs(this.workoutDate).format('DD/MM/YYYY');
        this.note = source.note;
        this.assessment = source.assessment || null;
        this.lines = (source.lines || []).map(line => ({
            exerciseId: line.exerciseId,
            exerciseName: line.exerciseName,
            exerciseDescription: line.exerciseDescription,
            trackingMode: line.trackingMode,
            exerciseType: line.exerciseType,
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
            lines: this.lines
        };
    }
}
