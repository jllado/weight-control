<template>
  <section class="p-mt-4" aria-label="Saved stretching sets">
    <DataTable :value="sets" :loading="loading" :tableStyle="{tableLayout: 'fixed'}">
      <template #header><div class="set-actions"><strong>Saved stretching sets</strong><Button label="New set" icon="pi pi-plus" @click="edit()" /></div></template>
      <template #empty>No saved stretching sets yet.</template>
      <Column header="Name"><template #body="{data}"><strong class="set-name">{{ data.name }}</strong><div class="set-name"><small>{{ summary(data) }}</small></div></template></Column>
      <Column headerStyle="width: 120px"><template #body="{data}"><div class="set-actions action-group action-group--compact"><CompactAction icon="pi pi-pencil" :aria-label="`Edit stretching set ${data.name}`" @click="edit(data)" /><CompactAction icon="pi pi-trash" :aria-label="`Delete stretching set ${data.name}`" @click="deleting = data" destructive /></div></template></Column>
    </DataTable>
    <p v-if="loadError" class="error" role="alert">{{ loadError }} <Button label="Retry" class="p-button-text" @click="load" /></p>
    <Dialog header="Stretching set" appendTo="body" v-model:visible="visible" :modal="true" :closable="!saving" :closeOnEscape="false" :style="{width: 'min(720px, 96vw)'}">
    <SaveFields :saving="saving">
      <div class="p-fluid">
        <div class="p-field"><label for="stretching-set-name">Name</label><InputText id="stretching-set-name" v-model="draft.name" maxlength="255" /><small v-if="errors.name" class="error" role="alert">{{ errors.name }}</small></div>
        <div class="p-field"><label for="stretching-set-exercises">Exercises</label><MultiSelect inputId="stretching-set-exercises" v-model="selected" :options="exercises" optionLabel="name" optionValue="id" filter placeholder="Select stretching exercises" :maxSelectedLabels="1" selectedItemsLabel="{0} exercises selected" :panelStyle="{maxWidth: '96vw'}" @change="selectExercises">
          <template #option="{option}"><span class="set-exercise-option"><img v-if="option.imageUrl" :src="option.imageUrl" alt="" loading="lazy" /><span>{{ option.name }}</span></span></template>
        </MultiSelect><small v-if="errors.entries" class="error" role="alert">{{ errors.entries }}</small></div>
        <div v-for="(entry, index) in draft.entries" :key="entry.exerciseId" class="set-entry">
          <div class="set-heading"><div class="set-heading-name"><ExercisePicture :src="exercise(entry).imageUrl" :name="exercise(entry).name" :description="exercise(entry).description" /><strong class="set-name">{{ exercise(entry).name }}</strong></div><div class="set-actions action-group action-group--compact"><CompactAction icon="pi pi-arrow-up" :aria-label="`Move stretch ${index + 1} up`" :disabled="index === 0" @click="move(index, -1)" /><CompactAction icon="pi pi-arrow-down" :aria-label="`Move stretch ${index + 1} down`" :disabled="index === draft.entries.length - 1" @click="move(index, 1)" /><CompactAction icon="pi pi-trash" :aria-label="`Remove stretch ${index + 1}`" @click="remove(index)" destructive /></div></div>
          <div class="p-field p-mt-3"><label :for="`stretching-unit-${entry.exerciseId}`">Mode</label><Dropdown :inputId="`stretching-unit-${entry.exerciseId}`" v-model="entry.stretchingUnit" aria-label="Mode" :options="stretchingUnitOptions" optionLabel="label" optionValue="value" @change="entry.holds = entry.holds.map(() => newHold())" /></div>
          <p v-if="entry.stretchingUnit === 'BREATHS'"><small>One breath means an inhale and exhale.</small></p>
          <div v-for="(hold, holdIndex) in entry.holds" :key="hold.id" class="set-hold" :class="{'breath-hold': entry.stretchingUnit === 'BREATHS'}">
            <strong>Hold {{ holdIndex + 1 }}</strong>
            <div v-if="entry.stretchingUnit === 'BREATHS'"><label :for="`hold-breaths-${hold.id}`">Breaths</label><InputNumber :inputId="`hold-breaths-${hold.id}`" v-model="hold.breaths" @update:modelValue="hold.error = ''" :min="1" :maxFractionDigits="0" :useGrouping="false" /></div>
            <div v-if="entry.stretchingUnit !== 'BREATHS'"><label :for="`hold-minutes-${hold.id}`">Minutes</label><InputNumber :inputId="`hold-minutes-${hold.id}`" v-model="hold.minutes" @update:modelValue="hold.error = ''" :min="0" :maxFractionDigits="0" /></div>
            <div v-if="entry.stretchingUnit !== 'BREATHS'"><label :for="`hold-seconds-${hold.id}`">Seconds</label><Dropdown :inputId="`hold-seconds-${hold.id}`" v-model="hold.seconds" @update:modelValue="hold.error = ''" :options="secondOptions" optionLabel="label" optionValue="value" /></div>
            <CompactAction icon="pi pi-trash" :aria-label="`Remove hold ${holdIndex + 1} from ${exercise(entry).name}`" :disabled="entry.holds.length === 1" @click="entry.holds.splice(holdIndex, 1)" destructive />
            <small v-if="hold.error" class="error hold-error" role="alert">{{ hold.error }}</small>
          </div>
          <Button label="Add hold" icon="pi pi-plus" class="p-button-outlined p-mt-2" @click="entry.holds.push(newHold())" />
        </div>
      </div>
      <p v-if="saveError" class="error" role="alert">{{ saveError }}</p>
      </SaveFields>
    <template #footer><div class="action-group"><Button :label="saving ? 'Saving…' : 'Save'" icon="pi pi-check" :loading="saving" @click="save" :aria-busy="saving" :disabled="saving" /><Button label="Cancel" icon="pi pi-times" class="p-button-secondary" :disabled="saving" @click="visible = false" /></div></template>
    </Dialog>
    <Dialog header="Delete stretching set" appendTo="body" :visible="!!deleting" :modal="true" :closable="false" :style="{width: 'min(440px, 96vw)'}">
      <p class="set-name">Delete “{{ deleting?.name }}”? Recorded workouts will keep their stretches.</p>
      <p v-if="deleteError" class="error" role="alert">{{ deleteError }}</p>
      <template #footer><div class="action-group"><Button label="Delete" icon="pi pi-trash" class="p-button-danger" :loading="saving" @click="removeSet" :disabled="saving" /><Button label="Cancel" class="p-button-secondary" :disabled="saving" @click="deleting = null; deleteError = ''" /></div></template>
    </Dialog>
  </section>
