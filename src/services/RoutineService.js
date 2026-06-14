import {del, get, post, put} from './api';
import Routine from '../model/Routine'

export default {
    async get_all_by() {
        return (await get('/routines')).map(item => new Routine({
            id: item.id,
            start_date: item.startDate,
            last_time_date: item.lastTimeDate,
            name: item.name,
            times: item.times,
            current_strike: item.currentStrike,
            best_strike: item.bestStrike,
            types: item.types
        })).sort((r1, r2) => r2.strike() - r1.strike());
    },
    async save(routine) {
        const payload = {name: routine.name, types: routine.typeNames()};
        const data = routine.id
            ? await put(`/routines/${routine.id}`, payload)
            : await post('/routines', payload);
        return new Routine({
            id: data.id,
            start_date: data.startDate,
            last_time_date: data.lastTimeDate,
            name: data.name,
            times: data.times,
            current_strike: data.currentStrike,
            best_strike: data.bestStrike,
            types: data.types
        });
    },
    delete(routine) {
        return del(`/routines/${routine.id}`);
    },
    async checkin(routineId, date) {
        const data = await post(`/routines/${routineId}/checkins`, {date: date.toISOString()});
        return new Routine({
            id: data.id,
            start_date: data.startDate,
            last_time_date: data.lastTimeDate,
            name: data.name,
            times: data.times,
            current_strike: data.currentStrike,
            best_strike: data.bestStrike,
            types: data.types
        });
    }
}
