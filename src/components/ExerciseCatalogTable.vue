<template>
  <DataTable :tableStyle="{tableLayout: 'fixed'}" :value="exercises" :paginator="true" :rows="10" :loading="loading" responsiveLayout="scroll"
             paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
             currentPageReportTemplate="{first} to {last} of {totalRecords}">
    <template #header><div class="table-header">{{ title }}<Button icon="pi pi-plus" label="New" @click="$emit('create')" /></div></template>
    <template #empty>{{ emptyMessage }}</template>
    <Column header="Name"><template #body="exercise"><div class="exercise-name-picture"><ExercisePicture :src="exercise.data.imageUrl" :name="exercise.data.name" :description="exercise.data.description" /><div class="exercise-name-details"><strong>{{ exercise.data.name }}</strong><small class="exercise-mobile-details">{{ exercise.data.description }}</small><small class="exercise-mobile-details">{{ trackingModeLabel(exercise.data.trackingMode) }}</small></div></div></template></Column>
    <Column header="Mode" headerClass="exercise-desktop-column" bodyClass="exercise-desktop-column" headerStyle="width: 110px"><template #body="exercise">{{ trackingModeLabel(exercise.data.trackingMode) }}</template></Column>
    <Column header="Description" field="description" headerClass="exercise-desktop-column" bodyClass="exercise-desktop-column" />
    <Column headerStyle="width: 120px"><template #body="exercise"><div class="diary-row-actions action-group action-group--compact"><CompactAction icon="pi pi-pencil" aria-label="Edit exercise" @click="$emit('edit', exercise.data)" /><CompactAction icon="pi pi-trash" aria-label="Delete exercise" :action="() => removeAction(exercise.data)" busyLabel="Deleting…" destructive /></div></template></Column>
  </DataTable>
</template>

<script>
import ExercisePicture from './ExercisePicture.vue';
import {trackingModeLabel} from '@/model/WorkoutExercise';

export default {
  components: {ExercisePicture},
  props: {title: {type: String, required: true}, exercises: {type: Array, required: true}, loading: {type: Boolean, required: true}, emptyMessage: {type: String, required: true}, removeAction: {type: Function, required: true}},
  emits: ['create', 'edit'],
  methods: {trackingModeLabel}
};
</script>

<style scoped>
.exercise-name-picture { display: flex; align-items: center; gap: .5rem; flex-wrap: wrap; }
.exercise-name-details { min-width: 0; overflow-wrap: anywhere; }
.exercise-mobile-details { display: none; }
@media (max-width: 640px) {
  :deep(.exercise-desktop-column) { display: none; }
  .exercise-mobile-details { display: block; margin-top: .4rem; }
}
</style>
