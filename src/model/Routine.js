import dayjs from 'dayjs';

import isYesterday from 'dayjs/plugin/isYesterday';
dayjs.extend(isYesterday)
import isToday from 'dayjs/plugin/isToday';
dayjs.extend(isToday)

export default class Routine {

    constructor(source) {
        if (source === undefined) {
            return;
        }
        if (source.data) {
            let fbData = source.data();
            this.id = source.id;
            this.cadence = fbData.cadence || 'DAILY';
            this.user = fbData.user;
            this.start_date = fbData.start_date.toDate();
            this.start_date_format = dayjs(this.start_date).format('DD/MM/YYYY')
            if (fbData.last_time_date) {
                this.last_time_date = fbData.last_time_date.toDate();
                this.last_time_date_format = dayjs(this.last_time_date).format('DD/MM/YYYY')
            } else {
                this.last_time_date = null;
                this.last_time_date_format = null
            }
            this.current_strike = fbData.current_strike;
            this.best_strike = fbData.best_strike;
            this.name = fbData.name;
            this.reminders = fbData.reminder_time ? [{id: null, time: fbData.reminder_time}] : [];
            this.times = fbData.times.map(t => t.toDate());
            this.types = normalizeTypes(fbData.types);
            this.automatic_trigger = fbData.automatic_trigger || RoutineAutomaticTrigger.NONE;
            this.automatic_completion_enabled = fbData.automatic_completion_enabled;
            return;
        }
        this.id = source.id;
        this.cadence = source.cadence || 'DAILY';
        this.user = source.user;
        this.start_date = new Date(source.start_date);
        this.start_date_format = dayjs(this.start_date).format('DD/MM/YYYY')
        if (source.last_time_date) {
            this.last_time_date = new Date(source.last_time_date);
            this.last_time_date_format = dayjs(this.last_time_date).format('DD/MM/YYYY')
        } else {
            this.last_time_date = null;
            this.last_time_date_format = null
        }
        this.current_strike = source.current_strike;
        this.best_strike = source.best_strike;
        this.personal_records_enabled = source.personal_records_enabled;
        this.name = source.name;
        this.reminders = (source.reminders || []).map(reminder => ({id: reminder.id, time: reminder.time, day: reminder.day}));
        this.times = (source.times || []).map(t => new Date(t));
        this.types = normalizeTypes(source.types);
        this.automatic_trigger = source.automatic_trigger || RoutineAutomaticTrigger.NONE;
        this.automatic_completion_enabled = source.automatic_completion_enabled;
    }

    plusTimes(date) {
        this.times.push(date);
        if (dayjs(date).diff(this.last_time_date, 'day') > 1) {
            this.current_strike = 0;
        }
        this.current_strike++;
        if (this.current_strike > this.best_strike) {
            this.best_strike = this.current_strike;
        }
        this.last_time_date = date;
        return this.toObject()
    }

    status(date) {
        return this.monthly_percentage(date || new Date());
    }

    typeValues() {
        if (this.types) {
            return this.types.map((type) => type.name).join(', ');
        }
        return '';
    }

    monthly_percentage(from) {
        if (this.isWeekly()) return this.weeklyStats(from).percentage;
        let days_last_month = this.days_last_month(from);
        if (days_last_month === 0) {
            return 0;
        }
        let percentage_status = this.times_last_month(from) * 100 / days_last_month
        let effective_percentage_status = percentage_status > 100 ? 100 : percentage_status
        return Math.round(effective_percentage_status * 100) / 100;
    }

    month_percentage(date, through = new Date()) {
        if (this.isWeekly()) {
            const month = madridDay(date);
            const periods = this.weeklyPeriods(month.startOf('month'), month.endOf('month'), through);
            if (periods.length === 0) return undefined;
            const completed = this.completedWeeks(through);
            return Math.round(periods.filter(period => completed.has(period)).length * 10000 / periods.length) / 100;
        }
        let month_start_date = dayjs(this.start_date).startOf('month').toDate();
        let month_start = dayjs(date).startOf('month').toDate();
        if (month_start < month_start_date) {
            return undefined;
        }
        let month_end = dayjs(date).endOf('month').toDate();
        let month_days = this.month_days(date);
        let times = this.times.filter(t => t > month_start && t < month_end).length;
        return Math.round(Math.min(times * 100 / month_days, 100) * 100) / 100;
    }

    isDone(date) {
        if (this.isWeekly()) return this.completedWeeks(date).has(weekStart(madridDay(date)).format('YYYY-MM-DD'));
        return this.times.filter(t => dayjs(date).isSame(t, 'day')).length === 1;
    }

    isWeekly() {
        return this.cadence === 'WEEKLY';
    }

