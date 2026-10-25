<template>
  <section class="nutrient-trend-summary" aria-label="Nutrient trends">
    <p v-if="loading" role="status">Loading nutrient trends…</p>
    <p v-else-if="error" role="alert">{{ error }}</p>
    <div v-else>
      <h3>30-day nutrient trends</h3>
      <p v-if="targetLoadFailed" role="status">Targets unavailable; averages shown.</p>
      <div class="nutrient-trend-rows">
        <div v-for="field in fields" :key="field.key" class="nutrient-trend-row">
          <strong>{{ field.label }}</strong>
          <div class="nutrient-trend-result">
            <span :class="status(field).className">{{ averageLabel(field) }}</span>
            <span class="trend-day-count">· {{ currentRows(field).length }} {{ currentRows(field).length === 1 ? 'day' : 'days' }}</span>
          </div>
          <div class="nutrient-trend-context">
            <span class="trend-change"><span :aria-hidden="!hasComparablePeriods(field)">{{ changeSummary(field) }}</span><span v-if="!hasComparablePeriods(field)" class="sr-only">{{ changeDetailLabel(field) }}</span></span>
            <span v-if="status(field).label" class="trend-status" :class="status(field).className" :aria-label="status(field).label">{{ shortStatus(field) }}</span>
          </div>
          <button type="button" class="trend-detail" :aria-label="`${field.label} trend details`" @click="openDetails(field, $event)">Details</button>
        </div>
      </div>
    </div>
    <Dialog v-if="selectedField" :header="`${selectedField.label} trend details`" :visible="true" modal appendTo="body" :style="{width: 'min(28rem, 95vw)'}" @update:visible="closeDetails">
      <p>Current 30 days: {{ periodLabel(dateKey) }}. {{ eligibleLabel(currentRows(selectedField)) }}.</p>
      <p>Previous 30 days: {{ periodLabel(previousEnd) }}. {{ eligibleLabel(previousRows(selectedField)) }}.</p>
      <p>Change: {{ changeDetailLabel(selectedField) }}.</p>
      <p v-if="selectedField.key === 'epaDhaMilligrams'">EPA and DHA are omega-3 fats commonly found in fish and seafood; they are separate from ALA and historical total omega-3.</p>
      <p>Target: {{ targetCriterion(selectedField) }}. {{ status(selectedField).label || 'No comparison available' }}. <span v-if="selectedField.kind === 'macro'">Rounded target display does not change the full-precision comparison. </span>This comparison uses eligible days only; it does not describe missing days or diagnose a deficiency.</p>
      <p>{{ selectedDayDetail(selectedField) }}</p>
    </Dialog>
  </section>
</template>
<script>
import dayjs from 'dayjs';
import nutritionService from '../services/NutritionService';
import {trendNutrientFields} from '../model/Dish';
import {coveredNutrientDays, averageNutrientLabel, nutrientChangeLabel, nutrientMinimumStatus, macroTargetStatus, meanMacroTargetLabel, isNutrientCovered, nutrientCoverage} from '../model/NutrientTrends';

const macroFields = [
  {key: 'proteinGrams', label: 'Protein', unit: 'g', kind: 'macro', targetKey: 'protein'},
  {key: 'carbohydrateGrams', label: 'Carbs', unit: 'g', kind: 'macro', targetKey: 'carbohydrate'},
  {key: 'fatGrams', label: 'Fat', unit: 'g', kind: 'macro', targetKey: 'fat'}
];
const fields = [
  ...macroFields, {...trendNutrientFields[0], targetKey: 'vitaminD'},
  {...trendNutrientFields[1], targetKey: 'epaDha'},
  {...trendNutrientFields[2], targetKey: 'magnesium'}
];

