<template>
  <section class="nutrient-summary" :class="{compact, daily}" :aria-label="daily ? 'Daily nutrients' : compact ? 'Meal nutrients' : 'Food nutrients'">
    <dl><div v-for="field in fields" :key="field.key"><dt>{{ field.label }}</dt><dd>
      <button type="button" class="nutrient-value" :aria-label="`${field.label} details: ${valueLabel(field)}`" aria-haspopup="dialog" @click="openDetails(field, $event)">{{ valueLabel(field) }}</button>
    </dd></div></dl>
    <Dialog v-if="selectedField" :header="`${selectedField.label} details`" :visible="true" modal appendTo="body" :style="{width: 'min(24rem, 95vw)'}" @update:visible="closeDetails">
      <p v-if="definition(selectedField)">{{ definition(selectedField) }}</p>
      <p>{{ summary[selectedField.key] == null ? 'No value recorded.' : `${partial(selectedField) ? 'Partial total' : 'Total'}: ${format(summary[selectedField.key])} ${selectedField.unit}` }}</p>
      <p>{{ summary[selectedField.coverageKey].foodsWithValues }} of {{ summary.totalFoods }} foods have a value.</p>
      <p v-if="summary[selectedField.coverageKey].estimatedFoods">Includes estimates.</p>
      <p v-if="summary.mealsWithoutFoods">{{ summary.mealsWithoutFoods }} meal(s) have no foods recorded.</p>
    </Dialog>
  </section>
</template>
<script>
import {nutrientFields, dailyNutrientFields, formatNutritionValue} from '../model/Dish';
export default {
  props: {summary: {type: Object, required: true}, compact: {type: Boolean, default: false}, daily: {type: Boolean, default: false}},
  data() { return {selectedField: null, detailTrigger: null}; },
  computed: {fields() { return this.daily ? dailyNutrientFields : nutrientFields; }},
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
    definition(field) {
      if (field.key === 'omega3Milligrams') return 'Stored total omega-3 from recorded foods. This is separate from EPA+DHA and ALA.';
      if (field.key === 'epaDhaMilligrams') return 'EPA and DHA are omega-3 fats commonly found in fish and seafood. This is separate from ALA and historical total omega-3.';
      if (field.key === 'alaMilligrams') return 'ALA (alpha-linolenic acid) is an omega-3 fat mainly found in plant foods. This is separate from EPA+DHA and historical total omega-3.';
      return '';
    },
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
.daily dl { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: .75rem; }
.daily dl > div { min-width: 0; }
.daily dt { overflow-wrap: anywhere; }
@container (max-width: 64rem) {
  .daily dl { grid-template-columns: minmax(0, 1fr); gap: 0; }
  .daily dl > div { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: baseline; gap: .75rem; padding: .45rem 0; border-bottom: 1px solid #e6edf1; }
  .daily dl > div:last-child { border-bottom: 0; }
  .daily dd { margin: 0; text-align: right; white-space: nowrap; }
}
</style>
