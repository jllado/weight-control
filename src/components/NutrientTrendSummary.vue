<template>
  <section class="nutrient-trend-summary" aria-label="Nutrient trends">
    <p v-if="loading" role="status">Loading nutrient trends…</p>
    <p v-else-if="error" role="alert">{{ error }}</p>
    <div v-else class="nutrient-trend-rows">
      <div v-for="field in fields" :key="field.key" class="nutrient-trend-row p-grid">
        <div class="p-col-5">Trend {{ field.label }}:</div>
        <div class="p-col-7 nutrient-trend-result">
          <span class="trend-average" :class="targetStatus(field)?.className">{{ averageLabel(field) }}</span>
          <span v-if="targetStatus(field)?.className" class="sr-only" role="status">{{ targetStatus(field).label }}</span>
          <span class="trend-day-count"> · {{ dayCountLabel(field) }}</span>
          <span class="trend-change"><span aria-hidden="true">{{ changeSummary(field) }}</span><span class="sr-only">{{ changeAccessibleLabel(field) }}</span></span>
        </div>
      </div>
    </div>
  </section>
</template>
<script>
import dayjs from 'dayjs';
import nutritionService from '../services/NutritionService';
import {trendNutrientFields} from '../model/Dish';
import {coveredNutrientDays, partialNutrientDays, nutrientCoverage, averageNutrientLabel, nutrientChangeLabel,
  macroTargetStatus, nutrientMinimumStatus} from '../model/NutrientTrends';

const fields = [
  {key: 'proteinGrams', label: 'Protein', unit: 'g', kind: 'macro', targetKey: 'protein'},
  {key: 'carbohydrateGrams', label: 'Carbs', unit: 'g', kind: 'macro', targetKey: 'carbohydrate'},
  {key: 'fatGrams', label: 'Fat', unit: 'g', kind: 'macro', targetKey: 'fat'},
  ...trendNutrientFields
];

export default {
  props: {date: {type: [String, Date], required: true}, mealRevision: {type: Number, required: true}, completed: {type: Boolean, required: true}},
  data() { return {fields, summaries: [], targets: null, loading: true, error: null, requestId: 0}; },
  computed: {
    dateKey() { return dayjs(this.date).format('YYYY-MM-DD'); },
    previousEnd() { return dayjs(this.dateKey).subtract(30, 'day').format('YYYY-MM-DD'); },
    loadKey() { return `${this.dateKey}|${this.mealRevision}|${this.completed}`; }
  },
  watch: {loadKey: {immediate: true, handler() { this.load(); }}},
  methods: {
    currentRows(field) { return coveredNutrientDays(this.summaries, this.dateKey, 30, field); },
    previousRows(field) { return coveredNutrientDays(this.summaries, this.previousEnd, 30, field); },
    partialRows(field) { return field.key === 'fiberGrams' ? partialNutrientDays(this.summaries, this.dateKey, 30, field) : []; },
    hasPartialFallback(field) { return !this.currentRows(field).length && this.partialRows(field).length > 0; },
    averageLabel(field) { return this.hasPartialFallback(field) ? `≥${averageNutrientLabel(this.partialRows(field), field)}` : averageNutrientLabel(this.currentRows(field), field); },
    dayCountLabel(field) {
      const count = this.hasPartialFallback(field) ? this.partialRows(field).length : this.currentRows(field).length;
      return `${count} ${this.hasPartialFallback(field) ? 'partial ' : ''}${count === 1 ? 'day' : 'days'}`;
    },
    changeLabel(field) { return nutrientChangeLabel(this.currentRows(field), this.previousRows(field), field); },
    hasComparablePeriods(field) { return this.currentRows(field).length > 0 && this.previousRows(field).length > 0; },
    changeAccessibleLabel(field) {
      if (this.hasPartialFallback(field)) {
        const coverage = this.partialRows(field).map(row => `${dayjs(row.date).format('YYYY-MM-DD')} ${nutrientCoverage(row, field).foodsWithValues}/${row.nutrients.totalFoods} foods${row.nutrients.mealsWithoutFoods ? `; ${row.nutrients.mealsWithoutFoods} meal(s) without foods` : ''}`).join(', ');
        return `${field.label} current 30 days: recorded partial lower bound from ${coverage}; zero fully covered days; change versus previous 30 days unavailable`;
      }
      if (!this.currentRows(field).length) return 'Current 30d: no data';
      if (!this.previousRows(field).length) return 'Prior 30d: no data';
      return `${field.label} change versus previous 30 days: ${this.changeLabel(field)} ${field.unit}/day`;
    },
    changeSummary(field) { return this.hasComparablePeriods(field) ? this.changeLabel(field) : '—'; },
    targetStatus(field) {
      const rows = this.currentRows(field);
      if (!rows.length || !this.targets) return null;
      if (field.kind === 'macro') return macroTargetStatus(rows, field, this.targets.overrides.macros.fatPercentage);
      if (field.key === 'omega3Milligrams') return null;
      const key = {vitaminDMicrograms: 'vitaminD', magnesiumMilligrams: 'magnesium', fiberGrams: 'fiber'}[field.key];
      const target = this.targets.targets[key]?.value;
      return target == null ? null : nutrientMinimumStatus(rows, field, target);
    },
    async load() {
      const requestId = ++this.requestId;
      this.loading = true;
      this.error = null;
      const from = dayjs(this.dateKey).subtract(59, 'day').format('YYYY-MM-DD');
      try {
        const [summaries, targets] = await Promise.all([
          nutritionService.get_daily_summaries(from, this.dateKey), nutritionService.get_targets(this.dateKey).catch(() => null)
        ]);
        if (requestId === this.requestId) {
          this.summaries = summaries;
          this.targets = targets;
        }
      } catch (error) { if (requestId === this.requestId) this.error = error.message; }
      finally { if (requestId === this.requestId) this.loading = false; }
    }
  }
};
</script>
<style scoped>
.nutrient-trend-row { margin: 0 -.5rem; }
.nutrient-trend-row > * { overflow-wrap: anywhere; min-width: 0; }
.nutrient-trend-result { padding-inline-end: .75rem; }
.trend-day-count, .trend-change { color: #5a6770; }
.trend-change { font-size: .85rem; margin-inline-start: .35rem; }
.target-met { color: #2f855a; }
.target-missed { color: #c05621; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
</style>
