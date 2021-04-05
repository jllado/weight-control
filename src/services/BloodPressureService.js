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
    save(blood_pressure) {
        if (blood_pressure.id) {
            return fb.bloodPressureCollection.doc(blood_pressure.id).set(blood_pressure);
        }
        return fb.bloodPressureCollection.add(blood_pressure);
    },
    delete(blood_pressure) {
        return fb.bloodPressureCollection.doc(blood_pressure.id).delete();
    }
}
