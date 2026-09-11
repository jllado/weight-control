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
            trackingMode: exercise.trackingMode,
            exerciseType: exercise.exerciseType
        };
        const data = exercise.id
            ? await put(`/workout-exercises/${exercise.id}`, payload)
            : await post('/workout-exercises', payload);
        return toExercise(data);
    },
    async uploadImage(id, file) {
        const body = new FormData();
        body.append('file', file);
        return toExercise(await post(`/workout-exercises/${id}/image`, body));
    },
    async removeImage(id) {
        return toExercise(await del(`/workout-exercises/${id}/image`));
    },
    delete(exercise) {
        return del(`/workout-exercises/${exercise.id}`);
    }
}
