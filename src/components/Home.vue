<template>
  <loading v-model:active="this.state.loading" :can-cancel="false" :is-full-page="true" />
  <div v-if="!this.state.loading">
    <div class="p-grid p-mt-1" >
      <div class="p-col-12" v-if="habits.length > 0" >
        <Panel>
          <template #header>
            <div class="table-header">
              <strong>Habits ({{this.habits.length}})</strong>
            </div>
          </template>
          <DataTable :value="this.habits" responsiveLayout="scroll"
                     paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
                     currentPageReportTemplate="{first} to {last} of {totalRecords}" >
            <Column headerStyle="width: 55px" bodyStyle="text-align: center" >
              <template #body="habit">
                <Button icon="pi pi-plus" class="p-button-rounded p-button-success" @click="plusHabit(habit.data)" :disabled="habit.data.isDisabled()" />
              </template>
            </Column>
            <Column>
              <template #body="habit" >
                {{ habit.data.name }}
              </template>
            </Column>
            <Column header="Strike" headerStyle="width: 80px" bodyStyle="text-align: center" >
              <template #body="habit" >
                {{ habit.data.print_strike() }}
              </template>
            </Column>
          </DataTable>
        </Panel>
      </div>
      <div class="p-col-12" v-if="routines.length > 0" >
        <Panel class="p-panel-content-without-padding" >
          <template #header>
            <div class="table-header">
              <strong>Routines ({{this.routines.length}})</strong><span v-bind:class="{'good': this.routines_status >= 60, 'normal': this.routines_status >= 50 && this.routines_status < 60, 'fail': this.routines_status >= 40 && this.routines_status < 50, 'bad': this.routines_status < 40}">Status: {{this.routines.length}}/{{this.routines_score}} ({{this.routines_status}}%)</span>
            </div>
          </template>
          <DataTable :value="this.routines" responsiveLayout="scroll"
                     paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
                     currentPageReportTemplate="{first} to {last} of {totalRecords}" >
            <Column headerStyle="width: 55px" bodyStyle="text-align: center" >
              <template #body="routine">
                <Button icon="pi pi-plus" class="p-button-rounded p-button-success" @click="plusRoutine(routine.data)" :disabled="routine.data.isDisabled()" />
              </template>
            </Column>
            <Column>
              <template #body="routine" >
                {{ routine.data.name }}
              </template>
            </Column>
            <Column header="Strike" headerStyle="width: 40px; text-align: center" bodyStyle="text-align: center" >
              <template #body="routine" >
                {{ routine.data.current_strike }}
              </template>
            </Column>
            <Column header="Fails" headerStyle="width: 40px; text-align: center" bodyStyle="text-align: center" >
              <template #body="routine" >
                {{ routine.data.fails() }}
              </template>
            </Column>
            <Column headerStyle="width: 40px; text-align: center" bodyStyle="text-align: center" >
              <template #body="routine" >
                <span v-bind:class="{'good': routine.data.status() >= 60, 'normal': routine.data.status() >= 50 && routine.data.status() < 60, 'fail': routine.data.status() >= 40 && routine.data.status() < 50, 'bad': routine.data.status() < 40}">{{ routine.data.status() }}%</span>
              </template>
            </Column>
          </DataTable>
        </Panel>
      </div>
      <div class="p-col-12">
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
      <div class="p-col-12">
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
    <div class="p-grid p-mt-1" v-if="muscle_chart_data" >
      <div class="p-col-4 p-text-right">
        <RadioButton id="chat_type2" name="chat_type" value="monthly" v-model="chart_type" @change="load_chart_data" />
        <label for="chat_type3" class="p-ml-1">Monthly</label>
      </div>
      <div class="p-col-4 p-text-center">
        <RadioButton id="chat_type1" name="chat_type" value="last_year" v-model="chart_type" @change="load_chart_data" />
        <label for="chat_type1" class="p-ml-1">Year</label>
      </div>
      <div class="p-col-4 p-text-left">
        <RadioButton id="chat_type2" name="chat_type" value="all" v-model="chart_type" @change="load_chart_data" />
        <label for="chat_type2" class="p-ml-1">All</label>
      </div>
      <div id="measures-chart" class="center">
        <TabView>
          <TabPanel header="Measures">
            <Chart type="line" :data="weight_chart_data.data" :options="weight_chart_data.options" :height="175" />
            <Chart type="line" :data="fat_chart_data.data" :options="fat_chart_data.options" :height="175" />
            <Chart type="line" :data="muscle_chart_data.data" :options="muscle_chart_data.options" :height="175" />
            <Chart type="line" :data="upper_pressure_chart_data.data" :options="upper_pressure_chart_data.options" :height="175" />
            <Chart type="line" :data="lower_pressure_chart_data.data" :options="lower_pressure_chart_data.options" :height="175" />
          </TabPanel>
          <TabPanel header="Lost">
            <Chart type="line" :data="weight_lost_chart_data.data" :options="weight_lost_chart_data.options" :height="175" />
            <Chart type="line" :data="fat_lost_chart_data.data" :options="fat_lost_chart_data.options" :height="175" />
            <Chart type="line" :data="muscle_lost_chart_data.data" :options="muscle_lost_chart_data.options" :height="175" />
            <Chart type="line" :data="upper_pressure_lost_chart_data.data" :options="upper_pressure_lost_chart_data.options" :height="175" />
            <Chart type="line" :data="lower_pressure_lost_chart_data.data" :options="lower_pressure_lost_chart_data.options" :height="175" />
          </TabPanel> :height="175"
        </TabView>
      </div>
    </div>
  </div>
