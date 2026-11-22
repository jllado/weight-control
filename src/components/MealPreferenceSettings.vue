<template>
  <Panel header="Meal preferences" class="p-mt-3">
    <p v-if="loading" role="status">Loading meal preferences…</p>
    <div v-else-if="error">
      <p role="alert">{{ error }}</p>
      <ActionButton label="Retry" busyLabel="Loading…" :action="load" />
    </div>
    <template v-else>
      <p>These preferences apply when you record or edit a meal. Earlier ratings keep their saved explanation.</p>
      <SaveFields :saving="saving">
        <fieldset class="main-meals">
          <legend>Usual main meals</legend>
          <div class="meal-options">
            <div v-for="meal in mealTypeOptions" :key="meal.value">
              <Checkbox :inputId="`main-meal-${meal.value}`" v-model="preferences.mainMeals" :value="meal.value" />
              <label :for="`main-meal-${meal.value}`">{{ meal.label }}</label>
            </div>
          </div>
        </fieldset>
        <div class="p-fluid p-formgrid p-grid">
          <div v-for="field in numberFields" :key="field.key" class="p-field p-col-12 p-md-6">
            <label :for="field.key">{{ field.label }}</label>
            <InputNumber :inputId="field.key" v-model="preferences[field.key]" :min="field.min" :max="field.max"
                         :maxFractionDigits="0" :useGrouping="false" :inputProps="{'aria-required': 'true'}" />
            <small v-if="preferences[field.key] == null" class="error">Enter a whole number from {{ field.min }} to {{ field.max }}.</small>
          </div>
        </div>
      </SaveFields>
      <ActionButton label="Save meal preferences" icon="pi pi-check" :action="save" :loading="saving" :disabled="missingValue" />
    </template>
  </Panel>
</template>

<script>
import Checkbox from 'primevue/checkbox';
import {mealTypeOptions} from '../model/Meal';
import mealService from '../services/MealService';

const numberFields = [
  {key: 'mainMealLimitPercent', label: 'Main meal limit (% of daily calorie target)', min: 1, max: 99},
  {key: 'minimumMealGapMinutes', label: 'Minimum gap between meals (minutes)', min: 0, max: 1440},
  {key: 'minimumBedtimeGapMinutes', label: 'Minimum gap before bedtime (minutes)', min: 0, max: 1440},
  {key: 'saturatedFatEnergyPercent', label: 'Saturated fat limit (% of meal calories)', min: 1, max: 100}
];

export default {
  components: {Checkbox},
  data() { return {mealTypeOptions, numberFields, preferences: null, loading: true, error: null, saving: false}; },
  computed: {
    missingValue() { return numberFields.some(field => this.preferences[field.key] == null); }
  },
  created() { this.load(); },
  methods: {
    async load() {
      this.loading = true;
      this.error = null;
      try { this.preferences = await mealService.get_rating_preferences(); }
      catch (error) { this.error = `Could not load meal preferences: ${error.message}`; }
      finally { this.loading = false; }
    },
    async save() {
      this.saving = true;
      try {
        this.preferences = await mealService.save_rating_preferences(this.preferences);
        this.$toast.add({severity: 'success', summary: 'Saved', detail: 'Meal preferences updated', life: 3000});
      } finally { this.saving = false; }
    }
  }
};
</script>

<style scoped>
.main-meals { margin: 0 0 1rem; padding: 0; border: 0; min-width: 0; }
.main-meals legend { padding: 0; margin-bottom: .5rem; }
.meal-options { display: flex; flex-wrap: wrap; gap: .75rem 1rem; }
.meal-options > div { display: flex; align-items: center; gap: .5rem; }
.p-field :deep(input) { width: 100%; min-width: 0; }
</style>
