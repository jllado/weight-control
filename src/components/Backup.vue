<template>
<button @click="doImportWeights()" >Do import</button>
<!--  <button @click="doRecalculate()" >Do recalculate</button>-->
  <br>
  {{ JSON.stringify(json, null, '\t') }}
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

export default {
  name: 'Backup',
  data () {
    return {
      json: {},
      state: userState()
    }
  },
  async created () {
    this.json = await this.doExportWeights();
  },
  methods: {
    doImportWeights() {
      console.log("START IMPORT")
      let json = require("../../weights.json");
      for(let i = 0; i < json.length; i++) {
        let weight = json[i];
        weight.date = dayjs(weight.date).toDate();
        weightService.save(json[i])
      }
      console.log("IMPORT FINISHED")
    },
    async doRecalculateWeights() {
      console.log("START RECALCULATE")
      let weights = await weightService.get_all_by(this.state.user.mail);
      for(let i = 0; i < weights.length; i++) {
        let weight = weights[i].toObject();
        let previous_weight = i == 0 ? null : weights[i - 1];
        weight.load_lost(previous_weight)
        await weightService.save(weight);
        console.log("weight: " + i);
      }
      console.log("RECALCULATE FINISHED")
    },
    async doExportWeights() {
      return await fb.weightCollection
          .where('user', '==', this.state.user.mail)
          .orderBy('date', 'desc')
          .get().then(q => q.docs.map(doc => {
            return new Weight(doc).toObject()
          }));
    },
    async doExportBloodPressures() {
      return await bloodPressureService.get_all_by(this.state.user.mail);
    },
    async doExportRoutines() {
      return await routineService.get_all_by(this.state.user.mail);
    },
    async doExportHabits() {
      return await habitService.get_all_by(this.state.user.mail);
    }
  }
}
</script>
