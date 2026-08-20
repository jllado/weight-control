<template>
  <Dialog id="workout-form" appendTo="body" header="Workout" v-model:visible="display_modal" :closeOnEscape="false" :closable="false" :modal="true" :style="{width: 'min(960px, 96vw)'}">
    <br>
    <div class="p-fluid">
      <div v-if="!fixed_date" class="p-field p-mb-4">
        <span class="p-float-label">
          <Calendar v-model="workout_form.workoutDate" dateFormat="dd/mm/yy" appendTo="body" v-model:locale="custom_locale" :maxDate="max_date" />
          <label>Date</label>
        </span>
        <span class="error">{{ workout_errors.workoutDate }}</span>
      </div>
      <div v-if="!is_editing && preload_options.length" class="p-field p-mb-4">
        <label class="p-d-block p-mb-2">Preload workout</label>
        <Dropdown v-model="selected_preload_workout_id" :options="preload_options" optionLabel="label" optionValue="id" placeholder="Start from scratch" @change="preloadWorkout" />
      </div>
      <div class="p-field p-mb-4">
        <label class="p-d-block p-mb-2">Note</label>
        <textarea v-model="workout_form.note" rows="3" class="p-inputtext p-component workout-textarea" maxlength="500"></textarea>
        <span class="error">{{ workout_errors.note }}</span>
      </div>

      <div v-for="(line, lineIndex) in workout_form.lines" :key="line.localId" class="workout-line-card p-mb-4">
        <div class="workout-line-header">
          <strong>Exercise {{ lineIndex + 1 }}</strong>
          <div class="workout-line-actions">
            <Button icon="pi pi-arrow-up" :aria-label="`Move exercise ${lineIndex + 1} up`" class="p-button-rounded p-button-text p-button-secondary" :disabled="lineIndex === 0" @click="moveLine(lineIndex, -1)" />
            <Button icon="pi pi-arrow-down" :aria-label="`Move exercise ${lineIndex + 1} down`" class="p-button-rounded p-button-text p-button-secondary" :disabled="lineIndex === workout_form.lines.length - 1" @click="moveLine(lineIndex, 1)" />
            <Button icon="pi pi-trash" class="p-button-rounded p-button-text p-button-danger" @click="removeLine(lineIndex)" />
          </div>
        </div>
        <div class="p-grid">
          <div class="p-col-12 p-md-6">
            <label class="p-d-block p-mb-2">Exercise</label>
            <Dropdown v-model="line.exerciseId" :options="availableExercises(line)" optionLabel="name" optionValue="id" placeholder="Select exercise" @change="onExerciseChanged(line)" />
          </div>
          <div class="p-col-12 p-md-6">
            <label class="p-d-block p-mb-2">Mode</label>
            <InputText :value="line.trackingMode ? trackingModeLabel(line.trackingMode) : ''" readonly />
          </div>
          <div class="p-col-12" v-if="line.exerciseDescription">
            <small>{{ line.exerciseDescription }}</small>
          </div>
          <div class="p-col-12 p-md-4" v-if="line.trackingMode === ExerciseTrackingMode.CARDIO">
            <label class="p-d-block p-mb-2">Calories</label>
            <InputNumber v-model="line.calories" :min="0" />
          </div>
          <div class="p-col-12 p-md-4" v-if="line.trackingMode === ExerciseTrackingMode.CARDIO">
            <label class="p-d-block p-mb-2">Average Heart Rate (bpm)</label>
            <InputNumber v-model="line.averageHeartRate" :min="0" :maxFractionDigits="0" />
          </div>
        </div>
        <span class="error">{{ line.error }}</span>

        <div v-if="line.trackingMode" class="p-mt-3">
          <div class="workout-line-header p-mb-2">
            <strong>{{ line.trackingMode === ExerciseTrackingMode.CARDIO ? 'Intervals' : 'Sets' }}</strong>
            <Button icon="pi pi-plus" :label="line.trackingMode === ExerciseTrackingMode.CARDIO ? 'Add interval' : 'Add set'" @click="addSegment(line)" />
          </div>

          <div v-for="(segment, segmentIndex) in line.segments" :key="segment.localId" class="segment-card p-mb-3">
            <div class="workout-line-header p-mb-2">
              <strong>{{ line.trackingMode === ExerciseTrackingMode.CARDIO ? 'Interval' : 'Set' }} {{ segmentIndex + 1 }}</strong>
              <Button icon="pi pi-trash" class="p-button-rounded p-button-text p-button-danger" @click="removeSegment(line, segmentIndex)" />
            </div>
            <div class="p-grid">
              <div class="p-col-12 p-md-4" v-if="line.trackingMode === ExerciseTrackingMode.REPS">
                <label class="p-d-block p-mb-2">Repetitions</label>
                <InputNumber v-model="segment.repetitions" :min="1" />
              </div>
              <div class="p-col-12 p-md-4" v-if="line.trackingMode === ExerciseTrackingMode.SECONDS || line.trackingMode === ExerciseTrackingMode.CARDIO">
                <label class="p-d-block p-mb-2">Minutes</label>
                <InputNumber v-model="segment.durationMinutes" :min="0" />
              </div>
              <div class="p-col-12 p-md-4" v-if="line.trackingMode === ExerciseTrackingMode.SECONDS">
                <label class="p-d-block p-mb-2">Seconds</label>
                <Dropdown v-model="segment.durationRemainder" :options="duration_second_options" optionLabel="label" optionValue="value" />
              </div>
              <div class="p-col-12 p-md-4" v-if="line.trackingMode !== ExerciseTrackingMode.CARDIO">
                <label class="p-d-block p-mb-2">Weight</label>
                <InputNumber v-model="segment.weight" mode="decimal" :min="0" :minFractionDigits="0" :maxFractionDigits="2" />
              </div>
              <template v-if="line.trackingMode === ExerciseTrackingMode.CARDIO">
                <div class="p-col-12 p-md-4">
                  <label class="p-d-block p-mb-2">Speed (km/h)</label>
                  <InputNumber v-model="segment.speedKph" mode="decimal" :min="0" :minFractionDigits="0" :maxFractionDigits="2" />
                </div>
                <div class="p-col-12 p-md-4">
                  <label class="p-d-block p-mb-2">Distance (km)</label>
                  <InputNumber v-model="segment.distanceKm" mode="decimal" :min="0" :minFractionDigits="0" :maxFractionDigits="2" />
                </div>
                <div class="p-col-12 p-md-4">
                  <label class="p-d-block p-mb-2">Incline (%)</label>
                  <InputNumber v-model="segment.inclinePercent" mode="decimal" :min="0" :minFractionDigits="0" :maxFractionDigits="2" />
                </div>
                <div class="p-col-12 p-md-4">
                  <label class="p-d-block p-mb-2">Resistance</label>
                  <InputNumber v-model="segment.resistanceLevel" :min="0" />
                </div>
              </template>
            </div>
            <span class="error">{{ segment.error }}</span>
          </div>
        </div>
      </div>
      <Button icon="pi pi-plus" label="Add exercise" class="p-button-secondary" @click="addLine" />
    </div>
    <template #footer>
      <Button label="Save" icon="pi pi-check" @click="saveWorkout" />
      <Button label="Cancel" icon="pi pi-times" @click="close_modal" class="p-button-secondary" />
    </template>
  </Dialog>
