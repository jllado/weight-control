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
        this.weight = this.round(fbData.weight);
        this.lost_weight = this.round(fbData.lost_weight);
        this.fat = this.round(fbData.fat);
        this.fat_percentage = this.round(fbData.fat_percentage);
        this.lost_fat = this.round(fbData.lost_fat);
        this.muscle = this.round(fbData.muscle);
        this.muscle_percentage = this.round(fbData.muscle_percentage);
        this.lost_muscle = this.round(fbData.lost_muscle);
    }

    round(value) {
        return Math.round(value * 100) / 100;
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
        return get_status(this.fat_percentage);
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

export function get_status(fat_percentage) {
    if (fat_percentage > 35) {
        return {
            name: "OBESITY",
            color: "darkred"
        }
    }
    if (fat_percentage > 30) {
        return {
            name: "OVERWEIGHT",
            color: "red"
        }
    }
    if (fat_percentage > 25) {
        return {
            name: "BAD_SHAPE",
            color: "yellow"
        }
    }
    if (fat_percentage > 22) {
        return {
            name: "NORMAL",
            color: "blue"
        }
    }
    if (fat_percentage > 17) {
        return {
            name: "GOOD",
            color: "greenyellow"
        }
    }
    if (fat_percentage > 13) {
        return {
            name: "EXCELLENT",
            color: "green"
        }
    }
    if (fat_percentage > 9) {
        return {
            name: "COMPETITION",
            color: "green"
        }
    }
    return {
        name: "LOW_FAT",
        color: "red"
    }
}
