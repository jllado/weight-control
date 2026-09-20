<template>
  <Dialog id="workout-form" appendTo="body" :header="planning ? 'Planned workout' : 'Workout'" v-model:visible="display_modal" :closeOnEscape="false" :closable="false" :modal="true" :style="{width: 'min(960px, 96vw)'}">
    <SaveFields :saving="saving">
    <br>
    <div class="p-fluid">
      <div v-if="!planning && !fixed_date" class="p-field p-mb-4">
        <span class="p-float-label">
          <Calendar :disabled="!!timerDraft" v-model="workout_form.workoutDate" dateFormat="dd/mm/yy" appendTo="body" v-model:locale="custom_locale" :maxDate="max_date" />
          <label>Date</label>
        </span>
        <span class="error">{{ workout_errors.workoutDate }}</span>
      </div>
      <div v-if="!planning && !is_editing && preload_options.length" class="p-field p-mb-4">
        <label for="preload-workout" class="p-d-block p-mb-2">Preload workout</label>
        <Dropdown inputId="preload-workout" v-model="selected_preload_workout_id" :options="preload_options" optionLabel="label" optionValue="id" placeholder="Start from scratch" class="workout-preload" :panelStyle="{maxWidth: 'calc(100vw - 2rem)'}" @change="preloadWorkout">
          <template #option="{option}"><span class="workout-preload-option">{{ option.label }}</span></template>
        </Dropdown>
      </div>
      <div v-if="planning" class="p-field p-mb-4">
        <label for="planned-preload-workout" class="p-d-block p-mb-2">Use completed workout</label>
        <Dropdown inputId="planned-preload-workout" aria-label="Use completed workout" v-model="selected_preload_workout_id" :options="preload_options" optionLabel="label" optionValue="id" placeholder="Select a workout" class="workout-preload" :disabled="planningPreloadsLoading || !!planningPreloadsError || !preload_options.length" :panelStyle="{maxWidth: 'calc(100vw - 2rem)'}" aria-describedby="planned-preload-help" @change="preloadWorkout">
          <template #option="{option}"><span class="workout-preload-option">{{ option.label }}</span></template>
        </Dropdown>
        <small id="planned-preload-help">Replaces draft exercises and targets; keeps your note. Adjust before saving.</small>
        <p v-if="planningPreloadsLoading" role="status">Loading completed workouts…</p>
        <p v-else-if="planningPreloadsError" role="alert" class="error">{{ planningPreloadsError }} <Button label="Retry" class="p-button-text" @click="loadPlanningPreloads" /></p>
        <p v-else-if="!preload_options.length">No completed workouts yet.</p>
      </div>
      <WorkoutPhaseTimers v-if="!planning" :draft="timerDraft" @start="startTimer" @stop="stopTimer" />
      <p v-if="timerError" class="error" role="alert">{{ timerError }}</p>
      <p v-if="legacyTiming" class="p-mb-3">Earlier training time may include cardio. New cardio time is recorded separately.</p>
      <section v-if="!planning" aria-label="Workout timing" class="p-mb-4">
        <div class="p-grid">
          <div class="p-col-12 p-md-6 p-field">
            <label for="workout-start-time">Start time (optional)</label>
            <Calendar inputId="workout-start-time" :disabled="timerRunning" v-model="workout_form.startTime" appendTo="body" :timeOnly="true" hourFormat="24" showButtonBar />
          </div>
          <div class="p-col-12 p-md-6 p-field">
            <label for="workout-duration">Duration (min){{ workout_form.breakdown ? '' : ' (optional)' }}</label>
            <InputNumber inputId="workout-duration" :modelValue="sessionDuration" @update:modelValue="workout_form.durationMinutes = $event; workout_errors.durationMinutes = null" :readonly="workout_form.breakdown" :min="1" :useGrouping="false" />
          </div>
        </div>
        <div class="workout-breakdown-toggle">
          <Checkbox inputId="workout-breakdown" :disabled="!!timerDraft" v-model="workout_form.breakdown" :binary="true" @change="toggleDurationBreakdown" />
          <label for="workout-breakdown">Break down duration</label>
        </div>
        <template v-if="workout_form.breakdown">
          <p class="p-mt-2 p-mb-2">Include rest in each phase. Enter zero for phases you skipped.</p>
          <div class="p-grid">
            <div v-for="phase in durationPhases" :key="phase.key" class="p-col-12 p-md-3 p-field">
              <label :for="`workout-${phase.key}`">{{ phase.label }} (min)</label>
              <InputNumber :inputId="`workout-${phase.key}`" :disabled="timerRunning" :modelValue="workout_form[phase.key]" @update:modelValue="changePhaseMinutes(phase.key, $event)" :min="0" :useGrouping="false" />
            </div>
          </div>
        </template>
        <span v-if="workout_errors.durationMinutes" class="error" role="alert">{{ workout_errors.durationMinutes }}</span>
      </section>
      <div class="p-field p-mb-4">
        <label for="workout-editor-note" class="p-d-block p-mb-2">Note</label>
        <textarea id="workout-editor-note" v-model="workout_form.note" rows="3" class="p-inputtext p-component workout-textarea" maxlength="500"></textarea>
        <span class="error">{{ workout_errors.note }}</span>
      </div>

      <section v-for="group in exerciseGroups" :key="group.type" class="workout-exercise-group" :class="{'workout-exercise-group--primary': group.primary}" :aria-labelledby="`workout-exercise-group-${group.type}-heading`">
        <h3 :id="`workout-exercise-group-${group.type}-heading`" class="workout-exercise-group-heading" :aria-label="group.label">
          <button type="button" class="workout-exercise-group-toggle p-link" :aria-expanded="!collapsedExerciseGroups[group.type]" :aria-controls="`workout-exercise-group-${group.type}`" :aria-label="`${collapsedExerciseGroups[group.type] ? 'Expand' : 'Collapse'} ${group.label}, ${group.lines.length} ${group.lines.length === 1 ? group.countSingular : group.countPlural}`" @click="toggleExerciseGroup(group.type)">
            <span class="workout-exercise-group-identity">
              <i :class="group.icon" class="workout-exercise-group-icon" aria-hidden="true"></i>
              <span class="workout-exercise-group-label"><small v-if="group.primary">Training</small><strong>{{ group.label }}</strong></span>
            </span>
            <span class="workout-exercise-group-count">{{ group.lines.length }}</span>
            <i :class="collapsedExerciseGroups[group.type] ? 'pi pi-chevron-right' : 'pi pi-chevron-down'" class="workout-exercise-group-chevron" aria-hidden="true"></i>
          </button>
        </h3>
        <div v-show="!collapsedExerciseGroups[group.type]" :id="`workout-exercise-group-${group.type}`" class="workout-exercise-group-lines">
      <div v-for="({line, lineIndex}, groupIndex) in group.lines" :key="line.localId" class="workout-line-card p-mb-4">
        <div class="workout-line-header">
          <button type="button" class="workout-line-toggle p-link" :aria-expanded="!line.collapsed" :aria-controls="`workout-line-${line.localId}`" :aria-label="`${line.collapsed ? 'Expand' : 'Collapse'} ${lineTitle(line, lineIndex)}`" @click="line.collapsed = !line.collapsed">
            <i :class="line.collapsed ? 'pi pi-chevron-right' : 'pi pi-chevron-down'" aria-hidden="true"></i>
            <strong>{{ lineTitle(line, lineIndex) }}</strong>
          </button>
          <div class="workout-line-actions action-group action-group--compact">
            <CompactAction icon="pi pi-arrow-up" :aria-label="`Move exercise ${groupIndex + 1} up`" :disabled="groupIndex === 0" @click="moveLine(lineIndex, -1)" />
            <CompactAction icon="pi pi-arrow-down" :aria-label="`Move exercise ${groupIndex + 1} down`" :disabled="groupIndex === group.lines.length - 1" @click="moveLine(lineIndex, 1)" />
            <CompactAction icon="pi pi-trash" :aria-label="`Delete exercise ${lineIndex + 1}`" @click="removeLine(lineIndex)" destructive />
          </div>
        </div>
        <div v-show="!line.collapsed" :id="`workout-line-${line.localId}`" class="workout-line-content">
          <div class="p-grid">
            <div class="p-col-12 p-md-6">
              <label :for="`exercise-${line.localId}`" class="p-d-block p-mb-2">Exercise</label>
              <Dropdown :inputId="`exercise-${line.localId}`" aria-label="Exercise" v-model="line.exerciseId" :options="availableExercises(line)" optionLabel="name" optionValue="id" placeholder="Select exercise" class="workout-exercise-picker" :panelStyle="{maxWidth: 'calc(100vw - 2rem)'}" @change="onExerciseChanged(line)">
                <template #option="{option}"><span class="workout-exercise-option"><img v-if="option.imageUrl" :src="option.imageUrl" alt="" loading="lazy" /><span>{{ option.name }}</span></span></template>
              </Dropdown>
            </div>
            <div class="p-col-12 p-md-6">
              <label :for="`mode-${line.localId}`" class="p-d-block p-mb-2">Mode</label>
              <Dropdown v-if="line.exerciseType === ExerciseType.STRETCHING" :inputId="`mode-${line.localId}`" v-model="line.stretchingUnit" aria-label="Mode" :options="stretchingUnitOptions" optionLabel="label" optionValue="value" @change="changeStretchingUnit(line)" />
              <InputText v-else :id="`mode-${line.localId}`" :value="line.trackingMode ? trackingModeLabel(line.trackingMode) : ''" readonly />
            </div>
            <div class="p-col-12" v-if="line.exerciseDescription">
              <ExercisePicture :src="exercises.find(exercise => exercise.id === line.exerciseId)?.imageUrl" :name="line.exerciseName" :description="line.exerciseDescription" />
              <small>{{ line.exerciseDescription }}</small>
            </div>
            <div class="p-col-12 p-md-4" v-if="!planning && line.trackingMode === ExerciseTrackingMode.CARDIO">
              <label class="p-d-block p-mb-2">Calories</label>
              <InputNumber v-model="line.calories" :min="0" />
              <div v-if="metricRecords(line, 'WORKOUT_CALORIES').length" class="field-record-context">
                <span v-for="record in metricRecords(line, 'WORKOUT_CALORIES')" :key="record.metric">{{ record.metricLabel }}: {{ formatRecordValue(record) }}</span>
              </div>
            </div>
            <div class="p-col-12 p-md-4" v-if="!planning && line.trackingMode === ExerciseTrackingMode.CARDIO">
              <label class="p-d-block p-mb-2">Average Heart Rate (bpm)</label>
              <InputNumber v-model="line.averageHeartRate" :min="0" :maxFractionDigits="0" />
              <div v-if="metricRecords(line, 'WORKOUT_AVERAGE_HEART_RATE').length" class="field-record-context">
                <span v-for="record in metricRecords(line, 'WORKOUT_AVERAGE_HEART_RATE')" :key="record.metric">{{ record.metricLabel }}: {{ formatRecordValue(record) }}</span>
              </div>
            </div>
          </div>
          <span class="error">{{ line.error }}</span>

          <div v-if="line.trackingMode" class="p-mt-3">
            <p v-if="line.stretchingUnit === 'BREATHS'" class="p-mt-0"><small>One breath means an inhale and exhale.</small></p>
            <div class="workout-line-header p-mb-2">
              <strong>
                {{ line.trackingMode === ExerciseTrackingMode.CARDIO ? 'Intervals' : 'Sets' }}
                <span v-if="line.trackingMode === ExerciseTrackingMode.CARDIO" class="interval-timing-summary">· Total {{ formatDuration(totalIntervalDuration(line)) }}</span>
              </strong>
            </div>

            <div v-for="(segment, segmentIndex) in line.segments" :key="segment.localId" class="segment-card p-mb-3">
              <div class="workout-line-header p-mb-2">
                <strong>
                  {{ line.trackingMode === ExerciseTrackingMode.CARDIO ? 'Interval' : 'Set' }} {{ segmentIndex + 1 }}
                  <span v-if="line.trackingMode === ExerciseTrackingMode.CARDIO" class="interval-timing-summary">· {{ formatDuration(intervalStartDuration(line, segmentIndex)) }}</span>
                </strong>
                <CompactAction icon="pi pi-trash" :aria-label="`Delete set ${segmentIndex + 1}`" @click="removeSegment(line, segmentIndex)" destructive />
              </div>
              <div class="p-grid">
                <div class="p-col-12 p-md-4" v-if="line.stretchingUnit === 'BREATHS'">
                  <label :for="`breaths-${segment.localId}`" class="p-d-block p-mb-2">Breaths</label>
                  <InputNumber :inputId="`breaths-${segment.localId}`" v-model="segment.breaths" :min="1" :maxFractionDigits="0" :useGrouping="false" />
                </div>
                <div class="p-col-12 p-md-4" v-if="line.trackingMode === ExerciseTrackingMode.REPS">
                  <label :for="`repetitions-${segment.localId}`" class="p-d-block p-mb-2">Repetitions</label>
                  <InputNumber :inputId="`repetitions-${segment.localId}`" v-model="segment.repetitions" :min="1" />
                  <div v-if="segmentMetricRecords(line, segment, 'WORKOUT_REPETITIONS').length" class="field-record-context">
                    <span v-for="record in segmentMetricRecords(line, segment, 'WORKOUT_REPETITIONS')" :key="record.metric">{{ record.metricLabel }}: {{ formatRecordValue(record) }}</span>
                  </div>
                </div>
                <div class="p-col-12 p-md-4" v-if="line.stretchingUnit !== 'BREATHS' && (line.trackingMode === ExerciseTrackingMode.SECONDS || line.trackingMode === ExerciseTrackingMode.CARDIO)">
                  <label :for="`minutes-${segment.localId}`" class="p-d-block p-mb-2">Minutes</label>
                  <InputNumber :inputId="`minutes-${segment.localId}`" v-model="segment.durationMinutes" :min="0" />
                  <div v-if="line.trackingMode === ExerciseTrackingMode.CARDIO && metricRecords(line, 'CARDIO_DURATION').length" class="field-record-context">
                    <span v-for="record in metricRecords(line, 'CARDIO_DURATION')" :key="record.metric">{{ record.metricLabel }}: {{ formatRecordValue(record) }}</span>
                  </div>
                </div>
                <div class="p-col-12 p-md-4" v-if="line.trackingMode === ExerciseTrackingMode.SECONDS && line.stretchingUnit !== 'BREATHS'">
                  <label :for="`seconds-${segment.localId}`" class="p-d-block p-mb-2">Seconds</label>
                  <Dropdown :inputId="`seconds-${segment.localId}`" v-model="segment.durationRemainder" :options="duration_second_options" optionLabel="label" optionValue="value" />
                  <div v-if="segmentMetricRecords(line, segment, 'WORKOUT_DURATION').length" class="field-record-context">
                    <span v-for="record in segmentMetricRecords(line, segment, 'WORKOUT_DURATION')" :key="record.metric">{{ record.metricLabel }}: {{ formatRecordValue(record) }}</span>
                  </div>
                </div>
                <div class="p-col-12 p-md-4" v-if="line.trackingMode !== ExerciseTrackingMode.CARDIO && line.exerciseType !== ExerciseType.STRETCHING">
                  <label :for="`weight-${segment.localId}`" class="p-d-block p-mb-2">Weight</label>
                  <InputNumber :inputId="`weight-${segment.localId}`" v-model="segment.weight" mode="decimal" :min="0" :minFractionDigits="0" :maxFractionDigits="2" />
                  <div v-if="loadMetricRecords(line).length" class="field-record-context">
                    <span v-for="record in loadMetricRecords(line)" :key="record.metric">{{ record.metricLabel }}: {{ formatRecordValue(record) }}</span>
                  </div>
                </div>
                <template v-if="line.trackingMode === ExerciseTrackingMode.CARDIO">
                  <div class="p-col-12 p-md-4" v-if="line.cardioMetric !== 'CADENCE_RPM'">
                    <label :for="`speedKph-${segment.localId}`" class="p-d-block p-mb-2">Speed (km/h)</label>
                    <InputNumber :inputId="`speedKph-${segment.localId}`" v-model="segment.speedKph" mode="decimal" :min="0" :minFractionDigits="0" :maxFractionDigits="2" />
                    <div v-if="metricRecords(line, 'CARDIO_SPEED').length" class="field-record-context">
                      <span v-for="record in metricRecords(line, 'CARDIO_SPEED')" :key="record.metric">{{ record.metricLabel }}: {{ formatRecordValue(record) }}</span>
                    </div>
                  </div>
                  <div class="p-col-12 p-md-4" v-else>
                    <label :for="`cadenceRpm-${segment.localId}`" class="p-d-block p-mb-2">Cadence (RPM)</label>
                    <InputNumber :inputId="`cadenceRpm-${segment.localId}`" v-model="segment.cadenceRpm" :min="0" :maxFractionDigits="0" :useGrouping="false" />
                    <div v-if="metricRecords(line, 'CARDIO_CADENCE').length" class="field-record-context">
                      <span v-for="record in metricRecords(line, 'CARDIO_CADENCE')" :key="record.metric">{{ record.metricLabel }}: {{ formatRecordValue(record) }}</span>
                    </div>
                  </div>
                  <div class="p-col-12 p-md-4">
                    <label :for="`distanceKm-${segment.localId}`" class="p-d-block p-mb-2">Distance (km)</label>
                    <InputNumber :inputId="`distanceKm-${segment.localId}`" v-model="segment.distanceKm" mode="decimal" :min="0" :minFractionDigits="0" :maxFractionDigits="2" />
                    <div v-if="metricRecords(line, 'CARDIO_DISTANCE').length" class="field-record-context">
                      <span v-for="record in metricRecords(line, 'CARDIO_DISTANCE')" :key="record.metric">{{ record.metricLabel }}: {{ formatRecordValue(record) }}</span>
                    </div>
                  </div>
                  <div class="p-col-12 p-md-4">
                    <label :for="`inclinePercent-${segment.localId}`" class="p-d-block p-mb-2">Incline (%)</label>
                    <InputNumber :inputId="`inclinePercent-${segment.localId}`" v-model="segment.inclinePercent" mode="decimal" :min="0" :minFractionDigits="0" :maxFractionDigits="2" />
                    <div v-if="metricRecords(line, 'CARDIO_INCLINE').length" class="field-record-context">
                      <span v-for="record in metricRecords(line, 'CARDIO_INCLINE')" :key="record.metric">{{ record.metricLabel }}: {{ formatRecordValue(record) }}</span>
                    </div>
                  </div>
                  <div class="p-col-12 p-md-4">
                    <label :for="`resistanceLevel-${segment.localId}`" class="p-d-block p-mb-2">Resistance</label>
                    <InputNumber :inputId="`resistanceLevel-${segment.localId}`" v-model="segment.resistanceLevel" :min="0" />
                    <div v-if="metricRecords(line, 'CARDIO_RESISTANCE').length" class="field-record-context">
                      <span v-for="record in metricRecords(line, 'CARDIO_RESISTANCE')" :key="record.metric">{{ record.metricLabel }}: {{ formatRecordValue(record) }}</span>
                    </div>
                  </div>
                </template>
              </div>
              <span class="error">{{ segment.error }}</span>
            </div>
            <div class="action-group">
              <Button icon="pi pi-plus" :label="line.trackingMode === ExerciseTrackingMode.CARDIO ? 'Add interval' : 'Add set'" class="p-button-outlined" @click="addSegment(line)" />
            </div>
          </div>
        </div>
      </div>
        </div>
      </section>
    </div>
    <div class="workout-add-line-actions action-group">
      <Button icon="pi pi-plus" label="Add warm-up" class="p-button-outlined" @click="addLine(ExerciseType.WARM_UP)" />
      <Button icon="pi pi-plus" label="Add exercise" class="p-button-outlined" @click="addLine(ExerciseType.TRAINING)" />
      <Button icon="pi pi-plus" label="Add stretching" class="p-button-outlined" @click="addLine(ExerciseType.STRETCHING)" />
      <Button icon="pi pi-plus" label="Add stretching set" class="p-button-outlined" @click="openStretchingPicker" />
    </div>
    <Dialog header="Add stretching set" appendTo="body" v-model:visible="stretchingPicker" :modal="true" :style="{width: 'min(560px, 96vw)'}">
      <p v-if="stretchingLoading" role="status">Loading stretching sets…</p>
      <p v-else-if="stretchingError" role="alert" class="error">{{ stretchingError }} <Button label="Retry" class="p-button-text" @click="openStretchingPicker" /></p>
      <div v-else class="p-fluid">
        <p v-if="!stretchingSets.length">No saved stretching sets yet. Create one in Workouts → Stretching.</p>
        <template v-else>
          <label for="workout-stretching-set" class="p-d-block p-mb-2">Stretching set</label>
          <Dropdown inputId="workout-stretching-set" v-model="selectedStretchingSet" :options="stretchingSets" optionLabel="name" optionValue="id" placeholder="Select a set" />
          <ol v-if="selectedSet" class="stretching-notice"><li v-for="entry in selectedSet.entries" :key="entry.exerciseId">{{ stretchName(entry) }}: {{ (entry.stretchingUnit === 'BREATHS' ? entry.breaths.map(breaths => `${breaths} ${breaths === 1 ? 'breath' : 'breaths'}`) : entry.durations.map(formatDuration)).join(' + ') }}</li></ol>
        </template>
      </div>
      <template #footer><div class="action-group"><Button label="Add" icon="pi pi-plus" :disabled="stretchingLoading || !!stretchingError || !selectedSet" @click="applyStretchingSet" /><Button label="Cancel" class="p-button-secondary" @click="stretchingPicker = false" /></div></template>
    </Dialog>
    </SaveFields>
    <template #footer>
      <div class="action-group"><Button :label="saving ? 'Saving…' : 'Save'" icon="pi pi-check" :loading="saving" :disabled="saving || timerRunning" :aria-busy="saving" @click="saveWorkout" />
      <Button :label="timerDraft ? 'Close' : 'Cancel'" :disabled="saving" icon="pi pi-times" @click="close_modal" class="p-button-secondary" />
    </div></template>
  </Dialog>
