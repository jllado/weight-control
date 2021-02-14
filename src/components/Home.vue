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
    </div>
    <div class="p-grid p-mt-1" v-if="weight_chart_data" >
      <div class="p-col-6 p-text-right">
        <RadioButton id="chat_type1" name="chat_type" value="last_year" v-model="chart_type" @change="load_chart_data" />
        <label for="chat_type1" class="p-ml-1">Last Year</label>
      </div>
      <div class="p-col-6 p-text-left">
        <RadioButton id="chat_type2" name="chat_type" value="all" v-model="chart_type" @change="load_chart_data" />
        <label for="chat_type2" class="p-ml-1">All</label>
      </div>
      <div class="p-col-12">
        <TabView>
          <TabPanel header="Weight">
            <Chart type="line" :data="weight_chart_data" />
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
import CreateWeight from "@/components/CreateWeight";
import dayjs from 'dayjs';

export default {
  components: {CreateWeight},
  data() {
    return {
      weights: [],
      last_weight: undefined,
      chart_type: "last_year",
      weight_chart_data: undefined,
      lost_chart_data: undefined,
      state: userState()
    }
  },
  async created() {
    await this.load_all_weights();
    this.state.loading = false;
  },
  methods: {
    async load_all_weights() {
      this.weights = await weightService.get_all_by(this.state.user.mail);
      this.last_weight = this.weights[0];
      this.load_chart_data();
    },
    load_chart_data() {
      if (!this.last_weight) {
        return;
      }
      this.state.loading = true;
      let from_date = get_from_date(this.chart_type, this.weights);
      let month_weights = get_month_weights(from_date, this.weights);
      this.weight_chart_data = build_month_weights(month_weights);
      this.lost_chart_data = build_month_lost(month_weights);
      this.state.loading = false;

      function build_month_lost(month_weights) {
        let lost_weight_data = [];
        let lost_fat_data = [];
        let lost_muscle_data = [];
        for (let i = 0; i < month_weights.month_average_weights.length; i++) {
          let month_average_weight = month_weights.month_average_weights[i];
          lost_weight_data.push(month_average_weight.lost_weight);
          lost_fat_data.push(month_average_weight.lost_fat);
          lost_muscle_data.push(month_average_weight.lost_muscle);
        }
        return {
          labels: month_weights.labels,
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
          }]
        }
      }
      function build_month_weights(month_weights) {
        let weight_data = [];
        let fat_data = [];
        let muscle_data = [];
        for (let i = 0; i < month_weights.month_average_weights.length; i++) {
          let month_average_weight = month_weights.month_average_weights[i];
          weight_data.push(month_average_weight.weight);
          fat_data.push(month_average_weight.fat);
          muscle_data.push(month_average_weight.muscle);
        }
        return {
          labels: month_weights.labels,
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
          }]
        }
      }
      function get_month_weights(from_date, weights) {
        let month_weight = {
          labels: [],
          month_average_weights: []
        };
        let current_date = dayjs(from_date);
        let next_month = dayjs().add(1, 'month').toDate();
        while (current_date.toDate() <= next_month) {
          month_weight.labels.push(current_date.format('MMM-YYYY'));
          let month_average_weight = weightService.get_month_average_weight_for(current_date, weights);
          month_weight.month_average_weights.push(month_average_weight)
          current_date = current_date.add(1, 'month')
        }
        return month_weight;
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