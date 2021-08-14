<template>
  <loading v-model:active="this.state.loading" :can-cancel="false" :is-full-page="true" />
  <div v-if="!this.state.loading">
    <div class="p-grid p-mt-1" >
      <div class="p-col-12 p-sm-6">
        <Panel>
          <template #header>
            <div class="table-header">
              <strong>Last Weight</strong>
              <CreateWeight @onSave="load_all" />
            </div>
          </template>
          <div class="p-grid" v-if="last_weight && current_weight_trend" >
            <div class="p-col-6">
              <div id="fat-bar-status" />
            </div>
            <div class="p-col-6">
              <div id="bmi-bar-status" />
            </div>
            <div class="p-col-5">Date: </div>
            <div class="p-col-7">{{ last_weight.dateFormat }}</div>
            <div class="p-col-5">Weight: </div>
            <div class="p-col-7">{{ last_weight.weight }} kg <span v-bind:class="{'bad': last_weight.lost_weight > 0, 'good': last_weight.lost_weight <= 0}">{{ last_weight.lost_weight > 0 ? '+' : '' }}{{ last_weight.lost_weight }}kg</span></div>
            <div class="p-col-5">Fat: </div>
            <div class="p-col-7">{{ last_weight.fat }} kg ({{ last_weight.fat_percentage }}%) <span v-bind:class="{'bad': last_weight.lost_fat > 0, 'good': last_weight.lost_fat <= 0}">{{ last_weight.lost_fat > 0 ? '+' : '' }}{{ last_weight.lost_fat }}kg</span></div>
            <div class="p-col-5">Muscle: </div>
            <div class="p-col-7">{{ last_weight.muscle }} kg ({{ last_weight.muscle_percentage }}%) <span class="extra_info" v-bind:class="{'good': last_weight.lost_muscle >= 0, 'bad': last_weight.lost_muscle < 0}">{{ last_weight.lost_muscle > 0 ? '+' : '' }}{{ last_weight.lost_muscle }}kg</span></div>
            <div class="p-col-5">Status: </div>
            <div class="p-col-7" :style="{color: last_weight.status().color}">{{ last_weight.status().name }}</div>
            <div class="p-col-5">BMI: </div>
            <div class="p-col-7">{{ last_weight.bmi().value }} <span :style="{color: last_weight.bmi().status().color}">{{ last_weight.bmi().status().name }}</span></div>
            <div class="p-col-5">Current Lost Trend: </div>
            <div class="p-col-7"><span v-bind:class="{'bad': current_weight_trend.lost_weight > 0, 'good': current_weight_trend.lost_weight <= 0}">{{ current_weight_trend.lost_weight > 0 ? '+' : '' }}{{ current_weight_trend.lost_weight }}kg</span> per month</div>
            <div class="p-col-5">Strike: </div>
            <div class="p-col-7">{{ current_weight_strike }} days below {{ last_weight.range() }} kg</div>
            <div class="p-col-5">Next Goal: </div>
            <div class="p-col-7">{{ months_next_range }} months for {{ last_weight.next_range() }} kg</div>
          </div>
        </Panel>
      </div>
      <div class="p-col-12 p-sm-6">
        <Panel>
          <template #header>
            <div class="table-header">
              <strong>Last Pressure</strong>
              <CreateBloodPressure @onSave="load_all" />
            </div>
          </template>
          <div class="p-grid" v-if="last_blood_pressure && current_blood_pressure_trend" >
            <div class="p-col-5">Date: </div>
            <div class="p-col-7">{{ last_blood_pressure.dateFormat }}</div>
            <div class="p-col-5">Status: </div>
            <div class="p-col-7" :style="{color: last_blood_pressure.stage().color}">{{ last_blood_pressure.stage().name }}</div>
            <div class="p-col-5">Upper: </div>
            <div class="p-col-7">{{ last_blood_pressure.upper }} mm Hg <span class="extra_info" v-bind:class="{'bad': last_blood_pressure.lost_upper > 0, 'good': last_blood_pressure.lost_upper <= 0}">{{ last_blood_pressure.lost_upper >= 0 ? '+' : '' }}{{ last_blood_pressure.lost_upper }} mm Hg</span></div>
            <div class="p-col-5">Lower: </div>
            <div class="p-col-7">{{ last_blood_pressure.lower }} mm Hg <span class="extra_info" v-bind:class="{'bad': last_blood_pressure.lost_lower > 0, 'good': last_blood_pressure.lost_lower <= 0}">{{ last_blood_pressure.lost_lower >= 0 ? '+' : '' }}{{ last_blood_pressure.lost_lower }} mm Hg</span></div>
            <div class="p-col-5">Current Status Trend: </div>
            <div class="p-col-7" :style="{color: current_blood_pressure_trend.stage().color}">{{ current_blood_pressure_trend.stage().name }}</div>
            <div class="p-col-5">Current Upper Trend: </div>
            <div class="p-col-7">
              {{ current_blood_pressure_trend.upper }} mm Hg
              <span class="extra_info" v-bind:class="{'bad': current_blood_pressure_trend.lost_upper > 0, 'good': current_blood_pressure_trend.lost_upper <= 0}">
                {{ current_blood_pressure_trend.lost_upper >= 0 ? '+' : '' }}{{ current_blood_pressure_trend.lost_upper }}
              </span> per month
            </div>
            <div class="p-col-5">Current Lower Trend: </div>
            <div class="p-col-7">
              {{ current_blood_pressure_trend.lower }} mm Hg
              <span class="extra_info" v-bind:class="{'bad': current_blood_pressure_trend.lost_lower > 0, 'good': current_blood_pressure_trend.lost_lower <= 0}">
                {{ current_blood_pressure_trend.lost_lower >= 0 ? '+' : '' }}{{ current_blood_pressure_trend.lost_lower }}
              </span> per month
            </div>
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
import {WeightStatus, BMIStatus} from "@/model/Weight";
import weightService from '../services/WeightService';
import summaryService from '../services/MeasuresSummaryService';
import bloodPressureService from '../services/BloodPressureService';
import CreateWeight from "@/components/CreateWeight";
import CreateBloodPressure from "@/components/CreateBloodPressure";
import dayjs from 'dayjs';
import anychart from 'anychart/dist/js/anychart-base.min'
import anychartLinearGauge from 'anychart/dist/js/anychart-linear-gauge.min'

