<template>
  <Panel header="Nutrient targets" class="p-mt-3">
    <p>Set personal daily targets. Leave a field blank to use an available adult reference or no target.</p>
    <p v-if="loading" role="status">Loading nutrient targets…</p>
    <p v-else-if="error" role="alert">{{ error }}</p>
    <template v-else>
    <SaveFields :saving="saving">
    <div class="p-fluid p-formgrid p-grid">
      <div v-for="field in fields" :key="field.key" class="p-field p-col-12 p-md-4">
        <label :for="field.key">{{ field.label }} ({{ field.unit }})</label>
        <InputNumber :inputId="field.key" v-model="values[field.key]" :min="0.01" :max="99999999.99" :maxFractionDigits="2" :useGrouping="false" :inputProps="{'aria-describedby': `${field.key}-reference`}" />
        <small :id="`${field.key}-reference`">{{ referenceDescription(field) }}</small>
      </div>
    </div>
    </SaveFields>
    <Button :label="saving ? 'Saving…' : 'Save nutrient targets'" icon="pi pi-check" :loading="saving" :disabled="saving" :aria-busy="saving" @click="save" />
    </template>
  </Panel>
</template>
<script>
import nutritionService from '../services/NutritionService';
import {nutrientFields} from '../model/Dish';
export default {
  data() { return {fields: nutrientFields, values: {vitaminDMicrograms: null, omega3Milligrams: null, magnesiumMilligrams: null}, targets: null, loading: true, error: null, saving: false}; },
  async created() {
    try {
      const response = await nutritionService.get_targets();
      this.values = response.overrides;
      this.targets = response.targets;
    } catch (error) { this.error = `Could not load nutrient targets: ${error.message}`; }
    finally { this.loading = false; }
  },
  methods: {
    referenceDescription(field) {
      const target = this.targets[{vitaminDMicrograms: 'vitaminD', omega3Milligrams: 'omega3', magnesiumMilligrams: 'magnesium'}[field.key]];
      if (target.referenceValue != null) return `Blank: adult EFSA reference of ${target.referenceValue} ${field.unit}/day.`;
      return field.key === 'omega3Milligrams' ? 'Blank: no target. Total omega-3 has no automatic reference.' : 'Blank: no reference for this profile; configure a personal target.';
    },
    async save() {
      this.saving = true;
      try {
        const response = await nutritionService.save_targets(this.values);
        this.values = response.overrides;
        this.targets = response.targets;
        this.$toast.add({severity: 'success', summary: 'Saved', detail: 'Nutrient targets updated', life: 3000});
      } catch (error) { this.$toast.add({severity: 'error', summary: 'Save failed', detail: error.message, life: 4000}); }
      finally { this.saving = false; }
    }
  }
};
</script>
