<template>
  <section class="weekly-plan" aria-label="Weekly workout plan">
    <div class="plan-toolbar">
      <h2>{{ draft ? (creating ? 'New weekly plan' : 'Edit weekly plan') : viewed ? 'Previous weekly plan' : 'Weekly plan' }}</h2>
      <div class="plan-actions" v-if="!draft">
        <Button v-if="viewed" label="Current plan" icon="pi pi-arrow-left" class="p-button-outlined" @click="viewed = null" />
        <Button v-else-if="current" label="Edit plan" icon="pi pi-pencil" class="p-button-outlined" :disabled="loading" @click="edit" />
        <Button label="New plan" icon="pi pi-plus" :disabled="loading" @click="newDialog = true" />
        <Button label="Previous plans" icon="pi pi-history" class="p-button-outlined" :disabled="loading" @click="showArchive" />
      </div>
    </div>
    <p v-if="loading" role="status">Loading workout plan…</p>
    <p v-if="error" role="alert" class="error">{{ error }} <Button v-if="!draft" label="Retry" class="p-button-text" @click="load" /></p>
    <p v-if="!loading && !displayed && !error">No weekly plan yet. Create a plan for your next commitment.</p>
    <template v-if="displayed">
      <div v-if="draft" class="p-fluid p-formgrid p-grid">
        <div class="p-field p-col-12 p-md-6"><label for="workout-plan-start">Start date</label><input id="workout-plan-start" v-model="draft.startDate" type="date" class="p-inputtext p-component" /></div>
        <div class="p-field p-col-12 p-md-6"><label for="workout-plan-review">Review date</label><input id="workout-plan-review" v-model="draft.reviewDate" type="date" :min="draft.startDate" class="p-inputtext p-component" /></div>
        <div class="p-field p-col-12"><label for="workout-plan-notes">Notes (optional)</label><textarea id="workout-plan-notes" v-model="draft.notes" rows="2" maxlength="500" class="p-inputtext p-component" /></div>
      </div>
      <template v-else>
        <p>Start {{ date(displayed.startDate) }} · Review {{ date(displayed.reviewDate) }} <Tag v-if="!viewed && reviewDue" value="Review due" severity="warning" /></p>
        <p v-if="viewed">Archived {{ date(viewed.archivedAt) }}</p>
        <p v-if="displayed.notes" class="plan-note">{{ displayed.notes }}</p>
      </template>
      <p class="plan-help">A repeating weekly schedule for reference. Record completed workouts in the diary.</p>
      <div class="plan-days">
        <article v-for="(day, index) in displayed.days" :key="day.day" class="plan-day">
          <div class="plan-day-header">
            <button type="button" class="plan-day-toggle" :aria-expanded="expanded.includes(day.day)" :aria-controls="`planned-${day.day}`" @click="toggle(day.day)">
              <i :class="expanded.includes(day.day) ? 'pi pi-chevron-down' : 'pi pi-chevron-right'" aria-hidden="true" />
              <span><strong>{{ dayLabel(day.day) }}</strong><span class="plan-day-summary">{{ summary(day) }}</span></span>
            </button>
            <div v-if="draft" class="plan-actions">
              <Button :label="day.lines.length ? 'Edit workout' : 'Add workout'" icon="pi pi-pencil" class="p-button-outlined p-button-sm" @click="editDay(index)" />
              <Button label="Rest" icon="pi pi-pause" :class="day.rest === true ? 'p-button-secondary p-button-sm' : 'p-button-outlined p-button-sm'" @click="setRest(index)" />
              <Button label="Copy" icon="pi pi-copy" class="p-button-outlined p-button-sm" @click="copyIndex = index; copySource = null" />
            </div>
          </div>
          <div v-show="expanded.includes(day.day)" :id="`planned-${day.day}`" class="plan-day-details">
            <p v-if="day.note" class="plan-note">{{ day.note }}</p>
            <div v-for="line in day.lines" :key="line.exerciseId" class="planned-exercise">
              <ExercisePicture :src="picture(line.exerciseId)" :name="line.exerciseName" :description="line.exerciseDescription" />
              <strong>{{ line.exerciseName }}</strong><small> · {{ exerciseTypeLabel(line.exerciseType) }}</small>
              <p v-if="line.exerciseDescription" class="plan-help">{{ line.exerciseDescription }}</p>
              <ol><li v-for="(segment, segmentIndex) in line.segments" :key="segmentIndex">{{ target(line, segment) }}</li></ol>
            </div>
          </div>
        </article>
      </div>
      <div v-if="draft" class="plan-actions plan-footer">
        <Button label="Save plan" icon="pi pi-check" :loading="saving" @click="save" />
        <Button label="Cancel" class="p-button-secondary" :disabled="saving" @click="cancel" />
      </div>
    </template>
    <Dialog header="New weekly plan" appendTo="body" v-model:visible="newDialog" :modal="true" :style="{width: 'min(480px, 94vw)'}">
      <p v-if="current">Saving a new plan archives your current commitment. You can review it in Previous plans.</p><p v-else>Choose a workout or rest for every weekday.</p>
      <template #footer><div class="plan-actions"><Button label="Start blank" @click="create(false)" /><Button v-if="current" label="Copy current plan" class="p-button-outlined" @click="create(true)" /><Button label="Cancel" class="p-button-secondary" @click="newDialog = false" /></div></template>
    </Dialog>
    <Dialog header="Copy a day" appendTo="body" :visible="copyIndex !== null" @update:visible="copyIndex = null" :modal="true" :style="{width: 'min(420px, 94vw)'}">
      <label for="plan-copy-source">Copy from</label><Dropdown inputId="plan-copy-source" aria-label="Copy from" v-model="copySource" :options="copyOptions" optionLabel="label" optionValue="value" placeholder="Select a day" class="plan-copy-select" />
      <p>This replaces the destination day’s workout and notes.</p>
      <template #footer><Button label="Copy" :disabled="copySource === null" @click="copyDay" /><Button label="Cancel" class="p-button-secondary" @click="copyIndex = null" /></template>
    </Dialog>
    <Dialog header="Previous plans" appendTo="body" v-model:visible="archiveDialog" :modal="true" :style="{width: 'min(640px, 94vw)'}">
      <p v-if="archiveError" class="error" role="alert">{{ archiveError }} <Button label="Retry" class="p-button-text" @click="loadArchive(archive.page)" /></p>
      <DataTable :value="archive.items" :loading="archiveLoading" :paginator="true" :lazy="true" :rows="10" :first="archive.page * 10" :totalRecords="archive.totalElements" @page="loadArchive($event.page)" responsiveLayout="scroll">
        <template #empty>No previous plans.</template>
        <Column header="Start"><template #body="{data}">{{ date(data.startDate) }}</template></Column>
        <Column header="Review"><template #body="{data}">{{ date(data.reviewDate) }}</template></Column>
        <Column header=""><template #body="{data}"><Button label="View" class="p-button-outlined p-button-sm" @click="viewArchive(data.id)" /></template></Column>
      </DataTable>
    </Dialog>
    <WorkoutEditor v-if="dayIndex !== null" :show="true" :planning="true" :workout="dayWorkout" @onSave="saveDay" @onClose="dayIndex = null" />
  </section>
