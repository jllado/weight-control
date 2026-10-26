<template>
  <slot />
  <section v-if="minimized && guidedWorkoutState.draft" class="guided-workout-minimized" aria-label="Minimized guided workout">
    <div>
      <strong>{{ guidedWorkoutState.draft.workout.plannedSessionName || 'Workout' }} · {{ timerStatus }}</strong>
      <span>{{ progressLabel }}<template v-if="guidedTimer"> · Total {{ totalElapsed }}</template></span>
    </div>
    <Button label="Reopen workout" icon="pi pi-window-maximize" class="p-button-outlined" @click="open()" />
  </section>
  <Dialog header="Discard guided workout?" v-model:visible="discardPrompt" appendTo="body" :modal="true" :style="{width: 'min(420px, 96vw)'}">
    <p>This removes the local draft. No workout has been saved.</p>
    <template #footer><div class="action-group"><Button label="Discard" class="p-button-danger" @click="discard" /><Button label="Keep draft" class="p-button-secondary" @click="discardPrompt = false" /></div></template>
  </Dialog>
</template>

<script>
import {userState} from '@/state';
import workoutService from '@/services/WorkoutService';
import {guidedWorkoutState, guidedPhaseKey, guidedWorkoutSteps, guidedWorkoutProgressLabel, guidedWorkoutExerciseProgress, selectGuidedWorkoutAccount, openGuidedWorkoutEditor, closeGuidedWorkoutEditor, createGuidedWorkoutDraft, estimateGuidedWorkout, ensureGuidedDraftIdentity, applyGuidedWorkoutEdit, saveGuidedWorkoutDraft, discardGuidedWorkoutDraft, discardSavedGuidedWorkoutDraft} from '@/services/GuidedWorkoutService';
import {workoutPhases, phaseMilliseconds, formatElapsed as formatElapsedValue, pausePhaseTimer, switchPhaseTimer, roundedPhaseMinutes} from '@/services/WorkoutTimerService';
import {createScreenWakeLockController} from '@/services/ScreenWakeLockService';
import {loadWorkoutRecordContext, metricRecords, loadMetricRecords, segmentMetricRecords, formatRecordValue} from '@/services/WorkoutRecordContext';

