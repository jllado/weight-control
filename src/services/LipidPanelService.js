import dayjs from 'dayjs';
import {del, get, post, put} from './api';
import LipidPanel from '../model/LipidPanel';
import {celebratePersonalRecords} from './CelebrationService';

function toPayload(panel) {
    return {
        date: dayjs(panel.date).format('YYYY-MM-DD'),
        totalCholesterol: panel.totalCholesterol,
        hdlCholesterol: panel.hdlCholesterol,
        ldlCholesterol: panel.ldlCholesterol,
        triglycerides: panel.triglycerides
    };
}

function toLipidPanel(data) {
    return new LipidPanel(data);
}

export default {
    async get_all() {
        const panels = (await get('/lipid-panels')).map(toLipidPanel);
        panels.forEach((panel, index) => panel.loadChanges(panels[index + 1]));
        return panels;
    },
    async save(panel) {
        const response = panel.id
            ? await put(`/lipid-panels/${panel.id}`, toPayload(panel))
            : await post('/lipid-panels', toPayload(panel));
        celebratePersonalRecords(response.recordAchievements);
        return toLipidPanel(response.result);
    },
    delete(panel) {
        return del(`/lipid-panels/${panel.id}`);
    }
};
