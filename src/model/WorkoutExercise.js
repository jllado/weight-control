export const ExerciseTrackingMode = {
    REPS: 'REPS',
    SECONDS: 'SECONDS',
    CARDIO: 'CARDIO'
};

export default class WorkoutExercise {

    constructor(source) {
        if (source === undefined) {
            return;
        }
        this.id = source.id;
        this.name = source.name;
        this.description = source.description;
        this.trackingMode = source.trackingMode;
    }

    toObject() {
        return {
            id: this.id,
            name: this.name,
            description: this.description,
            trackingMode: this.trackingMode
        };
    }
}

export function trackingModeLabel(mode) {
    switch (mode) {
        case ExerciseTrackingMode.REPS:
            return 'Reps';
        case ExerciseTrackingMode.SECONDS:
            return 'Seconds';
        case ExerciseTrackingMode.CARDIO:
            return 'Cardio';
        default:
            return mode;
    }
}