export default {
  name: 'GuidedWorkout',
  provide() { return {guidedWorkout: this}; },
  data() { return {state: userState(), guidedWorkoutState, editor: Symbol('guided-workout'), visible: false, minimized: false, opening: false, openGeneration: 0, editDialog: false, editWorkoutDialog: false, restDialog: false, editSegment: null, editLine: null, editError: '', saving: false, saveError: '', lockError: '', discardPrompt: false, keepScreenOn: false, keepScreenOnStorageKey: null, restDuration: 60, restDurationStorageKey: null, wakeLockStatus: 'off', wakeLockController: null, exerciseRecords: {}, phases: workoutPhases, now: Date.now(), tick: null, cardioFields: [{key: 'speedKph', label: 'Speed (km/h)', metric: 'CARDIO_SPEED'}, {key: 'cadenceRpm', label: 'Cadence (rpm)', metric: 'CARDIO_CADENCE'}, {key: 'distanceKm', label: 'Distance (km)', metric: 'CARDIO_DISTANCE'}, {key: 'inclinePercent', label: 'Incline (%)', metric: 'CARDIO_INCLINE'}, {key: 'resistanceLevel', label: 'Resistance', metric: 'CARDIO_RESISTANCE'}]}; },
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
    plannedSegment() { return this.currentStep ? this.guidedWorkoutState.draft.plannedTargetsBySegmentKey?.[this.currentSegment.guidedSegmentKey] || null : null; },
    nextPlannedSegment() { return this.nextStep ? this.guidedWorkoutState.draft.plannedTargetsBySegmentKey?.[this.nextLine.segments[this.nextStep.segmentIndex].guidedSegmentKey] || null : null; },
    currentSetEstimate() { return formatElapsedValue(this.guidedWorkoutState.draft.estimatedSecondsByLine[this.currentStep.lineIndex][this.currentStep.segmentIndex] * 1000); },
    nextSetEstimate() { return formatElapsedValue(this.guidedWorkoutState.draft.estimatedSecondsByLine[this.nextStep.lineIndex][this.nextStep.segmentIndex] * 1000); },
    currentExerciseEstimate() {
      const estimates = this.guidedWorkoutState.draft.estimatedSecondsByLine[this.currentStep.lineIndex];
      return formatElapsedValue(this.currentLine.segments.reduce((sum, segment, index) => sum + (segment.skipped ? 0 : estimates[index]), 0) * 1000);
    },
    restActive() { return !!this.guidedWorkoutState.draft?.restTimer; },
    restPrepared() { return !!this.guidedWorkoutState.draft?.preparedRest; },
    restTimer() { return this.guidedWorkoutState.draft?.restTimer; },
    restTimerLabel() {
      const remaining = this.restTimer.paused ? this.restTimer.pausedRemainingMilliseconds : this.restTimer.endsAt - this.now;
      const label = remaining < 0 ? `+${formatElapsedValue(-remaining)} overtime` : `${formatElapsedValue(remaining)} remaining`;
      return this.restTimer.paused ? `Paused · ${label}` : label;
    },
    progressLabel() { return guidedWorkoutProgressLabel(this.guidedWorkoutState.draft); },
    exerciseProgress() { return guidedWorkoutExerciseProgress(this.guidedWorkoutState.draft); },
    guidedTimer() { return this.guidedWorkoutState.draft?.timer; },
    timerStatus() { return !this.currentStep ? (this.guidedWorkoutState.draft.workout.endTime || this.steps.length && !this.guidedWorkoutState.draft.workout.saunaSession ? 'Complete' : 'Ready to complete') : !this.guidedTimer ? 'Untimed' : this.guidedTimer.runningPhase ? 'Running' : 'Paused'; },
    phaseLabel() { return this.phases.find(phase => phase.key === guidedPhaseKey(this.currentLine)).label; },
    phaseElapsed() { return formatElapsedValue(phaseMilliseconds(this.guidedTimer, guidedPhaseKey(this.currentLine), this.now)); },
    totalElapsed() { return formatElapsedValue(this.phases.reduce((sum, {key}) => sum + phaseMilliseconds(this.guidedTimer, key, this.now), 0)); },
    savedPhaseMinutes() { return this.guidedTimer ? roundedPhaseMinutes(this.guidedTimer) : null; },
    savedDurationMinutes() { return this.savedPhaseMinutes ? this.phases.reduce((sum, {key}) => sum + this.savedPhaseMinutes[key], 0) : 0; },
    currentTarget() {
      const parts = [this.describeSegment(this.currentSegment)];
      if (this.currentLine.trackingMode === 'CARDIO') {
        if (this.currentLine.calories != null) parts.push(`${this.currentLine.calories} kcal`);
        if (this.currentLine.averageHeartRate != null) parts.push(`${this.currentLine.averageHeartRate} bpm`);
      }
      return parts.join(' · ');
    },
    currentDurationBased() { return !!this.plannedSegment && this.currentLine.stretchingUnit !== 'BREATHS' && (this.currentLine.trackingMode === 'SECONDS' || this.currentLine.trackingMode === 'CARDIO'); },
    nextDurationBased() { return !!this.nextPlannedSegment && this.nextLine.stretchingUnit !== 'BREATHS' && (this.nextLine.trackingMode === 'SECONDS' || this.nextLine.trackingMode === 'CARDIO'); },
    setElapsed() {
      const timer = this.guidedWorkoutState.draft.exerciseTimer;
      return formatElapsedValue(timer.pendingMilliseconds + (timer.startedAt == null ? 0 : Math.max(0, this.now - timer.startedAt)));
    },
    exerciseElapsed() {
      const timer = this.guidedWorkoutState.draft?.exerciseTimer;
      if (!this.currentLine || !timer || timer.lineIndex !== this.currentStep.lineIndex) return '00:00:00';
      const completed = this.currentLine.exerciseElapsedBySegment.reduce((sum, milliseconds, index) => sum + (index === this.currentStep.segmentIndex ? 0 : (milliseconds || 0)), 0);
      const pending = timer.pendingMilliseconds + (timer.startedAt == null ? 0 : Math.max(0, this.now - timer.startedAt));
      return formatElapsedValue(completed + pending);
    },
    reviewLines() { return this.guidedWorkoutState.draft.workout.lines.filter(line => !line.omitFromActual); },
    editorWorkout() {
      const draft = this.guidedWorkoutState.draft;
      if (!draft) return null;
      const phaseMinutes = draft.timer ? Object.fromEntries(this.phases.map(({key}) => [key, Math.ceil(phaseMilliseconds(draft.timer, key, Date.now()) / 60000)])) : null;
      const durationMinutes = phaseMinutes ? Object.values(phaseMinutes).reduce((sum, minutes) => sum + minutes, 0) : this.savedDurationMinutes;
      return {...draft.workout, id: null, workoutDate: draft.workout.workoutDate || new Date(), durationMinutes,
        warmUpMinutes: phaseMinutes?.warmUpMinutes ?? null, trainingMinutes: phaseMinutes?.trainingMinutes ?? null,
        cardioMinutes: phaseMinutes?.cardioMinutes ?? null, stretchingMinutes: phaseMinutes?.stretchingMinutes ?? null,
        lines: this.reviewLines.map(line => ({...line, exerciseDurationSeconds: Math.floor(line.exerciseDurationMilliseconds / 1000),
          sets: line.trackingMode === 'CARDIO' ? [] : line.segments.map(segment => ({...segment})),
          intervals: line.trackingMode === 'CARDIO' ? line.segments.map(segment => ({...segment})) : []}))};
    },
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
    '$route.fullPath'() { if (this.opening && this.$route.name !== 'GuidedWorkoutPage') this.leavePage(); },
    visible() { this.updateWakeLock(); },
    opening() { this.updateWakeLock(); },
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
  beforeUnmount() { this.openGeneration += 1; clearInterval(this.tick); this.wakeLockController.dispose(); closeGuidedWorkoutEditor(this.editor); selectGuidedWorkoutAccount(null); },
  methods: {
    selectAccount(email) {
      this.openGeneration += 1;
      this.opening = false;
      this.editDialog = false;
      this.restDialog = false;
      this.visible = false;
      this.minimized = false;
      this.wakeLockController.setActive(false);
      this.wakeLockController.setEnabled(false);
      selectGuidedWorkoutAccount(email);
      this.keepScreenOnStorageKey = email ? `guided-workout-screen-lock-v1:${email}` : null;
      this.keepScreenOn = this.keepScreenOnStorageKey ? localStorage.getItem(this.keepScreenOnStorageKey) === 'true' : false;
      this.restDurationStorageKey = email ? `guided-workout-rest-duration-v1:${email}` : null;
      this.restDuration = this.restDurationStorageKey && localStorage.getItem(this.restDurationStorageKey) != null ? Number(localStorage.getItem(this.restDurationStorageKey)) : 60;
      this.exerciseRecords = {};
      this.wakeLockController.setEnabled(this.keepScreenOn);
    },
    updateKeepScreenOn() {
      if (this.keepScreenOnStorageKey) localStorage.setItem(this.keepScreenOnStorageKey, String(this.keepScreenOn));
      this.wakeLockController.setEnabled(this.keepScreenOn);
    },
    updateWakeLock() { this.wakeLockController.setActive((this.visible || this.minimized || this.opening) && !!guidedWorkoutState.draft && guidedWorkoutState.editor === this.editor); },
    async open(source) {
      const generation = ++this.openGeneration;
      this.opening = true;
      this.lockError = '';
      const acquired = await openGuidedWorkoutEditor(this.editor);
      if (generation !== this.openGeneration) {
        if (!this.opening && !this.visible && !this.minimized) closeGuidedWorkoutEditor(this.editor);
        return;
      }
      if (!acquired) { this.lockError = 'This guided workout is open in another tab. Close it there first.'; this.opening = false; this.visible = true; await this.$router.push('/workouts/guided'); return; }
      if (!guidedWorkoutState.draft && source) createGuidedWorkoutDraft(this.normalizeSource(source));
      if (guidedWorkoutState.draft && !guidedWorkoutState.draft.exerciseTimer) {
        const step = guidedWorkoutSteps(guidedWorkoutState.draft.workout)[guidedWorkoutState.draft.currentStep || 0];
        guidedWorkoutState.draft.exerciseTimer = {lineIndex: step?.lineIndex ?? null, segmentIndex: step?.segmentIndex ?? null, pendingMilliseconds: 0, startedAt: guidedWorkoutState.draft.timer?.runningPhase ? Date.now() : null};
        guidedWorkoutState.draft.workout.lines.forEach(line => Object.assign(line, {exerciseDurationMilliseconds: (line.exerciseDurationSeconds || 0) * 1000, exerciseElapsedBySegment: [], omitFromActual: false}));
      }
      if (guidedWorkoutState.draft && !guidedWorkoutState.draft.estimatedSecondsByLine) guidedWorkoutState.draft.estimatedSecondsByLine = estimateGuidedWorkout(guidedWorkoutState.draft.workout);
      if (guidedWorkoutState.draft) ensureGuidedDraftIdentity(guidedWorkoutState.draft);
      this.minimized = false;
      this.visible = false;
      this.updateWakeLock();
      if (this.$route.name !== 'GuidedWorkoutPage') await this.$router.push('/workouts/guided');
      if (generation !== this.openGeneration) return;
      const records = guidedWorkoutState.draft ? await loadWorkoutRecordContext(guidedWorkoutState.draft.workout.lines) : {};
      if (generation !== this.openGeneration) return;
      this.exerciseRecords = records;
      this.now = Date.now();
      this.visible = true;
      this.opening = false;
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
    openEdit() {
      this.editSegment = {...this.currentSegment};
      this.editLine = {calories: this.currentLine.calories, averageHeartRate: this.currentLine.averageHeartRate};
      this.editError = '';
      this.editDialog = true;
    },
    openWorkoutEdit() { this.editWorkoutDialog = true; },
    applyWorkoutEdit(form) {
      this.editWorkoutDialog = false;
      applyGuidedWorkoutEdit(this.guidedWorkoutState.draft, form, Date.now());
      this.now = Date.now();
      this.exerciseRecords = {};
      loadWorkoutRecordContext(this.guidedWorkoutState.draft.workout.lines).then(records => { this.exerciseRecords = records; });
    },
    openRest() {
      const draft = this.guidedWorkoutState.draft;
      if (this.restActive) { this.restDialog = true; return; }
      if (draft.preparedRest) {
        const now = Date.now();
        if (this.currentStep) this.pauseExerciseTimer(now);
        this.now = now;
        saveGuidedWorkoutDraft();
        this.restDialog = true;
        return;
      }
      const completedPhase = guidedPhaseKey(this.currentLine);
      const phaseWasRunning = !!draft.timer?.runningPhase;
      if (!this.currentStep || !this.completeSet()) return;
      const now = Date.now();
      this.pauseExerciseTimer(now);
      draft.preparedRest = {phase: completedPhase, phaseWasRunning};
      this.now = now;
      saveGuidedWorkoutDraft();
      this.restDialog = true;
    },
    segmentError(segment) {
      if (this.currentLine.trackingMode === 'REPS' && (!Number.isInteger(segment.repetitions) || segment.repetitions <= 0)) return 'Enter the completed repetitions as a positive whole number.';
      if (this.currentLine.stretchingUnit === 'BREATHS' && (!Number.isInteger(segment.breaths) || segment.breaths <= 0)) return 'Enter the completed breaths as a positive whole number.';
      if (this.currentDurationBased && (!Number.isInteger(segment.guidedMinutes) || segment.guidedMinutes < 0 || !Number.isInteger(segment.guidedSeconds) || segment.guidedSeconds < 0 || segment.guidedSeconds > 59 || segment.guidedMinutes * 60 + segment.guidedSeconds <= 0)) return 'Enter the completed duration using whole minutes and seconds.';
      return '';
    },
    applyEdit() {
      this.editError = this.segmentError(this.editSegment);
      if (this.editError) return false;
      if (this.currentDurationBased) this.editSegment.durationSeconds = this.editSegment.guidedMinutes * 60 + this.editSegment.guidedSeconds;
      Object.assign(this.currentSegment, this.editSegment);
      if (this.currentLine.trackingMode === 'CARDIO') Object.assign(this.currentLine, this.editLine);
      this.editDialog = false;
      this.saveError = '';
      saveGuidedWorkoutDraft();
      return true;
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
      if (segment.cadenceRpm != null) parts.push(`${segment.cadenceRpm} RPM`);
      if (segment.inclinePercent != null) parts.push(`${segment.inclinePercent}% incline`);
      if (segment.resistanceLevel != null) parts.push(`Resistance ${segment.resistanceLevel}`);
      return parts.join(' · ') || 'Not set';
    },
    metricRecords(line, metric) { return metricRecords(this.exerciseRecords, line, metric); },
    loadMetricRecords(line) { return loadMetricRecords(this.exerciseRecords, line); },
    segmentMetricRecords(line, segment, metric) { return segmentMetricRecords(this.exerciseRecords, line, segment, metric); },
    formatRecordValue,
    formatElapsed(milliseconds) { return formatElapsedValue(milliseconds); },
    toggleTimer() {
      const now = Date.now();
      if (this.guidedTimer.runningPhase) {
        this.pauseExerciseTimer(now);
        pausePhaseTimer(this.guidedTimer, now);
        if (this.restActive && !this.restTimer.paused) this.pauseRestTimer(now);
      } else {
        switchPhaseTimer(this.guidedTimer, guidedPhaseKey(this.currentLine), now);
        if (this.restActive) this.resumeRestTimer(now);
        else this.guidedWorkoutState.draft.exerciseTimer.startedAt = now;
      }
      this.now = now;
      saveGuidedWorkoutDraft();
    },
    updateRestDuration() {
      if (this.restDurationStorageKey) localStorage.setItem(this.restDurationStorageKey, String(this.restDuration));
    },
    closeRest() {
      const now = Date.now();
      this.restDialog = false;
      if (this.guidedWorkoutState.draft.preparedRest && this.currentStep && this.guidedTimer?.runningPhase && !this.restActive) {
        this.guidedWorkoutState.draft.exerciseTimer.startedAt = now;
      }
      this.now = now;
      saveGuidedWorkoutDraft();
    },
    addRestTime() {
      this.restDuration += 30;
      this.updateRestDuration();
      if (this.restActive) {
        this.restTimer.durationSeconds += 30;
        if (this.restTimer.paused) this.restTimer.pausedRemainingMilliseconds += 30000;
        else this.restTimer.endsAt += 30000;
        saveGuidedWorkoutDraft();
      }
    },
    startRest() {
      const now = Date.now();
      if (!this.currentStep && this.guidedTimer && this.guidedWorkoutState.draft.preparedRest.phaseWasRunning) {
        switchPhaseTimer(this.guidedTimer, this.guidedWorkoutState.draft.preparedRest.phase, now);
      }
      const paused = !!this.guidedTimer && !this.guidedTimer.runningPhase;
      this.pauseExerciseTimer(now);
      this.guidedWorkoutState.draft.restTimer = {durationSeconds: this.restDuration, endsAt: now + this.restDuration * 1000, paused, pausedRemainingMilliseconds: paused ? this.restDuration * 1000 : null};
      this.now = now;
      saveGuidedWorkoutDraft();
    },
    pauseRestTimer(now) {
      this.restTimer.pausedRemainingMilliseconds = this.restTimer.endsAt - now;
      this.restTimer.paused = true;
    },
    resumeRestTimer(now) {
      this.restTimer.endsAt = now + this.restTimer.pausedRemainingMilliseconds;
      this.restTimer.pausedRemainingMilliseconds = null;
      this.restTimer.paused = false;
    },
    toggleRestPause() {
      const now = Date.now();
      if (this.restTimer.paused) {
        if (!this.currentStep && this.guidedTimer && this.guidedWorkoutState.draft.preparedRest.phaseWasRunning) {
          switchPhaseTimer(this.guidedTimer, this.guidedWorkoutState.draft.preparedRest.phase, now);
        }
        this.resumeRestTimer(now);
      } else {
        if (!this.currentStep && this.guidedTimer) pausePhaseTimer(this.guidedTimer, now);
        this.pauseRestTimer(now);
      }
      this.now = now;
      saveGuidedWorkoutDraft();
    },
    continueRest() {
      const now = Date.now();
      const draft = this.guidedWorkoutState.draft;
      const finalRestStarted = !this.currentStep && !!draft.restTimer;
      draft.restTimer = null;
      draft.preparedRest = null;
      this.restDialog = false;
      if (finalRestStarted) {
        if (this.guidedTimer?.runningPhase) pausePhaseTimer(this.guidedTimer, now);
        draft.workout.endTime = new Date(now).toISOString();
      }
      else if (this.guidedTimer?.runningPhase) draft.exerciseTimer.startedAt = now;
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
      if (line.trackingMode === 'REPS' && !(segment.repetitions > 0)) { this.saveError = 'Enter the completed repetitions.'; return false; }
      if (line.stretchingUnit === 'BREATHS' && !(segment.breaths > 0)) { this.saveError = 'Enter the completed breaths.'; return false; }
      if (durationRequired && (segment.guidedMinutes * 60 + segment.guidedSeconds <= 0)) { this.saveError = 'Enter the completed duration.'; return false; }
      if (durationRequired) segment.durationSeconds = segment.guidedMinutes * 60 + segment.guidedSeconds;
      this.saveError = '';
      const draft = this.guidedWorkoutState.draft;
      const now = Date.now();
      const timerWasRunning = !!draft.timer?.runningPhase;
      const exerciseTimer = draft.exerciseTimer;
      const elapsed = exerciseTimer.pendingMilliseconds + (exerciseTimer.startedAt == null ? 0 : Math.max(0, now - exerciseTimer.startedAt));
      this.currentLine.exerciseElapsedBySegment[this.currentStep.segmentIndex] = elapsed;
      this.currentLine.exerciseDurationMilliseconds = this.currentLine.exerciseElapsedBySegment.reduce((sum, milliseconds) => sum + (milliseconds || 0), 0);
      draft.completedSegmentKeys ||= [];
      if (!draft.completedSegmentKeys.includes(segment.guidedSegmentKey)) draft.completedSegmentKeys.push(segment.guidedSegmentKey);
      const completed = new Set(draft.completedSegmentKeys);
      draft.currentStep = this.steps.findIndex(step => !completed.has(draft.workout.lines[step.lineIndex].segments[step.segmentIndex].guidedSegmentKey));
      if (draft.currentStep < 0) draft.currentStep = this.steps.length;
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
      return true;
    },
    backToLastSet() {
      this.guidedWorkoutState.draft.reviewing = false;
      this.guidedWorkoutState.draft.currentStep = Math.max(0, this.guidedWorkoutState.draft.currentStep - 1);
      const previousStep = this.steps[this.guidedWorkoutState.draft.currentStep];
      const previousSegment = this.guidedWorkoutState.draft.workout.lines[previousStep.lineIndex].segments[previousStep.segmentIndex];
      this.guidedWorkoutState.draft.completedSegmentKeys = this.guidedWorkoutState.draft.completedSegmentKeys.filter(key => key !== previousSegment.guidedSegmentKey);
      this.guidedWorkoutState.draft.workout.endTime = null;
      if (this.guidedTimer) { this.now = Date.now(); switchPhaseTimer(this.guidedTimer, guidedPhaseKey(this.currentLine), this.now); this.startExerciseTimer(this.currentStep, this.now); }
      this.wakeLockController.setActive(this.visible && guidedWorkoutState.editor === this.editor);
    },
    backOneStep() {
      const draft = this.guidedWorkoutState.draft;
      const now = Date.now();
      draft.restTimer = null;
      draft.preparedRest = null;
      this.discardPendingExerciseTime();
      draft.currentStep -= 1;
      const previousStep = this.steps[draft.currentStep];
      const previousSegment = draft.workout.lines[previousStep.lineIndex].segments[previousStep.segmentIndex];
      draft.completedSegmentKeys = draft.completedSegmentKeys.filter(key => key !== previousSegment.guidedSegmentKey);
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
      draft.restTimer = null;
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
      const email = this.state.user.mail;
      const generation = this.openGeneration;
      try {
        const timing = draft.timer ? roundedPhaseMinutes(draft.timer) : {};
        const lines = draft.workout.lines.filter(line => !line.omitFromActual && line.segments.length).map(line => ({...line, exerciseDurationSeconds: Math.floor(line.exerciseDurationMilliseconds / 1000)}));
        await workoutService.save({...draft.workout, lines, ...timing, ...(draft.timer ? {durationMinutes: Object.values(timing).reduce((sum, minutes) => sum + minutes, 0) + this.saunaDuration} : {}), recordingKey: draft.recordingKey});
        discardSavedGuidedWorkoutDraft(email, draft.recordingKey);
        if (generation !== this.openGeneration) return;
        this.saving = false;
        this.close();
        window.dispatchEvent(new Event('timed-workout-saved'));
      } catch (error) { this.saveError = error.message || 'Could not save the workout.'; }
      finally { this.saving = false; }
    },
    leavePage() {
      this.openGeneration += 1;
      this.opening = false;
      this.editDialog = false;
      this.editWorkoutDialog = false;
      this.restDialog = false;
      if (guidedWorkoutState.editor === this.editor && guidedWorkoutState.draft) { saveGuidedWorkoutDraft(); this.minimized = true; }
      this.visible = false;
    },
    minimize() { this.leavePage(); this.$router.push('/workouts'); },
    close() { this.openGeneration += 1; this.opening = false; if (guidedWorkoutState.editor === this.editor) saveGuidedWorkoutDraft(); closeGuidedWorkoutEditor(this.editor); this.minimized = false; this.visible = false; this.editDialog = false; this.editWorkoutDialog = false; this.restDialog = false; this.$router.push('/workouts'); },
    async discard() {
      this.discardPrompt = false;
      const generation = ++this.openGeneration;
      const acquired = await openGuidedWorkoutEditor(this.editor);
      if (generation !== this.openGeneration) return;
      if (!acquired) { this.lockError = 'This guided workout is open in another tab. Close it there first.'; this.visible = true; await this.$router.push('/workouts/guided'); return; }
      discardGuidedWorkoutDraft();
      this.close();
    }
  }
};
</script>

<style scoped>
.guided-workout-minimized { position: fixed; z-index: 999; right: max(1rem, env(safe-area-inset-right)); bottom: max(1rem, env(safe-area-inset-bottom)); display: flex; flex-wrap: wrap; align-items: center; gap: .5rem 1rem; max-width: min(100% - 2rem, 520px); padding: .75rem; border: 1px solid #c8cdd2; border-radius: 8px; background: var(--surface-card, #fff); box-shadow: 0 2px 12px #0003; }
.guided-workout-minimized > div { display: grid; gap: .25rem; min-width: 0; overflow-wrap: anywhere; }
.guided-workout-minimized span { color: #59636e; }

.error { color: #c62828; }
</style>
