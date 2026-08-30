<template>
  <div>
    <TabView>
      <TabPanel header="Diary">
        <div>
          <DataTable class="diary-desktop" :value="workouts" :paginator="true" :lazy="true" :rows="10" :totalRecords="total_workouts" :first="diary_page * 10" :loading="state.loading" responsiveLayout="scroll" @page="loadDiaryPage"
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
          <Column header="Assessment" headerStyle="min-width: 220px">
            <template #body="workout">
              <div class="assessment-cell">
                <button v-if="workout.data.assessment" class="assessment-summary" type="button" @click="showAssessment(workout.data)">
                  Goal {{ workout.data.assessment.goalAlignmentScore }} · Demand {{ workout.data.assessment.estimatedTrainingDemandScore }}
                </button>
                <Button
                    :label="workout.data.assessment ? 'Reassess with Coach' : 'Assess with Coach'"
                    class="p-button-sm p-button-text assessment-action"
                    @click="assessWithCoach(workout.data)" />
              </div>
            </template>
          </Column>
          <Column headerStyle="width: 100px">
            <template #body="workout">
              <div class="diary-row-actions">
                <Button icon="pi pi-pencil" aria-label="Edit workout" class="p-button-rounded p-button-success" @click="editWorkout(workout.data)" />
                <Button icon="pi pi-trash" aria-label="Delete workout" class="p-button-rounded p-button-warning" @click="removeWorkout(workout.data)" />
              </div>
            </template>
          </Column>
          </DataTable>
          <div class="diary-mobile">
          <div class="table-header">
            Workouts
            <Button icon="pi pi-plus" label="New" @click="createWorkout" />
          </div>
          <div v-if="state.loading" class="mobile-diary-message">Loading workouts…</div>
          <div v-else-if="workouts.length === 0" class="mobile-diary-message">No workouts recorded.</div>
          <article v-for="workout in workouts" :key="workout.id" class="mobile-diary-workout">
            <button
                class="mobile-diary-summary"
                type="button"
                :aria-expanded="expanded_mobile_workout_id === workout.id"
                :aria-controls="`mobile-workout-details-${workout.id}`"
                @click="toggleMobileWorkout(workout.id)">
              <span>
                <strong>{{ mobileWorkoutTitle(workout) }}</strong>
                <span class="mobile-diary-date">{{ workout.workoutDateFormat }}</span>
              </span>
              <i :class="expanded_mobile_workout_id === workout.id ? 'pi pi-chevron-up' : 'pi pi-chevron-down'" aria-hidden="true"></i>
            </button>
            <div v-if="expanded_mobile_workout_id === workout.id" :id="`mobile-workout-details-${workout.id}`" class="mobile-diary-details">
              <div v-for="line in workout.lines" :key="line.position" class="diary-workout-line">
                <strong>{{ line.exerciseName }}</strong><span v-if="line.exerciseType === ExerciseType.WARM_UP" class="mobile-warm-up-label">Warm-up</span>
                <div v-for="segment in workoutSegments(line)" :key="segment.position" class="diary-workout-segment">
                  {{ formatWorkoutSegment(line, segment) }}<WorkoutRecordBadges :events="segment.recordEvents" />
                </div>
              </div>
              <p v-if="workout.note" class="mobile-diary-note">{{ workout.note }}</p>
              <div class="assessment-cell">
                <button v-if="workout.assessment" class="assessment-summary" type="button" @click="showAssessment(workout)">
                  Goal {{ workout.assessment.goalAlignmentScore }} · Demand {{ workout.assessment.estimatedTrainingDemandScore }}
                </button>
                <Button
                    :label="workout.assessment ? 'Reassess with Coach' : 'Assess with Coach'"
                    class="p-button-sm p-button-text assessment-action"
                    @click="assessWithCoach(workout)" />
              </div>
              <div class="diary-row-actions mobile-diary-actions">
                <Button icon="pi pi-pencil" aria-label="Edit workout" class="p-button-rounded p-button-success" @click="editWorkout(workout)" />
                <Button icon="pi pi-trash" aria-label="Delete workout" class="p-button-rounded p-button-warning" @click="removeWorkout(workout)" />
              </div>
            </div>
          </article>
            <div v-if="total_workouts > 10" class="mobile-diary-pagination">
              <Button label="Previous" class="p-button-sm p-button-text" :disabled="diary_page === 0" @click="loadDiaryPage({page: diary_page - 1})" />
              <span>{{ diary_page + 1 }} of {{ Math.ceil(total_workouts / 10) }}</span>
              <Button label="Next" class="p-button-sm p-button-text" :disabled="(diary_page + 1) * 10 >= total_workouts" @click="loadDiaryPage({page: diary_page + 1})" />
            </div>
          </div>
        </div>
      </TabPanel>
      <TabPanel header="Exercises">
        <DataTable :value="trainingExercises" :paginator="true" :rows="10" :loading="this.exercises_loading" responsiveLayout="scroll"
                   paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
                   currentPageReportTemplate="{first} to {last} of {totalRecords}">
          <template #header>
            <div class="table-header">
              Exercises
              <Button icon="pi pi-plus" label="New" @click="createExercise(ExerciseType.TRAINING)" />
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
              <div class="diary-row-actions">
                <Button icon="pi pi-pencil" class="p-button-rounded p-button-success" @click="editExercise(exercise.data)" />
                <Button icon="pi pi-trash" class="p-button-rounded p-button-warning" @click="removeExercise(exercise.data)" />
              </div>
            </template>
          </Column>
        </DataTable>
      </TabPanel>
      <TabPanel header="Warm-ups">
        <DataTable :value="warmUpExercises" :paginator="true" :rows="10" :loading="this.exercises_loading" responsiveLayout="scroll"
                   paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
                   currentPageReportTemplate="{first} to {last} of {totalRecords}">
          <template #header><div class="table-header">Warm-ups<Button icon="pi pi-plus" label="New" @click="createExercise(ExerciseType.WARM_UP)" /></div></template>
          <Column header="Name" field="name" headerStyle="min-width: 180px" />
          <Column header="Mode" headerStyle="width: 110px"><template #body="exercise">{{ trackingModeLabel(exercise.data.trackingMode) }}</template></Column>
          <Column header="Description" field="description" />
          <Column header="Default" headerStyle="width: 100px"><template #body="exercise">{{ exercise.data.defaultWarmUp ? 'Yes' : 'No' }}</template></Column>
          <Column headerStyle="width: 100px"><template #body="exercise"><div class="diary-row-actions"><Button icon="pi pi-pencil" aria-label="Edit warm-up" class="p-button-rounded p-button-success" @click="editExercise(exercise.data)" /><Button icon="pi pi-trash" aria-label="Delete warm-up" class="p-button-rounded p-button-warning" @click="removeExercise(exercise.data)" /></div></template></Column>
        </DataTable>
      </TabPanel>
    </TabView>

    <WorkoutForm :workout="selected_workout" @onSave="saveWorkout" @onClose="closeWorkoutModal" v-model:show="display_workout_modal" />

    <Dialog appendTo="body" header="Workout assessment" v-model:visible="display_assessment_modal" :modal="true" :style="{width: 'min(640px, 96vw)'}">
      <div v-if="selected_assessment_workout" class="assessment-details">
        <p><strong>Workout:</strong> {{ selected_assessment_workout.workoutDateFormat }}</p>
        <p><strong>Goal:</strong> {{ selected_assessment_workout.assessment.goalSnapshot }}</p>
        <p><strong>Goal alignment:</strong> {{ selected_assessment_workout.assessment.goalAlignmentScore }}/10</p>
        <p><strong>Estimated training demand:</strong> {{ selected_assessment_workout.assessment.estimatedTrainingDemandScore }}/10</p>
        <p><strong>Rationale:</strong> {{ selected_assessment_workout.assessment.rationale }}</p>
        <p><strong>Strength:</strong> {{ selected_assessment_workout.assessment.strength }}</p>
        <p><strong>Improvement:</strong> {{ selected_assessment_workout.assessment.improvement }}</p>
        <p><strong>Next workout:</strong> {{ selected_assessment_workout.assessment.nextWorkoutAction }}</p>
      </div>
      <template #footer>
        <Button label="Close" icon="pi pi-times" class="p-button-secondary" @click="closeAssessment" />
      </template>
    </Dialog>

    <Dialog id="exercise-form" appendTo="body" :header="exercise_form.exerciseType === ExerciseType.WARM_UP ? 'Warm-up' : 'Exercise'" v-model:visible="display_exercise_modal" :closeOnEscape="false" :closable="false" :modal="true" :style="{width: 'min(640px, 96vw)'}">
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
        <div v-if="exercise_form.exerciseType === ExerciseType.WARM_UP" class="p-field-checkbox p-mb-4">
          <Checkbox inputId="default-warm-up" v-model="exercise_form.defaultWarmUp" :binary="true" />
          <label for="default-warm-up">Add to new workouts by default</label>
        </div>
        <div v-if="exercise_form.defaultWarmUp" class="p-field p-mb-4">
          <label for="default-repetitions" class="p-d-block p-mb-2">Default repetitions</label>
          <InputNumber id="default-repetitions" v-model="exercise_form.defaultRepetitions" :min="1" />
          <span class="error">{{ exercise_errors.defaultRepetitions }}</span>
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
import WorkoutExercise, { ExerciseTrackingMode, ExerciseType, trackingModeLabel } from "@/model/WorkoutExercise";
import WorkoutRecordBadges from "@/components/WorkoutRecordBadges";
import dayjs from 'dayjs';
import {buildWorkoutAssessmentPrompt, openCoach} from '@/services/CoachService';

