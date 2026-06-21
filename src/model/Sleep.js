import dayjs from 'dayjs';

export default class Sleep {

    constructor(source) {
        if (source === undefined) {
            return;
        }
        this.id = source.id;
        this.date = new Date(source.date);
        this.dateFormat = source.dateFormat || dayjs(this.date).format('DD/MM/YYYY');
        this.bedtimeStart = new Date(source.bedtimeStart);
        this.bedtimeEnd = new Date(source.bedtimeEnd);
        this.bedtimeStartFormat = source.bedtimeStartFormat || dayjs(this.bedtimeStart).format('DD/MM/YYYY HH:mm');
        this.bedtimeEndFormat = source.bedtimeEndFormat || dayjs(this.bedtimeEnd).format('DD/MM/YYYY HH:mm');
        this.totalSleepDuration = source.totalSleepDuration;
        this.deepSleepDuration = source.deepSleepDuration;
        this.remSleepDuration = source.remSleepDuration;
        this.lightSleepDuration = source.lightSleepDuration;
        this.awakeTime = source.awakeTime;
        this.averageHeartRate = this.round(source.averageHeartRate);
        this.averageHrv = source.averageHrv;
    }

    round(value) {
        return Math.round(Number(value) * 100) / 100;
    }

    totalSleepDurationFormat() {
        return formatDuration(this.totalSleepDuration);
    }

    deepSleepDurationFormat() {
        return formatDuration(this.deepSleepDuration);
    }

    remSleepDurationFormat() {
        return formatDuration(this.remSleepDuration);
    }

    lightSleepDurationFormat() {
        return formatDuration(this.lightSleepDuration);
    }

    awakeTimeFormat() {
        return formatDuration(this.awakeTime);
    }

    bedtimeWindowFormat() {
        return `${dayjs(this.bedtimeStart).format('DD/MM HH:mm')} - ${dayjs(this.bedtimeEnd).format('DD/MM HH:mm')}`;
    }

    heartRateFormat() {
        return `${this.round(this.averageHeartRate)} bpm`;
    }

    hrvFormat() {
        return `${this.averageHrv} ms`;
    }

    toObject() {
        return {
            id: this.id,
            date: this.date,
            bedtimeStart: this.bedtimeStart,
            bedtimeEnd: this.bedtimeEnd,
            totalSleepDuration: this.totalSleepDuration,
            deepSleepDuration: this.deepSleepDuration,
            remSleepDuration: this.remSleepDuration,
            lightSleepDuration: this.lightSleepDuration,
            awakeTime: this.awakeTime,
            averageHeartRate: this.averageHeartRate,
            averageHrv: this.averageHrv
        };
    }
}

export function formatDuration(seconds) {
    if (seconds === null || seconds === undefined) {
        return '-';
    }
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    return `${hours}h ${minutes}m`;
}

export function formatTimeOfDayFromMinutes(value) {
    if (value === null || value === undefined) {
        return '-';
    }
    const normalized = ((Math.round(value) % 1440) + 1440) % 1440;
    const hours = Math.floor(normalized / 60);
    const minutes = normalized % 60;
    return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`;
}
