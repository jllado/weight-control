import * as fb from '../firebase';
import Routine from '../model/Routine'

export default {
    get_all_by(user) {
        return fb.routineCollection
            .where('user', '==', user)
            .get().then(q => q.docs
                .map(doc => { return new Routine(doc) })
                .sort((r1, r2) => r2.strike() - r1.strike()));
    },
    save(routine) {
        if (routine.id !== null) {
            return fb.routineCollection.doc(routine.id).set(routine);
        }
        return fb.routineCollection.add(routine);
    },
    delete(routine) {
        return fb.routineCollection.doc(routine.id).delete();
    }
}

