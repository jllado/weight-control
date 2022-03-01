import * as fb from '../firebase';
import Habit from '../model/Habit'

export default {
    get_all_by(user) {
        return fb.habitCollection
            .where('user', '==', user)
            .get().then(q => q.docs.map(doc => { return new Habit(doc) }));
    },
    save(habit) {
        if (habit.id !== null) {
            return fb.habitCollection.doc(habit.id).set(habit);
        }
        return fb.habitCollection.add(habit);
    },
    delete(habit) {
        return fb.habitCollection.doc(habit.id).delete();
    }
}

