<template>
  <article :class="next ? 'guided-next' : 'guided-card'" :aria-label="`${line.exerciseName}, set ${setIndex + 1}`">
    <p class="guided-step-label">{{ next ? 'Next' : 'Current' }} · Set {{ setIndex + 1 }} of {{ line.segments.length }}</p>
    <div class="guided-summary-heading"><ExercisePicture :src="line.imageUrl" :name="line.exerciseName" :description="line.exerciseDescription" /><div class="guided-summary-title"><component :is="next ? 'h3' : 'h2'">{{ line.exerciseName }}</component><Tag v-if="line.supersetGroupId" value="Superset" severity="info" /></div><Button v-if="line.exerciseDescription" label="Details" icon="pi pi-info-circle" class="p-button-text guided-details-button" @click="detailsVisible = true" /></div>
    <div class="guided-target"><div><small>{{ plannedTarget ? (next ? 'Saved target' : 'Current target') : 'Unplanned work' }}</small><strong>{{ target }}</strong></div><slot name="action" /></div>
    <div class="guided-set-times"><slot name="elapsed" /><div class="guided-reference-time"><span>{{ timeLabel }}</span> <strong>{{ timeValue }}</strong></div></div>
  </article>
  <Dialog v-model:visible="detailsVisible" header="Exercise details" appendTo="body" :modal="true" :style="{width: 'min(520px, 96vw)'}"><h2>{{ line.exerciseName }}</h2><p class="guided-exercise-type">{{ exerciseTypeLabel(line.exerciseType) }}</p><p>{{ line.exerciseDescription }}</p><template #footer><Button label="Close" @click="detailsVisible = false" /></template></Dialog>
</template>
<script>
import ExercisePicture from './ExercisePicture.vue';
import Tag from 'primevue/tag';
import {exerciseTypeLabel} from '@/model/WorkoutExercise';
export default {
  name: 'GuidedExerciseSummary',
  components: {ExercisePicture, Tag},
  props: {line: {type: Object, required: true}, setIndex: {type: Number, required: true}, target: {type: String, required: true}, timeLabel: {type: String, required: true}, timeValue: {type: String, required: true}, plannedTarget: Boolean, next: Boolean},
  data() { return {detailsVisible: false}; },
  methods: {exerciseTypeLabel}
};
</script>
<style scoped>
.guided-card, .guided-next { min-width: 0; overflow-wrap: anywhere; }
.guided-next { background: #f6f7f8; padding: .75rem; border: 1px solid #d6d6d6; border-radius: 4px; }
.guided-step-label { margin: 0 0 .5rem; color: #59636e; font-weight: 600; font-size: .875rem; }
.guided-summary-heading { display: flex; align-items: center; gap: .6rem; min-width: 0; }
.guided-summary-heading > div { min-width: 0; }
.guided-summary-title { display: grid; gap: .25rem; min-width: 0; }
.guided-details-button { flex: 0 0 auto; margin-left: auto; }
.guided-summary-heading :deep(.exercise-picture) { margin: 0; }
.guided-summary-heading :deep(.exercise-picture-button) { width: 56px; height: 56px; }
h2, h3 { margin: 0; font-size: 1.35rem; }
.guided-next h3 { font-size: 1.1rem; }
.guided-target { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: .5rem; padding: .6rem; margin: .6rem 0; border-left: 3px solid var(--primary-color, #007ad9); background: #f3f8fc; }
.guided-target > div { display: grid; gap: .25rem; min-width: 0; flex: 1 1 10rem; }
.guided-target small { color: #59636e; }
.guided-target strong { font-size: 1.15rem; }
.guided-target :deep(.p-button) { flex-shrink: 0; }
.guided-set-times { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: .5rem 1rem; }
.guided-reference-time { display: grid; gap: .25rem; color: #59636e; }
.guided-reference-time strong { color: #212529; font-size: 1.6rem; font-variant-numeric: tabular-nums; }
.guided-next .guided-set-times { grid-template-columns: minmax(0, 1fr); }
.guided-next .guided-reference-time { display: flex; flex-wrap: wrap; gap: .25rem; font-size: .875rem; }
.guided-next .guided-reference-time strong { font-size: .875rem; font-weight: normal; color: #59636e; }
</style>
