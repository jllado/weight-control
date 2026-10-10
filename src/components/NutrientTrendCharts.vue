<template>
  <section class="nutrient-trend-charts" aria-label="Nutrient charts">
    <p v-if="loading" role="status">Loading nutrient charts…</p>
    <p v-else-if="error" role="alert">{{ error }}</p>
    <p v-else-if="!fieldsWithData.length">No completed days with complete nutrient coverage in the selected period.</p>
    <template v-else>
      <p class="trend-legend">Green points are sourced values; amber points include food estimates. Missing days are left blank.</p>
      <div v-for="field in fieldsWithData" :key="field.key" class="trend-chart">
        <h4>{{ field.label }} ({{ field.unit }})</h4>
        <div class="chart-canvas"><Chart class="nutrient-line-chart" type="line" :data="chart(field)" :options="options" :canvasProps="{role: 'img', 'aria-label': `${field.label} nutrient trend`, 'aria-describedby': `nutrient-chart-${field.key}`}" /></div>
        <p class="sr-only" :id="`nutrient-chart-${field.key}`">{{ chartDescription(field) }}</p>
        <details class="chart-data">
          <summary>Show data</summary>
          <table>
            <thead><tr><th scope="col">Date</th><th scope="col">Value or reason</th></tr></thead>
            <tbody><tr v-for="row in chartRows(field)" :key="row.date"><th scope="row">{{ row.date }}</th><td>{{ row.description }}</td></tr></tbody>
          </table>
        </details>
      </div>
    </template>
  </section>
</template>
<script>
import dayjs from 'dayjs';
import nutritionService from '../services/NutritionService';
import {nutrientFields} from '../model/Dish';
import {coveredNutrientDays, nutrientChartData, nutrientChartDescription, nutrientChartRows} from '../model/NutrientTrends';

export default {
  props: {date: {type: [String, Date], required: true}, chartType: {type: String, required: true}, mealRevision: {type: Number, required: true}, completed: {type: Boolean, required: true}},
  data() {
    return {fields: nutrientFields, summaries: [], loading: true, error: null, requestId: 0, options: {responsive: true, maintainAspectRatio: false, plugins: {legend: {display: false}}, scales: {y: {beginAtZero: true}, x: {ticks: {autoSkip: true, maxTicksLimit: 12}}}}};
  },
  computed: {
    dateKey() { return dayjs(this.date).format('YYYY-MM-DD'); },
    loadKey() { return `${this.dateKey}|${this.chartType}|${this.mealRevision}|${this.completed}`; },
    periodStart() {
      if (this.chartType === 'all') {
        const dates = this.summaries.filter(item => !dayjs(item.date).isAfter(this.dateKey, 'day')).map(item => dayjs(item.date));
        return dates.length ? dates.reduce((first, date) => date.isBefore(first) ? date : first).format('YYYY-MM-DD') : this.dateKey;
      }
      return dayjs(this.dateKey).subtract(this.chartType === 'monthly' ? 89 : 364, 'day').format('YYYY-MM-DD');
    },
    fieldsWithData() { return this.fields.filter(field => coveredNutrientDays(this.summaries, this.dateKey, dayjs(this.dateKey).diff(dayjs(this.periodStart), 'day') + 1, field).length); }
  },
  watch: {loadKey: {immediate: true, handler() { this.load(); }}},
  methods: {
    async load() {
      const requestId = ++this.requestId;
      this.loading = true;
      this.error = null;
      const from = this.chartType === 'monthly' ? dayjs(this.dateKey).subtract(89, 'day').format('YYYY-MM-DD') : null;
      try {
        const summaries = await nutritionService.get_daily_summaries(from, from ? this.dateKey : undefined);
        if (requestId === this.requestId) this.summaries = summaries;
      } catch (error) { if (requestId === this.requestId) this.error = error.message; }
      finally { if (requestId === this.requestId) this.loading = false; }
    },
    chart(field) { return nutrientChartData(this.summaries, this.periodStart, this.dateKey, field); },
    chartDescription(field) { return nutrientChartDescription(this.summaries, this.periodStart, this.dateKey, field); },
    chartRows(field) { return nutrientChartRows(this.summaries, this.periodStart, this.dateKey, field); }
  }
};
</script>
<style scoped>
.trend-legend { font-size: .85rem; }
.trend-chart { min-width: 0; margin: 1rem 0; }
.chart-canvas { height: 12rem; position: relative; }
.nutrient-line-chart { height: 100%; }
.trend-chart h4 { margin-bottom: .25rem; }
.chart-data { margin: .5rem 0 1rem; }
.chart-data summary { cursor: pointer; font-weight: 600; }
.chart-data table { width: 100%; border-collapse: collapse; }
.chart-data th, .chart-data td { padding: .35rem; border-bottom: 1px solid #dce4ea; text-align: left; overflow-wrap: anywhere; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
</style>
