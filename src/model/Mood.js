import dayjs from 'dayjs';

const MOOD_OPTIONS = [
    {value: 1, emoji: '😞', label: 'Very Bad'},
    {value: 2, emoji: '🙁', label: 'Bad'},
    {value: 3, emoji: '😐', label: 'Neutral'},
    {value: 4, emoji: '🙂', label: 'Good'},
    {value: 5, emoji: '😄', label: 'Great'}
];

const MOOD_PERIOD_OPTIONS = [
    {value: 'MORNING', label: 'Morning'},
    {value: 'MIDDAY', label: 'Midday'},
    {value: 'EVENING', label: 'Evening'}
];

export default class Mood {

    constructor(source) {
        if (source === undefined) {
            return;
        }
        this.id = source.id;
        this.date = new Date(source.date);
        this.dateFormat = dayjs(this.date).format('DD/MM/YYYY');
        this.period = source.period;
        this.value = source.value;
        this.note = source.note;
    }

    option() {
        return getMoodOption(this.value);
    }

    label() {
        return this.option().label;
    }

    emoji() {
        return this.option().emoji;
    }

    periodLabel() {
        return getMoodPeriodOption(this.period).label;
    }

    toObject() {
        let mood = {};
        mood.id = this.id;
        mood.date = this.date;
        mood.period = this.period;
        mood.value = this.value;
        mood.note = this.note;
        return mood;
    }
}

export function getMoodOption(value) {
    return MOOD_OPTIONS.find(option => option.value === value) || MOOD_OPTIONS[2];
}

export function getMoodOptions() {
    return MOOD_OPTIONS.map(option => ({
        value: option.value,
        label: `${option.emoji} ${option.label}`
    }));
}

export function getMoodPeriodOption(value) {
    return MOOD_PERIOD_OPTIONS.find(option => option.value === value);
}

export function getMoodPeriodOptions() {
    return MOOD_PERIOD_OPTIONS;
}

export function getMoodPeriodOrder(value) {
    return MOOD_PERIOD_OPTIONS.findIndex(option => option.value === value);
}
