import dayjs from 'dayjs';

const isYesterday = require('dayjs/plugin/isYesterday');
dayjs.extend(isYesterday)
const isToday = require('dayjs/plugin/isToday');
dayjs.extend(isToday)

export default class Habit {

    constructor(fbDoc) {
        if (fbDoc === undefined) {
            return;
        }
        let fbData = fbDoc.data();
        this.id = fbDoc.id;
        this.user = fbData.user;
        this.start_date = fbData.start_date.toDate();
        this.start_date_format= dayjs(this.start_date).format('DD/MM/YYYY')
        this.duration = fbData.duration;
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
    }

    plusTimes() {
        this.times++;
        if (this.last_time_date && dayjs(this.last_time_date).isYesterday()) {
            this.current_strike++;
        } else {
            this.current_strike = 1;
        }
        if (this.current_strike > this.best_strike) {
            this.best_strike = this.current_strike;
        }
        this.last_time_date = new Date();
        return this.toObject()
    }

    isTodayAlreadyDone() {
        return this.last_time_date && dayjs(this.last_time_date).isToday();
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
        return habit;
    }

}
