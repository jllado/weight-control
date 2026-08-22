import dayjs from 'dayjs';
import {del, get, post, put} from './api';
import Medication from '../model/Medication';
import {notificationsChanged} from './InAppNotificationService';

function toMedication(data) {
    return new Medication(data);
}

function toPayload(medication) {
    return {
        name: medication.name,
        doseAmount: medication.doseAmount,
        doseUnit: medication.doseUnit,
        notes: medication.notes || null,
        startDate: dayjs(medication.startDate).format('YYYY-MM-DD'),
        endDate: dayjs(medication.endDate).format('YYYY-MM-DD'),
        repeatEvery: medication.repeatEvery,
        repeatUnit: medication.repeatUnit,
        reminderTimes: medication.reminderTimes,
        active: medication.active
    };
}

export default {
    async getAll() {
        return (await get('/medications')).map(toMedication);
    },
    async save(medication) {
        const data = medication.id
            ? await put(`/medications/${medication.id}`, toPayload(medication))
            : await post('/medications', toPayload(medication));
        notificationsChanged();
        return toMedication(data);
    },
    async delete(medication) {
        await del(`/medications/${medication.id}`);
        notificationsChanged();
    },
    getDoses(from, to) {
        return get(`/medications/doses?from=${dayjs(from).format('YYYY-MM-DD')}&to=${dayjs(to).format('YYYY-MM-DD')}`);
    },
    getDose(id) {
        return get(`/medications/doses/${id}`);
    },
    async takeDose(id, takenAt = new Date()) {
        const dose = await post(`/medications/doses/${id}/take`, {takenAt: takenAt.toISOString()});
        notificationsChanged();
        return dose;
    },
    async snoozeDose(id, minutes) {
        const result = await post(`/medications/doses/${id}/snooze`, {minutes});
        notificationsChanged();
        return result;
    },
    async logDose(medicationId, takenAt = new Date()) {
        const dose = await post(`/medications/${medicationId}/doses`, {takenAt: takenAt.toISOString()});
        notificationsChanged();
        return dose;
    }
};
