import {get, post, put, del} from './api';
import {foodPayload, quantityLabel} from '../model/Dish';

export default {
    async get_all() {
        return (await get('/foods')).map(food => ({...food, label: `${food.name} · ${quantityLabel(food)} · ${food.calories} kcal`}));
    },
    save(food) { return food.id ? put(`/foods/${food.id}`, foodPayload(food)) : post('/foods', foodPayload(food)); },
    delete(id) { return del(`/foods/${id}`); }
};
