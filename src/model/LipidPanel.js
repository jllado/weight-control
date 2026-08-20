import dayjs from 'dayjs';
import {UserSex} from './UserProfile';

const LipidStatus = {
    DESIRABLE: {label: 'Desirable', className: 'perfect'},
    BEST: {label: 'Best', className: 'perfect'},
    NORMAL: {label: 'Normal', className: 'perfect'},
    OPTIMAL: {label: 'Optimal', className: 'perfect'},
    ACCEPTABLE: {label: 'Acceptable', className: 'good'},
    NEAR_OPTIMAL: {label: 'Near optimal', className: 'good'},
    BORDERLINE_HIGH: {label: 'Borderline high', className: 'fail'},
    LOW: {label: 'Low', className: 'bad'},
    HIGH: {label: 'High', className: 'bad'},
    VERY_HIGH: {label: 'Very high', className: 'bad'}
};

export default class LipidPanel {

    constructor(source) {
        if (source === undefined) {
            return;
        }
        this.id = source.id;
        this.date = source.date instanceof Date ? source.date : new Date(`${source.date}T12:00:00`);
        this.dateFormat = source.dateFormat || dayjs(this.date).format('DD/MM/YYYY');
        this.totalCholesterol = source.totalCholesterol;
        this.hdlCholesterol = source.hdlCholesterol;
        this.ldlCholesterol = source.ldlCholesterol;
        this.triglycerides = source.triglycerides;
        this.totalChange = null;
        this.hdlChange = null;
        this.ldlChange = null;
        this.triglyceridesChange = null;
    }

    loadChanges(previous) {
        if (!previous) {
            return;
        }
        this.totalChange = this.totalCholesterol - previous.totalCholesterol;
        this.hdlChange = this.hdlCholesterol - previous.hdlCholesterol;
        this.ldlChange = this.ldlCholesterol - previous.ldlCholesterol;
        this.triglyceridesChange = this.triglycerides - previous.triglycerides;
    }

    formatChange(change) {
        if (change === null) {
            return '—';
        }
        return `${change > 0 ? '+' : ''}${change} mg/dL`;
    }

    metricStatus(metricKey, sex) {
        const value = this[metricKey];
        if (metricKey === 'totalCholesterol') {
            return value < 200 ? LipidStatus.DESIRABLE : value < 240 ? LipidStatus.BORDERLINE_HIGH : LipidStatus.HIGH;
        }
        if (metricKey === 'hdlCholesterol') {
            if (value >= 60) {
                return LipidStatus.BEST;
            }
            return value < (sex === UserSex.MALE ? 40 : 50) ? LipidStatus.LOW : LipidStatus.ACCEPTABLE;
        }
        if (metricKey === 'ldlCholesterol') {
            if (value < 100) {
                return LipidStatus.OPTIMAL;
            }
            if (value < 130) {
                return LipidStatus.NEAR_OPTIMAL;
            }
            if (value < 160) {
                return LipidStatus.BORDERLINE_HIGH;
            }
            return value < 190 ? LipidStatus.HIGH : LipidStatus.VERY_HIGH;
        }
        if (metricKey === 'triglycerides') {
            if (value < 150) {
                return LipidStatus.NORMAL;
            }
            if (value < 200) {
                return LipidStatus.BORDERLINE_HIGH;
            }
            return value < 500 ? LipidStatus.HIGH : LipidStatus.VERY_HIGH;
        }
    }

    changeClass(metricKey, change) {
        if (change === null || change === 0) {
            return '';
        }
        const improved = metricKey === 'hdlCholesterol' ? change > 0 : change < 0;
        return improved ? 'good' : 'bad';
    }

    toObject() {
        return {
            id: this.id,
            date: this.date,
            totalCholesterol: this.totalCholesterol,
            hdlCholesterol: this.hdlCholesterol,
            ldlCholesterol: this.ldlCholesterol,
            triglycerides: this.triglycerides
        };
    }
}
