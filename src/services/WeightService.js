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
                return new WeightGraphData(0, 0, 0, 0, 0, 0);
            }
            return new WeightGraphData(previousWeight.weight, previousWeight.lost_weight, previousWeight.fat_percentage, previousWeight.lost_fat, previousWeight.muscle_percentage, previousWeight.lost_muscle);
        }

        function get_average_weight(monthWeights) {
            if (monthWeights.length === 0) {
                return undefined;
            }
            let average_weight = get_average(monthWeights.map(w => w.weight));
            let average_lost_weight = get_average(monthWeights.map(w => w.lost_weight));
            let average_fat = get_average(monthWeights.map(w => w.fat_percentage));
            let average_lost_fat = get_average(monthWeights.map(w => w.lost_fat));
            let average_muscle = get_average(monthWeights.map(w => w.muscle_percentage));
            let average_lost_muscle = get_average(monthWeights.map(w => w.lost_muscle));
            return new WeightGraphData(average_weight, average_lost_weight, average_fat, average_lost_fat, average_muscle, average_lost_muscle);
        }

        function get_average(values) {
            let sum = values.reduce((w1, w2) => w1 + w2, 0);
            return sum / values.length;
        }
    }
}

class WeightGraphData {
    constructor(weight, lost_weight, fat, lost_fat, muscle, lost_muscle) {
        this.weight = weight;
        this.lost_weight = lost_weight;
        this.fat = fat;
        this.lost_fat = lost_fat;
        this.muscle = muscle;
        this.lost_muscle = lost_muscle;
    }
}