<template>
  <section class="guided-workout-page" aria-label="Guided workout">
    <header class="guided-page-header">
      <div><h1>Guided workout</h1></div>
      <div class="guided-header-actions">
        <Button v-if="session.visible && !session.lockError && session.guidedWorkoutState.draft && !session.reviewing && session.currentStep" label="Edit workout" icon="pi pi-list" class="p-button-outlined" @click="session.openWorkoutEdit" />
        <CompactAction v-if="session.guidedWorkoutState.draft && !session.lockError" icon="pi pi-window-minimize" aria-label="Minimize" aria-describedby="guided-minimize-description" :tooltip="actionHelp.minimize" :disabled="session.saving || session.guidedWorkoutState.editor !== session.editor" @click="session.minimize" />
        <CompactAction icon="pi pi-times" aria-label="Close" aria-describedby="guided-close-description" :tooltip="actionHelp.close" :disabled="session.saving" @click="session.close" />
      </div>
      <span id="guided-minimize-description" class="p-hidden-accessible">{{ actionHelp.minimize }}</span>
      <span id="guided-close-description" class="p-hidden-accessible">{{ actionHelp.close }}</span>
    </header>
    <p v-if="!session.visible" role="status">Opening guided workout…</p>
    <p v-else-if="session.lockError" role="alert" class="error">{{ session.lockError }}</p>
    <template v-else-if="session.guidedWorkoutState.draft">
      <div class="guided-screen-lock">
        <Checkbox inputId="guided-keep-screen-on" v-model="session.keepScreenOn" :binary="true" @change="session.updateKeepScreenOn" />
        <label for="guided-keep-screen-on">Keep screen on</label>
        <span v-if="session.keepScreenOn && session.wakeLockMessage" role="status" aria-live="polite">{{ session.wakeLockMessage }}</span>
      </div>
      <p v-if="session.guidedWorkoutState.draft.workout.plannedSessionName" class="guided-session-name">{{ session.guidedWorkoutState.draft.workout.plannedSessionName }}</p>
      <section v-if="session.guidedTimer && session.steps.length" class="guided-timer" aria-label="Guided workout timer">
        <div class="guided-timer-summary">
          <strong role="status" aria-live="polite">{{ session.currentStep ? session.phaseLabel : 'Workout' }} · {{ session.timerStatus }}</strong>
          <Button v-if="session.currentStep" :label="session.guidedTimer.runningPhase ? 'Pause' : 'Resume'" :icon="session.guidedTimer.runningPhase ? 'pi pi-pause' : 'pi pi-play'" class="p-button-outlined" @click="session.toggleTimer" />
        </div>
        <div class="guided-status-row"><span v-if="!session.reviewing" class="guided-progress" role="status">{{ session.progressLabel }}</span><span role="timer" aria-live="off" aria-label="Total elapsed time">Total {{ session.totalElapsed }}</span></div>
      </section>
      <p v-else-if="!session.guidedTimer" class="guided-timing-note">This draft started before automatic timing. Finish it without phase times or start a new guided workout.</p>
      <section v-else-if="!session.steps.length" class="guided-completion" aria-label="Guided workout completion"><strong role="status">{{ session.timerStatus }}</strong></section>
      <p v-if="!session.reviewing && !session.guidedTimer" class="guided-progress" role="status">{{ session.progressLabel }}</p>
      <template v-if="!session.reviewing">
        <template v-if="session.currentStep">
          <GuidedExerciseSummary :line="session.currentLine" :setIndex="session.currentStep.segmentIndex" :target="session.currentTarget" :plannedTarget="!!session.plannedSegment" :timeLabel="session.currentDurationBased ? 'Planned duration' : 'Estimated set time'" :timeValue="session.currentDurationBased ? session.formatElapsed(session.plannedSegment.durationSeconds * 1000) : session.currentSetEstimate">
            <template #action><Button ref="editButton" label="Edit set" class="p-button-outlined" @click="session.openEdit" /></template>
            <template #elapsed><div class="guided-set-elapsed"><span>Set elapsed</span><strong v-if="session.guidedTimer" role="timer" aria-live="off" aria-label="Set elapsed time">{{ session.setElapsed }}</strong><strong v-else>Untimed</strong></div></template>
          </GuidedExerciseSummary>
          <small class="guided-active-help">Active time · excludes rest and pauses. Estimates are rough.</small>
          <div class="guided-exercise-times"><span v-if="session.guidedTimer" role="timer" aria-live="off" :aria-label="`${session.phaseLabel} elapsed time`">Phase {{ session.phaseElapsed }} · includes rest</span><span class="guided-exercise-time" role="timer" aria-live="off">Exercise time {{ session.exerciseElapsed }}</span><span class="guided-estimate">Estimated total {{ session.currentExerciseEstimate }}</span></div>
          <div class="guided-rest-row"><span v-if="session.restActive" class="guided-rest-status" role="timer" aria-live="off" :aria-label="`Rest timer ${session.restTimerLabel}`">Rest · {{ session.restTimerLabel }}</span><span v-else>Rest duration: {{ session.restDuration }} sec</span><div class="guided-set-actions"><Button ref="restButton" label="Rest" icon="pi pi-clock" class="p-button-outlined" @click="session.openRest" /><Button label="Complete set" icon="pi pi-check" :disabled="session.restActive || session.restPrepared" @click="session.completeSet" /></div></div>
          <div class="action-group guided-move-actions" aria-label="Move on from this exercise"><Button label="Skip" icon="pi pi-ban" class="p-button-outlined" :disabled="session.restPrepared" @click="session.markExerciseSkipped" /><Button label="Remove" class="p-button-outlined" :disabled="session.restPrepared" @click="session.moveOnWithoutLogging" /></div>
          <div class="action-group guided-back-actions"><Button v-if="session.guidedWorkoutState.draft.currentStep > 0" label="Back" class="p-button-outlined" @click="session.backOneStep" /></div>
          <GuidedExerciseSummary v-if="session.nextStep" next :line="session.nextLine" :setIndex="session.nextStep.segmentIndex" :target="session.describeSegment(session.nextPlannedSegment || session.nextLine.segments[session.nextStep.segmentIndex])" :plannedTarget="!!session.nextPlannedSegment" :timeLabel="session.nextDurationBased ? 'Planned duration' : 'Estimated set time'" :timeValue="session.nextDurationBased ? session.formatElapsed(session.nextPlannedSegment.durationSeconds * 1000) : session.nextSetEstimate" />
          <details class="guided-progress-details"><summary>Exercise progress</summary><table class="guided-exercise-progress"><caption>Exercises by phase</caption><thead><tr><th scope="col">Type</th><th scope="col">Done</th><th scope="col">Now</th><th scope="col">Ahead</th></tr></thead><tbody><tr v-for="row in session.exerciseProgress" :key="row.key"><th scope="row">{{ row.label }}</th><td>{{ row.done }}</td><td>{{ row.now }}</td><td>{{ row.ahead }}</td></tr></tbody></table></details>
        </template>
        <div v-else-if="session.restPrepared || session.restActive" class="guided-final-rest-actions"><span>Rest after the completed workout</span><Button ref="restButton" label="Rest" icon="pi pi-clock" class="p-button-outlined" @click="session.openRest" /></div>
        <div v-else class="guided-review"><h2>{{ session.timerStatus === 'Ready to complete' ? 'Ready to complete' : 'Workout complete' }}</h2><p>Review the recorded sets before saving.</p><p v-for="line in session.guidedWorkoutState.draft.workout.lines" :key="line.exerciseId">{{ line.exerciseName }} · {{ line.segments.map(session.describeSegment).join(', ') }}</p></div>
      </template>
      <section v-else class="guided-review" aria-label="Review workout">
        <h2>Review workout</h2>
        <div v-if="session.guidedTimer && session.guidedWorkoutState.draft.workout.lines.length" class="guided-timer-review">
          <strong>Recorded phase times · {{ session.savedDurationMinutes }} min</strong>
          <ul><li v-for="phase in session.phases" :key="phase.key">{{ phase.label }}: {{ session.savedPhaseMinutes[phase.key] }} min</li></ul>
          <small>Each phase rounds up to whole minutes after its intervals are added.</small>
        </div>
        <SaveFields :saving="session.saving"><SaunaRoundsEditor v-if="session.guidedWorkoutState.draft.workout.saunaSession" idPrefix="guided-sauna-round" v-model="session.guidedWorkoutState.draft.workout.saunaRoundsMinutes" :plannedRounds="session.guidedWorkoutState.draft.workout.plannedSaunaRoundsMinutes" title="Completed sauna rounds" /></SaveFields>
        <p v-if="session.guidedWorkoutState.draft.workout.saunaSession">Session duration: {{ session.savedDurationMinutes + session.saunaDuration }} min, including {{ session.saunaDuration }} min in sauna.</p>
        <article v-for="line in session.reviewLines" :key="line.exerciseId" class="guided-review-line">
          <ExercisePicture :src="line.imageUrl" :name="line.exerciseName" :description="line.exerciseDescription" />
          <div><strong>{{ line.exerciseName }}</strong><p v-if="line.exerciseDurationMilliseconds > 0">Exercise time {{ session.formatElapsed(line.exerciseDurationMilliseconds) }}</p></div>
          <ol><li v-for="(segment, index) in line.segments" :key="index">{{ session.describeSegment(segment) }}</li></ol>
        </article>
      </section>

      <p v-if="session.saveError" role="alert" class="error">{{ session.saveError }}</p>
      <footer class="action-group guided-actions" :aria-busy="session.saving">      <template v-if="!session.lockError">
        <Button v-if="session.reviewing && session.steps.length" label="Back to last set" class="p-button-outlined" :disabled="session.saving" @click="session.backToLastSet" />
        <Button v-else-if="!session.reviewing && session.currentStep" label="Review" icon="pi pi-check" @click="session.startReview" />
        <Button v-else-if="!session.reviewing" label="Review" icon="pi pi-check" @click="session.startReview" />
        <Button v-if="session.reviewing && session.guidedWorkoutState.draft.workout.saunaSession && !session.guidedWorkoutState.draft.workout.endTime" label="Complete workout" icon="pi pi-check" @click="session.completeWorkout" />
        <Button v-else-if="session.reviewing" label="Save" aria-label="Save workout" icon="pi pi-save" :loading="session.saving" :disabled="session.saving" @click="session.save" />
      </template>
