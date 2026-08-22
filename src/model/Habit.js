import dayjs from 'dayjs';

const isYesterday = require('dayjs/plugin/isYesterday');
dayjs.extend(isYesterday)
const isToday = require('dayjs/plugin/isToday');
dayjs.extend(isToday)

export default class Habit {

    constructor(source) {
        if (source === undefined) {
            return;
        }
        if (source.data) {
            let fbData = source.data();
            this.id = source.id;
            this.user = fbData.user;
            this.start_date = fbData.start_date.toDate();
            this.start_date_format= dayjs(this.start_date).format('DD/MM/YYYY')
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
            this.times = fbData.times;
            return;
        }
        this.id = source.id;
        this.user = source.user;
        this.start_date = new Date(source.start_date);
        this.start_date_format= dayjs(this.start_date).format('DD/MM/YYYY')
        this.duration = source.duration;
        if (source.last_time_date) {
            this.last_time_date = new Date(source.last_time_date);
            this.last_time_date_format = dayjs(this.last_time_date).format('DD/MM/YYYY')
        } else {
            this.last_time_date = null;
            this.last_time_date_format = null
        }
        this.current_strike = source.current_strike;
        this.best_strike = source.best_strike;
        this.name = source.name;
        this.times = source.times;
        this.checkins = source.checkins || [];
        this.legacy_baseline = source.legacy_baseline || null;
    }

    plusTimes(date) {
        this.times++;
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

    getStatus() {
        if (this.isPending()) {
            return "PENDING";
        }
        return "DONE";
    }

    isPending() {
        return this.current_strike < this.duration;
    }

    isAlreadyDone(date) {
        return this.last_time_date && dayjs(this.last_time_date).isSame(date);
    }

    isDisabled(date) {
        return this.isAlreadyDone(date)
    }

    print_strike() {
        return this.current_strike + "/" + this.duration;
    }

    daily_percentage() {
        return Math.round(this.current_strike * 100 / this.duration * 100) / 100
    }

    toObject() {
        let habit = {}
        habit.id = this.id;
        habit.user = this.user;
        habit.start_date = this.start_date;
        habit.duration = this.duration;
        habit.last_time_date = this.last_time_date;
        habit.name = this.name;
        habit.times = this.times;
        habit.current_strike = this.current_strike;
        habit.best_strike = this.best_strike;
        habit.checkins = this.checkins;
        habit.legacy_baseline = this.legacy_baseline;
        return habit;
    }

}
