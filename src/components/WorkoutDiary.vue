<template>
  <div>
    <TabView>
      <TabPanel header="Diary">
        <DataTable :value="this.workouts" :paginator="true" :rows="10" :loading="this.state.loading" responsiveLayout="scroll"
                   paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
                   currentPageReportTemplate="{first} to {last} of {totalRecords}">
          <template #header>
            <div class="table-header">
              Workouts
              <Button icon="pi pi-plus" label="New" @click="createWorkout" />
            </div>
          </template>
          <Column header="Date" headerStyle="width: 120px">
            <template #body="workout">
              {{ workout.data.workoutDateFormat }}
            </template>
          </Column>
          <Column header="Exercises">
            <template #body="workout">
              {{ workout.data.summary() }}
            </template>
          </Column>
          <Column header="Note">
            <template #body="workout">
              {{ workout.data.note }}
            </template>
          </Column>
          <Column headerStyle="width: 100px">
            <template #body="workout">
              <div style="width: 100px; text-align: center">
                <Button icon="pi pi-pencil" class="p-button-rounded p-button-success p-mr-2" @click="editWorkout(workout.data)" />
                <Button icon="pi pi-trash" class="p-button-rounded p-button-warning" @click="removeWorkout(workout.data)" />
              </div>
            </template>
          </Column>
        </DataTable>
      </TabPanel>
      <TabPanel header="Exercises">
        <DataTable :value="this.exercises" :paginator="true" :rows="10" :loading="this.exercises_loading" responsiveLayout="scroll"
                   paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
                   currentPageReportTemplate="{first} to {last} of {totalRecords}">
          <template #header>
            <div class="table-header">
              Exercises
              <Button icon="pi pi-plus" label="New" @click="createExercise" />
            </div>
          </template>
          <Column header="Name" field="name" headerStyle="min-width: 180px" />
          <Column header="Mode" headerStyle="width: 110px">
            <template #body="exercise">
              {{ trackingModeLabel(exercise.data.trackingMode) }}
            </template>
          </Column>
          <Column header="Description" field="description" />
          <Column headerStyle="width: 100px">
            <template #body="exercise">
              <div style="width: 100px; text-align: center">
                <Button icon="pi pi-pencil" class="p-button-rounded p-button-success p-mr-2" @click="editExercise(exercise.data)" />
                <Button icon="pi pi-trash" class="p-button-rounded p-button-warning" @click="removeExercise(exercise.data)" />
              </div>
            </template>
          </Column>
        </DataTable>
      </TabPanel>
    </TabView>

    <Dialog id="workout-form" appendTo="body" header="Workout" v-model:visible="display_workout_modal" :closeOnEscape="false" :closable="false" :modal="true" :style="{width: 'min(960px, 96vw)'}">
      <br>
      <div class="p-fluid">
        <div class="p-field p-mb-4">
          <span class="p-float-label">
            <Calendar v-model="workout_form.workoutDate" dateFormat="dd/mm/yy" appendTo="body" v-model:locale="custom_locale" :maxDate="max_date" />
            <label>Date</label>
          </span>
          <span class="error">{{ workout_errors.workoutDate }}</span>
        </div>
        <div class="p-field p-mb-4">
          <label class="p-d-block p-mb-2">Note</label>
          <textarea v-model="workout_form.note" rows="3" class="p-inputtext p-component workout-textarea" maxlength="500"></textarea>
          <span class="error">{{ workout_errors.note }}</span>
        </div>

        <div v-for="(line, lineIndex) in workout_form.lines" :key="line.localId" class="workout-line-card p-mb-4">
          <div class="workout-line-header">
            <strong>Exercise {{ lineIndex + 1 }}</strong>
            <Button icon="pi pi-trash" class="p-button-rounded p-button-text p-button-danger" @click="removeLine(lineIndex)" />
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
                    <label class="p-d-block p-mb-2">Incline (%)</label>
                    <InputNumber v-model="segment.inclinePercent" mode="decimal" :min="0" :minFractionDigits="0" :maxFractionDigits="2" />
                  </div>
                  <div class="p-col-12 p-md-4">
                    <label class="p-d-block p-mb-2">Resistance</label>
                    <InputNumber v-model="segment.resistanceLevel" :min="0" />
                  </div>
                  <div class="p-col-12 p-md-4">
                    <label class="p-d-block p-mb-2">Calories</label>
                    <InputNumber v-model="segment.calories" :min="0" />
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
        <Button label="Cancel" icon="pi pi-times" @click="closeWorkoutModal" class="p-button-secondary" />
      </template>
    </Dialog>

    <Dialog id="exercise-form" appendTo="body" header="Exercise" v-model:visible="display_exercise_modal" :closeOnEscape="false" :closable="false" :modal="true" :style="{width: 'min(640px, 96vw)'}">
      <br>
      <div class="p-fluid">
        <div class="p-field p-mb-4">
          <span class="p-float-label">
            <InputText id="exercise-name" v-model="exercise_form.name" maxlength="255" />
            <label for="exercise-name">Name</label>
          </span>
          <span class="error">{{ exercise_errors.name }}</span>
        </div>
        <div class="p-field p-mb-4">
          <label class="p-d-block p-mb-2">Mode</label>
          <Dropdown v-model="exercise_form.trackingMode" :options="tracking_mode_options" optionLabel="label" optionValue="value" />
          <span class="error">{{ exercise_errors.trackingMode }}</span>
        </div>
        <div class="p-field p-mb-4">
          <label class="p-d-block p-mb-2">Description</label>
          <textarea v-model="exercise_form.description" rows="4" class="p-inputtext p-component workout-textarea" maxlength="500"></textarea>
          <span class="error">{{ exercise_errors.description }}</span>
        </div>
      </div>
      <template #footer>
        <Button label="Save" icon="pi pi-check" @click="saveExercise" />
        <Button label="Cancel" icon="pi pi-times" @click="closeExerciseModal" class="p-button-secondary" />
      </template>
    </Dialog>
  </div>