</template>

<script>
import dayjs from 'dayjs';
import workoutService from '../services/WorkoutService';
import exerciseService from '../services/WorkoutExerciseService';
import Workout from "@/model/Workout";
import {ExerciseTrackingMode, trackingModeLabel} from "@/model/WorkoutExercise";

let nextLocalId = 1;

export default {
  name: "WorkoutForm",
  emits: ["onSave", "onClose"],
  props: {
    show: Boolean,
    initial_date: Date,
    workout: Object,
    fixed_date: Boolean,
    workouts: {
      type: Array,
      default: () => []
    }
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
      exercises: [],
      display_modal: this.show,
      selected_preload_workout_id: null,
      workout_form: buildEmptyWorkoutForm(this.initial_date),
      workout_errors: {}
    };
  },
  computed: {
    is_editing() {
      return !!this.workout;
    },
    preload_options() {
      const formDate = dayjs(this.workout_form.workoutDate).startOf('day');
      return this.workouts
          .filter(workout => dayjs(workout.workoutDate).isBefore(formDate, 'day'))
          .sort((left, right) => dayjs(right.workoutDate).valueOf() - dayjs(left.workoutDate).valueOf())
          .slice(0, 10)
          .map(workout => ({
            id: workout.id,
            label: `${workout.workoutDateFormat} - ${this.firstExerciseName(workout)}`
          }));
    }
  },
  watch: {
    show(value) {
      this.display_modal = value;
      if (value) {
        this.load_form();
      }
    },
    workout() {
      if (this.display_modal) {
        this.load_form();
      }
    },
    initial_date() {
      if (this.display_modal && !this.workout) {
        this.load_form();
      }
    }
  },
  async created() {
    this.exercises = await exerciseService.get_all();
  },
  methods: {
    trackingModeLabel,
    load_form() {
      this.selected_preload_workout_id = null;
      this.workout_errors = {};
      if (this.workout) {
        this.workout_form = this.formFromWorkout(this.workout, this.workout.workoutDate, this.workout.note || '', this.workout.id);
        return;
      }
      this.workout_form = buildEmptyWorkoutForm(this.initial_date);
      this.addLine();
    },
    formFromWorkout(workout, workoutDate, note, id) {
      return {
        id,
        workoutDate: new Date(workoutDate),
        note,
        lines: workout.lines.map(line => ({
          localId: nextId(),
          exerciseId: line.exerciseId,
          exerciseDescription: line.exerciseDescription,
          trackingMode: line.trackingMode,
          calories: line.calories ?? null,
          averageHeartRate: line.averageHeartRate ?? null,
          segments: this.segmentsFromWorkoutLine(line),
          error: null
        }))
      };
    },
    segmentsFromWorkoutLine(line) {
      const sourceSegments = line.trackingMode === ExerciseTrackingMode.CARDIO ? line.intervals : line.sets;
      return sourceSegments.map(segment => ({
        localId: nextId(),
        repetitions: segment.repetitions ?? null,
        durationMinutes: segment.durationSeconds ? Math.floor(segment.durationSeconds / 60) : 0,
        durationRemainder: segment.durationSeconds ? segment.durationSeconds % 60 : 0,
        weight: segment.weight ?? null,
        speedKph: segment.speedKph ?? null,
        distanceKm: segment.distanceKm ?? null,
        inclinePercent: segment.inclinePercent ?? null,
        resistanceLevel: segment.resistanceLevel ?? null,
        error: null
      }));
    },
    preloadWorkout() {
      const source = this.workouts.find(workout => workout.id === this.selected_preload_workout_id);
      const targetDate = this.workout_form.workoutDate;
      this.workout_form = this.formFromWorkout(source, targetDate, '', null);
    },
    firstExerciseName(workout) {
      return [...workout.lines].sort((left, right) => left.position - right.position)[0].exerciseName;
    },
    addLine() {
      this.workout_form.lines.push({
        localId: nextId(),
        exerciseId: null,
        exerciseDescription: '',
        trackingMode: null,
        calories: null,
        averageHeartRate: null,
        segments: [],
        error: null
      });
    },
    removeLine(index) {
      this.workout_form.lines.splice(index, 1);
    },
    moveLine(index, offset) {
      const [line] = this.workout_form.lines.splice(index, 1);
      this.workout_form.lines.splice(index + offset, 0, line);
    },
    onExerciseChanged(line) {
      const exercise = this.exercises.find(item => item.id === line.exerciseId);
      line.trackingMode = exercise?.trackingMode || null;
      line.exerciseDescription = exercise?.description || '';
      line.calories = line.trackingMode === ExerciseTrackingMode.CARDIO ? line.calories : null;
      line.averageHeartRate = line.trackingMode === ExerciseTrackingMode.CARDIO ? line.averageHeartRate : null;
      line.segments = [];
      if (line.trackingMode) {
        this.addSegment(line);
      }
    },
    addSegment(line) {
      const previous = line.segments[line.segments.length - 1];
      line.segments.push({
        localId: nextId(),
        repetitions: previous?.repetitions ?? null,
        durationMinutes: previous?.durationMinutes ?? 0,
        durationRemainder: line.trackingMode === ExerciseTrackingMode.CARDIO ? 0 : (previous?.durationRemainder ?? 0),
        weight: line.trackingMode === ExerciseTrackingMode.CARDIO ? null : previous?.weight ?? null,
        speedKph: previous?.speedKph ?? null,
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
      return this.exercises.filter(exercise => !usedIds.has(exercise.id));
    },
    validateWorkoutForm() {
      const errors = {};
      if (!this.workout_form.workoutDate) {
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
      this.workout_errors = errors;
      return Object.keys(errors).length === 0
          && this.workout_form.lines.every(line => !line.error)
          && this.workout_form.lines.every(line => line.segments.every(segment => !segment.error));
    },
    validateSegment(line, segment) {
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
      workout.lines = this.workout_form.lines.map(line => ({
        exerciseId: line.exerciseId,
        calories: line.trackingMode === ExerciseTrackingMode.CARDIO ? line.calories : null,
        averageHeartRate: line.trackingMode === ExerciseTrackingMode.CARDIO ? line.averageHeartRate : null,
        segments: line.segments.map(segment => ({
          repetitions: line.trackingMode === ExerciseTrackingMode.REPS ? segment.repetitions : null,
          durationSeconds: line.trackingMode === ExerciseTrackingMode.REPS ? null : this.toDurationSeconds(segment),
          weight: line.trackingMode === ExerciseTrackingMode.CARDIO ? null : segment.weight,
          speedKph: line.trackingMode === ExerciseTrackingMode.CARDIO ? segment.speedKph : null,
          distanceKm: line.trackingMode === ExerciseTrackingMode.CARDIO ? segment.distanceKm : null,
          inclinePercent: line.trackingMode === ExerciseTrackingMode.CARDIO ? segment.inclinePercent : null,
          resistanceLevel: line.trackingMode === ExerciseTrackingMode.CARDIO ? segment.resistanceLevel : null
        }))
      }));
      return workout.toObject();
    },
    async saveWorkout() {
      if (!this.validateWorkoutForm()) {
        return;
      }
      await workoutService.save(this.buildWorkoutPayload())
          .then(() => {
            this.$toast.add({severity:'success', summary: 'Workout saved', life: 3000});
            this.close_modal();
            this.$emit('onSave');
          })
          .catch(e => {
            this.handleError(e);
          });
    },
    close_modal() {
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
    lines: []
  };
}
</script>

<style scoped>
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
.workout-line-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.workout-line-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}
.workout-textarea {
  width: 100%;
  resize: vertical;
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
