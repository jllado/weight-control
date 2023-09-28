import * as fb from '../firebase';
import Routine from '../model/Routine'

export default {
    get_all_by(user) {
        return fb.routineCollection
            .where('user', '==', user)
            .orderBy('current_strike', 'desc')
            .get().then(q => q.docs.map(doc => { return new Routine(doc) }));
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

