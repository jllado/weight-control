import * as fb from '../firebase';
import BloodPressure from '../model/BloodPressure'

export default {
    get_all_by(user) {
        return fb.bloodPressureCollection
            .where('user', '==', user)
            .orderBy('date', 'desc')
            .get().then(q => q.docs.map(doc => {
            return new BloodPressure(doc)
        }));
    },
    get_last(user) {
        return fb.bloodPressureCollection
            .where('user', '==', user)
            .orderBy('date', 'desc')
            .limit(1)
            .get().then(q => q.docs.map(doc => { return new BloodPressure(doc) })).then(q => { return q[0] });
    },
    get_previous(date, user) {
        return fb.bloodPressureCollection
            .where('user', '==', user)
            .where('date', '<', date)
            .orderBy('date', 'desc')
            .limit(1)
            .get().then(q => q.docs.map(doc => { return new BloodPressure(doc) })).then(q => { return q[0] });
    },
    get_all() {
        return fb.bloodPressureCollection
            .orderBy('date', 'asc')
            .get().then(q => q.docs.map(doc => {
            return new BloodPressure(doc)
        }));
    },
    save(blood_pressure) {
        if (blood_pressure.id) {
            return fb.bloodPressureCollection.doc(blood_pressure.id).set(blood_pressure);
        }
        return fb.bloodPressureCollection.add(blood_pressure);
    },
    delete(blood_pressure) {
        return fb.bloodPressureCollection.doc(blood_pressure.id).delete();
    },
    get_month_average_blood_pressure_for(date, blood_pressures) {
        let month_blood_pressures = get_month_blood_pressures_for(date, blood_pressures);
        return get_average_blood_pressure(month_blood_pressures) || get_previous_blood_pressure(date, blood_pressures);

        function get_month_blood_pressures_for(date, blood_pressures) {
            let start = date.startOf('month').toDate();
            let end = date.endOf('month').toDate();
            return blood_pressures.filter(w => w.date >= start && w.date <= end);
        }
        function get_previous_blood_pressure(date, blood_pressures) {
            let previous_blood_pressure = blood_pressures.find(w => w.date < date.toDate());
            if (!previous_blood_pressure) {
                return new BloodPressureGraphData(0, 0, 0, 0, 0, 0);
            }
            return new BloodPressureGraphData(previous_blood_pressure.upper, previous_blood_pressure.lost_upper, previous_blood_pressure.lower, previous_blood_pressure.lost_lower);
        }

        function get_average_blood_pressure(month_blood_pressures) {
            if (month_blood_pressures.length === 0) {
                return undefined;
            }
            let average_upper = get_average(month_blood_pressures.map(w => w.upper));
            let average_lost_upper = get_average(month_blood_pressures.map(w => w.lost_upper));
            let average_lower = get_average(month_blood_pressures.map(w => w.lower));
            let average_lost_lower = get_average(month_blood_pressures.map(w => w.lost_lower));
            return new BloodPressureGraphData(average_upper, average_lost_upper, average_lower, average_lost_lower);
        }

        function get_average(values) {
            let sum = values.reduce((w1, w2) => w1 + w2, 0);
            return sum / values.length;
        }
    }
}

class BloodPressureGraphData {
    constructor(upper, lost_upper, lower, lost_lower) {
        this.upper = upper;
        this.lost_upper = lost_upper;
        this.lower = lower;
        this.lost_lower = lost_lower;
    }
}