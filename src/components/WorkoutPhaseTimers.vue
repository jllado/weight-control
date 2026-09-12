<template>
  <section aria-label="Workout timers" class="workout-timers">
    <p>Time each phase, including rest. Stop to exclude a break; start another phase to switch.</p>
    <p v-if="draft"><strong>Total: {{ total }}</strong> · {{ draft.runningPhase ? 'Running' : 'Stopped' }}</p>
    <div class="phase-timer-grid">
      <div v-for="phase in phases" :key="phase.key" class="phase-timer">
        <strong>{{ phase.label }}</strong>
        <span class="elapsed">{{ elapsed(phase.key) }}</span>
        <Button v-if="draft?.runningPhase === phase.key" label="Stop" icon="pi pi-stop" :aria-label="`Stop ${phase.label.toLowerCase()}`" @click="$emit('stop')" />
        <Button v-else label="Start" icon="pi pi-play" class="p-button-outlined" :aria-label="`Start ${phase.label.toLowerCase()}`" @click="$emit('start', phase.key)" />
      </div>
    </div>
    <small>Stored in this browser only. Clearing browser storage removes the draft. Saved phases round up to whole minutes.</small>
  </section>
</template>
<script>
import {workoutPhases, phaseMilliseconds, formatElapsed} from '@/services/WorkoutTimerService';
export default {
  name: 'WorkoutPhaseTimers',
  props: {draft: Object},
  emits: ['start', 'stop'],
  data() { return {phases: workoutPhases, now: Date.now(), tick: null}; },
  computed: {total() { return formatElapsed(this.phases.reduce((sum, phase) => sum + phaseMilliseconds(this.draft, phase.key, this.now), 0)); }},
  mounted() { this.tick = setInterval(() => { this.now = Date.now(); }, 1000); },
  beforeUnmount() { clearInterval(this.tick); },
  methods: {elapsed(key) { return formatElapsed(this.draft ? phaseMilliseconds(this.draft, key, this.now) : 0); }}
};
</script>
<style scoped>
.workout-timers { margin-bottom: 1rem; }
.phase-timer-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: .75rem; margin-bottom: .75rem; }
.phase-timer { display: flex; flex-direction: column; gap: .5rem; padding: .75rem; border: 1px solid #d6d6d6; border-radius: 6px; }
.elapsed { font-variant-numeric: tabular-nums; }
@media (max-width: 640px) { .phase-timer-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
