import {get, post, put, del} from './api';
import {foodPayload, nutrientSchemaVersion} from '../model/Dish';

export default {
    get_all() { return get('/dishes'); },
    get(id) { return get(`/dishes/${id}`); },
    save(recipe) {
        const payload = {name: recipe.name, servings: recipe.servings, ingredients: recipe.ingredients.map(foodPayload), nutrientSchemaVersion};
        return recipe.id ? put(`/dishes/${recipe.id}`, payload) : post('/dishes', payload);
    },
    delete(id) { return del(`/dishes/${id}`); }
};
