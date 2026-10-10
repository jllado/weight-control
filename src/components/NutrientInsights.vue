<template>
  <section class="nutrient-insights" aria-label="Nutrient targets and trends">
    <p v-if="loading" role="status">Loading nutrient targets and trends…</p>
    <p v-else-if="error" role="alert">{{ error }}</p>
    <div v-else-if="targets">
      <ul class="target-status">
        <li v-for="field in fields" :key="field.key">
          <strong>{{ field.label }}:</strong>
          <template v-if="target(field).value != null">
            {{ target(field).value }} {{ field.unit }} {{ target(field).source === 'PERSONAL' ? 'personal target' : 'adult reference' }}
            <span v-if="complete">· {{ progress(field) }}% of target · {{ comparison(field) }}</span>
            <span v-else>· comparison unavailable</span>
          </template>
          <span v-else>No target set</span>
        </li>
      </ul>
      <small v-if="!completed">Day not marked complete; recorded totals are provisional.</small>
      <small v-if="!fullCoverage">Comparison requires complete recorded food coverage.</small>
      <small v-if="summary.estimatedFoods">Totals include estimated food values.</small>
      <small>References compare recorded intake; they do not diagnose deficiency or excess.</small>
      <details class="nutrient-trends">
        <summary>Nutrient trends</summary>
        <div v-for="days in [7, 30]" :key="days" class="trend-window">
          <h4>{{ days }} days ending {{ dateKey }}</h4>
          <p>Daily averages use completed days with complete nutrient coverage; missing values are left blank.</p>
          <p class="trend-legend">Green points are sourced values; amber points include food estimates.</p>
          <div v-for="field in fields" :key="`${days}-${field.key}`" class="trend-chart">
            <h5>{{ field.label }} ({{ field.unit }})</h5>
            <div class="chart-canvas"><Chart class="nutrient-line-chart" type="line" :data="chart(days, field)" :options="options" :canvasProps="{role: 'img', 'aria-label': `${field.label}, ${days}-day daily trend`, 'aria-describedby': `nutrient-chart-${days}-${field.key}`}" /></div>
            <p class="sr-only" :id="`nutrient-chart-${days}-${field.key}`">{{ chartDescription(days, field) }}</p>
            <small>Average: {{ average(days, field) }} {{ field.unit }} · {{ covered(days, field).length }} covered completed day{{ covered(days, field).length === 1 ? '' : 's' }}</small>
          </div>
        </div>
      </details>
    </div>
  </section>
