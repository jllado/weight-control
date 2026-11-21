<template>
  <Panel class="training-balance">
    <template #header><strong>Training balance</strong></template>
    <div class="balance-navigation">
      <div class="balance-date"><label for="training-balance-date">Date in week</label><Calendar inputId="training-balance-date" :modelValue="calendarDate" :maxDate="latestSelectableDate" dateFormat="dd/mm/yy" showIcon @update:modelValue="selectCalendarDate" /></div>
      <div class="balance-week-actions"><Button label="Previous week" icon="pi pi-chevron-left" class="p-button-outlined" @click="moveWeek(-1)" /><Button label="Next week" icon="pi pi-chevron-right" class="p-button-outlined" :disabled="nextWeekDisabled" @click="moveWeek(1)" /><Button label="This week" class="p-button-outlined" @click="selectDate(today())" /></div>
    </div>
    <p class="balance-explanation">{{ periodLabel(selectedWeek.start, selectedWeek.end) }} · Europe/Madrid</p>
    <p v-if="loading" role="status">Loading training balance…</p>
    <div v-else-if="error" role="alert"><p class="error">{{ error }}</p><Button label="Retry" class="p-button-outlined" @click="load" /></div>
    <template v-else-if="balance">
      <p class="balance-summary" aria-live="polite"><strong>{{ formatDate(balance.weekStart) }} – {{ formatDate(balance.weekEnd) }}</strong><span>{{ balance.totalSets }} total {{ balance.totalSets === 1 ? 'set' : 'sets' }}</span></p>
      <p v-if="balance.totalSets === 0">No strength sets recorded this week.</p>
      <ul class="balance-chart" aria-label="Weekly equivalent sets by muscle group">
        <li v-for="group in balance.groups" :key="group.muscleGroup" class="balance-row">
          <span class="balance-label">{{ primaryMuscleGroupLabel(group.muscleGroup) }}</span>
          <span class="balance-track" aria-hidden="true"><span class="balance-bar" :style="{width: `${group.equivalentSets / maximumSets * 100}%`}"></span></span>
          <span class="balance-count">{{ formatEquivalentSets(group.equivalentSets) }} equivalent {{ group.equivalentSets === 1 ? 'set' : 'sets' }}</span>
        </li>
      </ul>
    </template>
  </Panel>
</template>

<script>
import dayjs from 'dayjs';
import {planningWeek, periodLabel, madridDate as today} from '../model/PlanningWeek';
import workoutService from '../services/WorkoutService';
import {primaryMuscleGroupLabel} from '../model/WorkoutExercise';

function validDate(value) { return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) && dayjs(value).format('YYYY-MM-DD') === value; }

export default {
  props: {active: {type: Boolean, required: true}, revision: {type: Number, required: true}},
  data() { return {balance: null, loading: false, error: '', requestVersion: 0}; },
  computed: {
    calendarDate() { return dayjs(this.selectedDate).toDate(); },
    currentWeek() { return planningWeek(today()); },
    selectedWeek() { return planningWeek(this.selectedDate); },
    latestSelectableDate() { return dayjs(this.currentWeek.end).toDate(); },
    nextWeekDisabled() { return this.selectedWeek.start >= this.currentWeek.start; },
    selectedDate() { return validDate(this.$route.query.date) && this.$route.query.date <= this.currentWeek.end ? this.$route.query.date : today(); },
    maximumSets() { return Math.max(1, ...this.balance.groups.map(group => group.equivalentSets)); }
  },
  created() { if (this.active) this.loadSelectedWeek(); },
  beforeUnmount() { this.requestVersion++; },
  watch: {
    '$route.query.date'() { if (this.active) this.loadSelectedWeek(); },
    active(value) { if (value) this.loadSelectedWeek(); },
    revision() { if (this.active) this.loadSelectedWeek(); }
  },
  methods: {
    primaryMuscleGroupLabel, today, periodLabel,
    formatEquivalentSets(value) { return Number.isInteger(value) ? value : value.toFixed(1); },
    formatDate(date) { return dayjs(date).format('DD/MM/YYYY'); },
    selectCalendarDate(date) { if (date) this.selectDate(dayjs(date).format('YYYY-MM-DD')); },
    selectDate(date) { if (validDate(date) && date <= this.currentWeek.end && date !== this.$route.query.date) this.$router.push({query: {...this.$route.query, date}}); },
    moveWeek(direction) {
      const adjacent = planningWeek(dayjs(direction < 0 ? this.selectedWeek.start : this.selectedWeek.end).add(direction, 'day'));
      const offset = dayjs(this.selectedDate).diff(this.selectedWeek.start, 'day');
      const samePosition = dayjs(adjacent.start).add(offset, 'day').format('YYYY-MM-DD');
      this.selectDate(samePosition < adjacent.end ? samePosition : adjacent.end);
    },
    loadSelectedWeek() {
      if (!validDate(this.$route.query.date) || this.$route.query.date > this.currentWeek.end) this.$router.replace({query: {...this.$route.query, date: this.selectedDate}});
      else this.load();
    },
    async load() {
      const version = ++this.requestVersion;
      this.loading = true;
      this.error = '';
      try {
        const balance = await workoutService.get_training_balance(this.selectedDate);
        if (version === this.requestVersion) this.balance = balance;
      } catch (error) {
        if (version === this.requestVersion) this.error = error.message;
      } finally {
        if (version === this.requestVersion) this.loading = false;
      }
    }
  }
};
</script>

<style scoped>
.balance-navigation, .balance-week-actions { display: flex; align-items: end; flex-wrap: wrap; gap: .5rem; }
.balance-week-actions { align-items: stretch; }
.balance-date { display: grid; gap: .35rem; }
.balance-date input { max-width: 100%; }
.balance-explanation { line-height: 1.5; }
.balance-summary { display: flex; flex-wrap: wrap; gap: .5rem 1rem; }
.balance-chart { list-style: none; margin: 1rem 0 0; padding: 0; display: grid; gap: .75rem; }
.balance-row { display: grid; grid-template-columns: 8rem minmax(2rem, 1fr) minmax(9rem, 11rem); align-items: center; gap: .5rem; }
.balance-label { overflow-wrap: anywhere; }
.balance-count { text-align: right; line-height: 1.25; overflow-wrap: normal; }
.balance-track { display: block; height: 1rem; background: #f0f0f0; border-radius: 2px; overflow: hidden; }
/* The existing primary theme color identifies all set counts equally; no status or target thresholds apply. */
.balance-bar { display: block; height: 100%; background: var(--primary-color, #007ad9); }
@media (max-width: 575px) {
  .balance-date { width: 100%; }
  .balance-week-actions { width: 100%; display: grid; grid-template-columns: minmax(0, 1fr); }
  .balance-week-actions :deep(.p-button) { min-width: 0; }
  .balance-week-actions :deep(.p-button-label) { white-space: normal; }
  .balance-row { grid-template-columns: 6.25rem minmax(2rem, 1fr) minmax(5rem, 7rem); }
}
</style>
