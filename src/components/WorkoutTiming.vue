<template>
  <div v-if="workout.startTime || workout.endTime || workout.durationMinutes != null || workout.saunaSession" class="workout-timing">
    <Tag v-if="workout.saunaSession" value="Sauna" class="sauna-tag" />
    <span v-if="workout.startTime">Start: {{ workout.startTime.slice(0, 5) }}</span>
    <span v-if="workout.endTime">End: {{ formatEndTime(workout.endTime) }}</span>
    <span v-if="workout.durationMinutes != null">Duration: {{ workout.durationMinutes }} min</span>
    <span v-if="workout.saunaSession">{{ saunaSummary(workout.saunaRoundsMinutes) }}</span>
    <small v-if="showPhaseBreakdown">Warm-up: {{ workout.warmUpMinutes }} min · Training: {{ workout.trainingMinutes }} min<template v-if="workout.cardioMinutes != null"> · Cardio: {{ workout.cardioMinutes }} min</template> · Stretching: {{ workout.stretchingMinutes }} min</small>
    <small v-if="workout.saunaSession">{{ workout.saunaRoundsMinutes.map((minutes, index) => `Round ${index + 1}: ${minutes} min`).join(' · ') }}</small>
  </div>
</template>

<script>
import Tag from 'primevue/tag';
import {saunaSummary} from '../model/Workout';
import {formatMadridWorkoutEnd} from '../model/WorkoutTiming';
export default {
  name: 'WorkoutTiming', components: {Tag}, props: {workout: {type: Object, required: true}},
  methods: {saunaSummary, formatEndTime(instant) { return formatMadridWorkoutEnd(instant, this.workout.workoutDate); }},
  computed: {showPhaseBreakdown() { return this.workout.warmUpMinutes != null && (!this.workout.saunaSession || this.workout.warmUpMinutes + this.workout.trainingMinutes + this.workout.stretchingMinutes + (this.workout.cardioMinutes || 0) > 0); }}
};
</script>

<style scoped>
.workout-timing { display: flex; flex-wrap: wrap; gap: .25rem .75rem; overflow-wrap: anywhere; }
.workout-timing small { flex-basis: 100%; }
</style>
