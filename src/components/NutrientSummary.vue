<template>
  <section class="nutrient-summary" aria-label="Food nutrients">
    <dl><div v-for="field in fields" :key="field.key"><dt>{{ field.label }}</dt><dd>{{ summary[field.key] == null ? '—' : `${format(summary[field.key])} ${field.unit}` }}</dd></div></dl>
    <small v-if="summary.foodsWithValues === 0">No nutrient data recorded.</small>
    <small v-else>{{ summary.foodsWithValues }}/{{ summary.totalFoods }} foods with values · {{ summary.estimatedFoods ? 'Includes estimates' : 'Sourced values' }}</small>
    <small v-if="summary.foodsWithValues < summary.totalFoods || summary.mealsWithoutFoods">Incomplete coverage<template v-if="summary.mealsWithoutFoods"> · {{ summary.mealsWithoutFoods }} meal(s) without foods</template>.</small>
    <small v-else-if="summary.foodsWithValues">Totals cover logged foods only.</small>
  </section>
</template>
<script>
import {nutrientFields, formatNutritionValue} from '../model/Dish';
export default {
  props: {summary: {type: Object, required: true}},
  data() { return {fields: nutrientFields}; },
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
</style>
