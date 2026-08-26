import dayjs from 'dayjs';

export default class FastingPeriod {

    constructor(source) {
        if (source === undefined) {
            return;
        }
        this.id = source.id;
        this.startTime = new Date(source.startTime);
        this.endTime = source.endTime ? new Date(source.endTime) : null;
        this.startTimeFormat = source.startTimeFormat || dayjs(this.startTime).format('DD/MM/YYYY HH:mm');
        this.endTimeFormat = source.endTimeFormat || (this.endTime ? dayjs(this.endTime).format('DD/MM/YYYY HH:mm') : 'In progress');
        this.notes = source.notes;
        this.source = source.source;
    }

    durationFormat(now = new Date()) {
        const minutes = Math.round(((this.endTime || now).getTime() - this.startTime.getTime()) / 60000);
        return `${Math.floor(minutes / 60)}h ${minutes % 60}m`;
    }

    toObject() {
        return {
            id: this.id,
            startTime: this.startTime,
            endTime: this.endTime,
            notes: this.notes
        };
    }
}
