<template>
  <Panel header="Nutrient targets">
    <p>Set personal daily targets. Leave a field blank to use an available adult reference or no target.</p>
    <div class="p-fluid p-formgrid p-grid">
      <div v-for="field in fields" :key="field.key" class="p-field p-col-12 p-md-4">
        <label :for="field.key">{{ field.label }} ({{ field.unit }})</label>
        <InputNumber :id="field.key" v-model="values[field.key]" :min="0.01" :maxFractionDigits="2" :useGrouping="false" />
      </div>
    </div>
    <Button label="Save nutrient targets" :loading="saving" :disabled="saving" @click="save" />
  </Panel>
</template>
<script>
import nutritionService from '../services/NutritionService';
import {nutrientFields} from '../model/Dish';
export default {
  data() { return {fields: nutrientFields, values: {vitaminDMicrograms: null, omega3Milligrams: null, magnesiumMilligrams: null}, saving: false}; },
  async created() {
    try { this.values = (await nutritionService.get_targets()).overrides; }
    catch (error) { this.$toast.add({severity: 'error', summary: 'Could not load targets', detail: error.message, life: 4000}); }
  },
  methods: {
    async save() {
      this.saving = true;
      try {
        const response = await nutritionService.save_targets(this.values);
        this.values = response.overrides;
        this.$toast.add({severity: 'success', summary: 'Saved', detail: 'Nutrient targets updated', life: 3000});
      } catch (error) { this.$toast.add({severity: 'error', summary: 'Save failed', detail: error.message, life: 4000}); }
      finally { this.saving = false; }
    }
  }
};
</script>
