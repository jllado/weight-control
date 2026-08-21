import dayjs from 'dayjs';

export default class FastingPeriod {

    constructor(source) {
        if (source === undefined) {
            return;
        }
        this.id = source.id;
        this.startTime = new Date(source.startTime);
        this.endTime = new Date(source.endTime);
        this.startTimeFormat = source.startTimeFormat || dayjs(this.startTime).format('DD/MM/YYYY HH:mm');
        this.endTimeFormat = source.endTimeFormat || dayjs(this.endTime).format('DD/MM/YYYY HH:mm');
        this.notes = source.notes;
    }

    durationFormat() {
        const minutes = Math.round((this.endTime.getTime() - this.startTime.getTime()) / 60000);
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
