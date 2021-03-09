<template>
  <loading v-model:active="this.state.loading" :can-cancel="false" :is-full-page="true" />
  <div v-if="!this.state.loading">
    <div class="p-grid p-mt-1" >
      <div class="p-col-12 p-sm-6">
        <Panel>
          <template #header>
            <div class="table-header">
              <strong>Last Weight</strong>
              <CreateWeight @onSave="load_all_weights" />
            </div>
          </template>
          <div class="p-grid" v-if="last_weight" >
            <div class="p-col-5">Date: </div>
            <div class="p-col-7">{{ last_weight.dateFormat }}</div>
            <div class="p-col-5">Weight: </div>
            <div class="p-col-7">{{ last_weight.weight }} kg <span v-bind:class="{'bad': last_weight.lost_weight > 0, 'good': last_weight.lost_weight <= 0}">{{ last_weight.lost_weight > 0 ? '+' : '' }}{{ last_weight.lost_weight }}kg</span></div>
            <div class="p-col-5">Fat: </div>
            <div class="p-col-7">{{ last_weight.fat }} kg ({{ last_weight.fat_percentage }}%) <span v-bind:class="{'bad': last_weight.lost_fat > 0, 'good': last_weight.lost_fat <= 0}">{{ last_weight.lost_fat > 0 ? '+' : '' }}{{ last_weight.lost_fat }}kg</span></div>
            <div class="p-col-5">Muscle: </div>
            <div class="p-col-7">{{ last_weight.muscle }} kg ({{ last_weight.muscle_percentage }}%) <span class="extra_info" v-bind:class="{'good': last_weight.lost_muscle >= 0, 'bad': last_weight.lost_muscle < 0}">{{ last_weight.lost_muscle > 0 ? '+' : '' }}{{ last_weight.lost_muscle }}kg</span></div>
          </div>
        </Panel>
      </div>
      <div class="p-col-12 p-sm-6">
        <Panel>
          <template #header>
            <div class="table-header">
              <strong>Last Blood Pressure</strong>
              <CreateBloodPressure @onSave="load_all_blood_pressures" />
            </div>
          </template>
          <div class="p-grid" v-if="last_blood_pressure" >
            <div class="p-col-5">Date: </div>
            <div class="p-col-7">{{ last_blood_pressure.dateFormat }}</div>
            <div class="p-col-5">Status: </div>
            <div class="p-col-7" :style="{color: last_blood_pressure.stage().color}">{{ last_blood_pressure.stage().name }}</div>
            <div class="p-col-5">Upper: </div>
            <div class="p-col-7">{{ last_blood_pressure.upper }} mm Hg <span class="extra_info" v-bind:class="{'bad': last_blood_pressure.lost_upper > 0, 'good': last_blood_pressure.lost_upper <= 0}">{{ last_blood_pressure.lost_upper >= 0 ? '+' : '' }}{{ last_blood_pressure.lost_upper }} mm Hg</span></div>
            <div class="p-col-5">Lower: </div>
            <div class="p-col-7">{{ last_blood_pressure.lower }} mm Hg <span class="extra_info" v-bind:class="{'bad': last_blood_pressure.lost_lower > 0, 'good': last_blood_pressure.lost_lower <= 0}">{{ last_blood_pressure.lost_lower >= 0 ? '+' : '' }}{{ last_blood_pressure.lost_lower }} mm Hg</span></div>
          </div>
        </Panel>
      </div>
    </div>
    <div class="p-grid p-mt-1" v-if="measures_chart_data" >
      <div class="p-col-6 p-text-right">
        <RadioButton id="chat_type1" name="chat_type" value="last_year" v-model="chart_type" @change="load_chart_data" />
        <label for="chat_type1" class="p-ml-1">Last Year</label>
      </div>
      <div class="p-col-6 p-text-left">
        <RadioButton id="chat_type2" name="chat_type" value="all" v-model="chart_type" @change="load_chart_data" />
        <label for="chat_type2" class="p-ml-1">All</label>
      </div>
      <div class="center">
        <TabView>
          <TabPanel header="Measures">
            <Chart type="line" :data="measures_chart_data" />
          </TabPanel>
          <TabPanel header="Lost">
            <Chart type="line" :data="lost_chart_data" />
          </TabPanel>
        </TabView>
      </div>
    </div>
  </div>
</template>

<script>
import { userState } from '../state';
import weightService from '../services/WeightService';
import graphService from '../services/MeasuresGraphService';
import bloodPressureService from '../services/BloodPressureService';
import CreateWeight from "@/components/CreateWeight";
import CreateBloodPressure from "@/components/CreateBloodPressure";
import dayjs from 'dayjs';

