import dayjs from 'dayjs';
import {post} from './api';
import {celebrateDecisionWin, celebratePersonalRecords} from './CelebrationService';

export default {
    async create(date, outcome) {
        const response = await post('/decision-outcomes', {
            date: dayjs(date).format('YYYY-MM-DD'),
            outcome
        });
        if (response.recordAchievements.length) {
            celebratePersonalRecords(response.recordAchievements);
        } else if (outcome === 'WIN') {
            celebrateDecisionWin();
        }
        return response.result;
    }
}
