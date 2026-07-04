import dayjs from 'dayjs';

const SICKNESS_TYPE_OPTIONS = [
    {value: 'COLD', label: 'Cold'},
    {value: 'FLU', label: 'Flu'},
    {value: 'COVID', label: 'Covid'},
    {value: 'FEVER', label: 'Fever'},
    {value: 'SORE_THROAT', label: 'Sore throat'},
    {value: 'COUGH', label: 'Cough'},
    {value: 'SINUS_CONGESTION', label: 'Sinus congestion'},
    {value: 'ALLERGIES', label: 'Allergies'},
    {value: 'HEADACHE', label: 'Headache'},
    {value: 'MIGRAINE', label: 'Migraine'},
    {value: 'STOMACHACHE', label: 'Stomachache'},
    {value: 'NAUSEA', label: 'Nausea'},
    {value: 'DIARRHEA', label: 'Diarrhea'},
    {value: 'CONSTIPATION', label: 'Constipation'},
    {value: 'ACID_REFLUX', label: 'Acid reflux'},
    {value: 'FATIGUE', label: 'Fatigue'},
    {value: 'DIZZINESS', label: 'Dizziness'},
    {value: 'LOWER_BACK_PAIN', label: 'Lower back pain'},
    {value: 'MIDDLE_BACK_PAIN', label: 'Middle back pain'},
    {value: 'UPPER_BACK_PAIN', label: 'Upper back pain'},
    {value: 'NECK_PAIN', label: 'Neck pain'},
    {value: 'MUSCLE_SORENESS', label: 'Muscle soreness'},
    {value: 'JOINT_PAIN', label: 'Joint pain'},
    {value: 'INJURY', label: 'Injury'},
    {value: 'CRAMPS', label: 'Cramps'},
    {value: 'TOOTHACHE', label: 'Toothache'},
    {value: 'EARACHE', label: 'Earache'},
    {value: 'EYE_IRRITATION', label: 'Eye irritation'},
    {value: 'SKIN_RASH', label: 'Skin rash'},
    {value: 'GENERAL_DISCOMFORT', label: 'General discomfort'}
];

const SICKNESS_SEVERITY_OPTIONS = [
    {value: 'LOW', label: 'Low'},
    {value: 'MEDIUM', label: 'Medium'},
    {value: 'HIGH', label: 'High'}
];

export default class Sickness {

    constructor(source) {
        if (source === undefined) {
            return;
        }
        this.id = source.id;
        this.date = new Date(source.date);
        this.dateFormat = source.dateFormat || dayjs(this.date).format('DD/MM/YYYY');
        this.type = source.type;
        this.severity = source.severity;
        this.note = source.note;
    }

    typeLabel() {
        return getSicknessTypeOption(this.type).label;
    }

    severityLabel() {
        return getSicknessSeverityOption(this.severity).label;
    }

    toObject() {
        return {
            id: this.id,
            date: this.date,
            type: this.type,
            severity: this.severity,
            note: this.note
        };
    }
}

export function getSicknessTypeOption(value) {
    return SICKNESS_TYPE_OPTIONS.find(option => option.value === value) || SICKNESS_TYPE_OPTIONS[0];
}

export function getSicknessTypeOptions() {
    return SICKNESS_TYPE_OPTIONS;
}

export function getSicknessSeverityOption(value) {
    return SICKNESS_SEVERITY_OPTIONS.find(option => option.value === value) || SICKNESS_SEVERITY_OPTIONS[0];
}

export function getSicknessSeverityOptions() {
    return SICKNESS_SEVERITY_OPTIONS;
}
