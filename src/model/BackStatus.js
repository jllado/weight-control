import dayjs from 'dayjs';

export const BACK_REGIONS = [
    {key: 'lower', label: 'Lower Back'},
    {key: 'middle', label: 'Middle Back'},
    {key: 'upper', label: 'Upper Back'}
];

export const BACK_METRICS = [
    {key: 'pain', label: 'Pain'},
    {key: 'stiffness', label: 'Stiffness'},
    {key: 'activityLimitation', label: 'Activity Limitation'}
];

export default class BackStatus {

    constructor(source) {
        if (source === undefined) {
            return;
        }
        this.id = source.id;
        this.date = new Date(source.date);
        this.dateFormat = source.dateFormat || dayjs(this.date).format('DD/MM/YYYY');
        this.lower = {...source.lower};
        this.middle = {...source.middle};
        this.upper = {...source.upper};
        this.note = source.note;
    }

    toObject() {
        return {
            id: this.id,
            date: this.date,
            lower: {...this.lower},
            middle: {...this.middle},
            upper: {...this.upper},
            note: this.note
        };
    }
}

export function getBackScoreBand(value) {
    if (value === 0) {
        return {label: 'None', className: 'perfect'};
    }
    if (value <= 3) {
        return {label: 'Mild', className: 'good'};
    }
    if (value <= 6) {
        return {label: 'Moderate', className: 'normal'};
    }
    if (value < 10) {
        return {label: 'Severe', className: 'fail'};
    }
    return {label: 'Extreme', className: 'bad'};
}

export function formatBackScore(value) {
    const band = getBackScoreBand(value);
    return `${value} (${band.label})`;
}
