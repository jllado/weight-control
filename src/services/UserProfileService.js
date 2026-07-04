import dayjs from 'dayjs';
import {get, put} from './api';
import UserProfile from '../model/UserProfile';

function toProfile(data) {
    return new UserProfile(data);
}

function toPayload(profile) {
    return {
        birthDate: profile.birthDate ? dayjs(profile.birthDate).format('YYYY-MM-DD') : null,
        heightCm: profile.heightCm,
        sex: profile.sex,
        fitnessLevel: profile.fitnessLevel,
        takesMedication: profile.takesMedication,
        typicalCaloriesPerDay: {...profile.typicalCaloriesPerDay}
    };
}

export default {
    async get() {
        return toProfile(await get('/profile'));
    },
    async save(profile) {
        return toProfile(await put('/profile', toPayload(profile)));
    }
}