    completedWeeks(through = new Date()) {
        const cutoff = madridDay(through).format('YYYY-MM-DD');
        return new Set(this.times.filter(time => madridDay(time).format('YYYY-MM-DD') <= cutoff)
            .map(time => weekStart(madridDay(time)).format('YYYY-MM-DD')));
    }

    weeklyPeriods(from, to, through) {
        const start = weekStart(madridDay(this.start_date));
        let period = weekStart(from);
        if (period.isBefore(start, 'day')) period = start;
        const cutoff = madridDay(through);
        const periods = [];
        while (!period.add(6, 'day').isAfter(to, 'day') && period.add(6, 'day').isBefore(cutoff, 'day')) {
            if (!period.add(6, 'day').isBefore(from, 'day')) periods.push(period.format('YYYY-MM-DD'));
            period = period.add(7, 'day');
        }
        return periods;
    }

    weeklyStats(through) {
        const end = madridDay(through);
        const periods = this.weeklyPeriods(end.subtract(30, 'day'), end, through);
        const weeks = this.completedWeeks(through);
        const completed = periods.filter(period => weeks.has(period)).length;
        return {completed, opportunities: periods.length, fails: periods.length - completed,
            percentage: periods.length ? Math.round(completed * 10000 / periods.length) / 100 : null};
    }

    bestStreak(through = new Date()) {
        if (!this.isWeekly()) return this.best_strike;
        let best = 0;
        let streak = 0;
        let previous;
        for (const week of [...this.completedWeeks(through)].sort()) {
            streak = previous && dayjs(week).diff(dayjs(previous), 'day') === 7 ? streak + 1 : 1;
            best = Math.max(best, streak);
            previous = week;
        }
        return best;
    }

    completionCount(through = new Date()) {
        return this.isWeekly() ? this.completedWeeks(through).size : this.times.length;
    }

    month_days(date) {
        function isCurrentMonth(date) {
            return dayjs(date).isSame(dayjs(new Date()), 'month')
        }

        if (isCurrentMonth(date)) {
            let month_start = dayjs(date).startOf('month').toDate();
            return dayjs(date).diff(month_start, 'day');
        }
        return dayjs(date).daysInMonth();
    }

    score(date) {
        let status = this.status(date);
        if (status >= 80) {
            return 1;
        }
        if (status >= 60) {
            return 0.75;
        }
        if (status >= 50) {
            return 0.5
        }
        if (status >= 40) {
            return 0.25
        }
        return 0;
    }

    strike(from) {
        let referenceDate = from || new Date();
        if (this.isWeekly()) {
            const completed = this.completedWeeks(referenceDate);
            let week = weekStart(madridDay(referenceDate));
            if (!completed.has(week.format('YYYY-MM-DD'))) week = week.subtract(7, 'day');
            let streak = 0;
            while (completed.has(week.format('YYYY-MM-DD'))) {
                streak++;
                week = week.subtract(7, 'day');
            }
            return streak;
        }
        let last_time_date = dayjs(this.last_time_date);
        if (!last_time_date.isSame(referenceDate, 'day') && !last_time_date.isSame(dayjs(referenceDate).subtract(1, 'day'), 'day')) {
            return 0;
        }
        return this.current_strike;
    }

    /*
     * Returns the number of days in last 31 from the start date. Could be less than 31.
     */
    days_last_month(from) {
        let days_last_month = dayjs(from).add(1, 'day').diff(dayjs(this.start_date).toDate(), 'day');
        let effective_days_last_month = days_last_month > 31 ? 31 : days_last_month;
        return effective_days_last_month;
    }

    times_last_month(from) {
        let month_ago = dayjs(from).subtract(31, 'day').toDate();
        let times_last_month = this.times.filter(t => t > month_ago && t < from).length;
        return times_last_month;
    }

    yesterday(from) {
        return dayjs(from || new Date()).subtract(1, 'day').toDate();
    }

    fails(from) {
        if (this.isWeekly()) return this.weeklyStats(from || new Date()).fails;
        let effectiveFrom = this.yesterday(from);
        let fails = this.days_last_month(effectiveFrom) - this.times_last_month(effectiveFrom);
        return fails < 0 ? 0 : fails;
    }

    isAlreadyDone(date) {
        return this.isDone(date);
    }

    isDisabled(date) {
        if (this.isWeekly() && madridDay(date).isBefore(madridDay(this.start_date), 'day')) return true;
        return this.isAlreadyDone(date)
    }

    isWeight() {
        return this.isType(RoutineType.WEIGHT);
    }

    isBloodPressure() {
        return this.isType(RoutineType.BLOOD_PRESSURE);
    }

    isFlexibility() {
        return this.isType(RoutineType.FLEXIBILITY);
    }