</template>

<script>
import dayjs from 'dayjs';
import Tag from 'primevue/tag';
import WorkoutPlan, {dayLabel, copyPlan} from '../model/WorkoutPlan';
import service from '../services/WorkoutPlanService';
import exerciseService from '../services/WorkoutExerciseService';
import {exerciseTypeLabel} from '../model/WorkoutExercise';
import ExercisePicture from './ExercisePicture.vue';
import WorkoutEditor from './WorkoutEditor.vue';

export default {
  name: 'WeeklyWorkoutPlan', components: {ExercisePicture, WorkoutEditor, Tag},
  data() { return {current: null, viewed: null, draft: null, creating: false, loading: false, saving: false, error: '', exercises: [], expanded: [], newDialog: false, dayIndex: null, copyIndex: null, copySource: null, archiveDialog: false, archiveLoading: false, archiveError: '', archive: {items: [], page: 0, totalElements: 0}}; },
  computed: {
    displayed() { return this.draft || this.viewed || this.current; },
    reviewDue() { return this.current && dayjs().startOf('day').isAfter(dayjs(this.current.reviewDate)); },
    dayWorkout() { const day = this.draft.days[this.dayIndex]; return {workoutDate: this.draft.startDate, note: day.note, lines: day.lines.map(line => ({...line, sets: line.segments, intervals: line.segments}))}; },
    copyOptions() { return this.draft ? this.draft.days.map((day, index) => ({label: dayLabel(day.day), value: index, ready: day.rest !== null})).filter(day => day.value !== this.copyIndex && day.ready) : []; }
  },
  async created() { await this.load(); },
  methods: {
    dayLabel, exerciseTypeLabel,
    date(value) { return dayjs(value).format('DD/MM/YYYY'); },
    picture(id) { return this.exercises.find(exercise => exercise.id === id)?.imageUrl; },
    summary(day) { return day.rest === null ? 'Choose workout or rest' : day.rest ? 'Rest' : day.lines.map(line => line.exerciseName).join(', '); },
    toggle(day) { this.expanded = this.expanded.includes(day) ? this.expanded.filter(value => value !== day) : [...this.expanded, day]; },
    async load() { this.loading = true; this.error = ''; try { [this.current, this.exercises] = await Promise.all([service.current(), exerciseService.get_all()]); } catch (e) { this.error = e.message; } finally { this.loading = false; } },
    edit() { this.draft = new WorkoutPlan(this.current); this.creating = false; this.error = ''; },
    create(copy) { this.draft = new WorkoutPlan(copy ? this.current : undefined); if (copy) { const dates = new WorkoutPlan(); this.draft.startDate = dates.startDate; this.draft.reviewDate = dates.reviewDate; } this.creating = true; this.viewed = null; this.newDialog = false; this.error = ''; },
    cancel() { this.draft = null; this.error = ''; },
    editDay(index) { this.dayIndex = index; },
    saveDay(workout) { const day = this.draft.days[this.dayIndex]; day.rest = false; day.note = workout.note; day.lines = workout.lines; if (!this.expanded.includes(day.day)) this.expanded.push(day.day); },
    setRest(index) { const day = this.draft.days[index]; if (day.lines.length && !confirm('Replace this workout with rest?')) return; day.rest = true; day.lines = []; },
    copyDay() { const destination = this.draft.days[this.copyIndex].day; this.draft.days[this.copyIndex] = {...copyPlan(this.draft.days[this.copySource]), day: destination}; this.copyIndex = null; },
    async save() {
      this.error = '';
      if (!this.draft.startDate || !this.draft.reviewDate || this.draft.reviewDate < this.draft.startDate) { this.error = 'Enter start and review dates, with review on or after start.'; return; }
      if (this.draft.days.some(day => day.rest === null || (!day.rest && !day.lines.length))) { this.error = 'Choose a workout or rest for all seven days.'; return; }
      this.saving = true;
      try { this.current = await (this.creating ? service.create(this.draft) : service.update(this.draft)); this.draft = null; this.$toast.add({severity: 'success', summary: 'Workout plan saved', life: 3000}); }
      catch (e) { this.error = e.message; }
      finally { this.saving = false; }
    },
    async showArchive() { this.archiveDialog = true; await this.loadArchive(0); },
    async loadArchive(page) { this.archiveLoading = true; this.archiveError = ''; try { this.archive = await service.archive(page); } catch (e) { this.archiveError = e.message; } finally { this.archiveLoading = false; } },
    async viewArchive(id) { this.archiveLoading = true; try { this.viewed = await service.get(id); this.archiveDialog = false; this.expanded = []; } catch (e) { this.archiveError = e.message; } finally { this.archiveLoading = false; } },
    target(line, segment) {
      const duration = seconds => `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
      if (line.trackingMode === 'REPS') return `${segment.weight ?? 0} kg × ${segment.repetitions} reps`;
      if (line.trackingMode === 'SECONDS') return `${line.exerciseType === 'STRETCHING' ? '' : `${segment.weight ?? 0} kg × `}${duration(segment.durationSeconds)}`;
      return [duration(segment.durationSeconds), ...[['distanceKm', 'km'], ['speedKph', 'km/h'], ['inclinePercent', '% incline'], ['resistanceLevel', 'resistance']].filter(([key]) => segment[key] != null).map(([key, unit]) => `${segment[key]} ${unit}`)].join(' · ');
    }
  }
};
</script>

<style scoped>
.weekly-plan { min-width: 0; }
.plan-toolbar, .plan-actions, .plan-day-header { display: flex; align-items: center; gap: .5rem; flex-wrap: wrap; }
.plan-toolbar { justify-content: space-between; margin-bottom: 1rem; }
.plan-toolbar h2 { font-size: 1.2rem; margin: 0; }
.plan-days { display: grid; gap: .75rem; }
.plan-day { border: 1px solid #dee2e6; border-radius: 4px; padding: .75rem; min-width: 0; }
.plan-day-toggle { display: flex; align-items: center; gap: .75rem; flex: 1; min-width: 0; text-align: left; color: inherit; background: none; border: 0; font: inherit; padding: .3rem 0; cursor: pointer; }
.plan-day-toggle span { min-width: 0; }
.plan-day-summary { display: block; margin-top: .25rem; font-size: .9rem; overflow-wrap: anywhere; }
.plan-day-details { margin-top: .75rem; overflow-wrap: anywhere; }
.plan-note { white-space: pre-wrap; overflow-wrap: anywhere; }
.plan-help { color: var(--text-color-secondary); font-size: .9rem; }
.planned-exercise + .planned-exercise { margin-top: 1rem; }
.planned-exercise ol { padding-left: 1.5rem; margin: .4rem 0; }
.plan-footer { margin-top: 1rem; }
.plan-copy-select { width: 100%; margin-top: .5rem; }
textarea { resize: vertical; }
@media (max-width: 640px) { .plan-day-toggle { flex-basis: 100%; } .plan-toolbar > .plan-actions { width: 100%; } }
</style>
