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
        let from = new Date();
        let days_last_month = this.days_last_month(from);
        if (days_last_month === 0) {
            return 0;
        }
        let percentage_status = this.times_last_month(from) * 100 / days_last_month
        let effective_percentage_status = percentage_status > 100 ? 100 : percentage_status
        return Math.round(effective_percentage_status * 100) / 100;
    }

    score() {
        let status = this.status();
        if (status >= 60) {
            return 1;
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
        return routine;
    }

}

