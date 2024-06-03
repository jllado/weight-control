export default class WeekStatus {

    constructor(daily_status_list) {
        this.saturday = daily_status_list[0] || undefined;
        this.sunday = daily_status_list[1] || undefined;
        this.monday = daily_status_list[2] || undefined;
        this.tuesday = daily_status_list[3] || undefined;
        this.wednesday = daily_status_list[4] || undefined;
        this.thursday = daily_status_list[5] || undefined;
        this.friday = daily_status_list[6] || undefined;
        this.routines_percentage = Math.round(daily_status_list.map(s => s.routines_percentage).reduce((s1, s2) => s1 + s2, 0) / daily_status_list.length) * 100 / 100;
        this.weight_percentage = Math.round(daily_status_list.map(s => s.weight_percentage).reduce((w1, w2) => w1 + w2, 0) / daily_status_list.length) * 100 / 100;
        this.blood_pressure_percentage = Math.round(daily_status_list.map(s => s.blood_pressure_percentage).reduce((w1, w2) => w1 + w2, 0) / daily_status_list.length) * 100 / 100;
        this.flexibility_percentage = Math.round(daily_status_list.map(s => s.flexibility_percentage).reduce((w1, w2) => w1 + w2, 0) / daily_status_list.length) * 100 / 100;
        this.mind_percentage = Math.round(daily_status_list.map(s => s.mind_percentage).reduce((w1, w2) => w1 + w2, 0) / daily_status_list.length) * 100 / 100;
    }

}