</footer>
    </template>
    <p v-else role="status">No guided workout draft. Start a workout from Workouts.</p>
  </section>
  <Dialog v-model:visible="session.editDialog" header="Edit current set" @after-hide="restoreFocus('editButton')" appendTo="body" :modal="true" :style="{width: 'min(520px, 96vw)'}">
    <template v-if="session.currentStep && session.editSegment"><h2 class="guided-edit-title">{{ session.currentLine.exerciseName }} · Set {{ session.currentStep.segmentIndex + 1 }}</h2>
          <div class="guided-fields">
            <template v-if="session.currentLine.trackingMode === 'REPS'">
              <label :for="`guided-reps-${session.currentStep.segmentIndex}`">Repetitions</label><InputNumber :inputId="`guided-reps-${session.currentStep.segmentIndex}`" v-model="session.editSegment.repetitions" :min="1" :useGrouping="false" />
              <div v-if="session.segmentMetricRecords(session.currentLine, session.editSegment, 'WORKOUT_REPETITIONS').length" class="field-record-context"><span v-for="record in session.segmentMetricRecords(session.currentLine, session.editSegment, 'WORKOUT_REPETITIONS')" :key="record.metric">{{ record.metricLabel }}: {{ session.formatRecordValue(record) }}</span></div>
            </template>
            <template v-if="session.currentLine.trackingMode === 'SECONDS' && session.currentLine.stretchingUnit === 'BREATHS'">
              <label :for="`guided-breaths-${session.currentStep.segmentIndex}`">Breaths</label><InputNumber :inputId="`guided-breaths-${session.currentStep.segmentIndex}`" v-model="session.editSegment.breaths" :min="1" :useGrouping="false" />
            </template>
            <template v-else-if="session.currentLine.trackingMode === 'SECONDS' || session.currentLine.trackingMode === 'CARDIO'">
              <label :for="`guided-duration-${session.currentStep.segmentIndex}`">Duration (minutes)</label><InputNumber :inputId="`guided-duration-${session.currentStep.segmentIndex}`" v-model="session.editSegment.guidedMinutes" :min="0" :useGrouping="false" />
              <div v-if="session.currentLine.trackingMode === 'CARDIO' && session.metricRecords(session.currentLine, 'CARDIO_DURATION').length" class="field-record-context"><span v-for="record in session.metricRecords(session.currentLine, 'CARDIO_DURATION')" :key="record.metric">{{ record.metricLabel }}: {{ session.formatRecordValue(record) }}</span></div>
              <label :for="`guided-seconds-${session.currentStep.segmentIndex}`">Seconds</label><InputNumber :inputId="`guided-seconds-${session.currentStep.segmentIndex}`" v-model="session.editSegment.guidedSeconds" :min="0" :max="59" :useGrouping="false" />
              <div v-if="session.currentLine.trackingMode === 'SECONDS' && session.segmentMetricRecords(session.currentLine, session.editSegment, 'WORKOUT_DURATION').length" class="field-record-context"><span v-for="record in session.segmentMetricRecords(session.currentLine, session.editSegment, 'WORKOUT_DURATION')" :key="record.metric">{{ record.metricLabel }}: {{ session.formatRecordValue(record) }}</span></div>
            </template>
            <template v-if="session.currentLine.trackingMode === 'CARDIO'">
              <template v-for="field in session.cardioFields" :key="field.key">
                <label :for="`guided-${field.key}-${session.currentStep.segmentIndex}`">{{ field.label }}</label>
                <InputNumber :inputId="`guided-${field.key}-${session.currentStep.segmentIndex}`" v-model="session.editSegment[field.key]" :min="0" :minFractionDigits="0" :maxFractionDigits="2" />
                <div v-if="session.metricRecords(session.currentLine, field.metric).length" class="field-record-context"><span v-for="record in session.metricRecords(session.currentLine, field.metric)" :key="record.metric">{{ record.metricLabel }}: {{ session.formatRecordValue(record) }}</span></div>
              </template>
              <label for="guided-calories">Calories</label><InputNumber inputId="guided-calories" v-model="session.editLine.calories" :min="0" />
              <div v-if="session.metricRecords(session.currentLine, 'WORKOUT_CALORIES').length" class="field-record-context"><span v-for="record in session.metricRecords(session.currentLine, 'WORKOUT_CALORIES')" :key="record.metric">{{ record.metricLabel }}: {{ session.formatRecordValue(record) }}</span></div>
              <label for="guided-heart-rate">Average heart rate (bpm)</label><InputNumber inputId="guided-heart-rate" v-model="session.editLine.averageHeartRate" :min="0" :useGrouping="false" />
              <div v-if="session.metricRecords(session.currentLine, 'WORKOUT_AVERAGE_HEART_RATE').length" class="field-record-context"><span v-for="record in session.metricRecords(session.currentLine, 'WORKOUT_AVERAGE_HEART_RATE')" :key="record.metric">{{ record.metricLabel }}: {{ session.formatRecordValue(record) }}</span></div>
            </template>
            <template v-if="session.currentLine.trackingMode !== 'CARDIO' && session.currentLine.exerciseType !== 'STRETCHING'">
              <label :for="`guided-weight-${session.currentStep.segmentIndex}`">Weight (kg)</label><InputNumber :inputId="`guided-weight-${session.currentStep.segmentIndex}`" v-model="session.editSegment.weight" :min="0" :minFractionDigits="0" :maxFractionDigits="2" />
              <div v-if="session.loadMetricRecords(session.currentLine).length" class="field-record-context"><span v-for="record in session.loadMetricRecords(session.currentLine)" :key="record.metric">{{ record.metricLabel }}: {{ session.formatRecordValue(record) }}</span></div>
            </template>
          </div>

      <p v-if="session.editError" role="alert" class="error">{{ session.editError }}</p>
    </template>
    <template #footer><div class="action-group"><Button label="Apply" icon="pi pi-check" @click="applyEdit" /><Button label="Cancel" class="p-button-secondary" @click="session.editDialog = false" /></div></template>
  </Dialog>
  <Dialog v-model:visible="session.restDialog" header="Rest" @after-hide="restoreFocus('restButton')" appendTo="body" :modal="true" :style="{width: 'min(460px, 96vw)'}">
    <section class="guided-rest" aria-label="Rest timer">
      <template v-if="session.restActive">
        <p role="status">Rest · {{ session.restTimer.paused ? 'Paused' : 'Running' }}</p><strong class="guided-rest-countdown" role="timer" aria-live="off" :aria-label="`Rest timer ${session.restTimerLabel}`">{{ session.restTimerLabel }}</strong>
        <div class="action-group"><Button label="+30 sec" class="p-button-outlined" @click="session.addRestTime" /><Button v-if="session.guidedTimer && session.currentStep" :label="session.guidedTimer.runningPhase ? 'Pause' : 'Resume'" :icon="session.guidedTimer.runningPhase ? 'pi pi-pause' : 'pi pi-play'" class="p-button-outlined" @click="session.toggleTimer" /><Button v-else :label="session.restTimer.paused ? 'Resume rest' : 'Pause rest'" class="p-button-outlined" @click="session.toggleRestPause" /></div>
      </template>
      <div v-else class="guided-rest-settings"><label for="guided-rest-duration">Rest duration (seconds)</label><InputNumber inputId="guided-rest-duration" v-model="session.restDuration" :min="1" :maxFractionDigits="0" :useGrouping="false" @update:modelValue="session.updateRestDuration" /><div class="action-group"><Button label="+30 sec" class="p-button-outlined" @click="session.addRestTime" /><Button label="Start rest" icon="pi pi-clock" @click="session.startRest" :disabled="!(session.restDuration > 0)" /></div></div>
      <p>Rest is excluded from set time. Closing this dialog keeps active rest running.</p>
    </section>
    <template #footer><div class="action-group"><Button v-if="session.restActive || session.restPrepared" label="Continue" icon="pi pi-arrow-right" @click="session.continueRest" :disabled="session.restActive && (session.restTimer.paused || (session.currentStep && session.guidedTimer && !session.guidedTimer.runningPhase))" /><Button label="Close" class="p-button-secondary" @click="session.closeRest" /></div></template>
  </Dialog>
  <WorkoutEditor v-if="session.editWorkoutDialog" :show="true" :guided_edit="true" :fixed_date="true" :workout="session.editorWorkout" @onApply="session.applyWorkoutEdit" @onClose="session.editWorkoutDialog = false" />
