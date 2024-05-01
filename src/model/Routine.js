import dayjs from 'dayjs';

const isYesterday = require('dayjs/plugin/isYesterday');
dayjs.extend(isYesterday)
const isToday = require('dayjs/plugin/isToday');
dayjs.extend(isToday)

export default class Routine {

    constructor(fbDoc) {
        if (fbDoc === undefined) {
            return;
        }
        let fbData = fbDoc.data();
        this.id = fbDoc.id;
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
        this.times = fbData.times.map(t => t.toDate());
        this.types = fbData.types;
    }

    plusTimes() {
        this.times.push(new Date());
        if (!dayjs(this.last_time_date).isYesterday() && !dayjs(this.last_time_date).isToday()) {
            this.current_strike = 0;
        }
        this.current_strike++;
        if (this.current_strike > this.best_strike) {
            this.best_strike = this.current_strike;
        }
        this.last_time_date = new Date();
        return this.toObject()
    }

    status() {
        return this.monthly_percentage(new Date());
    }

    typeValues() {
        if (this.types) {
            return this.types.map(t => t.name);
        }
        return [];
    }

    monthly_percentage(from) {
        let days_last_month = this.days_last_month(from);
        if (days_last_month === 0) {
            return 0;
        }
        let percentage_status = this.times_last_month(from) * 100 / days_last_month
        let effective_percentage_status = percentage_status > 100 ? 100 : percentage_status
        return Math.round(effective_percentage_status * 100) / 100;
    }

    month_percentage(date) {
        let month_start_date = dayjs(this.start_date).startOf('month').toDate();
        let month_start = dayjs(date).startOf('month').toDate();
        if (month_start < month_start_date) {
            return undefined;
        }
        let month_end = dayjs(date).endOf('month').toDate();
        let month_days = this.month_days(date);
        let times = this.times.filter(t => t > month_start && t < month_end).length;
        return Math.round(times * 100 / month_days * 100) / 100;
    }

    isDone(date) {
        return this.times.filter(t => dayjs(date).isSame(t, 'day')).length === 1;
    }

    isDoneToday() {
        return this.isDone(new Date())
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

    score() {
        let status = this.status();
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

    strike() {
        let last_time_date = dayjs(this.last_time_date);
        if (!last_time_date.isToday() && !dayjs(this.last_time_date).isYesterday()) {
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

    yesterday() {
        return dayjs(new Date()).subtract(1, 'day').toDate();
    }

    fails() {
        let from = this.yesterday();
        let fails = this.days_last_month(from) - this.times_last_month(from);
        return fails < 0 ? 0 : fails;
    }

    isTodayAlreadyDone() {
        return this.last_time_date && dayjs(this.last_time_date).isToday();
    }

    isDisabled() {
        return this.isTodayAlreadyDone()
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
        return this.types.find(t => t.name === type.name);
    }

    toObject() {
        let routine = {}
        routine.id = this.id;
        routine.user = this.user;
        routine.start_date = this.start_date;
        routine.last_time_date = this.last_time_date;
        routine.name = this.name;
        routine.times = this.times;
        routine.current_strike = this.current_strike;
        routine.best_strike = this.best_strike;
        routine.types = this.types;
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