</template>

<script>
import WorkoutPhaseTimers from './WorkoutPhaseTimers.vue';
import {timerState, workoutPhases, openTimerEditor, closeTimerEditor, createTimerDraft, saveTimerForm, startPhase, stopPhase, setPhaseMinutes, discardTimer, resumeTimer} from '@/services/WorkoutTimerService';
import ExercisePicture from './ExercisePicture.vue';
import stretchingSetService from '../services/StretchingSetService';
import dayjs from 'dayjs';
import workoutService from '../services/WorkoutService';
import workoutPlanService from '../services/WorkoutPlanService';
import exerciseService from '../services/WorkoutExerciseService';
import Workout from "@/model/Workout";
import {ExerciseTrackingMode, ExerciseType, exerciseTypeLabel, trackingModeLabel, stretchingUnitOptions} from "@/model/WorkoutExercise";
import personalRecordService, {formatRecordValue} from "@/services/PersonalRecordService";

let nextLocalId = 1;

export default {
  name: "WorkoutEditor",
  components: {ExercisePicture, WorkoutPhaseTimers},
  emits: ["onSave", "onClose"],
  props: {
    show: Boolean,
    initial_date: Date,
    workout: Object,
    fixed_date: Boolean,
    planning: Boolean,
    resume_timer: Boolean,
  },
  data() {
    const locale = {
      firstDayOfWeek: 1,
      dayNames: ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"],
      dayNamesShort: ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"],
      dayNamesMin: ["Su","Mo","Tu","We","Th","Fr","Sa"],
      monthNames: [ "January","February","March","April","May","June","July","August","September","October","November","December" ],
      monthNamesShort: [ "Jan", "Feb", "Mar", "Apr", "May", "Jun","Jul", "Aug", "Sep", "Oct", "Nov", "Dec" ],
      today: 'Today',
      clear: 'Clear',
      dateFormat: 'mm/dd/yy',
      weekHeader: 'Wk'
    };
    return {
      ExerciseTrackingMode,
      ExerciseType,
      custom_locale: locale,
      max_date: new Date(),
      duration_second_options: [
        {label: '00', value: 0},
        {label: '05', value: 5},
        {label: '10', value: 10},
        {label: '15', value: 15},
        {label: '20', value: 20},
        {label: '25', value: 25},
        {label: '30', value: 30},
        {label: '35', value: 35},
        {label: '40', value: 40},
        {label: '45', value: 45},
        {label: '50', value: 50},
        {label: '55', value: 55}
      ],
      stretchingUnitOptions,
      durationPhases: workoutPhases,
      timerState,
      timerEditor: Symbol('workout-editor'),
      timerError: '',
      legacyTiming: false,
      stretchingSets: [],
      stretchingPicker: false,
      stretchingLoading: false,
      stretchingError: '',
      selectedStretchingSet: null,
      exercises: [],
      exercise_records: {},
      display_modal: this.show,
      saving: false,
      selected_preload_workout_id: null,
      preload_workouts: [],
      active_plan: null,
      planningPreloadsLoading: false,
      planningPreloadsError: '',
      collapsedExerciseGroups: {
        [ExerciseType.WARM_UP]: true,
        [ExerciseType.TRAINING]: true,
        [ExerciseType.STRETCHING]: true
      },
      workout_form: buildEmptyWorkoutForm(this.initial_date),
      workout_errors: {}
    };
  },
  computed: {
    timerDraft() { return this.timerState.editor === this.timerEditor ? this.timerState.draft : null; },
    timerRunning() { return !!this.timerDraft?.runningPhase; },
    sessionDuration() {
      if (!this.workout_form.breakdown) return this.workout_form.durationMinutes;
      const values = this.durationPhases.map(phase => this.workout_form[phase.key]);
      return values.some(value => value === null) ? null : values.reduce((sum, value) => sum + value, 0);
    },
    recordedCardioMinutes() {
      const seconds = this.workout_form.lines
          .filter(line => line.trackingMode === ExerciseTrackingMode.CARDIO)
          .reduce((total, line) => total + this.totalIntervalDuration(line), 0);
      return seconds ? Math.ceil(seconds / 60) : null;
    },
    selectedSet() { return this.stretchingSets.find(set => set.id === this.selectedStretchingSet); },
    exerciseGroups() {
      return [
        [ExerciseType.WARM_UP, 'Warm-up', 'pi pi-sun', 'warm-up', 'warm-ups'],
        [ExerciseType.TRAINING, 'Exercises', 'pi pi-bolt', 'exercise', 'exercises'],
        [ExerciseType.STRETCHING, 'Stretching', 'pi pi-arrows-v', 'stretch', 'stretches']
      ].map(([type, label, icon, countSingular, countPlural]) => ({
        type,
        label,
        icon,
        countSingular,
        countPlural,
        primary: type === ExerciseType.TRAINING,
        lines: this.workout_form.lines.map((line, lineIndex) => ({line, lineIndex})).filter(entry => entry.line.exerciseType === type)
      }));
    },
    is_editing() {
      return !!this.workout_form.id;
    },
    preload_options() {
      const formDate = dayjs(this.planning ? new Date() : this.workout_form.workoutDate).startOf('day');
      const workouts = this.preload_workouts
          .filter(workout => !dayjs(workout.workoutDate).isAfter(formDate, 'day'))
          .sort((left, right) => dayjs(right.workoutDate).valueOf() - dayjs(left.workoutDate).valueOf())
          .slice(0, this.planning ? 14 : 40)
          .map(workout => ({id: workout.id, label: this.preloadWorkoutLabel(workout)}));
      if (this.planning || !this.active_plan) return workouts;
      const day = this.active_plan.days.find(candidate => candidate.day === formDate.format('dddd').toUpperCase());
      return day?.lines.length ? [{id: '__plan__', label: `Active plan · ${day.lines.length} ${day.lines.length === 1 ? 'exercise' : 'exercises'}`}, ...workouts] : workouts;
    }
  },
  watch: {
    workout_form: {deep: true, handler() { if (this.timerDraft) saveTimerForm(this.workout_form); }},
    show(value) {
      this.display_modal = value;
      if (value) {
        this.load_form().catch(this.handleError);
      }
    },
    workout() {
      if (this.display_modal) {
        this.load_form().catch(this.handleError);
      }
    },
    initial_date() {
      if (this.display_modal && !this.workout) {
        this.load_form().catch(this.handleError);
      }
    },
    'workout_form.workoutDate'() {
      if (!this.planning && this.display_modal && !this.workout && this.workout_form.workoutDate) {
        this.loadPreloadWorkouts();
      }
    }
  },
  beforeUnmount() { closeTimerEditor(this.timerEditor); },
  async created() {
    if (this.show) await this.load_form().catch(this.handleError);
    else this.exercises = await exerciseService.get_all();
  },
  methods: {
    async openStretchingPicker() {
      this.stretchingPicker = true;
      this.stretchingLoading = true;
      this.stretchingError = '';
      this.selectedStretchingSet = null;
      try { [this.stretchingSets, this.exercises] = await Promise.all([stretchingSetService.get_all(), exerciseService.get_all()]); }
      catch (e) { this.stretchingError = e.message; }
      finally { this.stretchingLoading = false; }
    },
    stretchName(entry) { return this.exercises.find(exercise => exercise.id === entry.exerciseId).name; },
    applyStretchingSet() {
      const used = new Set(this.workout_form.lines.map(line => line.exerciseId));
      const skipped = this.selectedSet.entries.filter(entry => used.has(entry.exerciseId));
      const added = this.selectedSet.entries.filter(entry => !used.has(entry.exerciseId)).map(entry => {
        const exercise = this.exercises.find(exercise => exercise.id === entry.exerciseId);
        return {exerciseId: exercise.id, exerciseName: exercise.name, exerciseDescription: exercise.description, exerciseType: exercise.exerciseType, trackingMode: exercise.trackingMode, stretchingUnit: entry.stretchingUnit ?? 'SECONDS', sets: entry.stretchingUnit === 'BREATHS' ? entry.breaths.map(breaths => ({breaths})) : entry.durations.map(durationSeconds => ({durationSeconds}))};
      });
      this.insertLines(ExerciseType.STRETCHING, this.formFromWorkout({lines: added}, this.workout_form.workoutDate, '', null).lines);
      this.$toast.add({
        severity: added.length ? 'success' : 'info',
        summary: added.length ? 'Stretching set added' : 'No exercises added',
        detail: skipped.length ? `Already present: ${skipped.map(this.stretchName).join(', ')}. Existing holds were kept.` : undefined,
        life: 3000
      });
      this.stretchingPicker = false;
    },
    formatRecordValue,
    trackingModeLabel,
    toggleExerciseGroup(exerciseType) {
      this.collapsedExerciseGroups[exerciseType] = !this.collapsedExerciseGroups[exerciseType];
    },
    lineTitle(line, index) {
      const label = `${line.exerciseType === ExerciseType.TRAINING ? 'Exercise' : exerciseTypeLabel(line.exerciseType)} ${index + 1}`;
      return line.exerciseName ? `${label}: ${line.exerciseName}` : label;
    },
    formatDuration(seconds) {
      return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
    },
    totalIntervalDuration(line) {
      return line.segments.reduce((total, segment) => total + this.toDurationSeconds(segment), 0);
    },
    intervalStartDuration(line, segmentIndex) {
      return line.segments.slice(0, segmentIndex).reduce((total, segment) => total + this.toDurationSeconds(segment), 0);
    },
    async load_form() {
      this.collapsedExerciseGroups = {
        [ExerciseType.WARM_UP]: true,
        [ExerciseType.TRAINING]: true,
        [ExerciseType.STRETCHING]: true
      };
      this.selected_preload_workout_id = null;
      this.workout_errors = {};
      this.exercises = await exerciseService.get_all();
      if (this.resume_timer) {
        if (!await openTimerEditor(this.timerEditor)) {
          this.timerError = 'This timed workout is open in another tab. Close it there first.';
          return;
        }
        if (!this.timerState.draft) { this.close_modal(); return; }
        this.workout_form = JSON.parse(JSON.stringify(this.timerState.draft.form));
        this.workout_form.lines.forEach(line => { line.stretchingUnit ??= 'SECONDS'; });
        this.workout_form.workoutDate = new Date(this.workout_form.workoutDate);
        this.workout_form.startTime = this.workout_form.startTime ? new Date(this.workout_form.startTime) : null;
        nextLocalId = Math.max(nextLocalId, ...this.workout_form.lines.flatMap(line => [line.localId + 1, ...line.segments.map(segment => segment.localId + 1)]));
        this.legacyTiming = !!this.workout_form.legacyTiming;
        this.loadExerciseRecordContext();
        return;
      }
      if (this.planning && this.workout) {
        const catalog = new Map(this.exercises.map(exercise => [exercise.id, exercise]));
        this.workout.lines.forEach(line => catalog.set(line.exerciseId, {...catalog.get(line.exerciseId), id: line.exerciseId, name: line.exerciseName, description: line.exerciseDescription, exerciseType: line.exerciseType, trackingMode: line.trackingMode}));
        this.exercises = [...catalog.values()];
      }
      if (this.workout) {
        this.legacyTiming = this.workout.warmUpMinutes != null && this.workout.cardioMinutes == null;
        this.workout_form = this.formFromWorkout(this.workout, this.workout.workoutDate, this.workout.note || '', this.workout.id);
        this.loadExerciseRecordContext();
        if (this.planning) await this.loadPlanningPreloads();
        return;
      }
      this.workout_form = buildEmptyWorkoutForm(this.initial_date);
      this.addLine(ExerciseType.TRAINING);
      if (this.planning) await this.loadPlanningPreloads();
      else await Promise.all([this.loadPreloadWorkouts(), this.loadActivePlan()]);
    },
    formFromWorkout(workout, workoutDate, note, id) {
      return {
        id,
        workoutDate: new Date(workoutDate),
        note,
        startTime: workout.startTime ? new Date(`2000-01-01T${workout.startTime}`) : null,
        durationMinutes: workout.durationMinutes ?? null,
        warmUpMinutes: workout.warmUpMinutes ?? null,
        trainingMinutes: workout.trainingMinutes ?? null,
        stretchingMinutes: workout.stretchingMinutes ?? null,
        cardioMinutes: workout.warmUpMinutes != null ? (workout.cardioMinutes ?? 0) : null,
        breakdown: workout.warmUpMinutes != null,
        lines: workout.lines.map(line => {
          const exercise = this.exercises.find(candidate => candidate.id === line.exerciseId);
          const cardioMetric = line.cardioMetric || exercise?.cardioMetric || null;
          const exerciseType = line.exerciseType || exercise?.exerciseType || ExerciseType.TRAINING;
          return {
          localId: nextId(),
          collapsed: true,
          exerciseName: line.exerciseName,
          exerciseId: line.exerciseId,
          exerciseDescription: line.exerciseDescription,
          trackingMode: line.trackingMode,
          cardioMetric,
          stretchingUnit: line.stretchingUnit ?? 'SECONDS',
          exerciseType,
          calories: this.planning ? null : line.calories ?? null,
          averageHeartRate: this.planning ? null : line.averageHeartRate ?? null,
          segments: this.segmentsFromWorkoutLine({...line, cardioMetric, exerciseType}),
          error: null
          };
        })
      };
    },
    segmentsFromWorkoutLine(line) {
      const sourceSegments = line.trackingMode === ExerciseTrackingMode.CARDIO ? line.intervals : line.sets;
      return sourceSegments.map(segment => ({
        localId: nextId(),
        repetitions: segment.repetitions ?? null,
        breaths: segment.breaths ?? null,
        durationMinutes: segment.durationSeconds ? Math.floor(segment.durationSeconds / 60) : 0,
        durationRemainder: segment.durationSeconds ? segment.durationSeconds % 60 : 0,
        weight: segment.weight ?? null,
        speedKph: line.cardioMetric === 'CADENCE_RPM' ? null : segment.speedKph ?? null,
        cadenceRpm: line.cardioMetric === 'CADENCE_RPM' ? segment.cadenceRpm ?? segment.speedKph ?? null : segment.cadenceRpm ?? null,
        distanceKm: segment.distanceKm ?? null,
        inclinePercent: segment.inclinePercent ?? null,
        resistanceLevel: segment.resistanceLevel ?? null,
        error: null
      }));
    },
    preloadWorkout() {
      const targetDate = this.workout_form.workoutDate;
      if (this.selected_preload_workout_id === '__plan__') this.workout_form.lines = this.planLines(targetDate);
      else {
        const source = this.preload_workouts.find(workout => workout.id === this.selected_preload_workout_id);
        this.workout_form.lines = this.formFromWorkout(source, targetDate, '', null).lines;
      }
      this.collapsedExerciseGroups = {
        [ExerciseType.WARM_UP]: !this.workout_form.lines.some(line => line.exerciseType === ExerciseType.WARM_UP),
        [ExerciseType.TRAINING]: !this.workout_form.lines.some(line => line.exerciseType === ExerciseType.TRAINING),
        [ExerciseType.STRETCHING]: !this.workout_form.lines.some(line => line.exerciseType === ExerciseType.STRETCHING)
      };
      if (!this.planning) this.workout_form.note = '';
      this.workout_errors = {};
      this.loadExerciseRecordContext();
    },
    preloadWorkoutLabel(workout) {
      const lines = [...workout.lines].sort((left, right) => left.position - right.position);
      const firstExercise = lines.find(line => line.exerciseType === ExerciseType.TRAINING) || lines[0];
      const exerciseCount = lines.filter(line => line.exerciseType === ExerciseType.TRAINING).length;
      const date = `${dayjs(workout.workoutDate).format('ddd')}, ${workout.workoutDateFormat}`;
      const title = firstExercise ? `${date} - ${firstExercise.exerciseName}` : date;
      const sameDay = this.preload_workouts.filter(item => dayjs(item.workoutDate).isSame(workout.workoutDate, 'day'));
      const time = workout.startTime ? ` · ${workout.startTime.slice(0, 5)}` : '';
      const session = sameDay.length > 1 ? ` · Session ${sameDay.findIndex(item => item.id === workout.id) + 1}` : '';
      return `${title} (${exerciseCount} ${exerciseCount === 1 ? 'exercise' : 'exercises'})${time}${session}`;
    },
    async loadPlanningPreloads() {
      this.planningPreloadsLoading = true;
      this.planningPreloadsError = '';
      try { this.preload_workouts = await workoutService.get_preloads(new Date()); }
      catch (e) { this.planningPreloadsError = e.message; }
      finally { this.planningPreloadsLoading = false; }
    },
    async loadPreloadWorkouts() {
      this.preload_workouts = await workoutService.get_preloads(this.workout_form.workoutDate);
    },
    async loadActivePlan() { this.active_plan = await workoutPlanService.current(); },
    planLines(date) {
      const day = this.active_plan.days.find(candidate => candidate.day === dayjs(date).format('dddd').toUpperCase());
      return this.formFromWorkout({lines: day.lines.map(line => ({...line, sets: line.segments, intervals: line.segments}))}, date, '', null).lines;
    },
    addLine(exerciseType) {
      const line = {
        localId: nextId(),
        collapsed: false,
        exerciseName: '',
        exerciseId: null,
        exerciseDescription: '',
        trackingMode: null,
        stretchingUnit: exerciseType === ExerciseType.STRETCHING ? 'BREATHS' : 'SECONDS',
        exerciseType,
        calories: null,
        averageHeartRate: null,
        segments: [],
        error: null
      };
      this.insertLines(exerciseType, [line]);
    },
    insertLines(exerciseType, added) {
      const lines = this.workout_form.lines;
      const lastMatchingIndex = lines.findLastIndex(line => line.exerciseType === exerciseType);
      const types = [ExerciseType.WARM_UP, ExerciseType.TRAINING, ExerciseType.STRETCHING];
      const nextTypeIndex = lines.findIndex(line => types.indexOf(line.exerciseType) > types.indexOf(exerciseType));
      const index = lastMatchingIndex >= 0 ? lastMatchingIndex + 1 : nextTypeIndex >= 0 ? nextTypeIndex : lines.length;
      lines.splice(index, 0, ...added);
      this.collapsedExerciseGroups[exerciseType] = false;
    },
    removeLine(index) {
      this.workout_form.lines.splice(index, 1);
    },
    moveLine(index, offset) {
      if (this.workout_form.lines[index].exerciseType !== this.workout_form.lines[index + offset].exerciseType) return;
      const [line] = this.workout_form.lines.splice(index, 1);
      this.workout_form.lines.splice(index + offset, 0, line);
    },
    async onExerciseChanged(line) {
      const exercise = this.exercises.find(item => item.id === line.exerciseId);
      line.stretchingUnit = exercise?.exerciseType === ExerciseType.STRETCHING ? 'BREATHS' : 'SECONDS';
      line.exerciseName = exercise?.name || '';
      line.trackingMode = exercise?.trackingMode || null;
      line.cardioMetric = exercise?.cardioMetric || null;
      line.exerciseType = exercise?.exerciseType || line.exerciseType;
      line.exerciseDescription = exercise?.description || '';
      line.calories = line.trackingMode === ExerciseTrackingMode.CARDIO ? line.calories : null;
      line.averageHeartRate = line.trackingMode === ExerciseTrackingMode.CARDIO ? line.averageHeartRate : null;
      line.segments = [];
      if (line.trackingMode) {
        this.addSegment(line);
        await this.ensureExerciseRecords(line.exerciseId);
      }
    },
    async loadExerciseRecordContext() {
      await Promise.all(this.workout_form.lines.map(line => this.ensureExerciseRecords(line.exerciseId)));
    },
    async ensureExerciseRecords(exerciseId) {
      if (this.planning) return;
      const exercise = this.exercises.find(item => item.id === exerciseId);
      if (exercise?.exerciseType !== ExerciseType.TRAINING) {
        return;
      }
      if (this.exercise_records[exerciseId] === undefined) {
        const records = await personalRecordService.getCurrent({domain: 'WORKOUT', exerciseId});
        this.exercise_records = {...this.exercise_records, [exerciseId]: records};
      }
    },
    recordsForLine(line) {
      return (this.exercise_records[line.exerciseId] || []).filter(record => record.subject.type !== 'EXERCISE_TOTAL');
    },
    metricRecords(line, metric) {
      return this.recordsForLine(line).filter(record => record.metric === metric || record.metric === `${metric}_MINIMUM`);
    },
    loadMetricRecords(line) {
      return this.metricRecords(line, 'WORKOUT_HEAVIEST_LOAD').filter(record => Number(record.value) > 0);
    },
    segmentMetricRecords(line, segment, metric) {
      const load = Number(segment.weight || 0).toFixed(2);
      return this.metricRecords(line, metric).filter(record => record.qualifier && Number(record.qualifier.loadKg).toFixed(2) === load);
    },
    changeStretchingUnit(line) {
      line.segments.forEach(segment => { segment.durationMinutes = 0; segment.durationRemainder = 0; segment.breaths = null; segment.error = null; });
    },
    addSegment(line) {
      const previous = line.segments[line.segments.length - 1];
      line.segments.push({
        localId: nextId(),
        repetitions: previous?.repetitions ?? null,
        breaths: previous?.breaths ?? null,
        durationMinutes: previous?.durationMinutes ?? 0,
        durationRemainder: line.trackingMode === ExerciseTrackingMode.CARDIO ? 0 : (previous?.durationRemainder ?? 0),
        weight: line.trackingMode === ExerciseTrackingMode.CARDIO || line.exerciseType === ExerciseType.STRETCHING ? null : previous?.weight ?? null,
        speedKph: previous?.speedKph ?? null,
        cadenceRpm: previous?.cadenceRpm ?? null,
        distanceKm: previous?.distanceKm ?? null,
        inclinePercent: previous?.inclinePercent ?? null,
        resistanceLevel: previous?.resistanceLevel ?? null,
        error: null
      });
    },
    removeSegment(line, index) {
      line.segments.splice(index, 1);
    },
    availableExercises(line) {
      const usedIds = new Set(this.workout_form.lines.map(item => item.exerciseId).filter(Boolean));
      if (line.exerciseId) {
        usedIds.delete(line.exerciseId);
      }
      return this.exercises.filter(exercise => exercise.exerciseType === line.exerciseType && !usedIds.has(exercise.id));
    },
    async startTimer(key) {
      this.timerError = '';
      try {
        if (this.workout_form.breakdown && this.durationPhases.some(phase => !Number.isInteger(this.workout_form[phase.key]) || this.workout_form[phase.key] < 0)) {
          this.timerError = 'Enter all four phase durations, using zero for skipped phases.';
          return;
        }
        if (!this.timerDraft) {
          if (this.timerState.draft) { this.close_modal(); resumeTimer(); return; }
          if (this.workout_form.durationMinutes != null && (!this.workout_form.breakdown || this.sessionDuration !== this.workout_form.durationMinutes)) {
            this.timerError = 'Split the existing total into phase minutes before starting a timer; keep the same total.';
            return;
          }
          if (!await openTimerEditor(this.timerEditor)) {
            this.timerError = 'This timed workout is open in another tab. Close it there first.';
            return;
          }
          if (this.timerState.draft) { this.close_modal(); resumeTimer(); return; }
          if (!this.workout_form.id) {
            this.workout_form.workoutDate = new Date();
            this.workout_form.startTime = new Date();
          }
          this.workout_form.breakdown = true;
          this.durationPhases.forEach(phase => { this.workout_form[phase.key] ??= 0; });
          this.workout_form.legacyTiming = this.legacyTiming;
          createTimerDraft(this.workout_form);
        }
        startPhase(key);
        this.syncTimerMinutes();
      } catch (error) { this.timerError = error.message; }
    },
    stopTimer() { stopPhase(); this.syncTimerMinutes(); },
    syncTimerMinutes() {
      this.durationPhases.forEach(({key}) => { this.workout_form[key] = Math.ceil(this.timerDraft.elapsed[key] / 60000); });
    },
    changePhaseMinutes(key, value) {
      this.workout_form[key] = value;
      this.workout_errors.durationMinutes = null;
      if (this.timerDraft && Number.isInteger(value) && value >= 0) setPhaseMinutes(key, value);
    },
    toggleDurationBreakdown() {
      this.workout_errors.durationMinutes = null;
      if (this.workout_form.breakdown) this.durationPhases.forEach(phase => { this.workout_form[phase.key] = 0; });
      if (!this.workout_form.breakdown) {
        const values = this.durationPhases.map(phase => this.workout_form[phase.key]);
        this.workout_form.durationMinutes = values.some(value => value === null) ? null : values.reduce((sum, value) => sum + value, 0);
        this.durationPhases.forEach(phase => { this.workout_form[phase.key] = null; });
      }
    },
    validateWorkoutForm() {
      const errors = {};
      if (!this.planning) {
        if (this.workout_form.breakdown && this.durationPhases.some(phase => !Number.isInteger(this.workout_form[phase.key]) || this.workout_form[phase.key] < 0)) {
          errors.durationMinutes = 'Enter all four duration values, using zero for phases you skipped';
        } else if ((this.workout_form.breakdown || this.sessionDuration !== null) && (!Number.isInteger(this.sessionDuration) || this.sessionDuration <= 0 || this.sessionDuration > 2147483647)) {
          errors.durationMinutes = 'Duration must be a positive whole number of minutes';
        }
      }
      if (!this.planning && !this.workout_form.workoutDate) {
        errors.workoutDate = 'Date is required';
      }
      if ((this.workout_form.note || '').length > 500) {
        errors.note = 'Note cannot be longer than 500 characters';
      }
      if (this.workout_form.lines.length === 0) {
        errors.lines = 'Add at least one exercise';
      }
      const usedIds = new Set();
      for (const line of this.workout_form.lines) {
        line.error = null;
        if (!line.exerciseId) {
          line.error = 'Exercise is required';
          continue;
        }
        if (usedIds.has(line.exerciseId)) {
          line.error = 'Exercise cannot be repeated in the same workout';
        }
        usedIds.add(line.exerciseId);
        if (line.segments.length === 0) {
          line.error = line.trackingMode === ExerciseTrackingMode.CARDIO ? 'Add at least one interval' : 'Add at least one set';
        }
        for (const segment of line.segments) {
          segment.error = this.validateSegment(line, segment);
        }
      }
      for (const line of this.workout_form.lines) {
        if (line.error || line.segments.some(segment => segment.error)) {
          line.collapsed = false;
        }
      }
      this.workout_errors = errors;
      return Object.keys(errors).length === 0
          && this.workout_form.lines.every(line => !line.error)
          && this.workout_form.lines.every(line => line.segments.every(segment => !segment.error));
    },
    validateSegment(line, segment) {
      if (line.stretchingUnit === 'BREATHS') return Number.isInteger(segment.breaths) && segment.breaths > 0 ? null : 'Enter a positive breath count';
      const duration = this.toDurationSeconds(segment);
      if (line.trackingMode === ExerciseTrackingMode.REPS) {
        if (!segment.repetitions || segment.repetitions < 1) {
          return 'Repetitions are required';
        }
      }
      if (line.trackingMode === ExerciseTrackingMode.SECONDS || line.trackingMode === ExerciseTrackingMode.CARDIO) {
        if (duration <= 0) {
          return 'Duration is required';
        }
      }
      return null;
    },
    toDurationSeconds(segment) {
      return (segment.durationMinutes || 0) * 60 + (segment.durationRemainder || 0);
    },
    buildWorkoutPayload() {
      const workout = new Workout();
      workout.id = this.workout_form.id;
      workout.workoutDate = this.workout_form.workoutDate;
      workout.note = this.workout_form.note || null;
      if (!this.planning) {
        workout.startTime = this.workout_form.startTime ? dayjs(this.workout_form.startTime).format('HH:mm') : null;
        let {warmUpMinutes, trainingMinutes, stretchingMinutes, cardioMinutes} = this.workout_form;
        if (this.recordedCardioMinutes !== null) {
          if (!this.workout_form.breakdown) {
            warmUpMinutes = 0;
            trainingMinutes = Math.max(0, (this.workout_form.durationMinutes || 0) - this.recordedCardioMinutes);
            stretchingMinutes = 0;
          }
          cardioMinutes = Math.max(cardioMinutes || 0, this.recordedCardioMinutes);
        }
        workout.durationMinutes = warmUpMinutes === null ? this.sessionDuration : warmUpMinutes + trainingMinutes + stretchingMinutes + (cardioMinutes ?? 0);
        Object.assign(workout, {warmUpMinutes, trainingMinutes, stretchingMinutes, cardioMinutes});
      }
      workout.lines = this.workout_form.lines.map(line => ({
        exerciseId: line.exerciseId,
        exerciseType: line.exerciseType,
        stretchingUnit: line.stretchingUnit ?? 'SECONDS',
        calories: line.trackingMode === ExerciseTrackingMode.CARDIO ? line.calories : null,
        averageHeartRate: line.trackingMode === ExerciseTrackingMode.CARDIO ? line.averageHeartRate : null,
        segments: line.segments.map(segment => ({
          repetitions: line.trackingMode === ExerciseTrackingMode.REPS ? segment.repetitions : null,
          durationSeconds: line.trackingMode === ExerciseTrackingMode.REPS || line.stretchingUnit === 'BREATHS' ? null : this.toDurationSeconds(segment),
          breaths: line.stretchingUnit === 'BREATHS' ? segment.breaths : null,
          weight: line.trackingMode === ExerciseTrackingMode.CARDIO || line.exerciseType === ExerciseType.STRETCHING ? null : segment.weight,
          speedKph: line.trackingMode === ExerciseTrackingMode.CARDIO ? segment.speedKph : null,
          cadenceRpm: line.trackingMode === ExerciseTrackingMode.CARDIO ? segment.cadenceRpm : null,
          distanceKm: line.trackingMode === ExerciseTrackingMode.CARDIO ? segment.distanceKm : null,
          inclinePercent: line.trackingMode === ExerciseTrackingMode.CARDIO ? segment.inclinePercent : null,
          resistanceLevel: line.trackingMode === ExerciseTrackingMode.CARDIO ? segment.resistanceLevel : null
        }))
      }));
      return workout.toObject();
    },
    async saveWorkout() {
      if (this.saving || this.timerRunning) return;
      this.saving = true;
      try {
        if (!this.validateWorkoutForm()) {
          return;
        }
        if (this.planning) {
          const payload = this.buildWorkoutPayload();
          const lines = payload.lines.map((line, index) => ({...line, exerciseName: this.workout_form.lines[index].exerciseName, exerciseDescription: this.workout_form.lines[index].exerciseDescription, trackingMode: this.workout_form.lines[index].trackingMode}));
          this.$emit('onSave', {note: payload.note, lines});
          this.close_modal();
          return;
        }
        await workoutService.save(this.buildWorkoutPayload())
            .then(() => {
              if (this.timerDraft) discardTimer();
              this.$toast.add({severity:'success', summary: 'Workout saved', life: 3000});
              this.close_modal();
              this.$emit('onSave');
            })
            .catch(e => {
              this.handleError(e);
            });
      } finally {
        this.saving = false;
      }
    },
    close_modal() {
      if (this.timerDraft) saveTimerForm(this.workout_form);
      closeTimerEditor(this.timerEditor);
      this.display_modal = false;
      this.workout_form = buildEmptyWorkoutForm(this.initial_date);
      this.selected_preload_workout_id = null;
      this.workout_errors = {};
      this.$emit('onClose');
    },
    handleError(e) {
      this.$log.error(e);
      this.$toast.add({severity:'error', summary: 'Failed', detail: e.message || e, life: 4000});
    }
  }
}