</template>

<script>
import service from '../services/StretchingSetService';
import ExercisePicture from './ExercisePicture.vue';
import {stretchingUnitOptions} from '../model/WorkoutExercise';
let holdId = 0;
export default {
  components: {ExercisePicture},
  props: {exercises: {type: Array, required: true}},
  data() { return {stretchingUnitOptions, sets: [], loading: false, loadError: '', visible: false, saving: false, draft: {name: '', entries: []}, selected: [], errors: {}, saveError: '', deleting: null, deleteError: ''}; },
  computed: { secondOptions() { return Array.from({length: 12}, (_, index) => ({label: String(index * 5).padStart(2, '0'), value: index * 5})); } },
  created() { this.load(); },
  methods: {
    async load() {
      this.loading = true;
      this.loadError = '';
      try { this.sets = await service.get_all(); } catch (e) { this.loadError = e.message; } finally { this.loading = false; }
    },
    exercise(entry) { return this.exercises.find(exercise => exercise.id === entry.exerciseId); },
    summary(set) { return set.entries.map(entry => `${this.exercise(entry).name}: ${(entry.stretchingUnit === 'BREATHS' ? entry.breaths.map(breaths => `${breaths} ${breaths === 1 ? 'breath' : 'breaths'}`) : entry.durations.map(duration => `${duration}s`)).join(' + ')}`).join(' · '); },
    newHold(duration = 0) { return {id: ++holdId, minutes: Math.floor(duration / 60), seconds: duration % 60, breaths: null, error: ''}; },
    edit(set) {
      this.draft = set ? {id: set.id, name: set.name, entries: set.entries.map(entry => ({exerciseId: entry.exerciseId, stretchingUnit: entry.stretchingUnit ?? 'SECONDS', holds: entry.stretchingUnit === 'BREATHS' ? entry.breaths.map(breaths => ({...this.newHold(), breaths})) : entry.durations.map(this.newHold)}))} : {name: '', entries: []};
      this.selected = this.draft.entries.map(entry => entry.exerciseId);
      this.errors = {};
      this.saveError = '';
      this.visible = true;
    },
    selectExercises() {
      this.draft.entries = this.draft.entries.filter(entry => this.selected.includes(entry.exerciseId));
      this.selected.filter(id => !this.draft.entries.some(entry => entry.exerciseId === id)).forEach(id => this.draft.entries.push({exerciseId: id, stretchingUnit: 'SECONDS', holds: [this.newHold()]}));
    },
    move(index, offset) { const [entry] = this.draft.entries.splice(index, 1); this.draft.entries.splice(index + offset, 0, entry); },
    remove(index) { this.draft.entries.splice(index, 1); this.selected = this.draft.entries.map(entry => entry.exerciseId); },
    async save() {
      if (this.saving) return;
      this.errors = {};
      this.saveError = '';
      if (!this.draft.name.trim()) { this.errors.name = 'Name is required'; }
      if (!this.draft.entries.length) { this.errors.entries = 'Select at least one stretching exercise'; }
      this.draft.entries.forEach(entry => entry.holds.forEach(hold => { hold.error = entry.stretchingUnit === 'BREATHS' ? (Number.isInteger(hold.breaths) && hold.breaths > 0 ? '' : 'Enter a positive breath count') : (hold.minutes || 0) * 60 + (hold.seconds || 0) > 0 ? '' : 'Enter a duration for this hold'; }));
      if (Object.keys(this.errors).length || this.draft.entries.some(entry => entry.holds.some(hold => hold.error))) { return; }
      this.saving = true;
      try {
        const saved = await service.save({id: this.draft.id, name: this.draft.name.trim(), entries: this.draft.entries.map(entry => ({exerciseId: entry.exerciseId, stretchingUnit: entry.stretchingUnit, breaths: entry.stretchingUnit === 'BREATHS' ? entry.holds.map(hold => hold.breaths) : [], durations: entry.stretchingUnit === 'BREATHS' ? [] : entry.holds.map(hold => (hold.minutes || 0) * 60 + (hold.seconds || 0))}))});
        this.sets = [...this.sets.filter(set => set.id !== saved.id), saved].sort((a, b) => a.name.localeCompare(b.name));
        this.visible = false;
        this.$toast.add({severity: 'success', summary: 'Stretching set saved', life: 3000});
      } catch (e) { this.saveError = e.message; } finally { this.saving = false; }
    },
    async removeSet() {
      this.saving = true;
      this.deleteError = '';
      try { await service.delete(this.deleting.id); this.sets = this.sets.filter(set => set.id !== this.deleting.id); this.deleting = null; } catch (e) { this.deleteError = e.message; } finally { this.saving = false; }
    }
  }
};
</script>