export default {
  props: {date: {type: [String, Date], required: true}, mealRevision: {type: Number, required: true}, completed: {type: Boolean, required: true}},
  data() { return {fields, summaries: [], targets: null, fatPercentage: null, targetLoadFailed: false, loading: true, error: null, requestId: 0, selectedField: null, detailTrigger: null}; },
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
    changeDetailLabel(field) {
      if (!this.currentRows(field).length) return 'Current 30d: no data';
      if (!this.previousRows(field).length) return 'Prior 30d: no data';
      return this.changeLabel(field);
    },
    changeSummary(field) {
      const change = this.changeLabel(field);
      return change === 'Prior 30d: no data' ? '—' : change.replace(' vs previous 30 days', '');
    },
    shortStatus(field) {
      return {'At or above current minimum': 'Meets min', 'Below current minimum': 'Below min',
        'Within current ceiling': 'Within max', 'Above current ceiling': 'Above max'}[this.status(field).label] || '';
    },
    periodLabel(end) { return `${dayjs(end).subtract(29, 'day').format('D MMM YYYY')}–${dayjs(end).format('D MMM YYYY')}`; },
    eligibleLabel(rows) {
      return `${rows.length} eligible ${rows.length === 1 ? 'day' : 'days'}${rows.length ? `: ${rows.map(row => dayjs(row.date).format('D MMM YYYY')).join(', ')}` : ''}`;
    },
    targetCriterion(field) {
      if (this.targetLoadFailed) return 'Unavailable';
      const rows = this.currentRows(field);
      if (!rows.length) return 'No eligible-day target comparison';
      if (field.kind === 'macro') {
        const mean = meanMacroTargetLabel(rows, field, this.fatPercentage);
        return mean == null ? 'Current target unavailable'
          : `${mean} ${field.unit}/day ${field.targetKey === 'protein' ? 'current minimum' : 'mean dated ceiling'}`;
      }
      const target = this.targets?.[field.targetKey]?.value;
      return target == null ? 'No current target' : `${target} ${field.unit}/day minimum`;
    },
    selectedDayDetail(field) {
      const day = this.summaries.find(row => dayjs(row.date).format('YYYY-MM-DD') === this.dateKey);
      const date = dayjs(this.dateKey).format('D MMM YYYY');
      if (!day) return `${date}: no meal day recorded; excluded from this average.`;
      if (!day.completed) return `${date}: day not marked complete; excluded from this average.`;
      if (isNutrientCovered(day, field)) return `${date}: included in this average.`;
      if (field.kind === 'macro') return `${date}: incomplete recorded macros; excluded from this average.`;
      const coverage = nutrientCoverage(day, field);
      return `${date}: ${coverage.foodsWithValues} of ${day.nutrients.totalFoods} foods have a ${field.label} value; unknown or partial coverage excludes this day from the average.`;
    },
    openDetails(field, event) { this.detailTrigger = event.currentTarget; this.selectedField = field; },
    closeDetails() { this.selectedField = null; this.$nextTick(() => this.detailTrigger.focus()); },
    status(field) {
      if (this.targetLoadFailed) return {label: '', className: ''};
      const rows = this.currentRows(field);
      if (!rows.length) return {label: '', className: ''};
      if (field.kind === 'macro') {
        const status = macroTargetStatus(rows, field, this.fatPercentage);
        return status.className ? status : {label: '', className: ''};
      }
      const target = this.targets?.[field.targetKey]?.value;
      if (target == null) return {label: '', className: ''};
      return nutrientMinimumStatus(rows, field, target);
    },
    async load() {
      const requestId = ++this.requestId;
      this.loading = true;
      this.error = null;
      const from = dayjs(this.dateKey).subtract(59, 'day').format('YYYY-MM-DD');
      try {
        const [summaries, targetRead] = await Promise.all([
          nutritionService.get_daily_summaries(from, this.dateKey),
          nutritionService.get_targets(this.dateKey).then(value => ({value, failed: false}), () => ({value: null, failed: true}))
        ]);
        if (requestId === this.requestId) {
          this.summaries = summaries;
          this.targetLoadFailed = targetRead.failed;
          this.targets = targetRead.value?.targets ?? null;
          this.fatPercentage = targetRead.value?.overrides?.macros?.fatPercentage ?? null;
        }
      } catch (error) { if (requestId === this.requestId) this.error = error.message; }
      finally { if (requestId === this.requestId) this.loading = false; }
    }
  }
};
</script>
<style scoped>
.nutrient-trend-summary { margin-top: .75rem; }
.nutrient-trend-summary h3 { margin: 0 0 .35rem; font-size: 1rem; }
.nutrient-trend-rows { display: grid; gap: 0; }
.nutrient-trend-row { display: grid; grid-template-columns: minmax(7rem, 1fr) minmax(0, 1.2fr) minmax(0, 1fr) auto; align-items: baseline; gap: .35rem .75rem; padding: .55rem 0; border-top: 1px solid #e6edf1; min-width: 0; }
.nutrient-trend-result, .nutrient-trend-context { min-width: 0; }
.nutrient-trend-context { display: flex; flex-wrap: wrap; gap: .25rem .5rem; }
.trend-day-count, .trend-change { color: #5a6770; }
.trend-change, .trend-status { font-size: .85rem; }
.trend-detail { background: none; border: 0; color: var(--primary-color); cursor: pointer; font: inherit; text-decoration: underline; padding: .2rem; }
.trend-detail:focus-visible { outline: 2px solid var(--primary-color); outline-offset: 2px; }
@media (max-width: 640px) {
  .nutrient-trend-row { grid-template-columns: minmax(0, 1fr) auto; gap: .2rem .5rem; }
  .nutrient-trend-result { grid-column: 1; }
  .nutrient-trend-context { grid-column: 1; }
  .trend-detail { grid-column: 2; grid-row: 1 / 4; align-self: center; }
}
.target-met { color: #2f855a; }
.target-missed { color: #9c6500; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
</style>
