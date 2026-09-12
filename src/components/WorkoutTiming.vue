<template>
  <div v-if="workout.startTime || workout.durationMinutes != null" class="workout-timing">
    <span v-if="workout.startTime">Start: {{ workout.startTime.slice(0, 5) }}</span>
    <span v-if="workout.durationMinutes != null">Duration: {{ workout.durationMinutes }} min</span>
    <small v-if="workout.warmUpMinutes != null">Warm-up: {{ workout.warmUpMinutes }} min · Training: {{ workout.trainingMinutes }} min · Stretching: {{ workout.stretchingMinutes }} min</small>
  </div>
</template>

<script>
export default {name: 'WorkoutTiming', props: {workout: {type: Object, required: true}}};
</script>

<style scoped>
.workout-timing { display: flex; flex-wrap: wrap; gap: .25rem .75rem; overflow-wrap: anywhere; }
.workout-timing small { flex-basis: 100%; }
</style>
