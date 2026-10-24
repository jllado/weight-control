<template>
  <section class="nutrient-summary" :class="{compact}" :aria-label="compact ? 'Meal nutrients' : 'Food nutrients'">
    <dl><div v-for="field in fields" :key="field.key"><dt>{{ field.label }}</dt><dd>{{ summary[field.key] == null ? '—' : `${format(summary[field.key])} ${field.unit}` }}</dd></div></dl>
    <template v-if="!compact">
      <small v-if="!hasDisplayedValues">No nutrient data recorded.</small>
      <small v-else>Coverage varies by nutrient · {{ hasEstimatedValues ? 'Includes estimates' : 'Sourced values' }}</small>
      <small v-if="incompleteCoverage">Incomplete coverage<template v-if="summary.mealsWithoutFoods"> · {{ summary.mealsWithoutFoods }} meal(s) without foods</template>.</small>
    </template>
  </section>
</template>
<script>
import {nutrientFields, formatNutritionValue} from '../model/Dish';
export default {
  props: {summary: {type: Object, required: true}, compact: {type: Boolean, default: false}},
  data() { return {fields: nutrientFields}; },
  computed: {
    hasDisplayedValues() { return this.fields.some(field => this.summary[field.coverageKey].foodsWithValues > 0); },
    hasEstimatedValues() { return this.fields.some(field => this.summary[field.coverageKey].estimatedFoods > 0); },
    incompleteCoverage() { return this.summary.mealsWithoutFoods > 0 || this.fields.some(field => this.summary[field.coverageKey].foodsWithValues < this.summary.totalFoods); }
  },
  methods: {format: formatNutritionValue}
};
</script>
<style scoped>
.nutrient-summary { min-width: 0; }
dl { display: flex; flex-wrap: wrap; gap: .5rem 1rem; margin: .5rem 0; }
dl > div { min-width: 5.5rem; }
dt { font-size: .85rem; }
dd { margin: .25rem 0 0; }
small { display: block; overflow-wrap: anywhere; }
.compact { margin-top: .25rem; font-size: .85rem; }
.compact dl { margin: .25rem 0; gap: .25rem .75rem; }
.compact dl > div { display: flex; gap: .25rem; min-width: 0; }
.compact dd { margin: 0; }
</style>
