import dayjs from 'dayjs';

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
