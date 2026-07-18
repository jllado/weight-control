import dayjs from 'dayjs';
import {get_stage} from '../model/BloodPressure'

export const TREND_WINDOW_DAYS = 30;

export default {

    get_month_average_routines_percentage_for(date, routines) {
        let month_percentages = routines.map(r => r.month_percentage(date)).filter(p => p !== undefined);
        if (month_percentages.length === 0) {
            return undefined;
        }
        return this.get_average(month_percentages);
    },
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
    get_month_average_sleeps_for(date, sleeps) {
        let month_sleeps = this.get_month_measures_for(date, sleeps);
        let average_sleep = this.get_average_sleep(month_sleeps);
        if (!average_sleep && date.isAfter(dayjs(new Date()))) {
            return this.get_sleep_projection(sleeps);
        }
        return average_sleep;
    },
    get_month_average_moods_for(date, moods) {
        let month_moods = this.get_month_measures_for(date, moods);
        let average_mood = this.get_average_moods(month_moods);
        if (average_mood === undefined && date.isAfter(dayjs(new Date()), 'month')) {
            return this.get_mood_projection(moods, date);
        }
        return average_mood;
    },
    get_month_average_calories_for(date, calories) {
        let month_calories = this.get_month_measures_for(date, calories);
        if (month_calories.length === 0) {
            if (date.isAfter(dayjs(new Date()))) {
                return this.get_calorie_projection(calories);
            }
            return undefined;
        }
        return this.get_average(month_calories.map(calorie => calorie.calories));
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
        if (previous_second_month_average_blood_pressure === undefined) {
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
    get_sleep_trend(sleeps, referenceDate = this.get_last_date(sleeps)) {
        let current_average_sleep = this.get_current_trend_average_sleep(sleeps, referenceDate);
        let previous_average_sleep = this.get_previous_trend_average_sleep(sleeps, referenceDate);
        if (current_average_sleep === undefined || previous_average_sleep === undefined) {
            return undefined;
        }
        return new SleepTrendSummaryData(
            current_average_sleep.totalSleepDuration,
            this.round(current_average_sleep.totalSleepDuration - previous_average_sleep.totalSleepDuration),
            current_average_sleep.averageHeartRate,
            this.round(current_average_sleep.averageHeartRate - previous_average_sleep.averageHeartRate),
            current_average_sleep.averageHrv,
            this.round(current_average_sleep.averageHrv - previous_average_sleep.averageHrv)
        );
    },
    get_calorie_trend(calories, referenceDate = this.get_last_date(calories)) {
        let current_average_calorie = this.get_current_trend_average_calorie(calories, referenceDate);
        let previous_average_calorie = this.get_previous_trend_average_calorie(calories, referenceDate);
        if (current_average_calorie === undefined || previous_average_calorie === undefined) {
            return undefined;
        }
        return new CalorieTrendSummaryData(
            current_average_calorie,
            this.round(current_average_calorie - previous_average_calorie)
        );
    },
    get_sleep_projection(sleeps) {
        let current_average_sleep = this.get_current_trend_average_sleep(sleeps);
        let previous_average_sleep = this.get_previous_trend_average_sleep(sleeps);
        if (current_average_sleep === undefined || previous_average_sleep === undefined) {
            return undefined;
        }
        return new SleepSummaryData(
            this.get_projected_value(current_average_sleep.totalSleepDuration, previous_average_sleep.totalSleepDuration),
            this.get_projected_value(current_average_sleep.deepSleepDuration, previous_average_sleep.deepSleepDuration),
            this.get_projected_value(current_average_sleep.remSleepDuration, previous_average_sleep.remSleepDuration),
            this.get_projected_value(current_average_sleep.lightSleepDuration, previous_average_sleep.lightSleepDuration),
            this.get_projected_value(current_average_sleep.awakeTime, previous_average_sleep.awakeTime),
            this.get_projected_value(current_average_sleep.averageHeartRate, previous_average_sleep.averageHeartRate),
            this.get_projected_value(current_average_sleep.averageHrv, previous_average_sleep.averageHrv),
            this.get_projected_value(current_average_sleep.bedtimeStartMinutes, previous_average_sleep.bedtimeStartMinutes),
            this.get_projected_value(current_average_sleep.bedtimeEndMinutes, previous_average_sleep.bedtimeEndMinutes)
        );
    },
    get_mood_projection(moods, projectedMonth = dayjs(this.get_last_date(moods)).add(1, 'month')) {
        let current_moods = this.get_rolling_period_measures_for(this.get_last_date(moods), moods);
        if (current_moods.length < 2) {
            return undefined;
        }
        let first_date = this.get_first_date(current_moods);
        let day_offsets = current_moods.map(mood => dayjs(mood.date).diff(first_date, 'day'));
        let mood_values = current_moods.map(mood => mood.value);
        let average_day_offset = day_offsets.reduce((total, value) => total + value, 0) / day_offsets.length;
        let average_mood = mood_values.reduce((total, value) => total + value, 0) / mood_values.length;
        let trend = day_offsets.reduce((total, value, index) => total + (value - average_day_offset) * (mood_values[index] - average_mood), 0)
            / day_offsets.reduce((total, value) => total + (value - average_day_offset) ** 2, 0);
        let projected_month_middle = (
            dayjs(projectedMonth).startOf('month').diff(first_date, 'day')
            + dayjs(projectedMonth).endOf('month').diff(first_date, 'day')
        ) / 2;
        let projected_average = average_mood + trend * (projected_month_middle - average_day_offset);
        return this.round(Math.min(5, Math.max(1, projected_average)));
    },
    get_calorie_projection(calories) {
        let calorie_trend = this.get_calorie_trend(calories);
        if (calorie_trend === undefined) {
            return undefined;
        }
        return this.round(calorie_trend.calories + calorie_trend.lostCalories);
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
    get_current_trend_average_sleep: function (sleeps, referenceDate = this.get_last_date(sleeps)) {
        return this.get_average_sleep(this.get_rolling_period_measures_for(referenceDate, sleeps));
    },
    get_previous_trend_average_sleep: function (sleeps, referenceDate = this.get_last_date(sleeps)) {
        return this.get_average_sleep(this.get_rolling_period_measures_for(referenceDate, sleeps, 1));
    },
    get_current_trend_average_calorie: function (calories, referenceDate = this.get_last_date(calories)) {
        return this.get_average_calories(this.get_rolling_period_measures_for(referenceDate, calories));
    },
    get_previous_trend_average_calorie: function (calories, referenceDate = this.get_last_date(calories)) {
        return this.get_average_calories(this.get_rolling_period_measures_for(referenceDate, calories, 1));
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
    get_rolling_period_measures_for(referenceDate, measures, periodsAgo = 0) {
        let end = dayjs(referenceDate).subtract(TREND_WINDOW_DAYS * periodsAgo, 'day').endOf('day').toDate();
        let start = dayjs(end).subtract(TREND_WINDOW_DAYS - 1, 'day').startOf('day').toDate();
        return measures.filter(measure => measure.date >= start && measure.date <= end);
    },
    get_dates(measures) {
        return measures.map(m => m.date);
    },
    get_last_date_of(dates) {
        return new Date(Math.max.apply(null, dates));
    },
    get_last_date(measures) {
        return this.get_last_date_of(this.get_dates(measures));
    },
    get_first_date(measures) {
        return new Date(Math.min.apply(null, this.get_dates(measures)));
    },
    get_last_month_measures_for(measures) {
        let last_date = this.get_last_date(measures);
        let previous_month = dayjs(last_date).subtract(1, 'month').toDate();
        return measures.filter(w => w.date >= previous_month);
    },
    get_last_second_month_measures_for(measures) {
        let last_date = this.get_last_date(measures);
        let previous_month = dayjs(last_date).subtract(1, 'month').toDate();
        let previous_weight_before_last_month = this.get_last_date_of(this.get_dates(measures.filter(w => w.date < previous_month)));
        let previous_second_month = dayjs(previous_weight_before_last_month).subtract(1, 'month').toDate();
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
    get_average_sleep(month_sleeps) {
        if (month_sleeps.length === 0) {
            return undefined;
        }
        return new SleepSummaryData(
            this.get_average(month_sleeps.map(w => w.totalSleepDuration)),
            this.get_average(month_sleeps.map(w => w.deepSleepDuration)),
            this.get_average(month_sleeps.map(w => w.remSleepDuration)),
            this.get_average(month_sleeps.map(w => w.lightSleepDuration)),
            this.get_average(month_sleeps.map(w => w.awakeTime)),
            this.get_average(month_sleeps.map(w => Number(w.averageHeartRate))),
            this.get_average(month_sleeps.map(w => w.averageHrv)),
            this.get_average(month_sleeps.map(w => this.get_bedtime_start_minutes(w.bedtimeStart))),
            this.get_average(month_sleeps.map(w => this.get_bedtime_end_minutes(w.bedtimeEnd)))
        );
    },
    get_average_moods(month_moods) {
        if (month_moods.length === 0) {
            return undefined;
        }
        return this.get_average(month_moods.map(mood => mood.value));
    },
    get_average_calories(month_calories) {
        if (month_calories.length === 0) {
            return undefined;
        }
        return this.get_average(month_calories.map(calorie => calorie.calories));
    },
    get_bedtime_start_minutes(date) {
        let minutes = date.getHours() * 60 + date.getMinutes();
        if (minutes < 720) {
            return minutes + 1440;
        }
        return minutes;
    },
    get_bedtime_end_minutes(date) {
        return date.getHours() * 60 + date.getMinutes();
    },
    round(value) {
        return Math.round(value * 100) / 100;
    },
    get_projected_value(previous_month_average, previous_second_month_average) {
        return this.round(previous_month_average + previous_month_average - previous_second_month_average);
    },
    get_average(values) {
        let sum = values.reduce((w1, w2) => w1 + w2, 0);
        let average = sum / values.length;
        return this.round(average);
    },
    get_total(values) {
        return this.round(values.reduce((w1, w2) => w1 + w2, 0));
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

class SleepSummaryData {
    constructor(totalSleepDuration, deepSleepDuration, remSleepDuration, lightSleepDuration, awakeTime, averageHeartRate, averageHrv, bedtimeStartMinutes, bedtimeEndMinutes) {
        this.totalSleepDuration = totalSleepDuration;
        this.deepSleepDuration = deepSleepDuration;
        this.remSleepDuration = remSleepDuration;
        this.lightSleepDuration = lightSleepDuration;
        this.awakeTime = awakeTime;
        this.averageHeartRate = averageHeartRate;
        this.averageHrv = averageHrv;
        this.bedtimeStartMinutes = bedtimeStartMinutes;
        this.bedtimeEndMinutes = bedtimeEndMinutes;
    }
}

class SleepTrendSummaryData {
    constructor(totalSleepDuration, lostTotalSleepDuration, averageHeartRate, lostAverageHeartRate, averageHrv, lostAverageHrv) {
        this.totalSleepDuration = totalSleepDuration;
        this.lostTotalSleepDuration = lostTotalSleepDuration;
        this.averageHeartRate = averageHeartRate;
        this.lostAverageHeartRate = lostAverageHeartRate;
        this.averageHrv = averageHrv;
        this.lostAverageHrv = lostAverageHrv;
    }
}

class CalorieTrendSummaryData {
    constructor(calories, lostCalories) {
        this.calories = calories;
        this.lostCalories = lostCalories;
    }
}
