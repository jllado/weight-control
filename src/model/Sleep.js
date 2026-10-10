import dayjs from 'dayjs';

const MIN_TIME_IN_BED_SECONDS = 7 * 60 * 60;
const MAX_TIME_IN_BED_SECONDS = 9 * 60 * 60;
const MIN_TOTAL_SLEEP_SECONDS = 6 * 60 * 60;
const MIN_SLEEP_EFFICIENCY_PERCENTAGE = 85;
const MAX_SLEEP_MIDPOINT_DEVIATION_MINUTES = 60;
const MINUTES_PER_DAY = 24 * 60;
export const SleepStatus = {
    BAD: {name: 'BAD', className: 'bad'},
    POOR: {name: 'POOR', className: 'fail'},
    FAIR: {name: 'FAIR', className: 'normal'},
    GOOD: {name: 'GOOD', className: 'good'},
    EXCELLENT: {name: 'EXCELLENT', className: 'perfect'}
};

const SLEEP_STATUS_BY_SCORE = [SleepStatus.BAD, SleepStatus.POOR, SleepStatus.FAIR, SleepStatus.GOOD, SleepStatus.EXCELLENT];

export default class Sleep {

    constructor(source) {
        if (source === undefined) {
            return;
        }
        this.id = source.id;
        this.date = new Date(source.date);
        this.dateFormat = source.dateFormat || dayjs(this.date).format('DD/MM/YYYY');
        this.bedtimeStart = source.bedtimeStart == null ? null : new Date(source.bedtimeStart);
        this.bedtimeEnd = source.bedtimeEnd == null ? null : new Date(source.bedtimeEnd);
        this.bedtimeStartFormat = source.bedtimeStartFormat || (this.bedtimeStart ? dayjs(this.bedtimeStart).format('DD/MM/YYYY HH:mm') : '-');
        this.bedtimeEndFormat = source.bedtimeEndFormat || (this.bedtimeEnd ? dayjs(this.bedtimeEnd).format('DD/MM/YYYY HH:mm') : '-');
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

    totalBedtimeFormat() {
        return formatDuration(this.timeInBedSeconds());
    }

    timeInBedSeconds() {
        if (this.bedtimeStart === null || this.bedtimeEnd === null) {
            return null;
        }
        return durationInSeconds(this.bedtimeStart, this.bedtimeEnd);
    }

    meetsTimeInBedGoal() {
        const seconds = this.timeInBedSeconds();
        return seconds === null ? null : seconds >= MIN_TIME_IN_BED_SECONDS;
    }

    meetsTotalSleepGoal() {
        return this.totalSleepDuration == null ? null : this.totalSleepDuration >= MIN_TOTAL_SLEEP_SECONDS;
    }

    totalSleepGoalDeficitFormat() {
        return formatSleepGoalDeficit(this.totalSleepDuration, MIN_TOTAL_SLEEP_SECONDS);
    }

    totalSleepGoalDeficitAccessibleLabel() {
        return formatSleepGoalDeficitAccessibleLabel(this.totalSleepDuration, MIN_TOTAL_SLEEP_SECONDS, 'total sleep');
    }

    timeInBedGoalDeficitFormat() {
        return formatSleepGoalDeficit(this.timeInBedSeconds(), MIN_TIME_IN_BED_SECONDS);
    }

    timeInBedGoalDeficitAccessibleLabel() {
        return formatSleepGoalDeficitAccessibleLabel(this.timeInBedSeconds(), MIN_TIME_IN_BED_SECONDS, 'in-bed');
    }

    meetsNightlySleepGoals() {
        const timeInBedMet = this.meetsTimeInBedGoal();
        const totalSleepMet = this.meetsTotalSleepGoal();
        return timeInBedMet === null || totalSleepMet === null ? null : timeInBedMet && totalSleepMet;
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
        if (this.bedtimeStart === null || this.bedtimeEnd === null) {
            return 'Not recorded';
        }
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
    return `${(seconds / 3600).toFixed(1)} h`;
}

export function formatTimeInBedAverage(seconds) {
    return seconds === null ? 'Not enough data' : formatDuration(seconds);
}

export function formatTimeInBedChange(seconds) {
    if (seconds === null) {
        return '';
    }
    const minutes = Math.round(Math.abs(seconds) / 60);
    const sign = minutes === 0 ? '' : seconds > 0 ? '+' : '-';
    return `${sign}${minutes} min`;
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

export function getSleepStatus(sleeps) {
    const averageTimeInBed = average(sleeps.map(sleep => durationInSeconds(sleep.bedtimeStart, sleep.bedtimeEnd)));
    const averageTotalSleep = average(sleeps.map(sleep => sleep.totalSleepDuration));
    const averageEfficiencyPercentage = average(sleeps.map(sleep => sleep.totalSleepDuration * 100 / durationInSeconds(sleep.bedtimeStart, sleep.bedtimeEnd)));
    const midpointDeviation = getSleepMidpointDeviation(sleeps);
    const score = [
        averageTimeInBed >= MIN_TIME_IN_BED_SECONDS && averageTimeInBed <= MAX_TIME_IN_BED_SECONDS,
        averageTotalSleep >= MIN_TOTAL_SLEEP_SECONDS,
        averageEfficiencyPercentage >= MIN_SLEEP_EFFICIENCY_PERCENTAGE,
        midpointDeviation < MAX_SLEEP_MIDPOINT_DEVIATION_MINUTES
    ].filter(Boolean).length;
    return {...SLEEP_STATUS_BY_SCORE[score], score};
}

function formatSleepGoalDeficit(seconds, minimum) {
    const minutes = sleepGoalDeficitMinutes(seconds, minimum);
    return minutes === null ? null : `-${minutes}`;
}

function formatSleepGoalDeficitAccessibleLabel(seconds, minimum, metric) {
    const minutes = sleepGoalDeficitMinutes(seconds, minimum);
    if (minutes === null) return null;
    return `${minutes} ${minutes === 1 ? 'minute' : 'minutes'} below the ${metric} goal`;
}

function sleepGoalDeficitMinutes(seconds, minimum) {
    if (seconds == null || seconds >= minimum) return null;
    return Math.ceil((minimum - seconds) / 60);
}

function durationInSeconds(start, end) {
    return (end.getTime() - start.getTime()) / 1000;
}

function getSleepMidpointDeviation(sleeps) {
    const midpointMinutes = sleeps.map(sleep => {
        const midpoint = new Date((sleep.bedtimeStart.getTime() + sleep.bedtimeEnd.getTime()) / 2);
        return midpoint.getHours() * 60 + midpoint.getMinutes();
    });
    const angles = midpointMinutes.map(minutes => minutes * 2 * Math.PI / MINUTES_PER_DAY);
    const meanAngle = Math.atan2(average(angles.map(Math.sin)), average(angles.map(Math.cos)));
    const meanMinutes = ((meanAngle * MINUTES_PER_DAY / (2 * Math.PI)) + MINUTES_PER_DAY) % MINUTES_PER_DAY;
    const deviations = midpointMinutes.map(minutes => (((minutes - meanMinutes + MINUTES_PER_DAY / 2) % MINUTES_PER_DAY + MINUTES_PER_DAY) % MINUTES_PER_DAY) - MINUTES_PER_DAY / 2);
    return Math.sqrt(average(deviations.map(deviation => deviation * deviation)));
}

function average(values) {
    return values.reduce((total, value) => total + value, 0) / values.length;
}
