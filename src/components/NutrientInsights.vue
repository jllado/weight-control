<template>
  <section class="nutrient-insights" aria-label="Nutrient target comparisons">
    <p v-if="loading" role="status">Loading nutrient targets and trends…</p>
    <p v-else-if="error" role="alert">{{ error }}</p>
    <div v-else-if="targets">
      <ul class="target-status">
        <li v-for="field in fields" :key="field.key">
          <strong>{{ field.label }}:</strong>
          <template v-if="target(field).value != null">
            {{ target(field).value }} {{ field.unit }} {{ target(field).source === 'PERSONAL' ? 'personal target' : 'adult reference' }}
            <span v-if="complete">· {{ progress(field) }}% of target · {{ comparison(field) }}</span>
            <span v-else>· comparison unavailable until {{ completed ? 'nutrient coverage is complete' : 'the day is marked complete' }}</span>
          </template>
          <span v-else>No personal target set</span>
        </li>
      </ul>
      <small v-if="!completed">Day not marked complete; recorded totals are provisional.</small>
      <small v-else-if="!fullCoverage">Comparison requires complete recorded food coverage.</small>
      <small v-if="summary.estimatedFoods">Totals include estimated food values.</small>
      <small>References compare recorded intake; they do not diagnose deficiency or excess.</small>
    </div>
  </section>
</template>
<script>
import dayjs from 'dayjs';
import nutritionService from '../services/NutritionService';
import {nutrientFields} from '../model/Dish';
export default {
  props: {date: {type: [String, Date], required: true}, summary: {type: Object, required: true}, completed: {type: Boolean, required: true}},
  data() { return {fields: nutrientFields, targets: null, loading: false, error: null, requestId: 0}; },
  computed: {
    dateKey() { return dayjs(this.date).format('YYYY-MM-DD'); },
    fullCoverage() { return this.summary.totalFoods > 0 && this.summary.foodsWithValues === this.summary.totalFoods && this.summary.mealsWithoutFoods === 0; },
    complete() { return this.completed && this.fullCoverage; },
    dataKey() { return this.dateKey; }
  },
  watch: {dataKey: {immediate: true, handler() { this.load(); }}},
  methods: {
    async load() {
      const requestId = ++this.requestId;
      this.loading = true; this.error = null;
      try {
        const targetResponse = await nutritionService.get_targets(this.dateKey);
        if (requestId === this.requestId) this.targets = targetResponse.targets;
      } catch (error) { if (requestId === this.requestId) this.error = error.message; }
      finally { if (requestId === this.requestId) this.loading = false; }
    },
    target(field) { return this.targets[{vitaminDMicrograms: 'vitaminD', omega3Milligrams: 'omega3', magnesiumMilligrams: 'magnesium'}[field.key]]; },
    progress(field) { return Number((this.summary[field.key] / this.target(field).value * 100).toFixed(2)); },
    comparison(field) {
      const value = this.target(field);
      const reached = this.summary[field.key] >= value.value;
      return `${reached ? 'At or above' : 'Below'} ${value.source === 'PERSONAL' ? 'personal target' : 'adult reference'}`;
    }
  }
};
</script>
<style scoped>
.target-status { padding-left: 1.25rem; }
.target-status li { margin: .35rem 0; overflow-wrap: anywhere; }
.target-status strong { margin-right: .25rem; }
.nutrient-insights small { display: block; }
</style>
