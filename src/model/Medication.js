export default class Medication {
    constructor(source = {}) {
        this.id = source.id;
        this.name = source.name || '';
        this.doseAmount = source.doseAmount ?? 1;
        this.doseUnit = source.doseUnit || 'tablet';
        this.notes = source.notes || '';
        this.startDate = source.startDate ? new Date(source.startDate instanceof Date ? source.startDate : `${source.startDate}T12:00:00`) : new Date();
        this.endDate = source.endDate ? new Date(source.endDate instanceof Date ? source.endDate : `${source.endDate}T12:00:00`) : new Date();
        this.repeatEvery = source.repeatEvery ?? 1;
        this.repeatUnit = source.repeatUnit || 'DAY';
        this.reminderTimes = source.reminderTimes || [];
        this.active = source.active ?? true;
    }
}