export default {
  components: {CreateWeight, CreateBloodPressure},
  data() {
    return {
      weights: [],
      blood_pressures: [],
      last_weight: undefined,
      last_blood_pressure: undefined,
      chart_type: "last_year",
      measures_chart_data: undefined,
      lost_chart_data: undefined,
      state: userState()
    }
  },
  async created() {
    await this.load_all_weights();
    await this.load_all_blood_pressures();
    this.state.loading = false;
  },
  methods: {
    async load_all_weights() {
      this.weights = await weightService.get_all_by(this.state.user.mail);
      this.last_weight = this.weights[0];
      this.load_chart_data();
    },
    async load_all_blood_pressures() {
      this.blood_pressures = await bloodPressureService.get_all_by(this.state.user.mail);
      this.last_blood_pressure = this.blood_pressures[0];
      this.load_chart_data();
    },
    load_chart_data() {
      if (!this.last_weight) {
        return;
      }
      this.state.loading = true;
      let from_date = get_from_date(this.chart_type, this.weights, this.blood_pressures);
      let month_measures = get_month_measures_from(from_date, this.weights, this.blood_pressures);
      this.measures_chart_data = build_month_measures_chart(month_measures);
      this.lost_chart_data = build_month_lost_chart(month_measures);
      this.state.loading = false;

      function build_month_lost_chart(measures) {
        let lost_weight_data = [];
        let lost_fat_data = [];
        let lost_muscle_data = [];
        let lost_upper_data = [];
        let lost_lower_data = [];
        for (let i = 0; i < measures.month_average_measures.length; i++) {
          let month_average_weight = measures.month_average_measures[i];
          lost_weight_data.push(month_average_weight.lost_weight);
          lost_fat_data.push(month_average_weight.lost_fat);
          lost_muscle_data.push(month_average_weight.lost_muscle);
          lost_upper_data.push(month_average_weight.lost_upper);
          lost_lower_data.push(month_average_weight.lost_lower);
        }
        return {
          labels: measures.labels,
          datasets: [{
            label: 'Lost Weigh Kg',
            borderColor: '#10bac9',
            fill: false,
            data: lost_weight_data
          },{
            label: 'Lost Fat Kg',
            borderColor: '#d2b918',
            fill: false,
            data: lost_fat_data
          },{
            label: 'Lost Muscle Kg',
            borderColor: '#6fb374',
            fill: false,
            data: lost_muscle_data
          }, {
            label: 'Lost Upper Blood Pressure mm Hg',
            borderColor: '#c95110',
            fill: false,
            data: lost_upper_data
          }, {
            label: 'Lost Lower Blood Pressure mm Hg',
            borderColor: '#06a089',
            fill: false,
            data: lost_lower_data
          }]
        }
      }

      function build_month_measures_chart(measures) {
        let weight_data = [];
        let fat_data = [];
        let muscle_data = [];
        let upper_data = [];
        let lower_data = [];
        for (let i = 0; i < measures.month_average_measures.length; i++) {
          let month_average_measure = measures.month_average_measures[i];
          weight_data.push(month_average_measure.weight);
          fat_data.push(month_average_measure.fat);
          muscle_data.push(month_average_measure.muscle);
          upper_data.push(month_average_measure.upper);
          lower_data.push(month_average_measure.lower);
        }
        return {
          labels: measures.labels,
          datasets: [{
            label: 'Weight Kg',
            borderColor: '#1a36c1',
            fill: false,
            data: weight_data
          },{
            label: 'Fat %',
            borderColor: '#c91016',
            fill: false,
            data: fat_data
          },{
            label: 'Muscle %',
            borderColor: '#06a01b',
            fill: false,
            data: muscle_data
          },{
            label: 'Upper Pressure mm Hg',
            borderColor: '#c95110',
            fill: false,
            data: upper_data
          },{
            label: 'Lower Pressure mm Hg',
            borderColor: '#06a089',
            fill: false,
            data: lower_data
          }]
        }
      }

      function get_month_measures_from(date, weights, blood_pressures) {
        let month_measure = {
          labels: [],
          month_average_measures: []
        };
        let current_date = dayjs(date);
        let next_month = dayjs().add(1, 'month').toDate();
        while (current_date.toDate() <= next_month) {
          month_measure.labels.push(current_date.format('MMM-YYYY'));
          let month_average_weight = graphService.get_month_average_weights_for(current_date, weights);
          let month_average_blood_pressure = graphService.get_month_average_blood_pressures_for(current_date, blood_pressures);
          month_measure.month_average_measures.push(build_measure_graph_date(month_average_weight, month_average_blood_pressure))
          current_date = current_date.add(1, 'month')
        }
        return month_measure;
      }

      function build_measure_graph_date(weight, blood_pressure) {
        return new MeasureGraphData(
            weight.weight,
            weight.lost_weight,
            weight.fat,
            weight.lost_fat,
            weight.muscle,
            weight.lost_muscle,
            blood_pressure.upper,
            blood_pressure.lower,
            blood_pressure.lost_upper,
            blood_pressure.lost_lower
        );
      }

      function get_from_date(chart_type, weights, blood_pressures) {
        return chart_type === 'all' ? get_first_date_measure(weights, blood_pressures) : dayjs().subtract(1, 'year').toDate();
      }

      function get_first_date_measure(weights, blood_pressures) {
        let first_weight_date = get_first_weight_date(weights);
        let first_blood_pressure_date = get_first_blood_pressure_date(blood_pressures);
        return first_weight_date > first_blood_pressure_date ? first_blood_pressure_date : first_weight_date;
      }

      function get_first_weight_date(weights) {
        return weights[weights.length - 1].date;
      }

      function get_first_blood_pressure_date(blood_pressures) {
        let last_blood_pressure = blood_pressures[blood_pressures.length - 1];
        return last_blood_pressure ? last_blood_pressure.date : new Date();
      }
    }
  }
}

class MeasureGraphData {
  constructor(weight, lost_weight, fat, lost_fat, muscle, lost_muscle, upper, lower, lost_upper, lost_lower) {
    this.weight = weight;
    this.lost_weight = lost_weight;
    this.fat = fat;
    this.lost_fat = lost_fat;
    this.muscle = muscle;
    this.lost_muscle = lost_muscle;
    this.upper = upper;
    this.lower = lower;
    this.lost_upper = lost_upper;
    this.lost_lower = lost_lower;
  }
}
</script>

<style>
@media (min-width: 1024px) {
  .center {
    display: block;
    margin-left: auto;
    margin-right: auto;
    width: 50%;
  }
}
@media (max-width: 1024px) {
  .center {
    display: block;
    width: 100%;
  }
}
</style>