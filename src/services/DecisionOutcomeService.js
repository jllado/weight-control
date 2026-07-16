import dayjs from 'dayjs';
import {post} from './api';

export default {
    create(date, outcome) {
        return post('/decision-outcomes', {
            date: dayjs(date).format('YYYY-MM-DD'),
            outcome
        });
    }
}
