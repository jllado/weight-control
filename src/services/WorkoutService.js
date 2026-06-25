import dayjs from 'dayjs';
import {del, get, post, put} from './api';
import Workout from '../model/Workout';

function toWorkout(data) {
    return new Workout(data);
}

function toSegmentPayload(segment) {
    return {
        repetitions: segment.repetitions,
        durationSeconds: segment.durationSeconds,
        weight: segment.weight,
        speedKph: segment.speedKph,
        inclinePercent: segment.inclinePercent,
        resistanceLevel: segment.resistanceLevel,
        calories: segment.calories
    };
}

function toPayload(workout) {
    return {
        workoutDate: dayjs(workout.workoutDate).format('YYYY-MM-DD'),
        note: workout.note,
        lines: workout.lines.map(line => ({
            exerciseId: line.exerciseId,
            segments: line.segments.map(toSegmentPayload)
        }))
    };
}

export default {
    async get_all() {
        return (await get('/workouts')).map(toWorkout);
    },
    async save(workout) {
        const data = workout.id
            ? await put(`/workouts/${workout.id}`, toPayload(workout))
            : await post('/workouts', toPayload(workout));
        return toWorkout(data);
    },
    delete(workout) {
        return del(`/workouts/${workout.id}`);
    }
}
