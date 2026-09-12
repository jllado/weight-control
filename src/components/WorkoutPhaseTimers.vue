<template>
  <section aria-label="Workout timers" class="workout-timers">
    <div class="timer-header">
      <button type="button" class="timer-toggle p-link" :aria-expanded="!collapsed" aria-controls="workout-timer-controls" :aria-label="`${collapsed ? 'Expand' : 'Collapse'} workout timers`" @click="collapsed = !collapsed">
        <i :class="collapsed ? 'pi pi-chevron-right' : 'pi pi-chevron-down'" aria-hidden="true"></i>
        <strong>Workout timers</strong>
      </button>
      <div v-if="draft && collapsed" class="timer-summary">
        <span><span class="elapsed">{{ total }}</span> · {{ running ? `${activePhase.label} · Running` : 'Stopped' }}</span>
        <Button v-if="running" label="Stop" icon="pi pi-stop" :aria-label="`Stop ${activePhase.label.toLowerCase()}`" @click="$emit('stop')" />
      </div>
    </div>
    <div v-show="!collapsed" id="workout-timer-controls" class="timer-content">
      <div class="timer-controls">
        <div class="timer-phase">
          <label for="workout-timer-phase">Phase</label>
          <Dropdown inputId="workout-timer-phase" aria-label="Phase" :modelValue="activePhase.key" :options="phases" optionLabel="label" optionValue="key" @update:modelValue="selectPhase" />
        </div>
        <div class="timer-clock">
          <span class="phase-elapsed elapsed" :aria-label="`${activePhase.label} elapsed time`">{{ elapsed(activePhase.key) }}</span>
          <small>Total: <span class="elapsed">{{ total }}</span> · {{ running ? 'Running' : 'Stopped' }}</small>
        </div>
        <Button v-if="running" label="Stop" icon="pi pi-stop" :aria-label="`Stop ${activePhase.label.toLowerCase()}`" @click="$emit('stop')" />
        <Button v-else label="Start" icon="pi pi-play" class="p-button-outlined" :aria-label="`Start ${activePhase.label.toLowerCase()}`" @click="$emit('start', selectedPhase)" />
      </div>
      <p class="timer-help">Includes rest. Stop for breaks; select another phase to switch.</p>
      <details class="timer-details">
        <summary>Timer details</summary>
        <p>Stored in this browser only. Clearing browser storage removes the draft. Saved phases round up to whole minutes.</p>
      </details>
    </div>
  </section>
</template>
<script>
import {workoutPhases, phaseMilliseconds, formatElapsed} from '@/services/WorkoutTimerService';
export default {
  name: 'WorkoutPhaseTimers',
  props: {draft: Object},
  emits: ['start', 'stop'],
  data() { return {phases: workoutPhases, selectedPhase: workoutPhases[0].key, collapsed: true, now: Date.now(), tick: null}; },
  computed: {
    running() { return !!this.draft?.runningPhase; },
    activePhase() { return this.phases.find(phase => phase.key === (this.draft?.runningPhase || this.selectedPhase)); },
    total() { return formatElapsed(this.draft ? this.phases.reduce((sum, phase) => sum + phaseMilliseconds(this.draft, phase.key, this.now), 0) : 0); }
  },
  watch: {
    draft: {immediate: true, handler(draft) {
      this.collapsed = !draft;
      this.selectedPhase = draft?.runningPhase || this.phases[0].key;
    }},
    'draft.runningPhase'(phase) { if (phase) this.selectedPhase = phase; }
  },
  mounted() { this.tick = setInterval(() => { this.now = Date.now(); }, 1000); },
  beforeUnmount() { clearInterval(this.tick); },
  methods: {
    elapsed(key) { return formatElapsed(this.draft ? phaseMilliseconds(this.draft, key, this.now) : 0); },
    selectPhase(key) { this.selectedPhase = key; if (this.running) this.$emit('start', key); }
  }
};
</script>
<style scoped>
.workout-timers { margin-bottom: 1rem; border: 1px solid #d6d6d6; border-radius: 6px; }
.timer-header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: .75rem; padding: .75rem; }
.timer-toggle { display: flex; align-items: center; gap: .5rem; min-width: 0; text-align: left; color: inherit; }
.timer-toggle:focus-visible { outline: 2px solid var(--primary-color); outline-offset: 3px; }
.timer-summary { display: flex; align-items: center; gap: .75rem; min-width: 0; }
.timer-summary > span { overflow-wrap: anywhere; }
.timer-summary .p-button { flex-shrink: 0; width: auto; }
.timer-content { padding: 0 .75rem .75rem; }
.timer-controls { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; align-items: end; gap: 1rem; }
.timer-phase { min-width: 0; }
.timer-phase label { display: block; margin-bottom: .5rem; }
.timer-clock { display: flex; flex-direction: column; gap: .25rem; }
.phase-elapsed { font-size: 2rem; line-height: 1.2; }
.elapsed { font-variant-numeric: tabular-nums; }
.timer-help { margin: .75rem 0 .5rem; }
.timer-details { font-size: .875rem; }
.timer-details summary { cursor: pointer; width: fit-content; }
.timer-details p { margin: .5rem 0 0; }
@media (max-width: 640px) {
  .timer-controls { grid-template-columns: minmax(0, 1fr) auto; gap: .75rem; }
  .timer-phase { grid-column: 1 / -1; }
  .timer-summary { width: 100%; justify-content: space-between; }
}
</style>
