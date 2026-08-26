export const ExerciseTrackingMode = {
    REPS: 'REPS',
    SECONDS: 'SECONDS',
    CARDIO: 'CARDIO'
};

export const ExerciseType = {
    WARM_UP: 'WARM_UP',
    TRAINING: 'TRAINING'
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
        this.exerciseType = source.exerciseType || ExerciseType.TRAINING;
        this.defaultWarmUp = source.defaultWarmUp;
        this.defaultRepetitions = source.defaultRepetitions;
    }

    toObject() {
        return {
            id: this.id,
            name: this.name,
            description: this.description,
            trackingMode: this.trackingMode,
            exerciseType: this.exerciseType,
            defaultWarmUp: this.defaultWarmUp,
            defaultRepetitions: this.defaultRepetitions
        };
    }
}

export function exerciseTypeLabel(type) {
    return type === ExerciseType.WARM_UP ? 'Warm-up' : 'Training';
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
