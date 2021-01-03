import * as fb from '../firebase';
import dayjs from 'dayjs';

export default {
    get_all(user) {
        return fb.weightCollection
            .where('user', '==', user)
            .orderBy('date', 'desc')
            .get().then(q => q.docs.map(doc => {
            return new Weight(doc)
        }));
    },
    save(weight) {
        if (weight.id) {
            return fb.weightCollection.doc(weight.id).set(weight);
        }
        return fb.weightCollection.add(weight);
    },
    delete(weight) {
        return fb.weightCollection.doc(weight.id).delete();
    },
    get_month_average_weight_for(date, weights) {
        let monthWeights = get_month_weights_for(date, weights);
        return get_average_weight(monthWeights) || get_previous_weight(date, weights);

        function get_month_weights_for(date, weights) {
            let start = date.startOf('month').toDate();
            let end = date.endOf('month').toDate();
            return weights.filter(w => w.date >= start && w.date <= end);
        }
        function get_previous_weight(date, weights) {
            let previousWeight = weights.find(w => w.date < date.toDate());
            if (!previousWeight) {
                return 0;
            }
            return previousWeight.weight;
        }
        function get_average_weight(monthWeights) {
            if (monthWeights.length === 0) {
                return undefined;
            }
            let sum = monthWeights.map(w => w.weight).reduce((w1, w2) => w1 + w2, 0);
            let number = sum / monthWeights.length;
            return number;
        }
    }
}

class Weight {
    constructor(fbDoc) {
        this.id = fbDoc.id;
        this.user = fbDoc.user;
        this.date = fbDoc.data().date.toDate();
        this.dateFormat= dayjs(this.date).format('DD/MM/YYYY')
        this.weight = Math.round(fbDoc.data().weight * 100) / 100;
        this.fat = Math.round(fbDoc.data().fat * 100) / 100;
        this.fat_percentage = Math.round(fbDoc.data().fat_percentage * 100) / 100;
        this.muscle = Math.round(fbDoc.data().muscle * 100) / 100;
        this.muscle_percentage = Math.round(fbDoc.data().muscle_percentage * 100) / 100;
    }
}