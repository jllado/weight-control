import {get, post, put} from './api';
import WorkoutPlan from '../model/WorkoutPlan';
export default {
    async current() { const result = await get('/workout-plans/current'); return result ? new WorkoutPlan(result) : null; },
    archive(page = 0) { return get(`/workout-plans?page=${page}&size=10`); },
    async get(id) { return new WorkoutPlan(await get(`/workout-plans/${id}`)); },
    async create(plan) { return new WorkoutPlan(await post('/workout-plans', plan.toPayload())); },
    async update(plan) { return new WorkoutPlan(await put(`/workout-plans/${plan.id}`, {plan: plan.toPayload(), updateToken: plan.updateToken})); }
};
