<template>
  <MealForm v-if="editing_index !== null" ref="editForm" :meal="draft[editing_index]" :initial_date="date" fixed_date local_mode @apply="apply_meal" @onClose="cancel_edit" />
  <section v-else id="meal-day-copy" ref="overview" class="day-copy-form" aria-label="Copy day">
    <SaveFields :saving="saving || refreshing">
      <h1 tabindex="-1">Copy day</h1>
      <p>{{ format_date(date) }}</p>
      <div class="day-source">
        <label id="copy-source-day-label" for="copy-source-day">Preload a previous day</label>
        <Dropdown inputId="copy-source-day" aria-labelledby="copy-source-day-label" :modelValue="selected_day" :options="days" optionLabel="label" optionValue="date" appendTo="body" :panelStyle="{maxWidth: 'calc(100vw - 2rem)'}" @update:modelValue="select_day">
          <template #option="{option}"><MealPreloadOption :option="option" /></template>
        </Dropdown>
      </div>
      <div class="day-meals" aria-label="Meals to copy">
        <article v-for="(meal, index) in draft" :key="index" class="day-meal-row" :aria-label="meal_label(meal)">
          <div class="day-meal-content">
            <div class="day-meal-heading"><strong>{{ meal_label(meal) }}</strong><strong>{{ kcal(meal.calories) }} kcal</strong></div>
            <p>{{ meal.dishes.length ? meal.dishes.map(food => food.name).join(', ') : 'No foods' }}</p>
            <small>{{ meal.mealTime ? meal.mealTime.slice(0, 5) : 'No start time' }}<template v-if="meal.durationMinutes !== null"> · {{ meal.durationMinutes }} min</template></small>
          </div>
          <div class="action-group action-group--compact"><CompactAction icon="pi pi-pencil" :aria-label="`Edit ${meal_label(meal).toLowerCase()}`" :data-copy-edit="index" @click="edit_meal(index)" /></div>
        </article>
      </div>
      <details class="day-nutrients">
        <summary>Nutrients</summary>
        <p>{{ macros }}</p>
        <NutrientSummary :summary="nutrients" compact include_total />
      </details>
      <p v-if="occupied" role="alert">This day already has meals. Refresh the target status or return to the dashboard to review them.</p>
      <p v-if="error" role="alert" class="error">{{ error }}</p>
    </SaveFields>
    <div v-if="error || occupied" class="day-recovery action-group">
      <Button :label="refreshing ? 'Refreshing…' : 'Refresh target status'" class="p-button-outlined" :disabled="saving || refreshing" :loading="refreshing" @click="refresh_target" />
    </div>
    <footer class="day-copy-footer">
      <strong role="status">{{ kcal(calories) }} kcal</strong>
      <div class="action-group">
        <Button :label="saving ? 'Saving…' : 'Save'" icon="pi pi-check" :loading="saving" :disabled="saving || refreshing || occupied" :aria-busy="saving" @click="save" />
        <Button label="Cancel" icon="pi pi-times" class="p-button-secondary" :disabled="saving || refreshing" @click="$emit('onClose')" />
      </div>
    </footer>
  </section>
</template>
<script>
import dayjs from 'dayjs';
import MealForm from './MealForm.vue';
import MealPreloadOption from './MealPreloadOption.vue';
import NutrientSummary from './NutrientSummary.vue';
import mealService from '../services/MealService';
import {copyMealSnapshot, previousDays} from '../model/MealDayCopy';
import {formatMealType} from '../model/Meal';
import {macroSummary, nutrientSummary} from '../model/Dish';

