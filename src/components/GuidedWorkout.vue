<template>
  <section v-if="minimized && guidedWorkoutState.draft" class="guided-workout-minimized" aria-label="Minimized guided workout">
    <div>
      <strong>{{ guidedWorkoutState.draft.workout.plannedSessionName || 'Workout' }} · {{ timerStatus }}</strong>
      <span>{{ progressLabel }}<template v-if="guidedTimer"> · Total {{ totalElapsed }}</template></span>
    </div>
    <Button label="Reopen workout" icon="pi pi-window-maximize" class="p-button-outlined" @click="open()" />
  </section>
  <Dialog v-model:visible="visible" class="guided-workout-dialog" appendTo="body" :header="guidedWorkoutState.draft?.workout.plannedSessionName || 'Guided workout'" :modal="true" :closable="false" :closeOnEscape="false" :style="{width: 'min(680px, 96vw)'}">
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
        <small v-if="currentStep">{{ guidedTimer.runningPhase ? 'Includes rest. Pause for breaks. Minimize or close keeps the timer running.' : 'Paused. Minimize or close keeps the timer paused.' }}</small>
      </section>
      <section v-else-if="guidedWorkoutState.draft.workout.saunaSession && !steps.length" class="guided-timer" aria-label="Guided workout completion">
        <strong role="status" aria-live="polite">{{ timerStatus }}</strong>
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
            <p class="guided-exercise-time" role="timer" aria-live="off">Exercise time {{ exerciseElapsed }}</p>
            </div>
          </div>
          <details v-if="currentLine.exerciseDescription" :key="guidedWorkoutState.draft.currentStep" class="guided-details">
            <summary>Details</summary>
            <p>{{ currentLine.exerciseDescription }}</p>
          </details>
          <div class="guided-fields">
            <template v-if="currentLine.trackingMode === 'REPS'">
              <label :for="`guided-reps-${currentStep.segmentIndex}`">Repetitions</label><InputNumber :inputId="`guided-reps-${currentStep.segmentIndex}`" v-model="currentSegment.repetitions" :min="1" :useGrouping="false" />
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
            <template v-if="currentLine.trackingMode !== 'CARDIO' && currentLine.exerciseType !== 'STRETCHING'">
              <label :for="`guided-weight-${currentStep.segmentIndex}`">Weight (kg)</label><InputNumber :inputId="`guided-weight-${currentStep.segmentIndex}`" v-model="currentSegment.weight" :min="0" :minFractionDigits="0" :maxFractionDigits="2" />
            </template>
          </div>
          <div class="guided-move-actions" aria-label="Move on from this exercise">
            <div><Button label="Mark skipped" icon="pi pi-ban" class="p-button-outlined" @click="markExerciseSkipped" /><small>Saved as skipped; no time recorded.</small></div>
            <div><Button label="Next without logging" icon="pi pi-step-forward" class="p-button-outlined" @click="moveOnWithoutLogging" /><small>Omit unfinished work and its time.</small></div>
          </div>
        </article>
        <div v-else class="guided-review"><h2>{{ timerStatus === 'Ready to complete' ? 'Ready to complete' : 'Workout complete' }}</h2><p>Review the recorded sets before saving.</p><p v-for="line in guidedWorkoutState.draft.workout.lines" :key="line.exerciseId">{{ line.exerciseName }} · {{ line.segments.map(describeSegment).join(', ') }}</p></div>
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
        <article v-for="line in reviewLines" :key="line.exerciseId" class="guided-review-line">
          <ExercisePicture :src="line.imageUrl" :name="line.exerciseName" :description="line.exerciseDescription" />
          <div><strong>{{ line.exerciseName }}</strong><p v-if="line.exerciseDurationMilliseconds > 0">Exercise time {{ formatElapsed(line.exerciseDurationMilliseconds) }}</p></div>
          <ol><li v-for="(segment, index) in line.segments" :key="index">{{ describeSegment(segment) }}</li></ol>
        </article>
      </section>
      </div>
    </template>
    <p v-if="saveError" role="alert" class="error">{{ saveError }}</p>
    <template #footer><div class="action-group guided-actions">
      <template v-if="!lockError">
        <Button v-if="reviewing && steps.length" label="Back to last set" class="p-button-outlined" :disabled="saving" @click="backToLastSet" />
        <Button v-else-if="!reviewing && currentStep" label="Complete set" icon="pi pi-check" @click="completeSet" />
        <Button v-else-if="!reviewing" label="Review" icon="pi pi-check" @click="startReview" />
        <Button v-if="!reviewing && currentStep && guidedWorkoutState.draft.currentStep > 0" label="Back" class="p-button-outlined" @click="backOneStep" />
        <Button v-if="reviewing && guidedWorkoutState.draft.workout.saunaSession && !guidedWorkoutState.draft.workout.endTime" label="Complete workout" icon="pi pi-check" @click="completeWorkout" />
        <Button v-else-if="reviewing" label="Save" aria-label="Save workout" icon="pi pi-save" :loading="saving" :disabled="saving" @click="save" />
      </template>
      <Button v-if="guidedWorkoutState.draft && !lockError" label="Minimize" icon="pi pi-window-minimize" class="p-button-outlined" :disabled="saving" @click="minimize" />
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
import {workoutPhases, phaseMilliseconds, formatElapsed as formatElapsedValue, pausePhaseTimer, switchPhaseTimer, roundedPhaseMinutes} from '@/services/WorkoutTimerService';
import {createScreenWakeLockController} from '@/services/ScreenWakeLockService';

