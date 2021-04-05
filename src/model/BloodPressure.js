import dayjs from 'dayjs';

export default class BloodPressure {

    constructor(fbDoc) {
        if (fbDoc === undefined) {
            return;
        }
        let fbData = fbDoc.data();
        this.id = fbDoc.id;
        this.user = fbData.user;
        this.date = fbData.date.toDate();
        this.dateFormat= dayjs(this.date).format('DD/MM/YYYY HH:mm')
        this.upper = this.round(fbData.upper);
        this.lower = this.round(fbData.lower);
        this.lost_upper = this.round(fbData.lost_upper);
        this.lost_lower = this.round(fbData.lost_lower);
    }

    round(value) {
        return Math.round(value * 100) / 100;
    }

    load_lost(previous) {
        console.log(this);
        if (previous) {
            this.lost_upper =  this.upper - previous.upper;
            this.lost_lower =  this.lower - previous.lower;
        } else {
            this.lost_upper =  0;
            this.lost_lower =  0;
        }
    }

    stage() {
        return get_stage(this.upper, this.lower);
    }

    toObject() {
        let blood_pressure = {}
        blood_pressure.id = this.id;
        blood_pressure.user = this.user;
        blood_pressure.date = this.date;
        blood_pressure.upper = this.upper;
        blood_pressure.lower = this.lower;
        blood_pressure.lost_upper =  this.lost_upper;
        blood_pressure.lost_lower =  this.lost_lower;
        return blood_pressure;
    }
}

export function get_stage(upper, lower) {
    if (upper > 180 || lower > 120) {
        return {
            name: "CRISIS",
            color: "red"
        }
    }
    if (upper > 140 || lower > 90) {
        return {
            name: "HYPER",
            color: "orange"
        }
    }
    if (upper > 130 || lower > 80) {
        return {
            name: "HIGH",
            color: "yellow"
        }
    }
    if (upper > 120 && lower <= 80) {
        return {
            name: "ELEVATED",
            color: "blue"
        }
    }
    return {
        name: "NORMAL",
        color: "green"
    }
}
