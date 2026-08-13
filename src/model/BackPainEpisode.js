import dayjs from 'dayjs';

export const BACK_REGIONS = [
    {value: 'UPPER', label: 'Upper'},
    {value: 'MIDDLE', label: 'Middle'},
    {value: 'LOWER', label: 'Lower'}
];

export const BACK_SIDES = [
    {value: 'LEFT', label: 'Left'},
    {value: 'CENTER', label: 'Center'},
    {value: 'RIGHT', label: 'Right'}
];

export const BACK_PAIN_SEVERITIES = [
    {value: 'MILD', label: 'Mild', className: 'good', rank: 1},
    {value: 'MODERATE', label: 'Moderate', className: 'normal', rank: 2},
    {value: 'SEVERE', label: 'Severe', className: 'fail', rank: 3},
    {value: 'EXTREME', label: 'Extreme', className: 'bad', rank: 4}
];

const NO_BACK_PAIN = {value: null, label: 'None', className: 'perfect', rank: 0};

export default class BackPainEpisode {

    constructor(source) {
        if (source === undefined) {
            return;
        }
        this.id = source.id;
        this.date = new Date(source.date);
        this.dateFormat = source.dateFormat || dayjs(this.date).format('DD/MM/YYYY');
        this.time = source.time;
        this.timeFormat = source.timeFormat;
        this.region = source.region;
        this.side = source.side;
        this.severity = source.severity;
        this.note = source.note;
    }

    toObject() {
        return {
            id: this.id,
            date: this.date,
            region: this.region,
            side: this.side,
            severity: this.severity,
            note: this.note
        };
    }
}

export function getBackPainSeverityOption(value) {
    return value === null ? NO_BACK_PAIN : BACK_PAIN_SEVERITIES.find(option => option.value === value);
}

export function getBackPainSeverityRank(value) {
    return getBackPainSeverityOption(value).rank;
}

export function formatBackPainSeverity(value) {
    return getBackPainSeverityOption(value).label;
}

export function formatBackPainLocation(episode) {
    const region = BACK_REGIONS.find(option => option.value === episode.region).label;
    if (!episode.side) {
        return `${region} — side not recorded`;
    }
    const side = BACK_SIDES.find(option => option.value === episode.side).label;
    return `${region} ${side}`;
}

export function formatBackPainTime(episode) {
    return episode.timeFormat || 'Time not recorded';
}
