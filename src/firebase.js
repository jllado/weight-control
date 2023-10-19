import * as firebase from 'firebase/app';
import 'firebase/firestore';
import 'firebase/auth';

// firebase init - add your own config here
const firebaseConfig = {
    apiKey: "AIzaSyBncBq4oJoS8BADpsPHmX7CYJbRdHbmesY",
    authDomain: "weight-control-jllado.firebaseapp.com",
    projectId: "weight-control-jllado",
    storageBucket: "weight-control-jllado.appspot.com",
    messagingSenderId: "674524030999",
    appId: "1:674524030999:web:5a995488cb7405cf77d452"
};

firebase.initializeApp(firebaseConfig);

// utils
const db = firebase.firestore();

// collection references
const weightCollection = db.collection('weights');
const bloodPressureCollection = db.collection('blood_pressures');
const habitCollection = db.collection('habits');
const routineCollection = db.collection('routine');

// export utils/refs
export {
    db,
    weightCollection,
    bloodPressureCollection,
    habitCollection,
    routineCollection
};
