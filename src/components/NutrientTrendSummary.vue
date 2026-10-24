<template>
  <section class="nutrient-trend-summary" aria-label="Nutrient trends">
    <p v-if="loading" role="status">Loading nutrient trends…</p>
    <p v-else-if="error" role="alert">{{ error }}</p>
    <div v-else>
      <p v-if="targetLoadFailed" role="status">Targets unavailable; averages shown.</p>
      <div class="p-grid">
        <template v-for="field in fields" :key="field.key">
          <div class="p-col-5">{{ field.label }}:</div>
          <div class="p-col-7">
            <span :class="status(field).className">{{ averageLabel(field) }}</span>
            <small>{{ changeLabel(field) }}</small>
            <small v-if="status(field).label" :class="status(field).className">{{ status(field).label }}</small>
          </div>
        </template>
      </div>
    </div>
  </section>
</template>
<script>
import dayjs from 'dayjs';
import nutritionService from '../services/NutritionService';
import {trendNutrientFields} from '../model/Dish';
import {coveredNutrientDays, averageNutrientLabel, nutrientChangeLabel, nutrientMinimumStatus, macroTargetStatus} from '../model/NutrientTrends';

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
  data() { return {fields, summaries: [], targets: null, fatPercentage: null, targetLoadFailed: false, loading: true, error: null, requestId: 0}; },
  computed: {
    dateKey() { return dayjs(this.date).format('YYYY-MM-DD'); },
    loadKey() { return `${this.dateKey}|${this.mealRevision}|${this.completed}`; }
  },
  watch: {loadKey: {immediate: true, handler() { this.load(); }}},
  methods: {
    currentRows(field) { return coveredNutrientDays(this.summaries, this.dateKey, 30, field); },
    previousRows(field) { return coveredNutrientDays(this.summaries, dayjs(this.dateKey).subtract(30, 'day').format('YYYY-MM-DD'), 30, field); },
    averageLabel(field) { return averageNutrientLabel(this.currentRows(field), field); },
    changeLabel(field) { return nutrientChangeLabel(this.currentRows(field), this.previousRows(field), field); },
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
.nutrient-trend-summary { margin-top: .5rem; }
.nutrient-trend-summary small { display: block; font-size: .8rem; line-height: 1.2rem; }
.target-met { color: #2f855a; }
.target-missed { color: #9c6500; }
</style>
