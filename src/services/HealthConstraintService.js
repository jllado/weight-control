import dayjs from 'dayjs';
import {del, get, post, put} from './api';
import HealthConstraint from '../model/HealthConstraint';

function toPayload(constraint) {
    return {
        type: constraint.type,
        title: constraint.title,
        details: constraint.details,
        source: constraint.source,
        startDate: dayjs(constraint.startDate).format('YYYY-MM-DD'),
        endDate: constraint.endDate ? dayjs(constraint.endDate).format('YYYY-MM-DD') : null,
        active: constraint.active
    };
}

export default {
    async getAll() {
        return (await get('/health-constraints')).map(constraint => new HealthConstraint(constraint));
    },
    async save(constraint) {
        const data = constraint.id
            ? await put(`/health-constraints/${constraint.id}`, toPayload(constraint))
            : await post('/health-constraints', toPayload(constraint));
        return new HealthConstraint(data);
    },
    delete(constraint) {
        return del(`/health-constraints/${constraint.id}`);
    }
};