</template>

<script>
import workoutService from '../services/WorkoutService';
import exerciseService from '../services/WorkoutExerciseService';
import { userState } from '../state';
import Workout from "@/model/Workout";
import WorkoutExercise, { ExerciseTrackingMode, trackingModeLabel } from "@/model/WorkoutExercise";

let nextLocalId = 1;

export default {
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
      tracking_mode_options: [
        {label: 'Reps', value: ExerciseTrackingMode.REPS},
        {label: 'Seconds', value: ExerciseTrackingMode.SECONDS},
        {label: 'Cardio', value: ExerciseTrackingMode.CARDIO}
      ],
      workouts: [],
      exercises: [],
      state: userState(),
      exercises_loading: false,
      display_workout_modal: false,
      display_exercise_modal: false,
      workout_form: buildEmptyWorkoutForm(),
      workout_errors: {},
      exercise_form: buildEmptyExerciseForm(),
      exercise_errors: {}
    }
  },
  async created() {
    await Promise.all([this.loadWorkouts(), this.loadExercises()]);
  },
  methods: {
    trackingModeLabel,
    emptyWorkoutForm() {
      return buildEmptyWorkoutForm();
    },
    emptyExerciseForm() {
      return buildEmptyExerciseForm();
    },
    async loadWorkouts() {
      this.state.loading = true;
      this.workouts = await workoutService.get_all();
      this.state.loading = false;
    },
    async loadExercises() {
      this.exercises_loading = true;
      this.exercises = await exerciseService.get_all();
      this.exercises_loading = false;
    },
    createWorkout() {
      this.workout_form = this.emptyWorkoutForm();
      this.workout_errors = {};
      this.addLine();
      this.display_workout_modal = true;
    },
    editWorkout(workout) {
      this.workout_form = {
        id: workout.id,
        workoutDate: workout.workoutDate,
        note: workout.note || '',
        lines: workout.lines.map(line => ({
          localId: nextId(),
          exerciseId: line.exerciseId,
          exerciseDescription: line.exerciseDescription,
          trackingMode: line.trackingMode,
          segments: this.segmentsFromWorkoutLine(line),
          error: null
        }))
      };
      this.workout_errors = {};
      this.display_workout_modal = true;
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
        inclinePercent: segment.inclinePercent ?? null,
        resistanceLevel: segment.resistanceLevel ?? null,
        calories: segment.calories ?? null,
        error: null
      }));
    },
    async removeWorkout(workout) {
      if (!confirm('Are you sure you want to delete this?')) {
        return;
      }
      await workoutService.delete(workout)
          .then(() => {
            this.$toast.add({severity:'success', summary: 'Workout deleted', life: 3000});
          })
          .catch(e => {
            this.handleError(e);
          });
      await this.loadWorkouts();
    },
    addLine() {
      this.workout_form.lines.push({
        localId: nextId(),
        exerciseId: null,
        exerciseDescription: '',
        trackingMode: null,
        segments: [],
        error: null
      });
    },
    removeLine(index) {
      this.workout_form.lines.splice(index, 1);
    },
    onExerciseChanged(line) {
      const exercise = this.exercises.find(item => item.id === line.exerciseId);
      line.trackingMode = exercise?.trackingMode || null;
      line.exerciseDescription = exercise?.description || '';
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
        inclinePercent: previous?.inclinePercent ?? null,
        resistanceLevel: previous?.resistanceLevel ?? null,
        calories: previous?.calories ?? null,
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
      return (segment.durationMinutes || 0) * 60
          + (segment.durationRemainder || 0);
    },
    buildWorkoutPayload() {
      const workout = new Workout();
      workout.id = this.workout_form.id;
      workout.workoutDate = this.workout_form.workoutDate;
      workout.note = this.workout_form.note || null;
      workout.lines = this.workout_form.lines.map(line => ({
        exerciseId: line.exerciseId,
        segments: line.segments.map(segment => ({
          repetitions: line.trackingMode === ExerciseTrackingMode.REPS ? segment.repetitions : null,
          durationSeconds: line.trackingMode === ExerciseTrackingMode.REPS ? null : this.toDurationSeconds(segment),
          weight: line.trackingMode === ExerciseTrackingMode.CARDIO ? null : segment.weight,
          speedKph: line.trackingMode === ExerciseTrackingMode.CARDIO ? segment.speedKph : null,
          inclinePercent: line.trackingMode === ExerciseTrackingMode.CARDIO ? segment.inclinePercent : null,
          resistanceLevel: line.trackingMode === ExerciseTrackingMode.CARDIO ? segment.resistanceLevel : null,
          calories: line.trackingMode === ExerciseTrackingMode.CARDIO ? segment.calories : null
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
            this.closeWorkoutModal();
          })
          .catch(e => {
            this.handleError(e);
          });
      await this.loadWorkouts();
    },
    closeWorkoutModal() {
      this.display_workout_modal = false;
      this.workout_form = this.emptyWorkoutForm();
      this.workout_errors = {};
    },
    createExercise() {
      this.exercise_form = this.emptyExerciseForm();
      this.exercise_errors = {};
      this.display_exercise_modal = true;
    },
    editExercise(exercise) {
      this.exercise_form = new WorkoutExercise(exercise).toObject();
      this.exercise_errors = {};
      this.display_exercise_modal = true;
    },
    validateExerciseForm() {
      const errors = {};
      if (!this.exercise_form.name.trim()) {
        errors.name = 'Name is required';
      }
      if (!this.exercise_form.trackingMode) {
        errors.trackingMode = 'Mode is required';
      }
      if (!this.exercise_form.description.trim()) {
        errors.description = 'Description is required';
      }
      this.exercise_errors = errors;
      return Object.keys(errors).length === 0;
    },
    async saveExercise() {
      if (!this.validateExerciseForm()) {
        return;
      }
      await exerciseService.save(new WorkoutExercise(this.exercise_form).toObject())
          .then(() => {
            this.$toast.add({severity:'success', summary: 'Exercise saved', life: 3000});
            this.closeExerciseModal();
          })
          .catch(e => {
            this.handleError(e);
          });
      await this.loadExercises();
    },
    async removeExercise(exercise) {
      if (!confirm('Are you sure you want to delete this?')) {
        return;
      }
      await exerciseService.delete(exercise)
          .then(() => {
            this.$toast.add({severity:'success', summary: 'Exercise deleted', life: 3000});
          })
          .catch(e => {
            this.handleError(e);
          });
      await this.loadExercises();
    },
    closeExerciseModal() {
      this.display_exercise_modal = false;
      this.exercise_form = this.emptyExerciseForm();
      this.exercise_errors = {};
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

function buildEmptyWorkoutForm() {
  return {
    id: null,
    workoutDate: new Date(),
    note: '',
    lines: []
  };
}

function buildEmptyExerciseForm() {
  return {
    id: null,
    name: '',
    description: '',
    trackingMode: null
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
