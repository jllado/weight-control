import * as fb from '../firebase';
import Weight from '../model/Weight'

export default {
    get_all_by(user) {
        return fb.weightCollection
            .where('user', '==', user)
            .orderBy('date', 'desc')
            .get().then(q => q.docs.map(doc => {
            return new Weight(doc)
        }));
    },
    get_last(user) {
        return fb.weightCollection
            .where('user', '==', user)
            .orderBy('date', 'desc')
            .limit(1)
            .get().then(q => q.docs.map(doc => { return new Weight(doc) })).then(q => { return q[0] });
    },
    get_previous(date, user) {
        return fb.weightCollection
            .where('user', '==', user)
            .where('date', '<', date)
            .orderBy('date', 'desc')
            .limit(1)
            .get().then(q => q.docs.map(doc => { return new Weight(doc) })).then(q => { return q[0] });
    },
    save(weight) {
        if (weight.id) {
            return fb.weightCollection.doc(weight.id).set(weight);
        }
        return fb.weightCollection.add(weight);
    },
    delete(weight) {
        return fb.weightCollection.doc(weight.id).delete();
    }
}