export default {
  name: 'GuidedWorkout',
  components: {ExercisePicture, Tag, SaunaRoundsEditor},
  data() { return {state: userState(), guidedWorkoutState, editor: Symbol('guided-workout'), visible: false, minimized: false, saving: false, saveError: '', lockError: '', discardPrompt: false, keepScreenOn: false, keepScreenOnStorageKey: null, wakeLockStatus: 'off', wakeLockController: null, phases: workoutPhases, now: Date.now(), tick: null, cardioFields: [{key: 'speedKph', label: 'Speed (km/h)'}, {key: 'cadenceRpm', label: 'Cadence (rpm)'}, {key: 'distanceKm', label: 'Distance (km)'}, {key: 'inclinePercent', label: 'Incline (%)'}, {key: 'resistanceLevel', label: 'Resistance'}]}; },
  computed: {
    steps() {
      return this.guidedWorkoutState.draft ? guidedWorkoutSteps(this.guidedWorkoutState.draft.workout) : [];
    },
    reviewing() { return this.guidedWorkoutState.draft ? (this.guidedWorkoutState.draft.reviewing ?? this.steps.length === 0) : false; },
    currentStep() { return this.steps[this.guidedWorkoutState.draft?.currentStep ?? 0] || null; },
    nextStep() { return this.currentStep ? this.steps[this.guidedWorkoutState.draft.currentStep + 1] || null : null; },
    currentLine() { return this.currentStep ? this.guidedWorkoutState.draft.workout.lines[this.currentStep.lineIndex] : null; },
    nextLine() { return this.nextStep ? this.guidedWorkoutState.draft.workout.lines[this.nextStep.lineIndex] : null; },
    currentSegment() { return this.currentStep ? this.currentLine.segments[this.currentStep.segmentIndex] : null; },
    plannedSegment() { return this.currentStep ? this.guidedWorkoutState.draft.workout.plannedTargets[this.currentStep.lineIndex].segments[this.currentStep.segmentIndex] : null; },
    progressLabel() { return guidedWorkoutProgressLabel(this.guidedWorkoutState.draft); },
    guidedTimer() { return this.guidedWorkoutState.draft?.timer; },
    timerStatus() { return !this.currentStep ? (this.guidedWorkoutState.draft.workout.endTime || this.steps.length && !this.guidedWorkoutState.draft.workout.saunaSession ? 'Complete' : 'Ready to complete') : !this.guidedTimer ? 'Untimed' : this.guidedTimer.runningPhase ? 'Running' : 'Paused'; },
    phaseLabel() { return this.phases.find(phase => phase.key === guidedPhaseKey(this.currentLine)).label; },
    phaseElapsed() { return formatElapsedValue(phaseMilliseconds(this.guidedTimer, guidedPhaseKey(this.currentLine), this.now)); },
    totalElapsed() { return formatElapsedValue(this.phases.reduce((sum, {key}) => sum + phaseMilliseconds(this.guidedTimer, key, this.now), 0)); },
    savedPhaseMinutes() { return this.guidedTimer ? roundedPhaseMinutes(this.guidedTimer) : null; },
    savedDurationMinutes() { return this.savedPhaseMinutes ? this.phases.reduce((sum, {key}) => sum + this.savedPhaseMinutes[key], 0) : 0; },
    exerciseElapsed() {
      const timer = this.guidedWorkoutState.draft?.exerciseTimer;
      if (!this.currentLine || !timer || timer.lineIndex !== this.currentStep.lineIndex) return '00:00:00';
      const completed = this.currentLine.exerciseElapsedBySegment.reduce((sum, milliseconds, index) => sum + (index === this.currentStep.segmentIndex ? 0 : (milliseconds || 0)), 0);
      const pending = timer.pendingMilliseconds + (timer.startedAt == null ? 0 : Math.max(0, this.now - timer.startedAt));
      return formatElapsedValue(completed + pending);
    },
    reviewLines() { return this.guidedWorkoutState.draft.workout.lines.filter(line => !line.omitFromActual); },
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
    visible() { this.updateWakeLock(); },
    minimized() { this.updateWakeLock(); },
    'guidedWorkoutState.resumeRequest'() { this.open(); },
    'guidedWorkoutState.discardRequest'() { this.discardPrompt = true; },
    'guidedWorkoutState.startRequest'() { this.open(guidedWorkoutState.startSource); },
    guidedWorkoutState: {deep: true, handler() { if (guidedWorkoutState.editor === this.editor && guidedWorkoutState.draft) saveGuidedWorkoutDraft(); }}
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
      this.minimized = false;
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
    updateWakeLock() { this.wakeLockController.setActive((this.visible || this.minimized) && !!guidedWorkoutState.draft && guidedWorkoutState.editor === this.editor); },
    async open(source) {
      this.lockError = '';
      if (!await openGuidedWorkoutEditor(this.editor)) { this.lockError = 'This guided workout is open in another tab. Close it there first.'; this.visible = true; return; }
      if (!guidedWorkoutState.draft && source) createGuidedWorkoutDraft(this.normalizeSource(source));
      if (guidedWorkoutState.draft && !guidedWorkoutState.draft.exerciseTimer) {
        const step = guidedWorkoutSteps(guidedWorkoutState.draft.workout)[guidedWorkoutState.draft.currentStep || 0];
        guidedWorkoutState.draft.exerciseTimer = {lineIndex: step?.lineIndex ?? null, segmentIndex: step?.segmentIndex ?? null, pendingMilliseconds: 0, startedAt: guidedWorkoutState.draft.timer?.runningPhase ? Date.now() : null};
        guidedWorkoutState.draft.workout.lines.forEach(line => Object.assign(line, {exerciseDurationMilliseconds: (line.exerciseDurationSeconds || 0) * 1000, exerciseElapsedBySegment: [], omitFromActual: false}));
      }
      this.now = Date.now();
      this.minimized = false;
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
      if (segment.skipped) return 'Skipped · no time recorded';
      const parts = [];
      if (segment.repetitions != null) parts.push(`${segment.repetitions} reps`);
      if (segment.breaths != null) parts.push(`${segment.breaths} breaths`);
      if (segment.durationSeconds != null) parts.push(`${Math.floor(segment.durationSeconds / 60)} min ${segment.durationSeconds % 60} sec`);
      if (segment.weight != null) parts.push(`${segment.weight} kg`);
      if (segment.distanceKm != null) parts.push(`${segment.distanceKm} km`);
      if (segment.speedKph != null) parts.push(`${segment.speedKph} km/h`);
      return parts.join(' · ') || 'Not set';
    },
    formatElapsed(milliseconds) { return formatElapsedValue(milliseconds); },
    toggleTimer() {
      const now = Date.now();
      if (this.guidedTimer.runningPhase) {
        this.pauseExerciseTimer(now);
        pausePhaseTimer(this.guidedTimer, now);
      } else {
        switchPhaseTimer(this.guidedTimer, guidedPhaseKey(this.currentLine), now);
        this.guidedWorkoutState.draft.exerciseTimer.startedAt = now;
      }
      this.now = now;
      saveGuidedWorkoutDraft();
    },
    startReview() {
      this.discardPendingExerciseTime();
      this.guidedWorkoutState.draft.reviewing = true;
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
      const timerWasRunning = !!draft.timer?.runningPhase;
      const exerciseTimer = draft.exerciseTimer;
      const elapsed = exerciseTimer.pendingMilliseconds + (exerciseTimer.startedAt == null ? 0 : Math.max(0, now - exerciseTimer.startedAt));
      this.currentLine.exerciseElapsedBySegment[this.currentStep.segmentIndex] = elapsed;
      this.currentLine.exerciseDurationMilliseconds = this.currentLine.exerciseElapsedBySegment.reduce((sum, milliseconds) => sum + (milliseconds || 0), 0);
      draft.currentStep += 1;
      const nextStep = this.steps[draft.currentStep];
      if (draft.timer) {
        if (!nextStep && timerWasRunning) {
          pausePhaseTimer(draft.timer, now);
        }
        else if (nextStep && timerWasRunning) switchPhaseTimer(draft.timer, guidedPhaseKey(draft.workout.lines[nextStep.lineIndex]), now);
      }
      this.startExerciseTimer(nextStep, now);
      if (!nextStep && !draft.workout.saunaSession) draft.workout.endTime = new Date(now).toISOString();
      this.now = now;
      saveGuidedWorkoutDraft();
      if (!this.currentStep) this.wakeLockController.setActive(false);
    },
    backToLastSet() {
      this.guidedWorkoutState.draft.reviewing = false;
      this.guidedWorkoutState.draft.currentStep -= 1;
      this.guidedWorkoutState.draft.workout.endTime = null;
      if (this.guidedTimer) { this.now = Date.now(); switchPhaseTimer(this.guidedTimer, guidedPhaseKey(this.currentLine), this.now); this.startExerciseTimer(this.currentStep, this.now); }
      this.wakeLockController.setActive(this.visible && guidedWorkoutState.editor === this.editor);
    },
    backOneStep() {
      const draft = this.guidedWorkoutState.draft;
      const now = Date.now();
      this.discardPendingExerciseTime();
      draft.currentStep -= 1;
      draft.workout.endTime = null;
      if (draft.timer?.runningPhase) switchPhaseTimer(draft.timer, guidedPhaseKey(this.currentLine), now);
      this.startExerciseTimer(this.currentStep, now);
      saveGuidedWorkoutDraft();
    },
    pauseExerciseTimer(now) {
      const timer = this.guidedWorkoutState.draft.exerciseTimer;
      if (timer.startedAt != null) timer.pendingMilliseconds += Math.max(0, now - timer.startedAt);
      timer.startedAt = null;
    },
    discardPendingExerciseTime() {
      const timer = this.guidedWorkoutState.draft.exerciseTimer;
      timer.pendingMilliseconds = 0;
      timer.startedAt = null;
    },
    startExerciseTimer(step, now) {
      const timer = this.guidedWorkoutState.draft.exerciseTimer;
      timer.lineIndex = step?.lineIndex ?? null;
      timer.segmentIndex = step?.segmentIndex ?? null;
      timer.pendingMilliseconds = step ? this.guidedWorkoutState.draft.workout.lines[step.lineIndex].exerciseElapsedBySegment[step.segmentIndex] || 0 : 0;
      timer.startedAt = step && this.guidedTimer?.runningPhase ? now : null;
    },
    movePastExercise(markSkipped) {
      const draft = this.guidedWorkoutState.draft;
      const before = this.steps;
      const position = draft.currentStep;
      const step = before[position];
      const line = draft.workout.lines[step.lineIndex];
      const now = Date.now();
      this.discardPendingExerciseTime();
      if (markSkipped) {
        for (let index = step.segmentIndex; index < line.segments.length; index += 1) line.segments[index] = {skipped: true};
        line.omitFromActual = false;
      } else {
        line.segments.splice(step.segmentIndex);
        line.omitFromActual = line.segments.length === 0;
      }
      const nextSteps = this.steps;
      const target = before.slice(position + 1).find(candidate => candidate.lineIndex !== step.lineIndex && nextSteps.some(next => next.lineIndex === candidate.lineIndex && next.segmentIndex === candidate.segmentIndex));
      const nextIndex = target ? nextSteps.findIndex(candidate => candidate.lineIndex === target.lineIndex && candidate.segmentIndex === target.segmentIndex) : -1;
      draft.currentStep = nextIndex < 0 ? nextSteps.length : nextIndex;
      const nextStep = this.steps[draft.currentStep];
      if (draft.timer) {
        if (!nextStep) pausePhaseTimer(draft.timer, now);
        else if (draft.timer.runningPhase) switchPhaseTimer(draft.timer, guidedPhaseKey(draft.workout.lines[nextStep.lineIndex]), now);
      }
      this.startExerciseTimer(nextStep, now);
      if (!nextStep && !draft.workout.saunaSession) draft.workout.endTime = new Date(now).toISOString();
      this.now = now;
      saveGuidedWorkoutDraft();
    },
    markExerciseSkipped() { this.movePastExercise(true); },
    moveOnWithoutLogging() { this.movePastExercise(false); },
    completeWorkout() {
      if (!this.validateSaunaRounds()) return;
      this.guidedWorkoutState.draft.workout.endTime = new Date().toISOString();
      saveGuidedWorkoutDraft();
    },
    validateSaunaRounds() {
      const rounds = this.guidedWorkoutState.draft.workout.saunaRoundsMinutes;
      if (this.guidedWorkoutState.draft.workout.saunaSession && (!rounds.length || rounds.some(minutes => !Number.isInteger(minutes) || minutes <= 0) || this.saunaDuration > 2147483647 - this.savedDurationMinutes)) {
        this.saveError = 'Enter at least one sauna round with positive whole minutes';
        return false;
      }
      this.saveError = '';
      return true;
    },
    async save() {
      if (!this.validateSaunaRounds()) return;
      this.saving = true;
      this.saveError = '';
      const draft = this.guidedWorkoutState.draft;
      try {
        const timing = draft.timer ? roundedPhaseMinutes(draft.timer) : {};
        const lines = draft.workout.lines.filter(line => !line.omitFromActual && line.segments.length).map(line => ({...line, exerciseDurationSeconds: Math.floor(line.exerciseDurationMilliseconds / 1000)}));
        await workoutService.save({...draft.workout, lines, ...timing, ...(draft.timer ? {durationMinutes: Object.values(timing).reduce((sum, minutes) => sum + minutes, 0) + this.saunaDuration} : {}), recordingKey: draft.recordingKey});
        discardGuidedWorkoutDraft();
        this.close();
        window.dispatchEvent(new Event('timed-workout-saved'));
      } catch (error) { this.saveError = error.message || 'Could not save the workout.'; }
      finally { this.saving = false; }
    },
    minimize() { saveGuidedWorkoutDraft(); this.minimized = true; this.visible = false; },
    close() { if (guidedWorkoutState.editor === this.editor) saveGuidedWorkoutDraft(); closeGuidedWorkoutEditor(this.editor); this.minimized = false; this.visible = false; },
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
:global(.guided-workout-dialog .p-dialog-footer > .guided-actions) { --action-min-width: 9rem; }
:global(.guided-workout-dialog .guided-actions .p-button-label) { overflow-wrap: normal; word-break: normal; }
:global(.guided-workout-dialog .p-dialog-content) { display: flex; flex-direction: column; min-height: 0; overflow: hidden; }
.guided-content { min-height: 0; overflow-y: auto; }
.guided-workout-minimized { position: fixed; z-index: 999; right: max(1rem, env(safe-area-inset-right)); bottom: max(1rem, env(safe-area-inset-bottom)); display: flex; flex-wrap: wrap; align-items: center; gap: .5rem 1rem; max-width: min(100% - 2rem, 520px); padding: .75rem; border: 1px solid #c8cdd2; border-radius: 8px; background: var(--surface-card, #fff); box-shadow: 0 2px 12px #0003; }
.guided-workout-minimized > div { display: grid; gap: .25rem; min-width: 0; overflow-wrap: anywhere; }
.guided-workout-minimized span { color: #59636e; }
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
.guided-exercise-time { color: #59636e; font-variant-numeric: tabular-nums; }
.guided-card-summary :deep(.p-tag) { margin: .1rem 0; }
.guided-details summary { cursor: pointer; }
.guided-details p { margin: .4rem 0; overflow-wrap: anywhere; }
.guided-fields { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); align-items: center; gap: .4rem .6rem; width: min(100%, 440px); }
.guided-fields label { overflow-wrap: anywhere; }
.guided-fields :deep(.p-inputnumber), .guided-fields :deep(.p-inputnumber-input) { min-width: 0; width: 100%; }
.guided-planned { text-align: left; overflow-wrap: anywhere; }
.guided-move-actions { display: grid; gap: .5rem; margin-top: .5rem; }
.guided-move-actions > div { display: flex; flex-wrap: wrap; align-items: center; gap: .35rem .65rem; }
.guided-move-actions small { color: #59636e; }
:global(.guided-workout-dialog .guided-move-actions .p-button) { min-height: 44px; }
.guided-review-line { display: grid; grid-template-columns: auto minmax(0, 1fr); align-items: start; gap: .5rem; margin: 1rem 0; overflow-wrap: anywhere; }
.guided-review-line ol { grid-column: 2; margin: 0; padding-left: 1.25rem; }
</style>