function nextId() {
  nextLocalId += 1;
  return nextLocalId;
}

function buildEmptyWorkoutForm(initialDate) {
  return {
    workoutDate: initialDate ? new Date(initialDate) : new Date(),
    note: '',
    startTime: null,
    durationMinutes: null,
    warmUpMinutes: null,
    trainingMinutes: null,
    stretchingMinutes: null,
    cardioMinutes: null,
    breakdown: false,
    lines: []
  };
}
</script>

<style scoped>
.workout-breakdown-toggle { display: flex; align-items: center; gap: .5rem; }

.workout-preload { width: 100%; }
.workout-preload :deep(.p-dropdown-label), .workout-preload-option { white-space: normal; overflow-wrap: anywhere; }
.workout-exercise-picker { width: 100%; }
.workout-exercise-option { display: flex; align-items: center; gap: .5rem; min-width: 0; white-space: normal; }
.workout-exercise-option img { width: 64px; height: 64px; flex-shrink: 0; object-fit: contain; border: 1px solid #d6d6d6; border-radius: 4px; background: white; padding: 2px; }
.workout-exercise-option > span { overflow-wrap: anywhere; }

.workout-exercise-group { display: block; margin-bottom: 1.5rem; }
.workout-exercise-group-heading { margin: 0 0 .75rem; font-size: 1rem; }
.workout-exercise-group-toggle {
  display: flex;
  align-items: center;
  gap: .75rem;
  width: 100%;
  min-width: 0;
  padding: .6rem .75rem;
  color: inherit;
  text-align: left;
  border: 1px solid #d6d6d6;
  border-radius: 999px;
  background: #f8fafc;
}
.workout-exercise-group-toggle:focus-visible { outline: 2px solid var(--primary-color); outline-offset: 3px; }
.workout-exercise-group-identity { display: flex; align-items: center; gap: .6rem; min-width: 0; flex: 1; }
.workout-exercise-group-icon { display: grid; flex: 0 0 2rem; width: 2rem; height: 2rem; place-items: center; color: #4b5563; border-radius: 50%; background: #e5e7eb; }
.workout-exercise-group-label { display: grid; min-width: 0; }
.workout-exercise-group-label small { color: #6b7280; font-size: .7rem; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; }
.workout-exercise-group-label strong { overflow-wrap: anywhere; }
.workout-exercise-group-count { flex: 0 0 auto; min-width: 2rem; padding: .25rem .5rem; color: #374151; font-size: .85rem; font-weight: 700; line-height: 1.2; text-align: center; border-radius: 999px; background: #e5e7eb; }
.workout-exercise-group-chevron { flex: 0 0 auto; color: #6b7280; }
.workout-exercise-group--primary .workout-exercise-group-toggle { min-height: 4rem; padding: .7rem .85rem; color: #075985; border: 1px solid #7dd3fc; border-left: 4px solid #007ad9; border-radius: 8px; background: #e0f2fe; }
.workout-exercise-group--primary .workout-exercise-group-icon { color: white; background: #007ad9; }
.workout-exercise-group--primary .workout-exercise-group-label small, .workout-exercise-group--primary .workout-exercise-group-chevron { color: #0369a1; }
.workout-exercise-group--primary .workout-exercise-group-count { color: #075985; border: 1px solid #7dd3fc; background: white; }
.workout-exercise-group-lines > :last-child { margin-bottom: 0 !important; }

.stretching-notice { overflow-wrap: anywhere; }
.workout-line-card {
  border: 1px solid #d6d6d6;
  border-radius: 6px;
  padding: 16px;
}
.segment-card {
  border: 1px solid #ececec;
  border-radius: 6px;
  padding: 12px;
  background: #fafafa;
}
.field-record-context {
  color: #075f46;
  display: grid;
  gap: 0.2rem;
  margin-top: 0.4rem;
  font-size: 0.85rem;
  font-weight: 600;
}
.workout-line-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.workout-line-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
  text-align: left;
  color: inherit;
}
.workout-line-toggle strong {
  overflow-wrap: anywhere;
}
.workout-line-toggle:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: 3px;
}
.workout-line-content {
  margin-top: 12px;
}
.interval-timing-summary {
  white-space: nowrap;
}

.workout-line-actions {
  flex-shrink: 0;
}
.workout-textarea {
  width: 100%;
  resize: vertical;
}
.workout-add-line-actions {
  align-items: stretch;
  flex-direction: column;
}
.workout-add-line-actions .p-button {
  justify-content: flex-start;
  width: 100%;
}
@media (max-width: 575px) {
  .workout-line-card {
    padding: 12px;
  }
  .segment-card {
    padding: 10px;
  }
}
</style>
