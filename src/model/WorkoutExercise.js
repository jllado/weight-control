export const ExerciseTrackingMode = {
    REPS: 'REPS',
    SECONDS: 'SECONDS',
    CARDIO: 'CARDIO'
};

export const primaryMuscleGroupOptions = ['Chest', 'Back', 'Shoulders', 'Biceps', 'Triceps', 'Forearms', 'Core', 'Glutes', 'Quadriceps', 'Hamstrings', 'Calves'].map(label => ({label, value: label.toUpperCase()}));
export function primaryMuscleGroupLabel(group) { return primaryMuscleGroupOptions.find(option => option.value === group).label; }
export function muscleGroupAssignmentsLabel(assignments) { return assignments.map(({muscleGroup, role}) => `${primaryMuscleGroupLabel(muscleGroup)} · ${role === 'PRIMARY' ? 'Primary' : 'Secondary'}`).join(', '); }

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
        this.cardioMetric = source.cardioMetric;
        this.primaryMuscleGroup = source.primaryMuscleGroup;
        this.muscleGroups = source.muscleGroups || (source.primaryMuscleGroup ? [{muscleGroup: source.primaryMuscleGroup, role: 'PRIMARY'}] : []);
        this.primaryMuscleGroups = source.primaryMuscleGroups || [];
        this.secondaryMuscleGroups = source.secondaryMuscleGroups || [];
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
            cardioMetric: this.cardioMetric,
            primaryMuscleGroup: this.primaryMuscleGroup,
            muscleGroups: this.muscleGroups,
            primaryMuscleGroups: this.primaryMuscleGroups,
            secondaryMuscleGroups: this.secondaryMuscleGroups,
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
