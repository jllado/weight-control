<template>
  <section class="nutrient-trend-summary" aria-label="Nutrient trends">
    <p v-if="loading" role="status">Loading nutrient trends…</p>
    <p v-else-if="error" role="alert">{{ error }}</p>
    <div v-else>
      <h4>Nutrient trends</h4>
      <p>Daily averages include completed days with complete nutrient coverage, including labeled estimates. Missing values are left blank.</p>
      <div v-for="days in [7, 30]" :key="days" class="trend-window">
        <h5>{{ days }}-day average ending {{ dateKey }}</h5>
        <ul class="trend-summary">
          <li v-for="field in fields" :key="`${days}-${field.key}`">
            <strong>{{ field.label }}:</strong> {{ average(days, field) }} {{ field.unit }} · {{ covered(days, field).length }} covered completed day{{ covered(days, field).length === 1 ? '' : 's' }}
          </li>
        </ul>
      </div>
    </div>
  </section>
</template>
<script>
import dayjs from 'dayjs';
import nutritionService from '../services/NutritionService';
import {nutrientFields} from '../model/Dish';
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
    covered(days, field) { return coveredNutrientDays(this.summaries, this.dateKey, days, field); },
    average(days, field) { return averageNutrientValue(this.covered(days, field), field); },
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
.nutrient-trend-summary { margin-top: 1rem; }
.trend-window { margin-top: 1rem; }
.trend-summary { padding-left: 1.25rem; margin: .35rem 0; }
.trend-summary strong { margin-right: .25rem; }
.trend-summary li { margin: .25rem 0; overflow-wrap: anywhere; }
</style>
