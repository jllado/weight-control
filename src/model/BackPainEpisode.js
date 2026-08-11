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
        this.pain = source.pain;
        this.note = source.note;
    }

    toObject() {
        return {
            id: this.id,
            date: this.date,
            region: this.region,
            side: this.side,
            pain: this.pain,
            note: this.note
        };
    }
}

export function getBackPainScoreBand(value) {
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

export function formatBackPainScore(value) {
    return `${value} (${getBackPainScoreBand(value).label})`;
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
