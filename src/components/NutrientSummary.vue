<template>
  <section class="nutrient-summary" :class="{compact}" :aria-label="compact ? 'Meal nutrients' : 'Food nutrients'">
    <dl><div v-for="field in fields" :key="field.key"><dt>{{ field.label }}</dt><dd>
      <button type="button" class="nutrient-value" :aria-label="`${field.label} details: ${valueLabel(field)}`" aria-haspopup="dialog" @click="openDetails(field, $event)">{{ valueLabel(field) }}</button>
    </dd></div></dl>
    <Dialog v-if="selectedField" :header="`${selectedField.label} details`" :visible="true" modal appendTo="body" :style="{width: 'min(24rem, 95vw)'}" @update:visible="closeDetails">
      <p>{{ summary[selectedField.key] == null ? 'No value recorded.' : `${partial(selectedField) ? 'Partial total' : 'Total'}: ${format(summary[selectedField.key])} ${selectedField.unit}` }}</p>
      <p>{{ summary[selectedField.coverageKey].foodsWithValues }} of {{ summary.totalFoods }} foods have a value.</p>
      <p v-if="summary[selectedField.coverageKey].estimatedFoods">Includes estimates.</p>
      <p v-if="summary.mealsWithoutFoods">{{ summary.mealsWithoutFoods }} meal(s) have no foods recorded.</p>
    </Dialog>
  </section>
</template>
<script>
import {nutrientFields, formatNutritionValue} from '../model/Dish';
export default {
  props: {summary: {type: Object, required: true}, compact: {type: Boolean, default: false}},
  data() { return {fields: nutrientFields, selectedField: null, detailTrigger: null}; },
  methods: {
    format: formatNutritionValue,
    openDetails(field, event) {
      this.detailTrigger = event.currentTarget;
      this.selectedField = field;
    },
    closeDetails() {
      this.selectedField = null;
      this.$nextTick(() => this.detailTrigger.focus());
    },
    partial(field) { return this.summary.mealsWithoutFoods > 0 || this.summary[field.coverageKey].foodsWithValues < this.summary.totalFoods; },
    valueLabel(field) {
      return this.summary[field.key] == null ? '—' : `${this.partial(field) ? '≥ ' : ''}${this.format(this.summary[field.key])} ${field.unit}`;
    }
  }
};
</script>
<style scoped>
.nutrient-summary { min-width: 0; }
dl { display: flex; flex-wrap: wrap; gap: .5rem 1rem; margin: .5rem 0; }
dl > div { min-width: 5.5rem; }
dt { font-size: .85rem; }
dd { margin: .25rem 0 0; }
.nutrient-value { color: inherit; font: inherit; padding: 0; border: 0; background: none; cursor: pointer; text-decoration: underline dotted; text-underline-offset: .2em; }
.nutrient-value:focus-visible { outline: 2px solid var(--primary-color); outline-offset: 3px; }
.compact { margin-top: .25rem; font-size: .85rem; }
.compact dl { margin: .25rem 0; gap: .25rem .75rem; }
.compact dl > div { display: flex; gap: .25rem; min-width: 0; }
.compact dd { margin: 0; }
</style>
