<template>
  <Panel header="Nutrient targets" class="p-mt-3">
    <p>These current targets apply to recorded dates. A saved calorie plan sets each day's fat and carbohydrate ceilings before weekday defaults.</p>
    <p v-if="loading" role="status">Loading nutrient targets…</p>
    <p v-else-if="error" role="alert">{{ error }}</p>
    <template v-else>
      <SaveFields :saving="saving">
        <div class="target-grid">
          <div>
            <label for="vitaminDMicrograms">Vitamin D (µg)</label>
            <InputNumber inputId="vitaminDMicrograms" v-model="values.vitaminDMicrograms" :min="0.01" :max="99999999.99" :maxFractionDigits="2" :useGrouping="false" :inputProps="{'aria-describedby': 'vitaminD-reference'}" />
            <small id="vitaminD-reference">{{ referenceDescription('vitaminD', 'µg') }}</small>
          </div>
          <div>
            <label for="magnesiumMilligrams">Magnesium (mg)</label>
            <InputNumber inputId="magnesiumMilligrams" v-model="values.magnesiumMilligrams" :min="0.01" :max="99999999.99" :maxFractionDigits="2" :useGrouping="false" :inputProps="{'aria-describedby': 'magnesium-reference'}" />
            <small id="magnesium-reference">{{ referenceDescription('magnesium', 'mg') }}</small>
          </div>
          <div>
            <label for="protein-target">Protein minimum (g/day)</label>
            <InputNumber inputId="protein-target" v-model="values.macros.proteinGrams" :min="0.01" :max="99999999.99" :maxFractionDigits="2" :useGrouping="false" />
          </div>
          <div>
            <label for="fat-percentage">Fat ceiling (% of daily calories)</label>
            <InputNumber inputId="fat-percentage" v-model="values.macros.fatPercentage" :min="0.01" :max="100" :maxFractionDigits="2" :useGrouping="false" />
          </div>
          <div class="derived-target">
            <strong>Carbohydrate ceiling</strong>
            <span>{{ derivedCarbohydrates }}</span>
            <small>Remaining calories after the current protein minimum and fat allocation.</small>
          </div>
          <div>
            <label for="epa-dha-target">Omega-3 (EPA+DHA) minimum (mg/day)</label>
            <InputNumber inputId="epa-dha-target" v-model="values.epaDha.value" :min="0.01" :max="99999999.99" :maxFractionDigits="2" :useGrouping="false" />
          </div>
          <div>
            <label for="ala-target">ALA minimum (mg/day)</label>
            <InputNumber inputId="ala-target" v-model="values.ala.value" :min="0.01" :max="99999999.99" :maxFractionDigits="2" :useGrouping="false" />
          </div>
          <div>
            <label for="fiber-target">Fiber minimum (g/day)</label>
            <InputNumber inputId="fiber-target" v-model="values.fiber.value" :min="0.01" :max="99999999.99" :maxFractionDigits="2" :useGrouping="false" />
          </div>
        </div>
        <p v-if="values.omega3Milligrams != null" class="legacy-target">Historical total omega-3 target: {{ values.omega3Milligrams }} mg/day (read only; separate from EPA+DHA).</p>
      </SaveFields>
      <Button class="target-save" :label="saving ? 'Saving…' : 'Save nutrient targets'" icon="pi pi-check" :loading="saving" :disabled="saving" :aria-busy="saving" @click="save" />
    </template>
  </Panel>
</template>
<script>
import nutritionService from '../services/NutritionService';
import {formatNutritionValue} from '../model/Dish';

export default {
  data() {
    return {values: null, targets: null, macroTargets: null, loading: true, error: null, saving: false};
  },
  computed: {
    derivedCarbohydrates() {
      const calories = this.macroTargets.calorieBudget;
      if (calories <= 0) return 'No effective calorie budget for this date';
      const protein = this.values.macros.proteinGrams;
      const fatPercentage = this.values.macros.fatPercentage;
      if (protein == null || fatPercentage == null) return 'Set protein and fat targets';
      const remaining = calories - 4 * protein - calories * fatPercentage / 100;
      return remaining < 0 ? 'Protein and fat allocations exceed this calorie budget'
        : `${formatNutritionValue(remaining / 4)} g/day for today`;
    }
  },
  async created() {
    try {
      const response = await nutritionService.get_targets();
      this.values = response.overrides;
      this.targets = response.targets;
      this.macroTargets = response.macroTargets;
    } catch (error) { this.error = `Could not load nutrient targets: ${error.message}`; }
    finally { this.loading = false; }
  },
  methods: {
    referenceDescription(key, unit) {
      const target = this.targets[key];
      return target.referenceValue != null ? `Blank: adult EFSA reference of ${target.referenceValue} ${unit}/day.`
        : 'Blank: no reference for this profile; configure a personal target.';
    },
    async save() {
      this.saving = true;
      try {
        const response = await nutritionService.save_targets(this.values);
        this.values = response.overrides;
        this.targets = response.targets;
        this.macroTargets = response.macroTargets;
        this.$toast.add({severity: 'success', summary: 'Saved', detail: 'Nutrient targets updated', life: 3000});
      } catch (error) { this.$toast.add({severity: 'error', summary: 'Save failed', detail: error.message, life: 4000}); }
      finally { this.saving = false; }
    }
  }
};
</script>
<style scoped>
.target-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem; }
.target-grid > div { min-width: 0; }
label, .derived-target strong, .derived-target span, small { display: block; }
label { margin-bottom: .5rem; }
.target-grid :deep(.p-inputnumber) { width: 100%; min-width: 0; }
small { margin-top: .35rem; overflow-wrap: anywhere; }
.derived-target { align-self: center; }
.legacy-target { margin: 1rem 0; }
.target-save { margin-top: 1rem; }
@media (max-width: 640px) { .target-grid { grid-template-columns: minmax(0, 1fr); } }
</style>
