import dayjs from 'dayjs';

export default class Weight {

    constructor(fbDoc) {
        if (fbDoc === undefined) {
            return;
        }
        let fbData = fbDoc.data();
        this.id = fbDoc.id;
        this.user = fbData.user;
        this.date = fbData.date.toDate();
        this.dateFormat= dayjs(this.date).format('DD/MM/YYYY')
        this.weight = round(fbData.weight);
        this.lost_weight = round(fbData.lost_weight);
        this.fat = round(fbData.fat);
        this.fat_percentage = round(fbData.fat_percentage);
        this.lost_fat = round(fbData.lost_fat);
        this.muscle = round(fbData.muscle);
        this.muscle_percentage = round(fbData.muscle_percentage);
        this.lost_muscle = round(fbData.lost_muscle);
    }

    load_lost(previous) {
        if (previous) {
            this.lost_weight =  this.weight - previous.weight;
            this.lost_fat =  this.fat - previous.fat;
            this.lost_muscle =  this.muscle - previous.muscle;
        } else {
            this.lost_weight =  0;
            this.lost_fat =  0;
            this.lost_muscle =  0;
        }
    }

    status() {
        for (let statusKey in WeightStatus) {
            let status = WeightStatus[statusKey];
            if (this.fat_percentage > status.fat) {
                return status;
            }
        }
    }

    range() {
        for (let range of WeightRanges) {
            if (this.weight < range) {
                return range;
            }
        }
    }

    next_range() {
        let current_range = this.range()
        for (let range of WeightRanges.slice().reverse()) {
            if (range < current_range) {
                return range;
            }
        }
    }

    months_next_range(current_weight_trend) {
        let next_range = this.next_range();
        let remain_weight = this.weight - next_range;
        let lost_weight_per_month = Math.abs(current_weight_trend.lost_weight);
        return round(remain_weight / lost_weight_per_month);
    }

    bmi() {
        return new BMI(this.weight)
    }

    toObject() {
        let weight = {}
        weight.id = this.id;
        weight.user = this.user;
        weight.date = this.date;
        weight.weight = this.weight;
        weight.fat_percentage = this.fat_percentage;
        weight.fat = this.fat;
        weight.muscle = this.muscle;
        weight.muscle_percentage = this.muscle_percentage;
        weight.lost_weight =  this.lost_weight;
        weight.lost_fat =  this.lost_fat;
        weight.lost_muscle =  this.lost_muscle;
        return weight;
    }
}

export class BMI {

    constructor(weight) {
        this.value = round(weight / (1.75 * 1.75))
    }

    status() {
        for (let statusKey in BMIStatus) {
            let status = BMIStatus[statusKey];
            if (this.value >= status.value) {
                return status;
            }
        }
    }
}


function round(value) {
    return Math.round(value * 100) / 100;
}


export const WeightRanges = [60, 65, 70, 75, 80, 85, 90]

export const BMIStatus = {
    OBESITY: {
        name: "OBESITY",
        value: 30,
        color: "red"
    },
    OVERWEIGHT: {
        name: "OVERWEIGHT",
        value: 25,
        color: "yellow"
    },
    NORMAL: {
        name: "NORMAL",
        value: 18.5,
        color: "green"
    },
    LOW: {
        name: "LOW",
        value: 0,
        color: "yellow"
    }
};

export const WeightStatus = {
    OBESITY: {
        name: "OBESITY",
        fat: 35,
        color: "red"
    },
    OVERWEIGHT: {
        name: "OVERWEIGHT",
        fat: 30,
        color: "orange"
    },
    BAD_SHAPE: {
        name: "BAD_SHAPE",
        fat: 25,
        color: "yellow"
    },
    NORMAL: {
        name: "NORMAL",
        fat: 22,
        color: "blue"
    },
    GOOD: {
        name: "GOOD",
        fat: 17,
        color: "greenyellow"
    },
    EXCELLENT: {
        name: "EXCELLENT",
        fat: 13,
        color: "green"
    },
    COMPETITION: {
        name: "COMPETITION",
        fat: 9,
        color: "darkgreen"
    },
    LOW_FAT: {
        name: "LOW_FAT",
        fat: 0,
        color: "red"
    }
};

