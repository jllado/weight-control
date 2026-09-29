<template>
  <Dialog v-model:visible="visible" class="guided-workout-dialog" appendTo="body" :header="guidedWorkoutState.draft?.workout.plannedSessionName || 'Guided workout'" :modal="true" :closable="false" :closeOnEscape="false" :style="{width: 'min(680px, 96vw)'}" @hide="close">
    <p v-if="lockError" role="alert" class="error">{{ lockError }}</p>
    <template v-else-if="guidedWorkoutState.draft">
      <div class="guided-screen-lock">
        <Checkbox inputId="guided-keep-screen-on" v-model="keepScreenOn" :binary="true" @change="updateKeepScreenOn" />
        <label for="guided-keep-screen-on">Keep screen on</label>
        <span v-if="keepScreenOn && wakeLockMessage" role="status" aria-live="polite">{{ wakeLockMessage }}</span>
      </div>
      <section v-if="guidedTimer && steps.length" class="guided-timer" aria-label="Guided workout timer">
        <div class="guided-timer-summary">
          <strong role="status" aria-live="polite">{{ currentStep ? phaseLabel : 'Workout' }} · {{ timerStatus }}</strong>
          <span v-if="currentStep" role="timer" aria-live="off" :aria-label="`${phaseLabel} elapsed time`">Phase {{ phaseElapsed }}</span>
          <span role="timer" aria-live="off" aria-label="Total elapsed time">Total {{ totalElapsed }}</span>
          <Button v-if="currentStep" :label="guidedTimer.runningPhase ? 'Pause' : 'Resume'" :icon="guidedTimer.runningPhase ? 'pi pi-pause' : 'pi pi-play'" class="p-button-outlined" @click="toggleTimer" />
        </div>
        <small v-if="currentStep">{{ guidedTimer.runningPhase ? 'Includes rest. Pause for breaks. Close keeps the timer running.' : 'Paused. Close keeps the timer paused.' }}</small>
      </section>
      <p v-else-if="!guidedTimer" class="guided-timing-note">This draft started before automatic timing. Finish it without phase times or start a new guided workout.</p>
      <p v-if="!reviewing" class="guided-progress" role="status">{{ progressLabel }}</p>
      <p v-if="!reviewing && nextStep" class="guided-next" role="status"><strong>Next:</strong> {{ nextLine.exerciseName }} <span>· Set {{ nextStep.segmentIndex + 1 }}</span></p>
      <div class="guided-content">
      <template v-if="!reviewing">
        <article v-if="currentStep" class="guided-card" :aria-label="`${currentLine.exerciseName}, set ${currentStep.segmentIndex + 1}`">
          <div class="guided-card-heading">
            <ExercisePicture :src="currentLine.imageUrl" :name="currentLine.exerciseName" :description="currentLine.exerciseDescription" />
            <div class="guided-card-summary">
              <h2>{{ currentLine.exerciseName }}</h2>
              <Tag v-if="currentLine.supersetGroupId" value="Superset" severity="info" />
              <p class="guided-planned"><strong>Planned:</strong> {{ describeSegment(plannedSegment) }}</p>
            </div>
          </div>
          <details v-if="currentLine.exerciseDescription" :key="guidedWorkoutState.draft.currentStep" class="guided-details">
            <summary>Details</summary>
            <p>{{ currentLine.exerciseDescription }}</p>
          </details>
          <div class="guided-fields">
            <template v-if="currentLine.trackingMode === 'REPS'">
              <label :for="`guided-reps-${currentStep.segmentIndex}`">Repetitions</label><InputNumber :inputId="`guided-reps-${currentStep.segmentIndex}`" v-model="currentSegment.repetitions" :min="1" :useGrouping="false" />
              <label :for="`guided-weight-${currentStep.segmentIndex}`">Weight (kg)</label><InputNumber :inputId="`guided-weight-${currentStep.segmentIndex}`" v-model="currentSegment.weight" :min="0" :minFractionDigits="0" :maxFractionDigits="2" />
            </template>
            <template v-if="currentLine.trackingMode === 'SECONDS' && currentLine.stretchingUnit === 'BREATHS'">
              <label :for="`guided-breaths-${currentStep.segmentIndex}`">Breaths</label><InputNumber :inputId="`guided-breaths-${currentStep.segmentIndex}`" v-model="currentSegment.breaths" :min="1" :useGrouping="false" />
            </template>
            <template v-else-if="currentLine.trackingMode === 'SECONDS' || currentLine.trackingMode === 'CARDIO'">
              <label :for="`guided-duration-${currentStep.segmentIndex}`">Duration (minutes)</label><InputNumber :inputId="`guided-duration-${currentStep.segmentIndex}`" v-model="currentSegment.guidedMinutes" :min="0" :useGrouping="false" />
              <label :for="`guided-seconds-${currentStep.segmentIndex}`">Seconds</label><InputNumber :inputId="`guided-seconds-${currentStep.segmentIndex}`" v-model="currentSegment.guidedSeconds" :min="0" :max="59" :useGrouping="false" />
            </template>
            <template v-if="currentLine.trackingMode === 'CARDIO'">
              <template v-for="field in cardioFields" :key="field.key">
                <label :for="`guided-${field.key}-${currentStep.segmentIndex}`">{{ field.label }}</label>
                <InputNumber :inputId="`guided-${field.key}-${currentStep.segmentIndex}`" v-model="currentSegment[field.key]" :min="0" :minFractionDigits="0" :maxFractionDigits="2" />
              </template>
              <label for="guided-calories">Calories</label><InputNumber inputId="guided-calories" v-model="currentLine.calories" :min="0" />
              <label for="guided-heart-rate">Average heart rate (bpm)</label><InputNumber inputId="guided-heart-rate" v-model="currentLine.averageHeartRate" :min="0" :useGrouping="false" />
            </template>
          </div>
        </article>
        <div v-else class="guided-review"><h2>Workout complete</h2><p>Review the recorded sets before saving.</p><p v-for="line in guidedWorkoutState.draft.workout.lines" :key="line.exerciseId">{{ line.exerciseName }} · {{ line.segments.map(describeSegment).join(', ') }}</p></div>
      </template>
      <section v-else class="guided-review" aria-label="Review workout">
        <h2>Review workout</h2>
        <div v-if="guidedTimer && guidedWorkoutState.draft.workout.lines.length" class="guided-timer-review">
          <strong>Recorded phase times · {{ savedDurationMinutes }} min</strong>
          <ul><li v-for="phase in phases" :key="phase.key">{{ phase.label }}: {{ savedPhaseMinutes[phase.key] }} min</li></ul>
          <small>Each phase rounds up to whole minutes after its intervals are added.</small>
        </div>
        <SaunaRoundsEditor v-if="guidedWorkoutState.draft.workout.saunaSession" idPrefix="guided-sauna-round" v-model="guidedWorkoutState.draft.workout.saunaRoundsMinutes" :plannedRounds="guidedWorkoutState.draft.workout.plannedSaunaRoundsMinutes" title="Completed sauna rounds" />
        <p v-if="guidedWorkoutState.draft.workout.saunaSession">Session duration: {{ savedDurationMinutes + saunaDuration }} min, including {{ saunaDuration }} min in sauna.</p>
        <article v-for="line in guidedWorkoutState.draft.workout.lines" :key="line.exerciseId" class="guided-review-line">
          <ExercisePicture :src="line.imageUrl" :name="line.exerciseName" :description="line.exerciseDescription" />
          <strong>{{ line.exerciseName }}</strong>
          <ol><li v-for="(segment, index) in line.segments" :key="index">{{ describeSegment(segment) }}</li></ol>
        </article>
      </section>
      </div>
    </template>
    <p v-if="saveError" role="alert" class="error">{{ saveError }}</p>
    <template #footer><div class="action-group">
      <template v-if="!lockError">
        <Button v-if="reviewing && steps.length" label="Back to last set" class="p-button-outlined" :disabled="saving" @click="backToLastSet" />
        <Button v-else-if="!reviewing && currentStep" label="Complete set" icon="pi pi-check" @click="completeSet" />
        <Button v-else-if="!reviewing" label="Review" icon="pi pi-check" @click="reviewing = true" />
        <Button v-if="reviewing" label="Save" aria-label="Save workout" icon="pi pi-save" :loading="saving" :disabled="saving" @click="save" />
      </template>
      <Button label="Close" class="p-button-secondary" :disabled="saving" @click="close" />
    </div></template>
  </Dialog>
  <Dialog header="Discard guided workout?" v-model:visible="discardPrompt" appendTo="body" :modal="true" :style="{width: 'min(420px, 96vw)'}">
    <p>This removes the local draft. No workout has been saved.</p>
    <template #footer><div class="action-group"><Button label="Discard" class="p-button-danger" @click="discard" /><Button label="Keep draft" class="p-button-secondary" @click="discardPrompt = false" /></div></template>
  </Dialog>
