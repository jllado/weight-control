<template>
  <section class="nutrient-trend-summary" aria-label="Nutrient trends">
    <p v-if="loading" role="status">Loading nutrient trends…</p>
    <p v-else-if="error" role="alert">{{ error }}</p>
    <div v-else class="p-grid">
      <template v-for="field in fields" :key="field.key">
        <div class="p-col-5">{{ field.label }}:</div>
        <div class="p-col-7">{{ average(field) }} {{ field.unit }}/day</div>
      </template>
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
</style>
