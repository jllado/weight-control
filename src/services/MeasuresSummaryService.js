import dayjs from 'dayjs';
import {get_stage} from '../model/BloodPressure'

export default {

    get_month_average_weights_for(date, weights) {
        let month_weights = this.get_month_measures_for(date, weights);
        let average_weight = this.get_average_weight(month_weights);
        if (!average_weight && date.isAfter(dayjs(new Date()))) {
            return this.get_weight_trend(weights)
        }
        return average_weight || this.get_previous_weight(date, weights);
    },
    get_month_average_blood_pressures_for(date, blood_pressures) {
        let month_blood_pressures = this.get_month_measures_for(date, blood_pressures);
        let average_blood_pressure = this.get_average_blood_pressure(month_blood_pressures);
        if (!average_blood_pressure && date.isAfter(dayjs(new Date()))) {
            return this.get_blood_pressure_trend(blood_pressures)
        }
        return average_blood_pressure || this.get_previous_blood_pressure(date, blood_pressures);
    },
    get_weight_trend(weights) {
        let previous_month_average_weight = this.get_previous_month_average_weight(weights);
        let previous_weight = this.get_previous_weight(dayjs(new Date()), weights);
        let lost_fat_percentage_trend = this.round((previous_month_average_weight.lost_fat * 100) / previous_month_average_weight.weight);
        let lost_muscle_percentage_trend = this.round((previous_month_average_weight.lost_muscle * 100) / previous_month_average_weight.weight);
        return new WeightSummaryData(
            previous_weight.weight + previous_month_average_weight.lost_weight,
            previous_month_average_weight.lost_weight,
            previous_weight.fat + lost_fat_percentage_trend,
            previous_month_average_weight.lost_fat,
            previous_weight.muscle + lost_muscle_percentage_trend,
            previous_month_average_weight.lost_muscle
        );
    },
    get_blood_pressure_trend(blood_pressures) {
        let previous_month_average_blood_pressure = this.get_previous_month_average_blood_pressure(blood_pressures);
        let previous_second_month_average_blood_pressure = this.get_previous_second_month_average_blood_pressure(blood_pressures);
        if (previous_second_month_average_blood_pressure == undefined) {
            return undefined
        }
        let lost_upper_trend = this.round(previous_month_average_blood_pressure.upper - previous_second_month_average_blood_pressure.upper);
        let lost_lower_trend = this.round(previous_month_average_blood_pressure.lower - previous_second_month_average_blood_pressure.lower);
        return new BloodPressureSummaryData(
            previous_month_average_blood_pressure.upper,
            previous_month_average_blood_pressure.lower,
            lost_upper_trend,
            lost_lower_trend
        );
    },
    get_previous_month_average_weight: function (weights) {
        let previous_month_weights = this.get_last_month_measures_for(weights);
        let previous_month_average_weight = this.get_average_weight(previous_month_weights)
        return previous_month_average_weight;
    },
    get_previous_month_average_blood_pressure: function (blood_pressures) {
        let previous_month_blood_pressures = this.get_last_month_measures_for(blood_pressures);
        let previous_month_average_blood_pressure = this.get_average_blood_pressure(previous_month_blood_pressures)
        return previous_month_average_blood_pressure;
    },
    get_previous_second_month_average_blood_pressure: function (blood_pressures) {
        let previous_second_month_blood_pressures = this.get_last_second_month_measures_for(blood_pressures);
        let previous_second_month_average_blood_pressure = this.get_average_blood_pressure(previous_second_month_blood_pressures)
        return previous_second_month_average_blood_pressure;
    },
    get_weight_strike_days(weight, weights) {
        let strikePreviousDate = weights.filter(w => w.weight > weight).map(w => w.date).sort((d1, d2) => d2 - d1)[0];
        let strikeStartDate = weights.filter(w => w.date > strikePreviousDate).map(w => w.date).sort((d1, d2) => d1 - d2)[0];
        return dayjs(new Date()).diff(strikeStartDate, 'day')
    },
    get_month_measures_for(date, measures) {
        let start = date.startOf('month').toDate();
        let end = date.endOf('month').toDate();
        return measures.filter(w => w.date >= start && w.date <= end);
    },
    get_last_date(measures) {
        let dates = measures.map(m => m.date);
        let last_date = new Date(Math.max.apply(null, dates))
        return last_date;
    },
    get_last_month_measures_for(measures) {
        let last_date = this.get_last_date(measures);
        let previous_month = dayjs(last_date).subtract(1, 'month').toDate();
        return measures.filter(w => w.date >= previous_month);
    },
    get_last_second_month_measures_for(measures) {
        let last_date = this.get_last_date(measures);
        let previous_month = dayjs(last_date).subtract(1, 'month').toDate();
        let previous_second_month = dayjs(last_date).subtract(2, 'month').toDate();
        return measures.filter(w => w.date >= previous_second_month && w.date < previous_month);
    },
    get_previous_weight(date, weights) {
        let previous_weight = this.get_previous_measure(date, weights);
        if (previous_weight === undefined) {
            return new WeightSummaryData(0, 0, 0, 0, 0, 0);
        }
        return new WeightSummaryData(previous_weight.weight, previous_weight.lost_weight, previous_weight.fat_percentage, previous_weight.lost_fat, previous_weight.muscle_percentage, previous_weight.lost_muscle);
    },
    get_previous_blood_pressure(date, blood_pressures) {
        let previous_blood_pressure = this.get_previous_measure(date, blood_pressures);
        if (previous_blood_pressure === undefined) {
            return new BloodPressureSummaryData(0, 0, 0, 0);
        }
        return new BloodPressureSummaryData(previous_blood_pressure.upper, previous_blood_pressure.lower, previous_blood_pressure.lost_upper, previous_blood_pressure.lost_lower);
    },
    get_previous_measure(date, measures) {
        let previous_measure = measures.find(w => w.date < date.toDate());
        if (!previous_measure) {
            return undefined;
        }
        return previous_measure;
    },
    get_average_weight(month_weights) {
        if (month_weights.length === 0) {
            return undefined;
        }
        let average_weight = this.get_average(month_weights.map(w => w.weight));
        let average_lost_weight = this.get_total(month_weights.map(w => w.lost_weight));
        let average_fat = this.get_average(month_weights.map(w => w.fat_percentage));
        let average_lost_fat = this.get_total(month_weights.map(w => w.lost_fat));
        let average_muscle = this.get_average(month_weights.map(w => w.muscle_percentage));
        let average_lost_muscle = this.get_total(month_weights.map(w => w.lost_muscle));
        return new WeightSummaryData(average_weight, average_lost_weight, average_fat, average_lost_fat, average_muscle, average_lost_muscle);
    },
    get_average_blood_pressure(month_blood_pressures) {
        if (month_blood_pressures.length === 0) {
            return undefined;
        }
        let average_upper = this.get_average(month_blood_pressures.map(w => w.upper));
        let average_lower = this.get_average(month_blood_pressures.map(w => w.lower));
        let average_lost_upper = this.get_total(month_blood_pressures.map(w => w.lost_upper));
        let average_lost_lower = this.get_total(month_blood_pressures.map(w => w.lost_lower));
        return new BloodPressureSummaryData(average_upper, average_lower, average_lost_upper, average_lost_lower);
    },
    round(value) {
        return Math.round(value * 100) / 100;
    },
    get_average(values) {
        let sum = values.reduce((w1, w2) => w1 + w2, 0);
        let average = sum / values.length;
        return this.round(average);
    },
    get_total(values) {
        return this.round(values.reduce((w1, w2) => w1 + w2, 0));
    },
    get_routines_status(routines) {
        let score = this.get_routines_score(routines);
        let routines_status = score + 100 / routines.length;
        return Math.round(routines_status * 100) / 100;
    },
    get_routines_score(routines) {
        return routines.map(r => r.score()).reduce((r1, r2) => r1 + r2, 0);
    }
}

class WeightSummaryData {
    constructor(weight, lost_weight, fat, lost_fat, muscle, lost_muscle) {
        this.weight = weight;
        this.lost_weight = lost_weight;
        this.fat = fat;
        this.lost_fat = lost_fat;
        this.muscle = muscle;
        this.lost_muscle = lost_muscle;
    }
}

class BloodPressureSummaryData {
    constructor(upper, lower, lost_upper, lost_lower) {
        this.upper = upper;
        this.lower = lower;
        this.lost_upper = lost_upper;
        this.lost_lower = lost_lower;
    }

    stage() {
        return get_stage(this.upper, this.lower);
    }
}
