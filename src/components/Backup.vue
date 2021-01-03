<template>
<!--  <button @click="doImport()" >Do import</button>-->
  <br>
  {{ JSON.stringify(json, null, '\t') }}
</template>

<script>

import service from '../services/WeightService';
import dayjs from 'dayjs';
import {useState} from '../state';

export default {
  name: 'Backup',
  data () {
    return {
      json: {},
      state: useState()
    }
  },
  async created () {
    this.json = await this.doExport();
  },
  methods: {
    doImport() {
      console.log("START IMPORT")
      let json = require("../../old-weights.json");
      for(let i = 0; i < json.length; i++) {
        let weight = json[i];
        let newWeight = {};
        newWeight.date = dayjs(weight.date,"YYYY-MM-DD").toDate();
        newWeight.weight = parseFloat(weight.weight);
        newWeight.fat_percentage = parseFloat(weight.fat_percentage);
        newWeight.muscle = parseFloat(weight.muscle);
        newWeight.fat = parseFloat(weight.fat);
        newWeight.muscle_percentage = parseFloat(weight.muscle_percentage);
        newWeight.user = "jllado@gmail.com";
        service.save(newWeight)
        console.log("weight: " + i)
      }
      console.log("IMPORT FINISHED")
    },
    async doExport() {
      return await service.get_all(this.state.user.mail);
    }
  }
}
</script>
