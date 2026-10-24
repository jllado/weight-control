<template>
  <article class="action-card" :class="{'action-card--compact': compact}" :aria-label="attempt.wording">
    <div class="action-card-heading">
      <div>
        <h3>{{ attempt.wording }}</h3>
        <p v-if="!compact" class="action-source">{{ attempt.sourceLabel || 'Custom action' }}</p>
      </div>
      <CompactAction v-if="!compact" icon="pi pi-pencil" :aria-label="'Edit ' + attempt.wording" @click="$emit('edit', attempt)" />
    </div>
    <div class="action-progress"><strong>{{ attempt.streak }} / {{ attempt.targetDays }}</strong> consecutive days<span v-if="attempt.status === 'SCHEDULED'"> · Starts {{ attempt.startDate }}</span><span v-else> · {{ statusLabel }}</span></div>
    <ProgressBar :value="attempt.percentage" :showValue="false" :aria-label="attempt.percentage + '% of target'" />
    <div v-if="!compact" class="action-days" aria-label="Recent check-ins">
      <div v-for="day in recentDays" :key="day.date" :class="{'action-day--checked': day.checked}"><span>{{ day.label }}</span><strong>{{ day.checked ? '✓' : '—' }}</strong></div>
    </div>
    <div class="action-card-footer">
      <span>{{ attempt.checked ? 'Checked' : attempt.status === 'SCHEDULED' ? 'Scheduled' : 'Not checked' }}</span>
      <div class="action-group action-group--compact">
        <CompactAction v-if="canCheck" :icon="attempt.checked ? 'pi pi-undo' : 'pi pi-plus'" :aria-label="(attempt.checked ? 'Undo ' : 'Complete ') + attempt.wording" :class="attempt.checked ? 'p-button-warning' : 'p-button-success'" :action="() => check(date, !attempt.checked)" />
        <CompactAction v-if="!compact && ['ACTIVE', 'SCHEDULED'].includes(attempt.status)" icon="pi pi-stop" :aria-label="'Stop ' + attempt.wording" @click="showStop = true" />
        <CompactAction v-if="!compact && ['STOPPED', 'COMPLETED'].includes(attempt.status)" icon="pi pi-refresh" :aria-label="'Start again: ' + attempt.wording" @click="$emit('restart', attempt)" />
      </div>
    </div>
    <details v-if="!compact" class="action-history">
      <summary>View check-ins</summary>
      <p>Best streak: {{ attempt.bestStreak }} days<span v-if="attempt.stoppedDate"> · Stopped {{ attempt.stoppedDate }}</span><span v-if="attempt.completedDate"> · Completed {{ attempt.completedDate }}</span></p>
      <p v-if="!attempt.checkinDates.length">No check-ins yet.</p>
      <ul v-else><li v-for="day in attempt.checkinDates" :key="day">{{ day }} ✓</li></ul>
      <label :for="'correction-' + attempt.id">Check-in date</label>
      <div class="correction-row">
        <InputText :id="'correction-' + attempt.id" v-model="correctionDate" type="date" :min="attempt.startDate" :max="correctionMax" />
        <CompactAction :icon="correctionChecked ? 'pi pi-undo' : 'pi pi-plus'" :aria-label="(correctionChecked ? 'Undo check-in for ' : 'Complete check-in for ') + correctionDate" :disabled="!correctionAllowed" :class="correctionChecked ? 'p-button-warning' : 'p-button-success'" :action="() => check(correctionDate, !correctionChecked)" />
      </div>
    </details>
    <Dialog v-model:visible="showStop" appendTo="body" modal header="Stop this attempt?" :closable="!stopping" :closeOnEscape="!stopping" :style="{width: 'min(28rem, calc(100vw - 2rem))'}">
      <p>Stopping preserves this attempt and its check-ins in History. Starting again creates an independent attempt.</p>
      <template #footer><div class="action-group">
        <Button label="Cancel" class="p-button-text" :disabled="stopping" @click="showStop = false" />
        <ActionButton label="Stop action" :action="stop" :loading="stopping" />
      </div></template>
    </Dialog>
  </article>
</template>

<script>
import dayjs from 'dayjs';
import actionService, {actionToday} from '../services/ActionService';
export default {
  props: {attempt: {type: Object, required: true}, date: {type: String, required: true}, compact: Boolean},
  emits: ['changed', 'edit', 'restart'],
  data() { return {correctionDate: this.date, showStop: false, stopping: false}; },
  computed: {
    statusLabel() { return {ACTIVE: 'Active', COMPLETED: 'Completed', STOPPED: 'Stopped', SCHEDULED: 'Scheduled'}[this.attempt.status]; },
    canCheck() { return this.date >= this.attempt.startDate && (!this.attempt.stoppedDate || this.date <= this.attempt.stoppedDate) && (!this.attempt.completedDate || this.date <= this.attempt.completedDate || this.attempt.checked); },
    correctionMax() { return this.attempt.stoppedDate || actionToday(); },
    correctionChecked() { return this.attempt.checkinDates.includes(this.correctionDate); },
    correctionAllowed() { return this.correctionDate >= this.attempt.startDate && this.correctionDate <= this.correctionMax
      && (this.correctionChecked || !this.attempt.completedDate || this.correctionDate <= this.attempt.completedDate); },
    recentDays() {
      return Array.from({length: 7}, (_, index) => {
        const day = dayjs(this.date).subtract(6 - index, 'day');
        return {date: day.format('YYYY-MM-DD'), label: day.format('ddd'), checked: this.attempt.checkinDates.includes(day.format('YYYY-MM-DD'))};
      });
    }
  },
  methods: {
    async check(date, checked) { await actionService.check(this.attempt.id, date, checked); this.$emit('changed'); },
    async stop() {
      this.stopping = true;
      try { await actionService.stop(this.attempt.id); this.showStop = false; this.$emit('changed'); }
      finally { this.stopping = false; }
    }
  }
};
</script>

<style scoped>
.action-card { min-width: 0; padding: 1rem; margin-bottom: 1rem; border: 1px solid #dedede; border-radius: 4px; background: white; overflow-wrap: anywhere; }
.action-card-heading, .action-card-footer { display: flex; align-items: center; justify-content: space-between; gap: .75rem; }
.action-card-heading > div { min-width: 0; }
.action-card h3 { margin: 0; font-size: 1rem; line-height: 1.4; }
.action-source { margin: .35rem 0; color: #63717b; font-size: .85rem; }
.action-progress { margin: .65rem 0 .3rem; color: #63717b; font-size: .9rem; }
.action-progress strong { color: #21313c; }
.action-card :deep(.p-progressbar) { height: .3rem; }
.action-days { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: .25rem; margin: .75rem 0; }
.action-days > div { display: grid; gap: .3rem; text-align: center; color: #63717b; font-size: .75rem; }
.action-days strong { border: 1px solid #ddd; padding: .4rem .1rem; }
.action-day--checked strong { background: #eef7ef; color: #35724b; border-color: #a8cbb2; }
.action-card-footer { margin-top: .65rem; font-size: .85rem; color: #63717b; }
.action-history { margin-top: .75rem; }
.action-history summary { color: #245b83; cursor: pointer; }
.action-history ul { display: flex; flex-wrap: wrap; gap: .4rem 1rem; padding-left: 1rem; }
.correction-row { display: flex; align-items: center; gap: .5rem; margin-top: .4rem; }
.correction-row input { min-width: 0; max-width: 100%; }
.action-card--compact { border: 0; border-bottom: 1px solid #dedede; border-radius: 0; padding: .75rem 0; margin: 0; }
</style>
