import * as fb from '../firebase'

export default {
    get_all() {
        return fb.weightCollection.orderBy('date', 'desc').get().then(q => q.docs.map(doc => { return new Weight(doc) }));
    },
    get_last() {
        return fb.weightCollection.orderBy('date', 'desc').limit(1).get().then(q => q.docs.map(doc => { return new Weight(doc) })).then(q => { return q[0] });
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

class Weight {
    constructor(fbDoc) {
        this.id = fbDoc.id;
        this.date = fbDoc.data().date;
        this.weight = Math.round(parseFloat(fbDoc.data().weight) * 100) / 100;
        this.fat = Math.round(parseFloat(fbDoc.data().fat) * 100) / 100;
        this.fat_percentage = Math.round(parseFloat(fbDoc.data().fat_percentage) * 100) / 100;
        this.muscle = Math.round(parseFloat(fbDoc.data().muscle) * 100) / 100;
        this.muscle_percentage = Math.round(parseFloat(fbDoc.data().muscle_percentage) * 100) / 100;
    }
}