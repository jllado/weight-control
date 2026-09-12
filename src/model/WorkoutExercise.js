export const ExerciseTrackingMode = {
    REPS: 'REPS',
    SECONDS: 'SECONDS',
    CARDIO: 'CARDIO'
};

export const stretchingUnitOptions = [{label: 'Time', value: 'SECONDS'}, {label: 'Breaths', value: 'BREATHS'}];

export const ExerciseType = {
    WARM_UP: 'WARM_UP',
    TRAINING: 'TRAINING',
    STRETCHING: 'STRETCHING'
};

export default class WorkoutExercise {

    constructor(source) {
        if (source === undefined) {
            return;
        }
        this.id = source.id;
        this.imageUrl = source.imageUrl;
        this.hasCustomImage = source.hasCustomImage;
        this.name = source.name;
        this.description = source.description;
        this.trackingMode = source.trackingMode;
        this.exerciseType = source.exerciseType || ExerciseType.TRAINING;
    }

    toObject() {
        return {
            id: this.id,
            imageUrl: this.imageUrl,
            hasCustomImage: this.hasCustomImage,
            name: this.name,
            description: this.description,
            trackingMode: this.trackingMode,
            exerciseType: this.exerciseType
        };
    }
}

export function exerciseTypeLabel(type) {
    return {[ExerciseType.WARM_UP]: 'Warm-up', [ExerciseType.TRAINING]: 'Training', [ExerciseType.STRETCHING]: 'Stretching'}[type];
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
