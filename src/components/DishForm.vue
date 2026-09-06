<template>
  <Dialog header="Dish" :visible="true" modal appendTo="body" :closable="false" :closeOnEscape="false" class="dish-dialog" :style="{width: 'min(36rem, 95vw)'}">
    <div class="dish-fields">
      <div class="dish-name"><label for="dish-name">Dish</label><InputText id="dish-name" v-model="draft.name" maxlength="255" /></div>
      <div><label for="dish-quantity">Quantity</label><InputNumber inputId="dish-quantity" v-model="draft.quantity" :min="0.001" :max="99999999.999" :maxFractionDigits="3" :useGrouping="false" @update:modelValue="scale" /></div>
      <div><label for="dish-unit">Unit</label><Dropdown inputId="dish-unit" v-model="draft.unit" :options="units" optionLabel="label" optionValue="value" @change="reset_reference" /></div>
      <p class="dish-name">Nutrition for {{ draft.quantity }} {{ unit_label }}. Changing quantity scales these values.</p>
      <div v-for="field in fields" :key="field.key"><label :for="`dish-${field.key}`">{{ field.label }}</label><InputNumber :inputId="`dish-${field.key}`" v-model="draft[field.key]" :min="0" :max="field.key === 'calories' ? 2147483647 : 99999999.99" :maxFractionDigits="field.key === 'calories' ? 0 : 2" :useGrouping="false" @update:modelValue="reset_reference" /></div>
      <small class="dish-name">Macros are optional; leave unknown values blank.</small>
      <p v-if="invalid" class="error dish-name" role="alert">Enter a name, a positive quantity, and non-negative calories.</p>
    </div>
    <template #footer><Button label="Apply" icon="pi pi-check" @click="apply" /><Button label="Cancel" class="p-button-secondary" @click="$emit('close')" /></template>
  </Dialog>
</template>

<script>
import {dishReference, dishUnits, normalizeDish, nutritionFields, scaleNutrition} from '../model/Dish';
export default {
  props: {dish: {type: Object, required: true}},
  emits: ['apply', 'close'],
  data() {
    return {draft: normalizeDish(this.dish), invalid: false, units: dishUnits, fields: [
      {key: 'calories', label: 'Calories'}, {key: 'proteinGrams', label: 'Protein (g)'},
      {key: 'carbohydrateGrams', label: 'Carbohydrates (g)'}, {key: 'fatGrams', label: 'Fat (g)'}
    ]};
  },
  computed: {unit_label() { return this.units.find(unit => unit.value === this.draft.unit).label; }},
  methods: {
    scale(quantity) {
      this.draft.quantity = quantity;
      if (!(quantity > 0)) return;
      nutritionFields.forEach(key => { this.draft[key] = scaleNutrition(this.draft.reference[key], quantity, this.draft.reference.quantity, key === 'calories' ? 0 : 2); });
    },
    reset_reference() { if (this.draft.quantity > 0) this.draft.reference = dishReference(this.draft); },
    apply() {
      this.invalid = !this.draft.name.trim() || !(this.draft.quantity > 0) || this.draft.calories === null || this.draft.calories < 0;
      if (!this.invalid) this.$emit('apply', {...this.draft, name: this.draft.name.trim()});
    }
  }
};
</script>

<style scoped>
.dish-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem; }
.dish-name { grid-column: 1 / -1; margin: 0; overflow-wrap: anywhere; }
label { display: block; margin-bottom: .5rem; }
.dish-fields :deep(.p-inputnumber), .dish-fields :deep(.p-inputtext), .dish-fields :deep(.p-dropdown) { width: 100%; min-width: 0; }
</style>