export default {
  components: {CreateWeight, CreateBloodPressure},
  data() {
    return {
      weights: [],
      blood_pressures: [],
      last_weight: undefined,
      last_blood_pressure: undefined,
      current_blood_pressure_trend: undefined,
      current_weight_trend: undefined,
      current_weight_strike: undefined,
      months_next_range: undefined,
      chart_type: "last_year",
      measures_chart_data: undefined,
      lost_chart_data: undefined,
      fat_status_bar: undefined,
      bmi_status_bar: undefined,
      state: userState()
    }
  },
  async created() {
    await this.load_all();
    await this.init_fat_status_bar();
    await this.init_bmi_status_bar();
    this.state.loading = false;
  },
  methods: {
    set_fat_status_bar_data() {
      if (this.fat_status_bar) {
        this.fat_status_bar.data([this.last_weight.fat_percentage]);
      }
    },
    set_bmi_status_bar_data() {
      if (this.bmi_status_bar) {
        this.bmi_status_bar.data([this.last_weight.bmi().value]);
      }
    },
    async init_fat_status_bar() {
      this.fat_status_bar = anychartLinearGauge.gauges.linear();
      this.fat_status_bar.top("-210px");
      this.fat_status_bar.height("450px");
      this.fat_status_bar.layout('horizontal');
      this.set_fat_status_bar_data();
      let scaleBarColorScale = buildBarColorScale();
      let scaleBar = this.fat_status_bar.scaleBar(0);
      scaleBar.width('5%');
      scaleBar.offset('31.5%');
      scaleBar.colorScale(scaleBarColorScale)
      let marker = this.fat_status_bar.marker(0);
      marker.offset('31.5%');
      marker.type('triangle-up');
      marker.zIndex(10);
      marker.color('black');
      let scale = this.fat_status_bar.scale();
      scale.minimum(0);
      scale.maximum(35);
      scale.ticks().interval(5);
      let axis = this.fat_status_bar.axis();
      axis.minorTicks(true)
      axis.minorTicks().stroke('#cecece');
      axis.width('1%');
      let title = axis.title();
      title.enabled(true);
      title.text('Fat %');
      title.padding(-45);
      axis.offset('29.5%');
      axis.orientation('top');
      axis.labels().format('{%value}%');
      this.fat_status_bar.container('fat-bar-status');
      this.fat_status_bar.draw();

      function buildBarColorScale() {
        let ranges = [];
        var toStatus = undefined;
        for (let statusKey in WeightStatus) {
          let status = WeightStatus[statusKey];
          if (toStatus) {
            ranges.push({
              from: status.fat,
              to: toStatus.fat,
              color: [status.color, toStatus.color]
            });
          }
          toStatus = status;
        }
        return anychart.scales.ordinalColor().ranges(ranges);
      }
    },
    async init_bmi_status_bar() {
      this.bmi_status_bar = anychartLinearGauge.gauges.linear();
      this.bmi_status_bar.top("-210px");
      this.bmi_status_bar.height("450px");
      this.bmi_status_bar.layout('horizontal');
      this.set_bmi_status_bar_data();
      let scaleBarColorScale = buildBarColorScale();
      let scaleBar = this.bmi_status_bar.scaleBar(0);
      scaleBar.width('5%');
      scaleBar.offset('31.5%');
      scaleBar.colorScale(scaleBarColorScale)
      let marker = this.bmi_status_bar.marker(0);
      marker.offset('31.5%');
      marker.type('triangle-up');
      marker.zIndex(10);
      marker.color('black');
      let scale = this.bmi_status_bar.scale();
      scale.minimum(0);
      scale.maximum(30);
      scale.ticks().interval(5);
      let axis = this.bmi_status_bar.axis();
      axis.minorTicks(true)
      axis.minorTicks().stroke('#cecece');
      axis.width('1%');
      let title = axis.title();
      title.enabled(true);
      title.text('BMI');
      title.padding(-45);
      axis.offset('29.5%');
      axis.orientation('top');
      axis.labels().format('{%value}');
      this.bmi_status_bar.container('bmi-bar-status');
      this.bmi_status_bar.draw();

      function buildBarColorScale() {
        let ranges = [];
        var toStatus = undefined;
        for (let statusKey in BMIStatus) {
          let status = BMIStatus[statusKey];
          if (toStatus) {
            ranges.push({
              from: status.value,
              to: toStatus.value,
              color: [status.color, toStatus.color]
            });
          }
          toStatus = status;
        }
        return anychart.scales.ordinalColor().ranges(ranges);
      }
    },
    async load_all_weights() {
      this.weights = await weightService.get_all_by(this.state.user.mail);
      this.last_weight = this.weights[0];
    },
    async load_all_blood_pressures() {
      this.blood_pressures = await bloodPressureService.get_all_by(this.state.user.mail);
      this.last_blood_pressure = this.blood_pressures[0];
    },
    async load_all() {
      await this.load_all_weights();
      await this.load_all_blood_pressures();
      await this.load_chart_data();
      await this.load_current_trend();
      this.load_current_weight_strike();
      this.load_months_next_range();
      this.set_fat_status_bar_data();
      this.set_bmi_status_bar_data();
    },
    async load_current_trend() {
      this.current_weight_trend = summaryService.get_weight_trend(this.weights);
      this.current_blood_pressure_trend = summaryService.get_blood_pressure_trend(this.blood_pressures);
    },
    load_current_weight_strike() {
      let range = this.last_weight.range();
      this.current_weight_strike = summaryService.get_weight_strike_days(range, this.weights);
    },
    load_months_next_range() {
      this.months_next_range = this.last_weight.months_next_range(this.current_weight_trend)
    },
    async load_chart_data() {
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
          let month_average_weight = summaryService.get_month_average_weights_for(current_date, weights);
          let month_average_blood_pressure = summaryService.get_month_average_blood_pressures_for(current_date, blood_pressures);
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