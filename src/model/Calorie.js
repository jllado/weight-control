import dayjs from 'dayjs';

export default class Calorie {

    constructor(source) {
        if (source === undefined) {
            return;
        }
        this.id = source.id;
        this.date = new Date(source.date);
        this.dateFormat = source.dateFormat || dayjs(this.date).format('DD/MM/YYYY');
        this.calories = source.calories;
    }

    toObject() {
        return {
            id: this.id,
            date: this.date,
            calories: this.calories
        };
    }
}
