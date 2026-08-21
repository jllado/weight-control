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
              <div v-for="line in workout.data.lines" :key="line.position" class="diary-workout-line">
                <strong>{{ line.exerciseName }}</strong>
                <div v-for="segment in workoutSegments(line)" :key="segment.position" class="diary-workout-segment">
                  {{ formatWorkoutSegment(line, segment) }}<WorkoutRecordBadges :events="segment.recordEvents" />
                </div>
              </div>
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

    <WorkoutForm :workout="selected_workout" :workouts="workouts" @onSave="saveWorkout" @onClose="closeWorkoutModal" v-model:show="display_workout_modal" />

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
import WorkoutForm from "@/components/WorkoutForm";
import WorkoutExercise, { ExerciseTrackingMode, trackingModeLabel } from "@/model/WorkoutExercise";
import WorkoutRecordBadges from "@/components/WorkoutRecordBadges";

export default {
  components: {WorkoutForm, WorkoutRecordBadges},
  data() {
    return {
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
      selected_workout: null,
      exercise_form: buildEmptyExerciseForm(),
      exercise_errors: {}
    }
  },
  async created() {
    await Promise.all([this.loadWorkouts(), this.loadExercises()]);
  },
  methods: {
    trackingModeLabel,
    workoutSegments(line) {
      return line.trackingMode === ExerciseTrackingMode.CARDIO ? line.intervals : line.sets;
    },
    formatDuration(seconds) {
      return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
    },
    formatWorkoutSegment(line, segment) {
      if (line.trackingMode === ExerciseTrackingMode.REPS) {
        return `${segment.weight ?? 0} kg × ${segment.repetitions} reps`;
      }
      if (line.trackingMode === ExerciseTrackingMode.SECONDS) {
        return `${segment.weight ?? 0} kg × ${this.formatDuration(segment.durationSeconds)}`;
      }
      const details = [this.formatDuration(segment.durationSeconds)];
      if (segment.distanceKm !== null) details.push(`${segment.distanceKm} km`);
      if (segment.speedKph !== null) details.push(`${segment.speedKph} km/h`);
      if (segment.inclinePercent !== null) details.push(`${segment.inclinePercent}% incline`);
      if (segment.resistanceLevel !== null) details.push(`resistance ${segment.resistanceLevel}`);
      return details.join(' · ');
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
      this.selected_workout = null;
      this.display_workout_modal = true;
    },
    editWorkout(workout) {
      this.selected_workout = workout;
      this.display_workout_modal = true;
    },
    async saveWorkout() {
      await this.loadWorkouts();
    },
    closeWorkoutModal() {
      this.display_workout_modal = false;
      this.selected_workout = null;
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
.workout-textarea {
  width: 100%;
  resize: vertical;
}
.diary-workout-line + .diary-workout-line {
  margin-top: 0.5rem;
}
.diary-workout-segment {
  color: #475569;
  font-size: 0.85rem;
}
</style>