export default {
  components: {MealForm, MealPreloadOption, NutrientSummary},
  props: {meals: {type: Array, required: true}, source: {type: Object, required: true}, date: {type: Date, required: true}},
  emits: ['onSave', 'onClose'],
  data() {
    const draft = this.source.meals.map(copyMealSnapshot);
    return {draft, baseline: JSON.stringify(draft), selected_day: this.source.date, loaded_meals: this.meals, editing_index: null, saving: false, refreshing: false, committed: false, error: ''};
  },
  computed: {
    days() { return previousDays(this.loaded_meals, this.date); },
    occupied() { return this.loaded_meals.some(meal => dayjs(meal.date).isSame(this.date, 'day')); },
    dirty() { return !this.committed; },
    edited() { return JSON.stringify(this.draft) !== this.baseline || !!this.$refs.editForm?.dirty; },
    calories() { return this.draft.reduce((sum, meal) => sum + meal.calories, 0); },
    nutrients() { return nutrientSummary(this.draft); },
    macros() {
      return macroSummary(Object.fromEntries(['proteinGrams', 'carbohydrateGrams', 'fatGrams'].map(key => [key, this.draft.some(meal => meal[key] === null) ? null : Math.round(this.draft.reduce((sum, meal) => sum + meal[key], 0) * 100) / 100])));
    }
  },
  methods: {
    meal_label(meal) { return formatMealType(meal.mealType, meal.mealSequence); },
    format_date(date) { return dayjs(date).format('DD/MM/YYYY'); },
    kcal(value) { return value.toLocaleString('en-US'); },
    select_day(date) {
      if (date === this.selected_day) return;
      if (this.edited && !window.confirm('Discard unsaved day changes?')) return;
      this.selected_day = date;
      this.draft = this.days.find(day => day.date === date).meals.map(copyMealSnapshot);
      this.baseline = JSON.stringify(this.draft);
      this.error = '';
    },
    async edit_meal(index) {
      this.editing_index = index;
      await this.$nextTick();
      this.$refs.editForm.$el.querySelector('h1').focus();
    },
    async finish_edit() {
      const index = this.editing_index;
      this.editing_index = null;
      await this.$nextTick();
      this.$refs.overview.querySelector(`[data-copy-edit="${index}"]`).focus();
    },
    apply_meal(meal) { this.draft.splice(this.editing_index, 1, meal); this.finish_edit(); },
    cancel_edit() { if (!this.$refs.editForm.dirty || window.confirm('Discard unsaved meal changes?')) this.finish_edit(); },
    async refresh_target() {
      this.refreshing = true;
      try {
        this.loaded_meals = await mealService.get_all();
        this.error = this.occupied ? '' : 'The target day is still empty. Your draft is ready to save.';
      } catch { this.error = 'Unable to refresh the target status. Your draft is still here.'; }
      finally { this.refreshing = false; }
    },
    async save() {
      if (this.saving || this.occupied) return;
      this.saving = true;
      this.error = '';
      try {
        await mealService.copy_day(this.date, this.draft);
        this.committed = true;
        this.saving = false;
        this.$emit('onSave');
        this.$toast.add({severity: 'success', summary: 'Day copied', life: 3000});
      } catch (error) {
        this.error = `Unable to copy the day. Your changes are still here. ${error.message} Refresh the target status before retrying if the response was lost.`;
      } finally { this.saving = false; }
    }
  }
};
</script>
<style scoped>
.day-copy-form { max-width: 60rem; margin: 0 auto; padding: 1rem; }
h1 { font-size: 1.5rem; margin-top: 0; }
.day-source { margin: 1.5rem 0 1rem; }
.day-source label { display: block; margin-bottom: .5rem; }
.day-source :deep(.p-dropdown) { width: 100%; min-width: 0; }
.day-meals { border: 1px solid var(--surface-border); border-radius: 3px; }
.day-meal-row { display: flex; align-items: center; gap: 1rem; padding: .75rem; border-bottom: 1px solid var(--surface-border); }
.day-meal-row:last-child { border-bottom: 0; }
.day-meal-content { min-width: 0; flex: 1; overflow-wrap: anywhere; }
.day-meal-heading { display: flex; align-items: baseline; justify-content: space-between; gap: .75rem; }
.day-meal-heading > :last-child { white-space: nowrap; }
.day-meal-content p { margin: .35rem 0; }
.day-nutrients { margin: 1rem 0; }
.day-nutrients summary { cursor: pointer; color: var(--primary-color); font-weight: bold; }
.day-nutrients summary:focus-visible { outline: 2px solid var(--primary-color); outline-offset: 3px; }
.day-recovery { margin-bottom: 1rem; }
.day-copy-footer { position: sticky; bottom: 0; background: var(--surface-a, white); border-top: 1px solid var(--surface-border); padding: 1rem 0; display: flex; align-items: center; justify-content: space-between; gap: 1rem; flex-wrap: wrap; z-index: 1; }
@media (max-width: 575px) { .day-copy-footer > .action-group { width: 100%; } .day-copy-footer > .action-group > * { flex: 1; } }
</style>