export default {
  components: {WorkoutForm, WorkoutRecordBadges},
  data() {
    return {
      ExerciseType,
      tracking_mode_options: [
        {label: 'Reps', value: ExerciseTrackingMode.REPS},
        {label: 'Seconds', value: ExerciseTrackingMode.SECONDS},
        {label: 'Cardio', value: ExerciseTrackingMode.CARDIO}
      ],
      workouts: [],
      diary_page: 0,
      total_workouts: 0,
      expanded_mobile_workout_id: null,
      exercises: [],
      state: userState(),
      exercises_loading: false,
      display_workout_modal: false,
      display_exercise_modal: false,
      display_assessment_modal: false,
      selected_workout: null,
      selected_assessment_workout: null,
      exercise_form: buildEmptyExerciseForm(),
      exercise_errors: {}
    }
  },
  async created() {
    await Promise.all([this.loadDiaryPage({page: 0}), this.loadExercises()]);
  },
  computed: {
    trainingExercises() {
      return this.exercises.filter(exercise => exercise.exerciseType === ExerciseType.TRAINING);
    },
    warmUpExercises() {
      return this.exercises.filter(exercise => exercise.exerciseType === ExerciseType.WARM_UP);
    }
  },
  methods: {
    trackingModeLabel,
    mobileWorkoutTitle(workout) {
      return workout.lines.find(line => line.exerciseType !== ExerciseType.WARM_UP)?.exerciseName || 'Warm-up workout';
    },
    toggleMobileWorkout(workoutId) {
      this.expanded_mobile_workout_id = this.expanded_mobile_workout_id === workoutId ? null : workoutId;
    },
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
    async loadDiaryPage({page}) {
      this.state.loading = true;
      try {
        const data = await workoutService.get_diary(page, 10);
        this.workouts = data.items;
        this.diary_page = data.page;
        this.total_workouts = data.totalElements;
        this.expanded_mobile_workout_id = null;
      } catch (e) {
        this.handleError(e);
      } finally {
        this.state.loading = false;
      }
    },
    async loadExercises() {
      this.exercises_loading = true;
      try {
        this.exercises = await exerciseService.get_all();
      } catch (e) {
        this.handleError(e);
      } finally {
        this.exercises_loading = false;
      }
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
      await this.loadDiaryPage({page: this.selected_workout ? this.diary_page : 0});
    },
    showAssessment(workout) {
      this.selected_assessment_workout = workout;
      this.display_assessment_modal = true;
    },
    closeAssessment() {
      this.display_assessment_modal = false;
      this.selected_assessment_workout = null;
    },
    assessWithCoach(workout) {
      const prompt = buildWorkoutAssessmentPrompt(dayjs(workout.workoutDate).format('YYYY-MM-DD'));
      const copyPrompt = navigator.clipboard.writeText(prompt);
      openCoach();
      copyPrompt
          .then(() => this.$toast.add({
            severity: 'info',
            summary: 'Assessment prompt copied',
            detail: 'Paste it into ChatGPT to continue.',
            life: 5000
          }))
          .catch(error => this.handleError(error));
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
      const page = this.workouts.length === 1 && this.diary_page > 0 ? this.diary_page - 1 : this.diary_page;
      await this.loadDiaryPage({page});
    },
    createExercise(exerciseType) {
      this.exercise_form = {...this.emptyExerciseForm(), exerciseType};
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
      if (this.exercise_form.defaultWarmUp && !this.exercise_form.defaultRepetitions) {
        errors.defaultRepetitions = 'Default repetitions are required';
      }
      if (this.exercise_form.defaultWarmUp && this.exercise_form.trackingMode !== ExerciseTrackingMode.REPS) {
        errors.trackingMode = 'Default warm-ups must use reps';
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
    trackingMode: null,
    exerciseType: ExerciseType.TRAINING,
    defaultWarmUp: false,
    defaultRepetitions: null
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
.assessment-cell {
  align-items: flex-start;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}
.diary-row-actions {
  align-items: center;
  display: flex;
  gap: 0.5rem;
  justify-content: center;
  min-width: 100px;
}
.assessment-summary {
  background: none;
  border: 0;
  color: #2563eb;
  cursor: pointer;
  font: inherit;
  font-weight: 600;
  padding: 0;
  text-align: left;
}
.assessment-action {
  padding-left: 0 !important;
}
.assessment-details p {
  margin: 0 0 0.75rem;
}
.diary-mobile {
  display: none;
}
@media (max-width: 575px) {
  .diary-desktop {
    display: none;
  }
  .diary-mobile {
    display: block;
  }
  .mobile-diary-message {
    color: #475569;
    padding: 1rem 0;
  }
  .mobile-diary-workout {
    border-bottom: 1px solid #dee2e6;
  }
  .mobile-diary-summary {
    align-items: center;
    background: none;
    border: 0;
    color: inherit;
    cursor: pointer;
    display: flex;
    font: inherit;
    justify-content: space-between;
    padding: 0.8rem 0;
    text-align: left;
    width: 100%;
  }
  .mobile-diary-summary strong,
  .mobile-diary-date {
    display: block;
  }
  .mobile-diary-date {
    color: #64748b;
    font-size: 0.85rem;
    margin-top: 0.15rem;
  }
  .mobile-diary-details {
    padding: 0 0 0.85rem;
  }
  .mobile-warm-up-label {
    color: #64748b;
    font-size: 0.8rem;
    margin-left: 0.5rem;
  }
  .mobile-diary-note {
    margin: 0.75rem 0;
    white-space: pre-wrap;
  }
  .mobile-diary-actions {
    justify-content: flex-start;
    margin-top: 0.75rem;
  }
  .mobile-diary-pagination {
    align-items: center;
    display: flex;
    justify-content: space-between;
    padding-top: 0.75rem;
  }
}
</style>
