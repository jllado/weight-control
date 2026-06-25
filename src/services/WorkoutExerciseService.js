import {del, get, post, put} from './api';
import WorkoutExercise from '../model/WorkoutExercise';

function toExercise(data) {
    return new WorkoutExercise(data);
}

export default {
    async get_all() {
        return (await get('/workout-exercises')).map(toExercise);
    },
    async save(exercise) {
        const payload = {
            name: exercise.name,
            description: exercise.description,
            trackingMode: exercise.trackingMode
        };
        const data = exercise.id
            ? await put(`/workout-exercises/${exercise.id}`, payload)
            : await post('/workout-exercises', payload);
        return toExercise(data);
    },
    delete(exercise) {
        return del(`/workout-exercises/${exercise.id}`);
    }
}