    isMind() {
        return this.isType(RoutineType.MIND);
    }

    isType(type) {
        return this.types.find((candidate) => candidate.name === type.name);
    }

    typeNames() {
        return this.types.map((type) => type.name);
    }

    toObject() {
        let routine = {}
        routine.id = this.id;
        routine.cadence = this.cadence;
        routine.user = this.user;
        routine.start_date = this.start_date;
        routine.last_time_date = this.last_time_date;
        routine.name = this.name;
        routine.reminders = this.reminders;
        routine.times = this.times;
        routine.current_strike = this.current_strike;
        routine.best_strike = this.best_strike;
        routine.types = this.types;
        routine.automatic_trigger = this.automatic_trigger;
        routine.automatic_completion_enabled = this.automatic_completion_enabled;
        return routine;
    }

}

export const RoutineType = {
    WEIGHT: {
        name: "WEIGHT"
    },
    BLOOD_PRESSURE: {
        name: "BLOOD_PRESSURE"
    },
    FLEXIBILITY: {
        name: "FLEXIBILITY"
    },
    MIND: {
        name: "MIND"
    }
};

export const RoutineAutomaticTrigger = {
    NONE: 'NONE',
    FAST_OVER_12_HOURS: 'FAST_OVER_12_HOURS',
    FRUIT_MEAL: 'FRUIT_MEAL',
    CARDIO_WORKOUT: 'CARDIO_WORKOUT',
    STRENGTH_WORKOUT: 'STRENGTH_WORKOUT',
    STRETCHING_WORKOUT: 'STRETCHING_WORKOUT',
    MCGILL_BIG_THREE: 'MCGILL_BIG_THREE',
    SLEEP_BED_AT_LEAST_7_HOURS: 'SLEEP_BED_AT_LEAST_7_HOURS',
    KIWI_MEAL: 'KIWI_MEAL',
    WAKE_BEFORE_7: 'WAKE_BEFORE_7',
    NIGHT_STRETCHING_WORKOUT: 'NIGHT_STRETCHING_WORKOUT',
    SITTING_RISING_WORKOUT: 'SITTING_RISING_WORKOUT',
    OUTDOOR_WALK_OVER_40_MINUTES: 'OUTDOOR_WALK_OVER_40_MINUTES',
    WEIGHT_MEASUREMENT: 'WEIGHT_MEASUREMENT',
    BLOOD_PRESSURE_MEASUREMENT: 'BLOOD_PRESSURE_MEASUREMENT'
};

export const RoutineAutomaticTriggerDescriptions = {
    NONE: null,
    FAST_OVER_12_HOURS: 'a completed fast over 12 hours',
    FRUIT_MEAL: 'a meal with fruit',
    CARDIO_WORKOUT: 'a workout with cardio',
    STRENGTH_WORKOUT: 'a workout with strength exercise',
    STRETCHING_WORKOUT: 'a workout with stretching',
    MCGILL_BIG_THREE: 'a workout with McGill Big Three',
    SLEEP_BED_AT_LEAST_7_HOURS: 'a sleep entry with at least 7 hours in bed',
    KIWI_MEAL: 'a meal containing Kiwi',
    WAKE_BEFORE_7: 'a sleep entry ending before 07:00 (Europe/Madrid)',
    NIGHT_STRETCHING_WORKOUT: 'a workout with stretching and a recorded start after 19:00 (Europe/Madrid)',
    SITTING_RISING_WORKOUT: 'a workout with an exercise named Sitting-Rising',
    OUTDOOR_WALK_OVER_40_MINUTES: 'a saved workout with more than 40 minutes of outdoor walking',
    WEIGHT_MEASUREMENT: 'a weight measurement',
    BLOOD_PRESSURE_MEASUREMENT: 'a blood pressure measurement'
};

const madridDateFormatter = new Intl.DateTimeFormat('en-CA', {timeZone: 'Europe/Madrid', year: 'numeric', month: '2-digit', day: '2-digit'});

function madridDay(value) {
    return dayjs(madridDateFormatter.format(new Date(value)));
}

function weekStart(date) {
    return date.subtract((date.day() + 1) % 7, 'day').startOf('day');
}

const ROUTINE_TYPES_BY_NAME = {
    WEIGHT: RoutineType.WEIGHT,
    BLOOD_PRESSURE: RoutineType.BLOOD_PRESSURE,
    FLEXIBILITY: RoutineType.FLEXIBILITY,
    MIND: RoutineType.MIND
};

function normalizeTypes(types) {
    return (types || []).map((type) => {
        if (typeof type === 'string') {
            return ROUTINE_TYPES_BY_NAME[type];
        }
        return ROUTINE_TYPES_BY_NAME[type.name];
    });
}
