<template>
<!--<button @click="doImportAll()" >Do import</button>-->
  <br>
  <h1>WEIGHTS</h1>
  {{ JSON.stringify(weightsJson, null, '\t') }}
  <h1>BLOOD PRESSURES</h1>
  {{ JSON.stringify(bloodPressuresJson, null, '\t') }}
  <h1>HABITS</h1>
  {{ JSON.stringify(habitsJson, null, '\t') }}
  <h1>ROUTINES</h1>
  {{ JSON.stringify(routinesJson, null, '\t') }}
</template>

<script>
import * as fb from '../firebase';
import weightService from '../services/WeightService';
import habitService from '../services/HabitService';
import routineService from '../services/RoutineService';
import bloodPressureService from '../services/BloodPressureService';
import {userState} from '../state';
import dayjs from 'dayjs';
import Weight from "@/model/Weight";
import Routine from "@/model/Routine";
import BloodPressure from "@/model/BloodPressure";
import Habit from "@/model/Habit";

function reviver(key, value) {
  if (typeof value === "string" && dayjs(value).isValid()) {
    return new Date(value);
  }
  return value;
}

export default {
  name: 'Backup',
  data () {
    return {
      weightsJson: {},
      bloodPressuresJson: {},
      habitsJson: {},
      routinesJson: {},
      state: userState()
    }
  },
  async created () {
    this.weightsJson = await this.doExportWeights();
    this.bloodPressuresJson = await this.doExportBloodPressures();
    this.habitsJson = await this.doExportHabits();
    this.routinesJson = await this.doExportRoutines();
  },
  methods: {
    doImportAll() {
      this.doImportWeights();
      this.doImportBloodPressures();
      this.doImportHabits();
      this.doImportRoutines();
    },
    doImportWeights() {
      console.log("START WEIGHTS IMPORT");
      let json = JSON.parse(JSON.stringify(require("../../backups/weights.json")), reviver);
      for(let i = 0; i < json.length; i++) {
        weightService.save(json[i]);
      }
      console.log("IMPORT WEIGHTS FINISHED");
    },
    doImportBloodPressures() {
      console.log("START BLOOD PRESSURES IMPORT");
      let json = JSON.parse(JSON.stringify(require("../../backups/blood_pressure.json")), reviver);
      for(let i = 0; i < json.length; i++) {
        bloodPressureService.save(json[i]);
      }
      console.log("IMPORT BLOOD PRESSURES FINISHED");
    },
    doImportHabits() {
      console.log("START HABITS IMPORT");
      let json = JSON.parse(JSON.stringify(require("../../backups/habits.json")), reviver);
      for(let i = 0; i < json.length; i++) {
        habitService.save(json[i]);
      }
      console.log("IMPORT HABITS FINISHED");
    },
    doImportRoutines() {
      console.log("START ROUTINES IMPORT");
      let json = JSON.parse(JSON.stringify(require("../../backups/routines.json")), reviver);
      for(let i = 0; i < json.length; i++) {
        routineService.save(json[i]);
      }
      console.log("IMPORT ROUTINES FINISHED");
    },
    async doExportWeights() {
      return await fb.weightCollection
          .where('user', '==', this.state.user.mail)
          .orderBy('date', 'desc')
          .get().then(q => q.docs.map(doc => { return new Weight(doc).toObject() }));
    },
    async doExportBloodPressures() {
      return await fb.bloodPressureCollection
          .where('user', '==', this.state.user.mail)
          .orderBy('date', 'desc')
          .get().then(q => q.docs.map(doc => { return new BloodPressure(doc).toObject() }));
    },
    async doExportRoutines() {
      return await fb.routineCollection
          .where('user', '==', this.state.user.mail)
          .get().then(q => q.docs.map(doc => { return new Routine(doc).toObject() }));
    },
    async doExportHabits() {
      return await fb.habitCollection
          .where('user', '==', this.state.user.mail)
          .get().then(q => q.docs.map(doc => { return new Habit(doc).toObject() }));
    }
  }
}
</script>