<style scoped>
.set-actions:not(.action-group), .set-heading, .set-heading-name { display: flex; align-items: center; gap: 0.5rem; }
.set-actions:not(.action-group) { flex-wrap: wrap; }
.set-heading { justify-content: space-between; }
.set-heading-name { min-width: 0; }
.set-heading > .set-actions:not(.action-group) { flex-shrink: 0; }
.set-name { overflow-wrap: anywhere; }
.set-exercise-option { display: flex; align-items: center; gap: 0.5rem; min-width: 0; white-space: normal; }
.set-exercise-option img { width: 64px; height: 64px; flex-shrink: 0; object-fit: contain; border: 1px solid #d6d6d6; border-radius: 4px; background: white; padding: 2px; }
.set-exercise-option > span { overflow-wrap: anywhere; }
.set-entry { border: 1px solid #d6d6d6; border-radius: 6px; padding: 12px; margin-top: 1rem; }
.set-hold { display: grid; grid-template-columns: auto minmax(0, 1fr) minmax(0, 1fr) auto; align-items: end; gap: 0.5rem; margin-top: 1rem; }
.set-hold.breath-hold { grid-template-columns: auto minmax(0, 1fr) auto; }
.set-hold label, .p-field > label { display: block; margin-bottom: 0.5rem; }
.hold-error { grid-column: 1 / -1; }
@media (max-width: 575px) { .set-hold.breath-hold { grid-template-columns: minmax(0, 1fr) auto; } .set-heading { align-items: flex-start; flex-direction: column; } .set-hold > strong { grid-column: 1 / -1; } .set-hold { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto; } }
</style>
