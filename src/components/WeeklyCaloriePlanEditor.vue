<template>
  <Dialog :visible="visible" header="Planned calories" modal appendTo="body" :style="{width: '38rem'}" :breakpoints="{'640px': '96vw'}" @update:visible="$emit('update:visible', $event)">
    <p>Changing one day leaves the others as they are.</p>
    <p v-if="loading" role="status">Loading this week's plan…</p>
    <p v-if="loadError" role="alert">{{ loadError }} <Button label="Retry" class="p-button-text" @click="load" /></p>
    <div class="plan-days">
      <div v-for="(date, index) in dates" :key="date" class="plan-day">
        <label :for="`planned-calories-${date}`">{{ formatDate(date) }}</label>
        <div class="plan-day-value">
          <InputNumber :inputId="`planned-calories-${date}`" :inputProps="{'aria-describedby': `planned-calories-unit-${date}`}" v-model="values[index]" :min="0" :max="2147483647" :maxFractionDigits="0" :useGrouping="false" :disabled="loading || saving" />
          <span :id="`planned-calories-unit-${date}`">kcal</span>
        </div>
      </div>
    </div>
    <p class="plan-average"><strong>Planned weekly average:</strong> {{ formatCalories(average) }} kcal/day</p>
    <p>Weekly average maximum: {{ formatCalories(weeklyMaximum) }} kcal/day · {{ maximumDifference }}</p>
    <p v-if="validationError" role="alert">{{ validationError }}</p>
    <p v-if="saveError" role="alert">{{ saveError }}</p>
    <template #footer>
      <div class="action-group">
        <Button label="Save" :loading="saving" :disabled="saving || loading" @click="save" />
        <Button label="Cancel" class="p-button-outlined p-button-secondary" :disabled="saving" @click="$emit('update:visible', false)" />
        <Button label="Reset to defaults" class="p-button-text" :disabled="saving || loading" @click="resetToDefaults" />
      </div>
    </template>
  </Dialog>
</template>

<script>
import dayjs from 'dayjs';
import Dialog from 'primevue/dialog';
import Button from 'primevue/button';
import InputNumber from 'primevue/inputnumber';
import weeklyCaloriePlanService from '@/services/WeeklyCaloriePlanService';

export default {
  name: 'WeeklyCaloriePlanEditor',
  components: {Dialog, Button, InputNumber},
  emits: ['update:visible', 'saved'],
  props: {
    visible: {type: Boolean, required: true},
    dates: {type: Array, required: true},
    initialValues: {type: Array, required: true},
    defaultValues: {type: Array, required: true},
    weeklyMaximum: {type: Number, required: true}
  },
  data() {
    return {values: [...this.initialValues], revision: null, loading: true, saving: false, loadError: '', saveError: '', validationError: ''};
  },
  computed: {
    average() {
      if (this.values.length !== 7 || this.values.some(value => !Number.isInteger(value) || value < 0)) return null;
      return this.values.reduce((sum, value) => sum + value, 0) / 7;
    },
    maximumDifference() {
      if (this.average === null) return 'Complete all seven daily targets.';
      const difference = this.weeklyMaximum - this.average;
      if (difference > 0) return `${formatCalories(difference)} kcal/day below`;
      if (difference < 0) return `${formatCalories(Math.abs(difference))} kcal/day above`;
      return 'at the configured maximum';
    }
  },
  async mounted() { await this.load(); },
  methods: {
    formatDate(date) { return dayjs(`${date}T12:00:00`).format('dddd, DD/MM/YYYY'); },
    formatCalories(value) { return value === null ? '—' : formatCalories(value); },
    async load() {
      this.loading = true;
      this.loadError = '';
      try {
        const saved = await weeklyCaloriePlanService.get(this.dates[0]);
        if (saved) {
          this.revision = saved.revision;
          this.values = saved.days.map(day => day.calories);
        }
      } catch (error) {
        this.loadError = `Could not load this week's saved plan. ${error.message}`;
      } finally {
        this.loading = false;
      }
    },
    resetToDefaults() {
      this.values = [...this.defaultValues];
      this.validationError = '';
      this.saveError = '';
    },
    async save() {
      this.validationError = '';
      this.saveError = '';
      if (this.values.length !== 7 || this.values.some(value => !Number.isInteger(value) || value < 0 || value > 2147483647)) {
        this.validationError = 'Enter a whole number of calories from 0 to 2,147,483,647 for each day.';
        return;
      }
      this.saving = true;
      try {
        const saved = await weeklyCaloriePlanService.save(this.dates[0], this.revision, this.dates.map((date, index) => ({date, calories: this.values[index]})));
        this.$emit('saved', saved);
        this.$emit('update:visible', false);
      } catch (error) {
        this.saveError = `Could not save this week's plan. ${error.message}`;
      } finally {
        this.saving = false;
      }
    }
  }
};

function formatCalories(value) {
  return new Intl.NumberFormat('en-GB', {minimumFractionDigits: 2, maximumFractionDigits: 2}).format(value);
}
</script>

<style scoped>
.plan-days { display: grid; gap: .5rem; grid-template-columns: repeat(2, minmax(0, 1fr)); }
.plan-day { display: grid; gap: .25rem; min-width: 0; }
.plan-day-value { display: flex; align-items: center; gap: .5rem; }
.plan-average { margin-top: 1rem; }
.plan-day-value :deep(.p-inputnumber) { width: 10rem; }
.plan-day-value :deep(.p-inputnumber-input) { width: 100%; }
@media (max-width: 480px) { .plan-days { grid-template-columns: minmax(0, 1fr); } }
</style>
