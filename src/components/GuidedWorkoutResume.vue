<template>
  <div v-if="guidedWorkoutState.draft" class="guided-workout-resume">
    <Button label="Resume guided workout" icon="pi pi-play" class="p-button-outlined" @click="resumeGuidedWorkout" />
    <CompactAction icon="pi pi-trash" aria-label="Discard guided workout" destructive @click="requestGuidedWorkoutDiscard" />
    <span>{{ guidedWorkoutState.draft.workout.plannedSessionName || 'Workout' }} · {{ progressLabel }}<template v-if="guidedWorkoutState.draft.timer"> · {{ timerStatus }}</template></span>
  </div>
</template>

<script>
import {guidedWorkoutState, guidedWorkoutSteps, guidedWorkoutProgressLabel, resumeGuidedWorkout, requestGuidedWorkoutDiscard} from '@/services/GuidedWorkoutService';

export default {
  name: 'GuidedWorkoutResume',
  data() { return {guidedWorkoutState}; },
  computed: {
    steps() { return guidedWorkoutSteps(this.guidedWorkoutState.draft.workout); },
    progressLabel() { return guidedWorkoutProgressLabel(this.guidedWorkoutState.draft); },
    timerStatus() {
      const draft = this.guidedWorkoutState.draft;
      const complete = draft.currentStep >= this.steps.length;
      return complete ? 'Complete' : draft.timer.runningPhase ? 'Running' : 'Paused';
    }
  },
  methods: {resumeGuidedWorkout, requestGuidedWorkoutDiscard}
};
</script>

<style scoped>
.guided-workout-resume { display: flex; flex-wrap: wrap; align-items: center; gap: .75rem; margin: 0 0 1rem; }
.guided-workout-resume > span { overflow-wrap: anywhere; }
</style>
