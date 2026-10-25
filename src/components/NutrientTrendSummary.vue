<template>
  <section class="nutrient-trend-summary" aria-label="Nutrient trends">
    <p v-if="loading" role="status">Loading nutrient trends…</p>
    <p v-else-if="error" role="alert">{{ error }}</p>
    <div v-else>
      <div class="nutrient-trend-rows">
        <div v-for="field in fields" :key="field.key" class="nutrient-trend-row">
          <strong>{{ field.label }}</strong>
          <div class="nutrient-trend-result">
            <span>{{ averageLabel(field) }}</span>
            <span class="trend-day-count">· {{ currentRows(field).length }} {{ currentRows(field).length === 1 ? 'day' : 'days' }}</span>
          </div>
          <div class="nutrient-trend-context">
            <span class="trend-change"><span aria-hidden="true">{{ changeSummary(field) }}</span><span class="sr-only">{{ changeAccessibleLabel(field) }}</span></span>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
<script>
import dayjs from 'dayjs';
import nutritionService from '../services/NutritionService';
import {trendNutrientFields} from '../model/Dish';
import {coveredNutrientDays, averageNutrientLabel, nutrientChangeLabel} from '../model/NutrientTrends';

const fields = [
  {key: 'proteinGrams', label: 'Protein', unit: 'g', kind: 'macro'},
  {key: 'carbohydrateGrams', label: 'Carbs', unit: 'g', kind: 'macro'},
  {key: 'fatGrams', label: 'Fat', unit: 'g', kind: 'macro'},
  ...trendNutrientFields
];

export default {
  props: {date: {type: [String, Date], required: true}, mealRevision: {type: Number, required: true}, completed: {type: Boolean, required: true}},
  data() { return {fields, summaries: [], loading: true, error: null, requestId: 0}; },
  computed: {
    dateKey() { return dayjs(this.date).format('YYYY-MM-DD'); },
    previousEnd() { return dayjs(this.dateKey).subtract(30, 'day').format('YYYY-MM-DD'); },
    loadKey() { return `${this.dateKey}|${this.mealRevision}|${this.completed}`; }
  },
  watch: {loadKey: {immediate: true, handler() { this.load(); }}},
  methods: {
    currentRows(field) { return coveredNutrientDays(this.summaries, this.dateKey, 30, field); },
    previousRows(field) { return coveredNutrientDays(this.summaries, this.previousEnd, 30, field); },
    averageLabel(field) { return averageNutrientLabel(this.currentRows(field), field); },
    changeLabel(field) { return nutrientChangeLabel(this.currentRows(field), this.previousRows(field), field); },
    hasComparablePeriods(field) { return this.currentRows(field).length > 0 && this.previousRows(field).length > 0; },
    changeAccessibleLabel(field) {
      if (!this.currentRows(field).length) return 'Current 30d: no data';
      if (!this.previousRows(field).length) return 'Prior 30d: no data';
      return `${field.label} change versus previous 30 days: ${this.changeLabel(field)} ${field.unit}/day`;
    },
    changeSummary(field) { return this.hasComparablePeriods(field) ? this.changeLabel(field) : '—'; },
    async load() {
      const requestId = ++this.requestId;
      this.loading = true;
      this.error = null;
      const from = dayjs(this.dateKey).subtract(59, 'day').format('YYYY-MM-DD');
      try {
        const summaries = await nutritionService.get_daily_summaries(from, this.dateKey);
        if (requestId === this.requestId) this.summaries = summaries;
      } catch (error) { if (requestId === this.requestId) this.error = error.message; }
      finally { if (requestId === this.requestId) this.loading = false; }
    }
  }
};
</script>
<style scoped>
.nutrient-trend-rows { display: grid; gap: 0; }
.nutrient-trend-row { display: grid; grid-template-columns: minmax(7rem, 1fr) minmax(0, 1.2fr) minmax(0, .8fr); align-items: baseline; gap: .35rem .75rem; padding: .55rem 0; border-top: 1px solid #e6edf1; min-width: 0; }
.nutrient-trend-result, .nutrient-trend-context { min-width: 0; }
.nutrient-trend-context { overflow-wrap: anywhere; padding-inline-end: .25rem; }
.trend-day-count, .trend-change { color: #5a6770; }
.trend-change { font-size: .85rem; }
@media (max-width: 640px) {
  .nutrient-trend-row { grid-template-columns: minmax(0, 1fr) minmax(2.5rem, 30%); gap: .2rem .5rem; align-items: center; }
  .nutrient-trend-result { grid-column: 1; }
  .nutrient-trend-context { grid-column: 2; grid-row: 1 / 3; justify-self: end; text-align: right; }
}
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
</style>
