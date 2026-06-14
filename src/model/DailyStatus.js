import dayjs from "dayjs";

export default class DailyStatus {

    constructor(source) {
        if (source === undefined) {
            return;
        }
        if (source.data) {
            let fbData = source.data();
            this.id = source.id;
            this.date = fbData.date.toDate();
            this.dateFormat = dayjs(this.date).format('DD/MM/YYYY');
            this.weight = fbData.weight;
            this.blood_pressure = fbData.blood_pressure;
            this.total_routines = fbData.total_routines;
            this.total_weight_routines = fbData.total_weight_routines;
            this.total_blood_pressure_routines = fbData.total_blood_pressure_routines;
            this.total_flexibility_routines = fbData.total_flexibility_routines;
            this.total_mind_routines = fbData.total_mind_routines;
            this.routines_done = fbData.routines_done;
            this.weight_done = fbData.weight_done;
            this.blood_pressure_done = fbData.blood_pressure_done;
            this.flexibility_done = fbData.flexibility_done;
            this.mind_done = fbData.mind_done;
            this.routines_percentage = fbData.routines_percentage;
            this.weight_percentage = fbData.weight_percentage;
            this.blood_pressure_percentage = fbData.blood_pressure_percentage;
            this.flexibility_percentage = fbData.flexibility_percentage;
            this.mind_percentage = fbData.mind_percentage;
            this.routines_score = fbData.routines_score;
            this.weight_score = fbData.weight_score;
            this.blood_pressure_score = fbData.blood_pressure_score;
            this.flexibility_score = fbData.flexibility_score;
            this.mind_score = fbData.mind_score;
            this.routines_status = fbData.routines_status;
            this.weight_status = fbData.weight_status;
            this.blood_pressure_status = fbData.blood_pressure_status;
            this.flexibility_status = fbData.flexibility_status;
            this.mind_status = fbData.mind_status;
            this.user = fbData.user;
            return;
        }
        this.id = source.id;
        this.date = new Date(source.date);
        this.dateFormat = dayjs(this.date).format('DD/MM/YYYY');
        this.weight = source.weight;
        this.blood_pressure = source.blood_pressure;
        this.total_routines = source.total_routines;
        this.total_weight_routines = source.total_weight_routines;
        this.total_blood_pressure_routines = source.total_blood_pressure_routines;
        this.total_flexibility_routines = source.total_flexibility_routines;
        this.total_mind_routines = source.total_mind_routines;
        this.routines_done = source.routines_done;
        this.weight_done = source.weight_done;
        this.blood_pressure_done = source.blood_pressure_done;
        this.flexibility_done = source.flexibility_done;
        this.mind_done = source.mind_done;
        this.routines_percentage = source.routines_percentage;
        this.weight_percentage = source.weight_percentage;
        this.blood_pressure_percentage = source.blood_pressure_percentage;
        this.flexibility_percentage = source.flexibility_percentage;
        this.mind_percentage = source.mind_percentage;
        this.routines_score = source.routines_score;
        this.weight_score = source.weight_score;
        this.blood_pressure_score = source.blood_pressure_score;
        this.flexibility_score = source.flexibility_score;
        this.mind_score = source.mind_score;
        this.routines_status = source.routines_status;
        this.weight_status = source.weight_status;
        this.blood_pressure_status = source.blood_pressure_status;
        this.flexibility_status = source.flexibility_status;
        this.mind_status = source.mind_status;
        this.user = source.user;
    }

    isToday() {
        return dayjs(this.date).isToday();
    }
}