</template>

<script>
import {userState} from '@/state';
import ExercisePicture from './ExercisePicture.vue';
import SaunaRoundsEditor from './SaunaRoundsEditor.vue';
import Tag from 'primevue/tag';
import workoutService from '@/services/WorkoutService';
import {guidedWorkoutState, guidedPhaseKey, guidedWorkoutSteps, guidedWorkoutProgressLabel, selectGuidedWorkoutAccount, openGuidedWorkoutEditor, closeGuidedWorkoutEditor, createGuidedWorkoutDraft, saveGuidedWorkoutDraft, discardGuidedWorkoutDraft} from '@/services/GuidedWorkoutService';
import {workoutPhases, phaseMilliseconds, formatElapsed, pausePhaseTimer, switchPhaseTimer, roundedPhaseMinutes} from '@/services/WorkoutTimerService';
import {createScreenWakeLockController} from '@/services/ScreenWakeLockService';

export default {
  name: 'GuidedWorkout',
  components: {ExercisePicture, Tag, SaunaRoundsEditor},
  data() { return {state: userState(), guidedWorkoutState, editor: Symbol('guided-workout'), visible: false, reviewing: false, saving: false, saveError: '', lockError: '', discardPrompt: false, keepScreenOn: false, keepScreenOnStorageKey: null, wakeLockStatus: 'off', wakeLockController: null, phases: workoutPhases, now: Date.now(), tick: null, cardioFields: [{key: 'speedKph', label: 'Speed (km/h)'}, {key: 'cadenceRpm', label: 'Cadence (rpm)'}, {key: 'distanceKm', label: 'Distance (km)'}, {key: 'inclinePercent', label: 'Incline (%)'}, {key: 'resistanceLevel', label: 'Resistance'}]}; },
  computed: {
    steps() {
      return this.guidedWorkoutState.draft ? guidedWorkoutSteps(this.guidedWorkoutState.draft.workout) : [];
    },
    currentStep() { return this.steps[this.guidedWorkoutState.draft?.currentStep ?? 0] || null; },
    nextStep() { return this.currentStep ? this.steps[this.guidedWorkoutState.draft.currentStep + 1] || null : null; },
    currentLine() { return this.currentStep ? this.guidedWorkoutState.draft.workout.lines[this.currentStep.lineIndex] : null; },
    nextLine() { return this.nextStep ? this.guidedWorkoutState.draft.workout.lines[this.nextStep.lineIndex] : null; },
    currentSegment() { return this.currentStep ? this.currentLine.segments[this.currentStep.segmentIndex] : null; },
    plannedSegment() { return this.currentStep ? this.guidedWorkoutState.draft.workout.plannedTargets[this.currentStep.lineIndex].segments[this.currentStep.segmentIndex] : null; },
    progressLabel() { return guidedWorkoutProgressLabel(this.guidedWorkoutState.draft); },
    guidedTimer() { return this.guidedWorkoutState.draft?.timer; },
    timerStatus() { return !this.currentStep ? 'Complete' : this.guidedTimer.runningPhase ? 'Running' : 'Paused'; },
    phaseLabel() { return this.phases.find(phase => phase.key === guidedPhaseKey(this.currentLine)).label; },
    phaseElapsed() { return formatElapsed(phaseMilliseconds(this.guidedTimer, guidedPhaseKey(this.currentLine), this.now)); },
    totalElapsed() { return formatElapsed(this.phases.reduce((sum, {key}) => sum + phaseMilliseconds(this.guidedTimer, key, this.now), 0)); },
    savedPhaseMinutes() { return this.guidedTimer ? roundedPhaseMinutes(this.guidedTimer) : null; },
    savedDurationMinutes() { return this.phases.reduce((sum, {key}) => sum + this.savedPhaseMinutes[key], 0); },
    saunaDuration() { return this.guidedWorkoutState.draft?.workout.saunaSession ? this.guidedWorkoutState.draft.workout.saunaRoundsMinutes.reduce((sum, minutes) => sum + (minutes || 0), 0) : 0; },
    wakeLockMessage() {
      return ({
        active: 'Screen lock is active for this guided workout.',
        requesting: 'Requesting screen lock…',
        paused: 'Screen lock paused while this page is hidden.',
        released: 'The browser released the screen lock.',
        unsupported: 'Screen lock is unavailable in this browser.',
        unavailable: 'Screen lock could not be acquired; the workout can continue.'
      })[this.wakeLockStatus] || '';
    }
  },
  watch: {
    'state.user.mail'(email) { this.selectAccount(email); },
    visible(value) { this.wakeLockController.setActive(value && !!guidedWorkoutState.draft && guidedWorkoutState.editor === this.editor); },
    'guidedWorkoutState.resumeRequest'() { this.open(); },
    'guidedWorkoutState.discardRequest'() { this.discardPrompt = true; },
    'guidedWorkoutState.startRequest'() { this.open(guidedWorkoutState.startSource); },
    guidedWorkoutState: {deep: true, handler() { if (this.visible && guidedWorkoutState.editor === this.editor && guidedWorkoutState.draft) saveGuidedWorkoutDraft(); }}
  },
  created() {
    this.wakeLockController = createScreenWakeLockController(status => { this.wakeLockStatus = status; });
    this.selectAccount(this.state.user.mail);
  },
  mounted() { this.tick = setInterval(() => { this.now = Date.now(); }, 1000); },
  beforeUnmount() { clearInterval(this.tick); this.wakeLockController.dispose(); closeGuidedWorkoutEditor(this.editor); selectGuidedWorkoutAccount(null); },
  methods: {
    selectAccount(email) {
      this.visible = false;
      this.wakeLockController.setActive(false);
      this.wakeLockController.setEnabled(false);
      selectGuidedWorkoutAccount(email);
      this.keepScreenOnStorageKey = email ? `guided-workout-screen-lock-v1:${email}` : null;
      this.keepScreenOn = this.keepScreenOnStorageKey ? localStorage.getItem(this.keepScreenOnStorageKey) === 'true' : false;
      this.wakeLockController.setEnabled(this.keepScreenOn);
    },
    updateKeepScreenOn() {
      if (this.keepScreenOnStorageKey) localStorage.setItem(this.keepScreenOnStorageKey, String(this.keepScreenOn));
      this.wakeLockController.setEnabled(this.keepScreenOn);
    },
    async open(source) {
      this.lockError = '';
      if (!await openGuidedWorkoutEditor(this.editor)) { this.lockError = 'This guided workout is open in another tab. Close it there first.'; this.visible = true; return; }
      if (!guidedWorkoutState.draft && source) createGuidedWorkoutDraft(this.normalizeSource(source));
      this.now = Date.now();
      this.reviewing = guidedWorkoutState.draft ? guidedWorkoutSteps(guidedWorkoutState.draft.workout).length === 0 : false;
      this.visible = true;
    },
    normalizeSource(source) {
      const workout = JSON.parse(JSON.stringify(source));
      if (!workout.plannedTargets) workout.plannedTargets = workout.lines.map(line => ({exerciseName: line.exerciseName, exerciseDescription: line.exerciseDescription, trackingMode: line.trackingMode, exerciseType: line.exerciseType, cardioMetric: line.cardioMetric, stretchingUnit: line.stretchingUnit, supersetGroupId: line.supersetGroupId, segments: line.segments.map(segment => ({...segment}))}));
      workout.lines.forEach(line => line.segments.forEach(segment => {
        segment.guidedMinutes = Math.floor((segment.durationSeconds || 0) / 60);
        segment.guidedSeconds = (segment.durationSeconds || 0) % 60;
      }));
      return workout;
    },
    describeSegment(segment) {
      const parts = [];
      if (segment.repetitions != null) parts.push(`${segment.repetitions} reps`);
      if (segment.breaths != null) parts.push(`${segment.breaths} breaths`);
      if (segment.durationSeconds != null) parts.push(`${Math.floor(segment.durationSeconds / 60)} min ${segment.durationSeconds % 60} sec`);
      if (segment.weight != null) parts.push(`${segment.weight} kg`);
      if (segment.distanceKm != null) parts.push(`${segment.distanceKm} km`);
      if (segment.speedKph != null) parts.push(`${segment.speedKph} km/h`);
      return parts.join(' · ') || 'Not set';
    },
    toggleTimer() {
      const now = Date.now();
      if (this.guidedTimer.runningPhase) pausePhaseTimer(this.guidedTimer, now);
      else switchPhaseTimer(this.guidedTimer, guidedPhaseKey(this.currentLine), now);
      this.now = now;
      saveGuidedWorkoutDraft();
    },
    completeSet() {
      const line = this.currentLine;
      const segment = this.currentSegment;
      const durationRequired = line.trackingMode === 'CARDIO' || (line.trackingMode === 'SECONDS' && line.stretchingUnit !== 'BREATHS');
      if (line.trackingMode === 'REPS' && !(segment.repetitions > 0)) { this.saveError = 'Enter the completed repetitions.'; return; }
      if (line.stretchingUnit === 'BREATHS' && !(segment.breaths > 0)) { this.saveError = 'Enter the completed breaths.'; return; }
      if (durationRequired && (segment.guidedMinutes * 60 + segment.guidedSeconds <= 0)) { this.saveError = 'Enter the completed duration.'; return; }
      if (durationRequired) segment.durationSeconds = segment.guidedMinutes * 60 + segment.guidedSeconds;
      this.saveError = '';
      const draft = this.guidedWorkoutState.draft;
      const now = Date.now();
      draft.currentStep += 1;
      if (draft.timer) {
        const nextStep = this.steps[draft.currentStep];
        if (!nextStep) pausePhaseTimer(draft.timer, now);
        else if (draft.timer.runningPhase) switchPhaseTimer(draft.timer, guidedPhaseKey(draft.workout.lines[nextStep.lineIndex]), now);
      }
      this.now = now;
      saveGuidedWorkoutDraft();
      if (!this.currentStep) this.wakeLockController.setActive(false);
    },
    backToLastSet() {
      this.reviewing = false;
      this.guidedWorkoutState.draft.currentStep -= 1;
      if (this.guidedTimer) { this.now = Date.now(); switchPhaseTimer(this.guidedTimer, guidedPhaseKey(this.currentLine), this.now); }
      this.wakeLockController.setActive(this.visible && guidedWorkoutState.editor === this.editor);
    },
    async save() {
      const rounds = this.guidedWorkoutState.draft.workout.saunaRoundsMinutes || [];
      if (this.guidedWorkoutState.draft.workout.saunaSession && (!rounds.length || rounds.some(minutes => !Number.isInteger(minutes) || minutes <= 0) || this.saunaDuration > 2147483647 - this.savedDurationMinutes)) {
        this.saveError = 'Enter at least one sauna round with positive whole minutes';
        return;
      }
      this.saving = true;
      this.saveError = '';
      const draft = this.guidedWorkoutState.draft;
      try {
        const timing = draft.timer ? roundedPhaseMinutes(draft.timer) : {};
        await workoutService.save({...draft.workout, ...timing, ...(draft.timer ? {durationMinutes: Object.values(timing).reduce((sum, minutes) => sum + minutes, 0) + this.saunaDuration} : {}), recordingKey: draft.recordingKey});
        discardGuidedWorkoutDraft();
        this.close();
        window.dispatchEvent(new Event('timed-workout-saved'));
      } catch (error) { this.saveError = error.message || 'Could not save the workout.'; }
      finally { this.saving = false; }
    },
    close() { if (guidedWorkoutState.editor === this.editor) saveGuidedWorkoutDraft(); closeGuidedWorkoutEditor(this.editor); this.visible = false; },
    async discard() {
      this.discardPrompt = false;
      if (!await openGuidedWorkoutEditor(this.editor)) { this.lockError = 'This guided workout is open in another tab. Close it there first.'; this.visible = true; return; }
      discardGuidedWorkoutDraft();
      this.close();
    }
  }
};
</script>

