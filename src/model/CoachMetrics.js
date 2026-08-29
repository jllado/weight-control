import dayjs from 'dayjs';

function chart(title, labels, datasets, options = {}) {
    return {
        data: {labels, datasets},
        options: {
            plugins: {title: {display: true, text: title}},
            ...options
        }
    };
}

export function buildPlanProgressChart(reflections) {
    return chart('Plan progress /10', reflections.map(reflection => dayjs(reflection.date).format('DD/MM/YYYY')), [{
        label: 'Plan progress', borderColor: '#1a36c1', fill: false, data: reflections.map(reflection => reflection.planProgressScore)
    }], {scales: {y: {min: 1, max: 10, ticks: {stepSize: 1}}}});
}

export function buildWorkoutAssessmentChart(workouts) {
    const assessed = workouts.filter(workout => workout.goalAlignmentScore !== null);
    return chart('Workout assessments /10', assessed.map(workout => dayjs(workout.date).format('DD/MM/YYYY')), [
        {label: 'Goal alignment', borderColor: '#0a9396', fill: false, data: assessed.map(workout => workout.goalAlignmentScore)},
        {label: 'Training demand', borderColor: '#ee9b00', fill: false, data: assessed.map(workout => workout.estimatedTrainingDemandScore)}
    ], {scales: {y: {min: 1, max: 10, ticks: {stepSize: 1}}}});
}

export function buildWeeklyWorkoutCharts(weeks) {
    const labels = weeks.map(week => `${dayjs(week.startDate).format('DD/MM')}–${dayjs(week.endDate).format('DD/MM')}`);
    return {
        sessions: chart('Workout sessions per week', labels, [{label: 'Sessions', borderColor: '#0a9396', fill: false, data: weeks.map(week => week.totals.workoutCount)}]),
        duration: chart('Timed training per week', labels, [{label: 'Minutes', borderColor: '#bb3e03', fill: false, data: weeks.map(week => week.totals.totalDurationSeconds / 60)}]),
        distance: chart('Distance per week', labels, [{label: 'Distance km', borderColor: '#8338ec', fill: false, data: weeks.map(week => week.totals.totalDistanceKm)}]),
        calories: chart('Workout calories per week', labels, [{label: 'Calories', borderColor: '#9c6644', fill: false, data: weeks.map(week => week.totals.totalCalories)}]),
        volume: chart('Strength volume per week', labels, [{label: 'kg × reps', borderColor: '#1976d2', fill: false, data: weeks.map(week => week.totals.strengthVolumeKg)}])
    };
}

export function buildWorkoutDetailCharts(workouts) {
    const labels = workouts.map(workout => dayjs(workout.date).format('DD/MM/YYYY'));
    return {
        duration: chart('Timed training per workout', labels, [{label: 'Minutes', borderColor: '#bb3e03', fill: false, data: workouts.map(workout => workout.totals.totalDurationSeconds / 60)}]),
        distance: chart('Distance per workout', labels, [{label: 'Distance km', borderColor: '#8338ec', fill: false, data: workouts.map(workout => workout.totals.totalDistanceKm)}]),
        calories: chart('Workout calories per workout', labels, [{label: 'Calories', borderColor: '#9c6644', fill: false, data: workouts.map(workout => workout.totals.totalCalories)}]),
        volume: chart('Strength volume per workout', labels, [{label: 'kg × reps', borderColor: '#1976d2', fill: false, data: workouts.map(workout => workout.totals.strengthVolumeKg)}])
    };
}
