export default class CoachingPlan {

    constructor(source) {
        if (source === undefined) {
            this.goal = '';
            this.principles = [];
            this.priorities = [];
            this.actions = [];
            this.startDate = new Date();
            this.reviewDate = null;
            this.notes = '';
            this.updatedAt = null;
            return;
        }
        this.goal = source.goal;
        this.principles = source.principles;
        this.priorities = source.priorities;
        this.actions = source.actions;
        this.startDate = toDate(source.startDate);
        this.reviewDate = source.reviewDate ? toDate(source.reviewDate) : null;
        this.notes = source.notes || '';
        this.updatedAt = source.updatedAt;
    }
}

function toDate(value) {
    return value instanceof Date ? value : new Date(`${value}T12:00:00`);
}
