export default class WeekStatus {

    constructor(daily_status_list, percentages = {}) {
        this.saturday = daily_status_list[0] || undefined;
        this.sunday = daily_status_list[1] || undefined;
        this.monday = daily_status_list[2] || undefined;
        this.tuesday = daily_status_list[3] || undefined;
        this.wednesday = daily_status_list[4] || undefined;
        this.thursday = daily_status_list[5] || undefined;
        this.friday = daily_status_list[6] || undefined;
        const effectiveDays = daily_status_list.filter(Boolean);
        this.routines_percentage = percentages.routines_percentage ?? average(effectiveDays, 'routines_percentage');
        this.weight_percentage = percentages.weight_percentage ?? average(effectiveDays, 'weight_percentage');
        this.blood_pressure_percentage = percentages.blood_pressure_percentage ?? average(effectiveDays, 'blood_pressure_percentage');
        this.flexibility_percentage = percentages.flexibility_percentage ?? average(effectiveDays, 'flexibility_percentage');
        this.mind_percentage = percentages.mind_percentage ?? average(effectiveDays, 'mind_percentage');
        this.mood_average = percentages.mood_average ?? averageMood(effectiveDays);
    }

}

function average(items, key) {
    if (items.length === 0) {
        return 0;
    }
    return Math.round(items.map(item => Number(item[key])).reduce((left, right) => left + right, 0) / items.length * 100) / 100;
}

function averageMood(items) {
    const moods = items.map(item => item.mood.average).filter(value => value !== null);
    if (moods.length === 0) {
        return null;
    }
    return Math.round(moods.reduce((left, right) => left + right, 0) / moods.length * 100) / 100;
}
