<template>
  <loading v-model:active="this.state.loading" :can-cancel="false" :is-full-page="true" />
  <div class="center" v-if="!this.state.loading">
    <div class="p-grid p-mt-1" >
      <div class="p-co-12 last-weight">
        <Panel>
          <template #header>
            <div class="table-header">
              Last Weight
              <CreateWeight @onSave="load_all_weights" />
            </div>
          </template>
          <div class="p-grid" v-if="last_weight" >
            <div class="p-col-6">Date: </div>
            <div class="p-col-6">{{ last_weight.dateFormat }}</div>
            <div class="p-col-6">Weight: </div>
            <div class="p-col-6">{{ last_weight.weight }} kg</div>
            <div class="p-col-6">Fat: </div>
            <div class="p-col-6">{{ last_weight.fat }} kg ( {{ last_weight.fat_percentage }}%)</div>
            <div class="p-col-6">Muscle: </div>
            <div class="p-col-6">{{ last_weight.muscle }} kg ( {{ last_weight.muscle_percentage }}%)</div>
          </div>
        </Panel>
      </div>
    </div>
    <div class="p-grid p-mt-1" v-if="chart_data" >
      <div class="p-col-6 p-text-right">
        <RadioButton id="chat_type1" name="chat_type" value="last_year" v-model="chart_type" @change="load_chart_data" />
        <label for="chat_type1" class="p-ml-1">Last Year</label>
      </div>
      <div class="p-col-6 p-text-left">
        <RadioButton id="chat_type2" name="chat_type" value="all" v-model="chart_type" @change="load_chart_data" />
        <label for="chat_type2" class="p-ml-1">All</label>
      </div>
      <div class="p-col-12">
        <Chart type="line" :data="chart_data" />
      </div>
    </div>
  </div>
</template>

<script>
import { useState } from '../state';
import weightService from '../services/WeightService';
import CreateWeight from "@/components/CreateWeight";
import dayjs from 'dayjs';

export default {
  components: {CreateWeight},
  data() {
    return {
      weights: [],
      last_weight: undefined,
      chart_type: "last_year",
      chart_data: undefined,
      state: useState()
    }
  },
  async created() {
    await this.load_all_weights();
    this.state.loading = false;
  },
  methods: {
    async load_all_weights() {
      this.weights = await weightService.get_all(this.state.user.mail);
      this.last_weight = this.weights[0];
      this.load_chart_data();
    },
    load_chart_data() {
      if (!this.last_weight) {
        return;
      }
      this.state.loading = true;
      let fromDate = get_from_date(this.chart_type, this.weights);
      this.chart_data = load_month_weights(fromDate, this.weights);
      this.state.loading = false;

      function load_month_weights(date, weights) {
        let labels = [];
        let weightData = [];
        let fatData = [];
        let muscleData = [];
        let currentDate = dayjs(date);
        let nextMonth = dayjs().add(1, 'month').toDate();
        while (currentDate.toDate() <= nextMonth) {
          labels.push(currentDate.format('MM-YYYY'));
          let monthAverageWeight = weightService.get_month_average_weight_for(currentDate, weights);
          weightData.push(monthAverageWeight.weight);
          fatData.push(monthAverageWeight.fat);
          muscleData.push(monthAverageWeight.muscle);
          currentDate = currentDate.add(1, 'month')
        }
        return {
          labels: labels,
          datasets: [{
            label: 'Weight Kg',
            borderColor: '#42A5F5',
            fill: false,
            data: weightData
          },{
            label: 'Fat %',
            borderColor: '#c91016',
            fill: false,
            data: fatData
          },{
            label: 'Muscle %',
            borderColor: '#06a01b',
            fill: false,
            data: muscleData
          }]
        }
      }
      function get_from_date(chart_type, weights) {
        return chart_type === 'all' ? get_first_weight_date(weights) : dayjs().subtract(1, 'year').toDate();
      }
      function get_first_weight_date(weights) {
        return weights[weights.length - 1].date;
      }
    }
  }
}
</script>

<style>
.last-weight {
  width: 100%;
}
.create-button {
  position: relative !important;
  top: 16px;
  right: 16px;
  z-index: 10000;
}
@media (min-width: 1024px) {
  .center {
    display: block;
    margin-left: auto;
    margin-right: auto;
    width: 50%;
  }
}
</style>