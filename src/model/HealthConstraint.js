import dayjs from 'dayjs';

const HEALTH_CONSTRAINT_TYPE_OPTIONS = [
    {value: 'INJURY', label: 'Injury'},
    {value: 'CLINICIAN_GUIDANCE', label: 'Clinician guidance'},
    {value: 'MEDICATION', label: 'Medication'},
    {value: 'ALLERGY', label: 'Allergy'},
    {value: 'DIETARY', label: 'Dietary'},
    {value: 'OTHER', label: 'Other'}
];

const HEALTH_CONSTRAINT_SOURCE_OPTIONS = [
    {value: 'SELF_REPORTED', label: 'Self-reported'},
    {value: 'DOCTOR', label: 'Doctor'},
    {value: 'PHYSIOTHERAPIST', label: 'Physiotherapist'},
    {value: 'OTHER_CLINICIAN', label: 'Other clinician'}
];

export default class HealthConstraint {

    constructor(source) {
        if (source === undefined) {
            return;
        }
        this.id = source.id;
        this.type = source.type;
        this.title = source.title;
        this.details = source.details;
        this.source = source.source;
        this.startDate = toDate(source.startDate);
        this.endDate = source.endDate ? toDate(source.endDate) : null;
        this.active = source.active;
    }

    typeLabel() {
        return HEALTH_CONSTRAINT_TYPE_OPTIONS.find(option => option.value === this.type).label;
    }

    sourceLabel() {
        return HEALTH_CONSTRAINT_SOURCE_OPTIONS.find(option => option.value === this.source).label;
    }

    status() {
        if (!this.active) {
            return 'Inactive';
        }
        if (dayjs(this.startDate).isAfter(dayjs(), 'day')) {
            return 'Upcoming';
        }
        if (this.endDate && dayjs(this.endDate).isBefore(dayjs(), 'day')) {
            return 'Expired';
        }
        return 'Active';
    }

    dateRange() {
        const start = dayjs(this.startDate).format('DD/MM/YYYY');
        return this.endDate ? `${start}–${dayjs(this.endDate).format('DD/MM/YYYY')}` : `${start}–ongoing`;
    }

    toObject() {
        return {
            id: this.id,
            type: this.type,
            title: this.title,
            details: this.details,
            source: this.source,
            startDate: this.startDate,
            endDate: this.endDate,
            active: this.active
        };
    }
}

function toDate(value) {
    return value instanceof Date ? value : new Date(`${value}T12:00:00`);
}

export function getHealthConstraintTypeOptions() {
    return HEALTH_CONSTRAINT_TYPE_OPTIONS;
}

export function getHealthConstraintSourceOptions() {
    return HEALTH_CONSTRAINT_SOURCE_OPTIONS;
}