</template>
<script>
import dayjs from 'dayjs';
import nutritionService from '../services/NutritionService';
import {nutrientFields} from '../model/Dish';
export default {
  props: {date: {type: [String, Date], required: true}, summary: {type: Object, required: true}, completed: {type: Boolean, required: true}, mealRevision: {type: Number, required: true}},
  data() { return {fields: nutrientFields, targets: null, summaries: [], loading: false, error: null, requestId: 0, options: {responsive: true, maintainAspectRatio: false, plugins: {legend: {display: false}}, scales: {y: {beginAtZero: true}}}}; },
  computed: {
    dateKey() { return dayjs(this.date).format('YYYY-MM-DD'); },
    fullCoverage() { return this.summary.totalFoods > 0 && this.summary.foodsWithValues === this.summary.totalFoods && this.summary.mealsWithoutFoods === 0; },
    complete() { return this.completed && this.fullCoverage; },
    dataKey() { return [this.dateKey, this.completed, this.mealRevision, this.summary.vitaminDMicrograms, this.summary.omega3Milligrams, this.summary.magnesiumMilligrams, this.summary.foodsWithValues, this.summary.totalFoods, this.summary.mealsWithoutFoods, this.summary.estimatedFoods].join('|'); }
  },
  watch: {dataKey: {immediate: true, handler() { this.load(); }}},
  methods: {
    async load() {
      const requestId = ++this.requestId;
      this.loading = true; this.error = null;
      const start = dayjs(this.dateKey).subtract(29, 'day').format('YYYY-MM-DD');
      try {
        const [targetResponse, summaries] = await Promise.all([nutritionService.get_targets(this.dateKey), nutritionService.get_daily_summaries(start, this.dateKey)]);
        if (requestId === this.requestId) { this.targets = targetResponse.targets; this.summaries = summaries; }
      } catch (error) { if (requestId === this.requestId) this.error = error.message; }
      finally { if (requestId === this.requestId) this.loading = false; }
    },
    target(field) { return this.targets[{vitaminDMicrograms: 'vitaminD', omega3Milligrams: 'omega3', magnesiumMilligrams: 'magnesium'}[field.key]]; },
    progress(field) { return Number((this.summary[field.key] / this.target(field).value * 100).toFixed(2)); },
    comparison(field) {
      const value = this.target(field);
      const reached = this.summary[field.key] >= value.value;
      return `${reached ? 'At or above' : 'Below'} ${value.source === 'PERSONAL' ? 'personal target' : 'adult reference'}`;
    },
    isCovered(item, field) {
      const nutrients = item?.nutrients;
      return item?.completed && nutrients?.[field.key] != null && nutrients.totalFoods > 0 && nutrients.foodsWithValues === nutrients.totalFoods && nutrients.mealsWithoutFoods === 0;
    },
    covered(days, field) {
      const start = dayjs(this.dateKey).subtract(days - 1, 'day');
      return this.summaries.filter(item => !dayjs(item.date).isBefore(start, 'day') && this.isCovered(item, field));
    },
    average(days, field) { const rows = this.covered(days, field); return rows.length ? (rows.reduce((sum, item) => sum + item.nutrients[field.key], 0) / rows.length).toFixed(2) : '—'; },
    chart(days, field) {
      const start = dayjs(this.dateKey).subtract(days - 1, 'day');
      const rows = new Map(this.summaries.map(item => [dayjs(item.date).format('YYYY-MM-DD'), item]));
      const labels = Array.from({length: days}, (_, i) => start.add(i, 'day'));
      const data = labels.map(day => { const row = rows.get(day.format('YYYY-MM-DD')); return this.isCovered(row, field) ? row.nutrients[field.key] : null; });
      const pointColors = labels.map(day => rows.get(day.format('YYYY-MM-DD'))?.nutrients?.estimatedFoods ? '#d69e2e' : '#2f855a');
      return {labels: labels.map(day => day.format('D MMM')), datasets: [{label: `${field.label} (${field.unit})`, data, spanGaps: false, borderColor: '#2f855a', backgroundColor: '#2f855a', pointBackgroundColor: pointColors, pointBorderColor: pointColors, pointRadius: 4, tension: 0.25}]};
    },
    chartDescription(days, field) {
      const start = dayjs(this.dateKey).subtract(days - 1, 'day');
      const rows = new Map(this.summaries.map(item => [dayjs(item.date).format('YYYY-MM-DD'), item]));
      return Array.from({length: days}, (_, i) => {
        const day = start.add(i, 'day');
        const item = rows.get(day.format('YYYY-MM-DD'));
        const nutrients = item?.nutrients;
        if (!item) return `${day.format('YYYY-MM-DD')}: no daily total recorded`;
        if (!item.completed) return `${day.format('YYYY-MM-DD')}: day not marked complete`;
        if (!this.isCovered(item, field)) return `${day.format('YYYY-MM-DD')}: incomplete nutrient coverage (${nutrients?.foodsWithValues ?? 0}/${nutrients?.totalFoods ?? 0} foods; ${nutrients?.mealsWithoutFoods ?? 0} meals without foods)`;
        return `${day.format('YYYY-MM-DD')}: ${nutrients[field.key]} ${field.unit}${nutrients.estimatedFoods ? ' (includes estimates)' : ''}`;
      }).join('; ');
    }
  }
};
</script>
<style scoped>
.target-status { padding-left: 1.25rem; }
.target-status li { margin: .35rem 0; overflow-wrap: anywhere; }
.nutrient-insights small { display: block; }
.nutrient-trends { margin-top: 1rem; }
.nutrient-trends summary { cursor: pointer; font-weight: 600; }
.trend-window { margin-top: 1rem; }
.trend-chart { min-width: 0; margin: 1rem 0; }
.chart-canvas { height: 12rem; position: relative; }
.nutrient-line-chart { height: 100%; }
.trend-chart h5 { margin-bottom: .25rem; }
.trend-legend { font-size: .85rem; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
</style>
