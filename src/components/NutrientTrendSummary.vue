<template>
  <section class="nutrient-trend-summary" aria-label="Nutrient trends">
    <p v-if="loading" role="status">Loading nutrient trends…</p>
    <p v-else-if="error" role="alert">{{ error }}</p>
    <div v-else>
      <div class="trend-heading">30-day averages ending {{ dateKey }}</div>
      <ul class="trend-summary">
        <li v-for="field in fields" :key="field.key">
          <strong>{{ field.label }}</strong><span>· {{ average(field) }} {{ field.unit }}/day · {{ covered(field).length }} <span>covered<span class="sr-only"> completed day{{ covered(field).length === 1 ? '' : 's' }}</span></span></span>
        </li>
      </ul>
    </div>
  </section>
</template>
<script>
import dayjs from 'dayjs';
import nutritionService from '../services/NutritionService';
import {nutrientFields, formatNutritionValue} from '../model/Dish';
import {coveredNutrientDays, averageNutrientValue} from '../model/NutrientTrends';

export default {
  props: {date: {type: [String, Date], required: true}, mealRevision: {type: Number, required: true}, completed: {type: Boolean, required: true}},
  data() { return {fields: nutrientFields, summaries: [], loading: true, error: null, requestId: 0}; },
  computed: {
    dateKey() { return dayjs(this.date).format('YYYY-MM-DD'); },
    loadKey() { return `${this.dateKey}|${this.mealRevision}|${this.completed}`; }
  },
  watch: {loadKey: {immediate: true, handler() { this.load(); }}},
  methods: {
    covered(field) { return coveredNutrientDays(this.summaries, this.dateKey, 30, field); },
    average(field) {
      const rows = this.covered(field);
      return rows.length ? formatNutritionValue(Number(averageNutrientValue(rows, field))) : '—';
    },
    async load() {
      const requestId = ++this.requestId;
      this.loading = true;
      this.error = null;
      const from = dayjs(this.dateKey).subtract(29, 'day').format('YYYY-MM-DD');
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
.nutrient-trend-summary { margin-top: .5rem; }
.trend-heading { font-size: .85rem; }
.trend-summary { list-style: none; padding: 0; margin: .35rem 0; font-size: .8rem; }
.trend-summary li { display: flex; gap: .25rem; margin: .25rem 0; }
.trend-summary strong, .trend-summary li > span { white-space: nowrap; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
@media (max-width: 575px) {
  .trend-summary { font-size: .75rem; }
}
</style>
