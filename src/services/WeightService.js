import {del, get, post, put} from './api';
import Weight from '../model/Weight'

function toPayload(weight) {
    return {
        date: weight.date.toISOString(),
        weight: weight.weight,
        fatPercentage: weight.fat_percentage,
        muscle: weight.muscle
    };
}

export default {
    async get_all_by() {
        const data = await get('/weights');
        return data.map(item => new Weight({
            id: item.id,
            date: item.date,
            weight: item.weight,
            lost_weight: item.lostWeight,
            fat: item.fat,
            fat_percentage: item.fatPercentage,
            lost_fat: item.lostFat,
            muscle: item.muscle,
            muscle_percentage: item.musclePercentage,
            lost_muscle: item.lostMuscle,
            photo_front: item.photoFront,
            photo_right: item.photoRight,
            photo_left: item.photoLeft
        }));
    },
    async get_all_photos_by() {
        const weights = await this.get_all_by();
        return weights.map(weight => weight.photo()).filter(photo => photo.photo_front || photo.photo_left || photo.photo_right);
    },
    async get_last() {
        const weights = await this.get_all_by();
        return weights[0];
    },
    async get_previous(date) {
        const weights = await this.get_all_by();
        return weights.find(weight => weight.date < date);
    },
    async save(weight) {
        if (weight.id) {
            const data = await put(`/weights/${weight.id}`, toPayload(weight));
            return new Weight({
                id: data.id,
                date: data.date,
                weight: data.weight,
                lost_weight: data.lostWeight,
                fat: data.fat,
                fat_percentage: data.fatPercentage,
                lost_fat: data.lostFat,
                muscle: data.muscle,
                muscle_percentage: data.musclePercentage,
                lost_muscle: data.lostMuscle,
                photo_front: data.photoFront,
                photo_right: data.photoRight,
                photo_left: data.photoLeft
            });
        }
        const data = await post('/weights', toPayload(weight));
        return new Weight({
            id: data.id,
            date: data.date,
            weight: data.weight,
            lost_weight: data.lostWeight,
            fat: data.fat,
            fat_percentage: data.fatPercentage,
            lost_fat: data.lostFat,
            muscle: data.muscle,
            muscle_percentage: data.musclePercentage,
            lost_muscle: data.lostMuscle,
            photo_front: data.photoFront,
            photo_right: data.photoRight,
            photo_left: data.photoLeft
        });
    },
    delete(weight) {
        return del(`/weights/${weight.id}`);
    },
    async upload_image(weightId, side, file) {
        const body = new FormData();
        body.append('file', file);
        const data = await post(`/weights/${weightId}/photos/${side}`, body);
        return new Weight({
            id: data.id,
            date: data.date,
            weight: data.weight,
            lost_weight: data.lostWeight,
            fat: data.fat,
            fat_percentage: data.fatPercentage,
            lost_fat: data.lostFat,
            muscle: data.muscle,
            muscle_percentage: data.musclePercentage,
            lost_muscle: data.lostMuscle,
            photo_front: data.photoFront,
            photo_right: data.photoRight,
            photo_left: data.photoLeft
        });
    }
}