</template>

<script>
import {userState} from '../state';
import {BMIStatus, WeightStatus} from "@/model/Weight";
import habitService from '../services/HabitService';
import routineService from '../services/RoutineService';
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
      routines: [],
      habits: [],
      weights: [],
      blood_pressures: [],
      routines_status: undefined,
      routines_score: undefined,
      last_weight: undefined,
      last_blood_pressure: undefined,
      current_blood_pressure_trend: undefined,
      current_weight_trend: undefined,
      current_weight_strike: undefined,
      months_next_range: undefined,
      chart_type: "monthly",
      weight_chart_data: undefined,
      fat_chart_data: undefined,
      muscle_chart_data: undefined,
      weight_lost_chart_data: undefined,
      upper_pressure_chart_data: undefined,
      lower_pressure_chart_data: undefined,
      fat_lost_chart_data: undefined,
      muscle_lost_chart_data: undefined,
      upper_pressure_lost_chart_data: undefined,
      lower_pressure_lost_chart_data: undefined,
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
    async load_all_routines() {
      this.routines = await routineService.get_all_by(this.state.user.mail);
      this.routines_status = summaryService.get_routines_status(this.routines);
      this.routines_score = summaryService.get_routines_score(this.routines);
    },
    async load_all_habits() {
      this.habits = await this.get_pending_habits();
    },
    async get_pending_habits() {
      let all_habits = await habitService.get_all_by(this.state.user.mail);
      return all_habits.filter(h => h.isPending());
    },
    async plusHabit(habit) {
      await habitService.save(habit.plusTimes())
          .then(() => {
            this.$toast.add({severity:'success', summary: 'Habit done it', life: 3000});
          })
          .catch(e => {
            this.handle_error(e)
          });
      await this.load_all_habits();
      this.$confetti.start();
      setTimeout(function (){
        this.$confetti.stop();
      }.bind(this), 2000);
    },
    async plusRoutine(routine) {
      await routineService.save(routine.plusTimes())
          .then(() => {
            this.$toast.add({severity:'success', summary: 'Routine done it', life: 3000});
          })
          .catch(e => {
            this.handle_error(e)
          });
      await this.load_all_routines();
      this.$confetti.start();
      setTimeout(function (){
        this.$confetti.stop();
      }.bind(this), 2000);
    },
    handle_error(e) {
      this.$log.error(e);
      this.$toast.add({severity:'error', summary: 'Failed', detail: e, life: 3000});
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
      await this.load_all_habits();
      await this.load_all_routines();
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
    load_chart_data: async function () {
      if (!this.last_weight) {
        return;
      }
      this.state.loading = true;
      let from_date = get_from_date(this.chart_type, this.weights, this.blood_pressures);
      let month_measures = get_month_measures_from(from_date, this.weights, this.blood_pressures);
      this.weight_chart_data = build_month_weight_chart(month_measures, this.chart_type);
      this.fat_chart_data = build_month_fat_chart(month_measures, this.chart_type);
      this.muscle_chart_data = build_month_muscle_chart(month_measures, this.chart_type);
      this.upper_pressure_chart_data = build_month_upper_pressure_chart(month_measures, this.chart_type);
      this.lower_pressure_chart_data = build_month_lower_pressure_chart(month_measures, this.chart_type);
      this.weight_lost_chart_data = build_month_weight_lost_chart(month_measures, this.chart_type);
      this.fat_lost_chart_data = build_month_fat_lost_chart(month_measures, this.chart_type);
      this.muscle_lost_chart_data = build_month_muscle_lost_chart(month_measures, this.chart_type);
      this.upper_pressure_lost_chart_data = build_month_upper_pressure_lost_chart(month_measures, this.chart_type);
      this.lower_pressure_lost_chart_data = build_month_lower_pressure_lost_chart(month_measures, this.chart_type);
      this.state.loading = false;

      function build_month_weight_lost_chart(measures, chart_type) {
        let current_data = [];
        for (const lost of measures.month_average_measures) {
          current_data.push(lost.lost_weight);
        }
        let year_ago_data = [];
        measures.year_ago_month_average_measures.forEach(measure => {
          year_ago_data.push(measure.lost_weight);
        });
        return build_chart_settings('Lost Weigh Kg', '#10bac9', chart_type, current_data, year_ago_data, measures.labels);
      }

      function build_month_fat_lost_chart(measures, chart_type) {
        let current_data = [];
        for (const lost of measures.month_average_measures) {
          current_data.push(lost.lost_fat);
        }
        let year_ago_data = [];
        measures.year_ago_month_average_measures.forEach(measure => {
          year_ago_data.push(measure.lost_fat);
        });
        return build_chart_settings('Lost Fat Kg', '#d2b918', chart_type, current_data, year_ago_data, measures.labels);
      }

      function build_month_muscle_lost_chart(measures, chart_type) {
        let current_data = [];
        for (const lost of measures.month_average_measures) {
          current_data.push(lost.lost_muscle);
        }
        let year_ago_data = [];
        measures.year_ago_month_average_measures.forEach(measure => {
          year_ago_data.push(measure.lost_muscle);
        });
        return build_chart_settings('Lost Muscle Kg', '#6fb374', chart_type, current_data, year_ago_data, measures.labels);
      }

      function build_month_upper_pressure_lost_chart(measures, chart_type) {
        let current_data = [];
        for (const lost of measures.month_average_measures) {
          current_data.push(lost.lost_upper);
        }
        let year_ago_data = [];
        measures.year_ago_month_average_measures.forEach(measure => {
          year_ago_data.push(measure.lost_upper);
        });
        return build_chart_settings('Lost Upper Blood Pressure mm Hg', '#c95110', chart_type, current_data, year_ago_data, measures.labels);
      }

      function build_month_lower_pressure_lost_chart(measures, chart_type) {
        let current_data = [];
        for (const lost of measures.month_average_measures) {
          current_data.push(lost.lost_lower);
        }
        let year_ago_data = [];
        measures.year_ago_month_average_measures.forEach(measure => {
          year_ago_data.push(measure.lost_lower);
        });
        return build_chart_settings('Lost Lower Blood Pressure mm Hg', '#06a089', chart_type, current_data, year_ago_data, measures.labels);
      }

      function build_month_upper_pressure_chart(measures, chart_type) {
        let current_data = [];
        for (const measure of measures.month_average_measures) {
          current_data.push(measure.upper);
        }
        let year_ago_data = [];
        measures.year_ago_month_average_measures.forEach(measure => {
          year_ago_data.push(measure.upper);
        });
        return build_chart_settings('Upper Pressure mm Hg', '#c95110', chart_type, current_data, year_ago_data, measures.labels);
      }

      function build_month_lower_pressure_chart(measures, chart_type) {
        let current_data = [];
        for (const measure of measures.month_average_measures) {
          current_data.push(measure.lower);
        }
        let year_ago_data = [];
        measures.year_ago_month_average_measures.forEach(measure => {
          year_ago_data.push(measure.lower);
        });
        return build_chart_settings('Lower Pressure mm Hg', '#06a089', chart_type, current_data, year_ago_data, measures.labels);
      }

      function build_month_muscle_chart(measures, chart_type) {
        let current_data = [];
        for (const measure of measures.month_average_measures) {
          current_data.push(measure.muscle);
        }
        let year_ago_data = [];
        measures.year_ago_month_average_measures.forEach(measure => {
          year_ago_data.push(measure.muscle);
        });
        return build_chart_settings('Muscle %', '#06a01b', chart_type, current_data, year_ago_data, measures.labels);
      }

      function build_month_fat_chart(measures, chart_type) {
        let current_data = [];
        for (const measure of measures.month_average_measures) {
          current_data.push(measure.fat);
        }
        let year_ago_data = [];
        measures.year_ago_month_average_measures.forEach(measure => {
          year_ago_data.push(measure.fat);
        });
        return build_chart_settings('Fat %', '#c91016', chart_type, current_data, year_ago_data, measures.labels);
      }

      function build_month_weight_chart(measures, chart_type) {
        let current_data = [];
        measures.month_average_measures.forEach(measure => {
          current_data.push(measure.weight);
        });
        let year_ago_data = [];
        measures.year_ago_month_average_measures.forEach(measure => {
          year_ago_data.push(measure.weight);
        });
        return build_chart_settings('Weight Kg', '#1a36c1', chart_type, current_data, year_ago_data, measures.labels);
      }

      function get_month_measures_from(from_date, weights, blood_pressures) {
        let month_measure = {
          labels: [],
          month_average_measures: [],
          year_ago_month_average_measures: []
        };
        let current_date = dayjs(from_date);
        let next_month = dayjs().add(1, 'month').toDate();
        var month_average_weight;
        var month_average_blood_pressure;
        while (current_date.toDate() <= next_month) {
          month_measure.labels.push(current_date.format('MMM-YYYY'));
          month_average_weight = summaryService.get_month_average_weights_for(current_date, weights) || month_average_weight;
          month_average_blood_pressure = summaryService.get_month_average_blood_pressures_for(current_date, blood_pressures) || month_average_blood_pressure;
          month_measure.month_average_measures.push(build_measure_graph_date(month_average_weight, month_average_blood_pressure))
          current_date = current_date.add(1, 'month')
        }
        let year_ago_current_date = dayjs(from_date).subtract(1, 'year');
        let year_ago_next_month = dayjs(next_month).subtract(1, 'year').toDate();
        var year_ago_month_average_weight;
        var year_ago_month_average_blood_pressure;
        while (year_ago_current_date.toDate() <= year_ago_next_month) {
          year_ago_month_average_weight = summaryService.get_month_average_weights_for(year_ago_current_date, weights) || year_ago_month_average_weight;
          year_ago_month_average_blood_pressure = summaryService.get_month_average_blood_pressures_for(year_ago_current_date, blood_pressures) || year_ago_month_average_blood_pressure;
          month_measure.year_ago_month_average_measures.push(build_measure_graph_date(year_ago_month_average_weight, year_ago_month_average_blood_pressure))
          year_ago_current_date = year_ago_current_date.add(1, 'month')
        }
        return month_measure;
      }

      function build_chart_settings(title, color, chart_type, current_data, year_ago_data, labels) {
        let data = {
          labels: labels,
          datasets: [
            {
              label: 'Current',
              borderColor: color,
              fill: false,
              data: current_data
            }
          ]
        };
        if (chart_type != 'all') {
          data.datasets.push({
            label: 'Year Ago',
            borderColor: '#a35da5',
            fill: false,
            data: year_ago_data
          });
        }
        return {
          data: data,
          options: {
            plugins: {
              title: {
                display: true,
                text: title
              }
            }
          }
        }
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
        if (chart_type === 'all') {
          return get_first_date_measure(weights, blood_pressures);
        }
        if (chart_type === 'monthly') {
          return dayjs().subtract(3, 'month').toDate();
        }
        return dayjs().subtract(1, 'year').toDate();
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