</template>
<script>
import GuidedExerciseSummary from './GuidedExerciseSummary.vue';
import ExercisePicture from './ExercisePicture.vue';
import SaunaRoundsEditor from './SaunaRoundsEditor.vue';
import WorkoutEditor from './WorkoutEditor.vue';
export default {
  name: 'GuidedWorkoutPage',
  inject: ['guidedWorkout'],
  data() { return {removeRouteGuard: null, actionHelp: {minimize: 'Return to Workouts with the floating panel; timing and screen lock continue.', close: 'Return to Workouts and release editing and screen lock; the draft and timers remain.'}}; },
  components: {GuidedExerciseSummary, ExercisePicture, SaunaRoundsEditor, WorkoutEditor},
  computed: {session() { return this.guidedWorkout; }},
  methods: {
    restoreFocus(name) { if (this.$refs[name]) this.$refs[name].$el.focus(); },
    async applyEdit() {
      if (this.session.applyEdit()) return;
      await this.$nextTick();
      const segment = this.session.editSegment;
      const field = this.session.currentLine.trackingMode === 'REPS' ? 'reps' : this.session.currentLine.stretchingUnit === 'BREATHS' ? 'breaths' : !Number.isInteger(segment.guidedMinutes) || segment.guidedMinutes < 0 ? 'duration' : 'seconds';
      const input = document.getElementById(`guided-${field}-${this.session.currentStep.segmentIndex}`);
      input.scrollIntoView({block: 'center'});
      input.focus();
    }
  },
  mounted() {
    const session = this.session;
    this.removeRouteGuard = this.$router.beforeEach((to, from) => {
      if (from.name !== 'GuidedWorkoutPage' || to.name === 'GuidedWorkoutPage') return;
      if (session.saving) return false;
      session.leavePage();
    });
    if (!session.visible && !session.opening) session.open();
  },
  beforeUnmount() { this.removeRouteGuard(); }
};
</script>
<style scoped>
.guided-workout-page { max-width: 42rem; margin: 0 auto; padding: 0 1rem 1rem; }
.guided-page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: .5rem; }
.guided-page-header > div:first-child { min-width: 0; }
h1 { font-size: 1.5rem; margin: 0; overflow-wrap: anywhere; }
.guided-session-name { margin: 0 0 .75rem; color: #59636e; overflow-wrap: anywhere; }
/* The labeled workout workflow shares a row with square Minimize/Close controls. */
.guided-header-actions { display: flex; align-items: center; gap: .5rem; flex: 0 0 auto; }
.guided-screen-lock { display: flex; flex-wrap: wrap; align-items: center; gap: .5rem; margin: .5rem 0 1rem; }
.guided-screen-lock span { flex-basis: 100%; font-size: .875rem; color: #59636e; }
.guided-timer { margin-bottom: 1rem; }
.guided-timer-summary, .guided-status-row { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: .5rem; }
.guided-status-row { color: #59636e; margin-top: .6rem; }
.guided-set-elapsed { display: grid; gap: .25rem; min-width: 0; }
.guided-set-elapsed span { color: #59636e; }
.guided-set-elapsed strong { font-size: 1.6rem; font-variant-numeric: tabular-nums; }
.guided-active-help { display: block; color: #59636e; margin-top: .5rem; }
.guided-rest-row { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: .5rem; margin: .75rem 0; padding: .75rem 0; border-top: 1px solid #d6d6d6; border-bottom: 1px solid #d6d6d6; color: #59636e; }
.guided-set-actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .5rem; flex: 1 1 100%; }
.guided-set-actions :deep(.p-button) { min-width: 0; justify-content: center; }
.guided-final-rest-actions { display: flex; align-items: center; justify-content: space-between; gap: .75rem; padding: .75rem 0; }
.guided-rest-status { font-variant-numeric: tabular-nums; }
.guided-exercise-times { display: flex; flex-wrap: wrap; gap: .35rem .75rem; color: #59636e; font-size: .875rem; margin-top: .75rem; font-variant-numeric: tabular-nums; }
.guided-progress-details { margin-top: .75rem; }
.guided-progress-details summary { cursor: pointer; }
.guided-exercise-progress { width: 100%; margin-top: .5rem; border-collapse: collapse; font-size: .875rem; text-align: center; }
.guided-exercise-progress th, .guided-exercise-progress td { padding: .2rem .35rem; border-bottom: 1px solid #e1e4e7; }
.guided-exercise-progress th:first-child { text-align: left; }
.guided-move-actions, .guided-back-actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); margin: .5rem 0; }
.guided-move-actions :deep(.p-button), .guided-back-actions :deep(.p-button) { min-width: 0; justify-content: center; }
.guided-actions { --action-min-width: 9rem; margin-top: 1rem; padding-top: .75rem; border-top: 1px solid #d6d6d6; }
.guided-workout-page :deep(.p-button-label) { word-break: normal; overflow-wrap: normal; }
.guided-fields { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); align-items: center; gap: .5rem .75rem; }
.guided-fields label { overflow-wrap: anywhere; }
.guided-fields :deep(.p-inputnumber), .guided-fields :deep(.p-inputnumber-input) { width: 100%; min-width: 0; }
.field-record-context { grid-column: 1 / -1; display: grid; gap: .2rem; font-size: .875rem; color: #59636e; }
.guided-edit-title { font-size: 1.1rem; margin: 0 0 1rem; overflow-wrap: anywhere; }
.guided-rest-countdown { display: block; font-size: 1.8rem; text-align: center; margin: 1rem 0; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.guided-rest-settings { display: grid; gap: .5rem; }
.guided-rest-settings :deep(.p-inputnumber), .guided-rest-settings :deep(.p-inputnumber-input) { min-width: 0; width: 100%; }
.guided-review-line { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: .5rem; margin: 1rem 0; overflow-wrap: anywhere; }
.guided-review-line ol { grid-column: 2; margin: 0; padding-left: 1.25rem; }
.error { color: #c62828; }
@media (max-width: 575px) {
  .guided-workout-page { padding-inline: .75rem; }
  .guided-page-header { flex-wrap: wrap; }
  .guided-page-header > div:first-child { width: 100%; }
  .guided-header-actions { margin-left: auto; }
}
</style>
