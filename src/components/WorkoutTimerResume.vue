<template>
  <div v-if="timerState.draft" class="workout-resume">
    <Button label="Resume workout" icon="pi pi-clock" class="p-button-outlined" @click="open" />
    <span>{{ timerState.draft.runningPhase ? 'Running' : 'Stopped' }} · {{ elapsed }}</span>
  </div>
  <WorkoutEditor v-if="show" :show="true" :resume_timer="true" @onClose="show = false" @onSave="saved" />
</template>
<script>
import WorkoutEditor from './WorkoutEditor.vue';
import {userState} from '@/state';
import {timerState, selectTimerAccount, workoutPhases, phaseMilliseconds, formatElapsed} from '@/services/WorkoutTimerService';
export default {
  name: 'WorkoutTimerResume',
  components: {WorkoutEditor},
  data() { return {state: userState(), timerState, show: false, now: Date.now(), tick: null}; },
  computed: {elapsed() { return formatElapsed(workoutPhases.reduce((sum, {key}) => sum + phaseMilliseconds(this.timerState.draft, key, this.now), 0)); }},
  watch: {
    'state.user.mail': {immediate: true, handler(email) { this.show = false; selectTimerAccount(email); }},
    'timerState.resumeRequest'() { this.open(); }
  },
  mounted() { this.tick = setInterval(() => { this.now = Date.now(); }, 1000); },
  beforeUnmount() { clearInterval(this.tick); selectTimerAccount(null); },
  methods: {
    saved() { window.dispatchEvent(new Event('timed-workout-saved')); },
    open() {
      if (this.timerState.editor) {
        document.querySelector('#workout-form')?.scrollIntoView({block: 'start'});
        return;
      }
      this.show = true;
    }
  }
};
</script>
<style scoped>
.workout-resume { display: flex; flex-wrap: wrap; align-items: center; gap: .75rem; margin: 0 1rem 1rem; }
</style>