<style scoped>
/* PrimeVue teleports the dialog without this component's scope attribute. */
:global(.guided-workout-dialog .p-dialog-content) { display: flex; flex-direction: column; min-height: 0; overflow: hidden; }
.guided-content { min-height: 0; overflow-y: auto; }
.guided-progress, .guided-next, .guided-screen-lock, .guided-timer, .guided-timing-note { flex-shrink: 0; }
.guided-progress { margin: 0 0 .4rem; text-align: center; font-weight: 600; }
.guided-next { margin: 0 0 .6rem; padding: .4rem .6rem; border-left: 3px solid #6c757d; background: #f6f7f8; overflow-wrap: anywhere; }
.guided-next span { color: #59636e; }
.guided-screen-lock { display: flex; flex-wrap: wrap; align-items: center; gap: .5rem; margin-bottom: .5rem; overflow-wrap: anywhere; }
.guided-screen-lock span { flex-basis: 100%; color: #59636e; font-size: .9rem; }
.guided-timer { margin-bottom: .5rem; padding: .5rem .65rem; border: 1px solid #d6d6d6; border-radius: 6px; }
.guided-timer-summary { display: flex; flex-wrap: wrap; align-items: center; gap: .5rem 1rem; font-variant-numeric: tabular-nums; }
.guided-timer-summary strong { margin-right: auto; }
.guided-timer-summary :deep(.p-button) { flex-shrink: 0; }
.guided-timer small { display: block; margin-top: .5rem; }
.guided-timer-review { margin-bottom: 1rem; }
.guided-timer-review ul { margin: .5rem 0 0; padding-left: 1.25rem; }
.guided-timer-review small { display: block; margin-top: .5rem; }
.guided-timing-note { margin: 0 0 1rem; }
.guided-card { display: grid; gap: .45rem; }
.guided-card-heading { display: flex; align-items: flex-start; gap: .35rem; min-width: 0; }
.guided-card-heading > :deep(.exercise-picture) { margin: 0; }
.guided-card-heading :deep(.exercise-picture-button) { width: 48px; height: 48px; }
.guided-card-summary { min-width: 0; }
.guided-card h2 { margin: 0 0 .2rem; overflow-wrap: anywhere; font-size: 1.2rem; }
.guided-card-summary > p { margin: .2rem 0; overflow-wrap: anywhere; }
.guided-card-summary :deep(.p-tag) { margin: .1rem 0; }
.guided-details summary { cursor: pointer; }
.guided-details p { margin: .4rem 0; overflow-wrap: anywhere; }
.guided-fields { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); align-items: center; gap: .4rem .6rem; width: min(100%, 440px); }
.guided-fields label { overflow-wrap: anywhere; }
.guided-fields :deep(.p-inputnumber), .guided-fields :deep(.p-inputnumber-input) { min-width: 0; width: 100%; }
.guided-planned { text-align: left; overflow-wrap: anywhere; }
.guided-review-line { display: grid; grid-template-columns: auto minmax(0, 1fr); align-items: start; gap: .5rem; margin: 1rem 0; overflow-wrap: anywhere; }
.guided-review-line ol { grid-column: 2; margin: 0; padding-left: 1.25rem; }
</style>
