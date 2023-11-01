import * as firebase from 'firebase/app';
import 'firebase/firestore';
import 'firebase/storage';

const firebaseConfig = {
    apiKey: "AIzaSyCEXUZV-9raYZF_0ptfgecWPbtz0ECq11A",
    authDomain: "jllado-weight-control.firebaseapp.com",
    projectId: "jllado-weight-control",
    storageBucket: "jllado-weight-control.appspot.com",
    messagingSenderId: "594571928091",
    appId: "1:594571928091:web:353d779099949197cd0ebe"
};

firebase.initializeApp(firebaseConfig);

// utils
const db = firebase.firestore();
const storage = firebase.storage();

// collection references
const weightCollection = db.collection('weights');
const bloodPressureCollection = db.collection('blood_pressures');
const habitCollection = db.collection('habits');
const routineCollection = db.collection('routines');

// export utils/refs
export {
    storage,
    db,
    weightCollection,
    bloodPressureCollection,
    habitCollection,
    routineCollection
};
