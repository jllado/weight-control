import dayjs from 'dayjs';
import CoachingPlan from '../model/CoachingPlan';
import {get, put} from './api';

function toPayload(plan) {
    return {
        goal: plan.goal.trim(),
        principles: plan.principles,
        priorities: plan.priorities,
        actions: plan.actions,
        startDate: dayjs(plan.startDate).format('YYYY-MM-DD'),
        reviewDate: plan.reviewDate ? dayjs(plan.reviewDate).format('YYYY-MM-DD') : null,
        notes: plan.notes?.trim() || null
    };
}

export default {
    async get() {
        const data = await get('/coaching-plan');
        return data ? new CoachingPlan(data) : null;
    },
    async save(plan) {
        return new CoachingPlan(await put('/coaching-plan', toPayload(plan)));
    }
};
