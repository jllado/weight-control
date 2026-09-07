import dayjs from 'dayjs';
import {get, post, put} from './api';
import {celebrateDecisionMiss, celebrateDecisionWin, celebratePersonalRecords} from './CelebrationService';

export default {
    history() { return get('/decision-outcomes'); },
    updateReason(id, reason) { return put(`/decision-outcomes/${id}/reason`, {reason}); },
    async create(date, outcome, reason) {
        const response = await post('/decision-outcomes', {
            date: dayjs(date).format('YYYY-MM-DD'),
            outcome,
            reason
        });
        if (response.recordAchievements.length) {
            celebratePersonalRecords(response.recordAchievements);
        } else if (outcome === 'WIN') {
            celebrateDecisionWin();
        } else if (outcome === 'MISS') {
            celebrateDecisionMiss();
        }
        return response.result;
    }
}
