<template>
<!--  <button @click="doImport()" >Do import</button>-->
<!--  <button @click="doRecalculate()" >Do recalculate</button>-->
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
    async doRecalculate() {
      console.log("START RECALCULATE")
      let weights = await service.get_all();
      for(let i = 0; i < weights.length; i++) {
        let weight = weights[i].toObject();
        if (i > 0) {
          let previous_weight = weights[i - 1];
          weight.lost_weight =  weight.weight - previous_weight.weight;
          weight.lost_fat =  weight.fat - previous_weight.fat;
          weight.lost_muscle =  weight.muscle - previous_weight.muscle;
        } else {
          weight.lost_weight =  0;
          weight.lost_fat =  0;
          weight.lost_muscle =  0;
        }
        await service.save(weight);
        console.log("weight: " + i);
      }
      console.log("RECALCULATE FINISHED")
    },
    async doExport() {
      return await service.get_all();
    }
  }
}
</script>
