import * as fb from '../firebase';
import DailyStatus from "@/model/DailyStatus";

export default {
    get_all_by(user) {
        return fb.dailyStatusCollection
            .where('user', '==', user)
            .get().then(q => q.docs.map(doc => { return new DailyStatus(doc) }));
    },
    get_last(user) {
        return fb.dailyStatusCollection
            .where('user', '==', user)
            .orderBy('date', 'desc')
            .limit(1)
            .get().then(q => q.docs.map(doc => { return new DailyStatus(doc) }));
    },
    save(dailyStatus) {
        if (dailyStatus.id !== null) {
            return fb.dailyStatusCollection.doc(dailyStatus.id).set(dailyStatus);
        }
        return fb.dailyStatusCollection.add(dailyStatus);
    },
    build(routines, user, weight, blood_pressure) {
        let total = routines.length;
        let total_weight_routines = routines.filter(r => r.isWeight()).length;
        let total_blood_pressures_routines = routines.filter(r => r.isBloodPressure()).length;
        let total_flexibility_routines = routines.filter(r => r.isFlexibility()).length;
        let total_mind_routines = routines.filter(r => r.isMind()).length;
        let done = routines.filter(r => r.isDoneToday());
        let routines_done = done.length;
        let weight_done = done.filter(r => r.isWeight()).length;
        let blood_pressure_done = done.filter(r => r.isBloodPressure()).length;
        let flexibility_done = done.filter(r => r.isFlexibility()).length;
        let mind_done = done.filter(r => r.isMind()).length;
        let routines_percentage = Math.round(routines_done * 100 / total * 100) / 100;
        let weight_percentage = Math.round(weight_done * 100 / total_weight_routines * 100) / 100;
        let blood_pressure_percentage = Math.round(blood_pressure_done * 100 / total_blood_pressures_routines * 100) / 100;
        let flexibility_percentage = Math.round(flexibility_done * 100 / total_flexibility_routines * 100) / 100;
        let mind_percentage = Math.round(mind_done * 100 / total_mind_routines * 100) / 100;
        let routines_score = routines.map(r => r.score()).reduce((r1, r2) => r1 + r2, 0);
        let weight_score = routines.filter(r => r.isWeight()).map(r => r.score()).reduce((r1, r2) => r1 + r2, 0);
        let blood_pressure_score = routines.filter(r => r.isBloodPressure()).map(r => r.score()).reduce((r1, r2) => r1 + r2, 0);
        let flexibility_score = routines.filter(r => r.isFlexibility()).map(r => r.score()).reduce((r1, r2) => r1 + r2, 0);
        let mind_score = routines.filter(r => r.isMind()).map(r => r.score()).reduce((r1, r2) => r1 + r2, 0);
        let routines_status = Math.round(routines_score * 100 / total * 100) / 100;
        let weight_status = Math.round(weight_score * 100 / total_weight_routines * 100) / 100;
        let blood_pressure_status = Math.round(blood_pressure_score * 100 / total_blood_pressures_routines * 100) / 100;
        let flexibility_status = Math.round(flexibility_score * 100 / total_flexibility_routines * 100) / 100;
        let mind_status = Math.round(mind_score * 100 / total_mind_routines * 100) / 100;
        return createDailyStatus(new Date(), user, weight, blood_pressure,
            total, total_weight_routines, total_blood_pressures_routines, total_flexibility_routines, total_mind_routines,
            routines_done, weight_done, blood_pressure_done, flexibility_done, mind_done,
            routines_percentage, weight_percentage, blood_pressure_percentage, flexibility_percentage, mind_percentage,
            routines_score, weight_score, blood_pressure_score, flexibility_score, mind_score,
            routines_status, weight_status, blood_pressure_status, flexibility_status, mind_status);

        function createDailyStatus(date, user, weight, blood_pressure,
                                   total_routines, total_weight_routines, total_blood_pressure_routines, total_flexibility_routines, total_mind_routines,
                                   routines_done, weight_done, blood_pressure_done, flexibility_done, mind_done,
                                   routines_percentage, weight_percentage, blood_pressure_percentage, flexibility_percentage, mind_percentage,
                                   routines_score, weight_score, blood_pressure_score, flexibility_score, mind_score,
                                   routines_status, weight_status, blood_pressure_status, flexibility_status, mind_status) {
            let dailyStatus = {};
            dailyStatus.date = date;
            dailyStatus.weight = weight;
            dailyStatus.blood_pressure = blood_pressure;
            dailyStatus.total_routines = total_routines;
            dailyStatus.total_weight_routines = total_weight_routines;
            dailyStatus.total_blood_pressure_routines = total_blood_pressure_routines;
            dailyStatus.total_flexibility_routines = total_flexibility_routines;
            dailyStatus.total_mind_routines = total_mind_routines;
            dailyStatus.routines_done = routines_done;
            dailyStatus.weight_done = weight_done;
            dailyStatus.blood_pressure_done = blood_pressure_done;
            dailyStatus.flexibility_done = flexibility_done;
            dailyStatus.mind_done = mind_done;
            dailyStatus.routines_percentage = routines_percentage;
            dailyStatus.weight_percentage = weight_percentage;
            dailyStatus.blood_pressure_percentage = blood_pressure_percentage;
            dailyStatus.flexibility_percentage = flexibility_percentage;
            dailyStatus.mind_percentage = mind_percentage;
            dailyStatus.routines_score = routines_score;
            dailyStatus.weight_score = weight_score;
            dailyStatus.blood_pressure_score = blood_pressure_score;
            dailyStatus.flexibility_score = flexibility_score;
            dailyStatus.mind_score = mind_score;
            dailyStatus.routines_status = routines_status;
            dailyStatus.weight_status = weight_status;
            dailyStatus.blood_pressure_status = blood_pressure_status;
            dailyStatus.flexibility_status = flexibility_status;
            dailyStatus.mind_status = mind_status;
            dailyStatus.user = user;
            return dailyStatus;
        }

    }
}

