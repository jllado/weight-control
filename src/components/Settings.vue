<template>
  <Panel header="Settings">
    <SaveFields :saving="saving">
    <div class="p-fluid p-formgrid p-grid">
      <div class="p-field p-col-12 p-md-6">
        <label for="birthDate">Birth Date</label>
        <Calendar id="birthDate" v-model="profile.birthDate" dateFormat="dd/mm/yy" :maxDate="maxDate" appendTo="body" />
        <span class="error">{{ errors.birthDate }}</span>
      </div>
      <div class="p-field p-col-12 p-md-6">
        <label for="heightCm">Height</label>
        <InputNumber id="heightCm" v-model="profile.heightCm" suffix=" cm" :min="1" />
        <span class="error">{{ errors.heightCm }}</span>
      </div>
      <div class="p-field p-col-12 p-md-6">
        <label for="inShapeBodyFatLimit">In-shape body-fat limit (%)</label>
        <InputNumber inputId="inShapeBodyFatLimit" v-model="profile.inShapeBodyFatLimit" suffix="%" :min="0" :max="100" :minFractionDigits="2" :maxFractionDigits="2" />
        <span class="error">{{ errors.inShapeBodyFatLimit }}</span>
      </div>
      <div class="p-field p-col-12 p-md-6">
        <label for="sex">Sex</label>
        <Dropdown id="sex" v-model="profile.sex" :options="sexOptions" optionLabel="label" optionValue="value" placeholder="Select sex" />
        <span class="error">{{ errors.sex }}</span>
      </div>
      <div class="p-field p-col-12 p-md-6">
        <label for="fitnessLevel">Fitness Level</label>
        <Dropdown id="fitnessLevel" v-model="profile.fitnessLevel" :options="fitnessOptions" optionLabel="label" optionValue="value" placeholder="Select fitness level" />
        <span class="error">{{ errors.fitnessLevel }}</span>
      </div>
      <div class="p-field p-col-12 p-md-6">
        <label for="takesMedication">Taking Medication</label>
        <Dropdown id="takesMedication" v-model="profile.takesMedication" :options="medicationOptions" optionLabel="label" optionValue="value" />
      </div>
      <div class="p-field p-col-12 p-md-6">
        <label for="weeklyAverageCalorieMaximum">Weekly Average Calorie Maximum</label>
        <InputNumber id="weeklyAverageCalorieMaximum" v-model="profile.weeklyAverageCalorieMaximum" suffix=" kcal" :min="0" />
        <span class="error">{{ errors.weeklyAverageCalorieMaximum }}</span>
      </div>
      <div class="p-field p-col-12 p-md-6">
        <label id="first-planning-day-label" for="first-planning-day">First day of planning week</label>
        <Dropdown inputId="first-planning-day" aria-labelledby="first-planning-day-label" :modelValue="selectedFirstDay" @update:modelValue="draftFirstDay = $event" :options="weekdayOptions" optionLabel="label" optionValue="value" />
        <div aria-live="polite" class="p-mt-1">
          <div>{{ periodLabel(currentWeek.start, currentWeek.end) }}</div>
          <template v-if="scheduledWeek">
            <div v-if="scheduledWeek.transition && scheduledWeek.transition.start !== currentWeek.start">{{ periodLabel(scheduledWeek.transition.start, scheduledWeek.transition.end) }}</div>
            <div>{{ scheduledWeek.range }} from {{ scheduledWeek.effectiveDate }} through {{ scheduledWeek.end }}.</div>
          </template>
        </div>
      </div>
      <div class="p-field p-col-12">
        <h3>Calorie Shortcuts</h3>
      </div>
      <div v-for="shortcut in calorieShortcutOptions" :key="shortcut.key" class="p-field p-col-12 p-md-6">
        <label :for="`calorieShortcut-${shortcut.key}`">{{ shortcut.label }}</label>
        <InputNumber :id="`calorieShortcut-${shortcut.key}`" v-model="profile.calorieShortcuts[shortcut.key]" suffix=" kcal" :min="0" />
        <span class="error">{{ errors[`calorieShortcut-${shortcut.key}`] }}</span>
      </div>
      <div class="p-field p-col-12">
        <h3>Typical Calories Per Day</h3>
      </div>
      <div v-for="day in typicalCaloriesDays" :key="day.key" class="p-field p-col-12 p-md-6">
        <label :for="day.key">{{ day.label }}</label>
        <InputNumber :inputId="day.key" v-model="profile.typicalCaloriesPerDay[day.key]" suffix=" kcal" :min="0" />
        <span class="error">{{ errors[day.key] }}</span>
      </div>
      <div class="p-field p-col-12" role="status" aria-label="Typical daily calorie average">
        <strong>Daily average:</strong> {{ formatCalories(typicalCaloriesAverage) }} kcal/day
      </div>
    </div>
    </SaveFields>
    <div class="action-group">
    <Button :label="saving ? 'Saving…' : 'Save'" icon="pi pi-check" @click="save" :loading="saving" :aria-busy="saving" :disabled="saving" />
    <Button label="Cancel" class="p-button-secondary" :disabled="saving" @click="cancel" />
    </div>
  </Panel>
  <PushNotificationSettings />
  <WeeklySummarySettings :today="today" />
  <NutrientTargetSettings />
</template>

<script>
import dayjs from 'dayjs';
import {planningWeek, nextOccurrence, weekdays, weekdaysFrom, dayLabel, weekdayRange, periodLabel, madridDate} from '../model/PlanningWeek';
import {madridInstant} from '../model/WorkoutTiming';
import {userState} from '../state';
import UserProfile, {calorieShortcutOptions, medicationOptions, typicalCaloriesDays, userFitnessLevelOptions, userSexOptions} from '../model/UserProfile';
import userProfileService from '../services/UserProfileService';
import PushNotificationSettings from './PushNotificationSettings.vue';
import WeeklySummarySettings from './WeeklySummarySettings.vue';
import NutrientTargetSettings from './NutrientTargetSettings.vue';
import {formatCalories} from '../services/CalorieFormatting';

export default {
  components: {PushNotificationSettings, WeeklySummarySettings, NutrientTargetSettings},
  data() {
    return {
      profile: new UserProfile({takesMedication: false}),
      draftFirstDay: null,
      weekdayOptions: weekdays.map(value => ({value, label: dayLabel(value)})),
      errors: {},
      maxDate: new Date(),
      midnightTimer: null,
      medicationOptions,
      fitnessOptions: userFitnessLevelOptions,
      sexOptions: userSexOptions,
      calorieShortcutOptions,
      saving: false,
      state: userState()
    }
  },
  computed: {
    today() { return madridDate(this.maxDate); },
    savedFirstDay() { return this.profile.planningWeekChanges.at(-1)?.firstDay || 'SATURDAY'; },
    selectedFirstDay() { return this.draftFirstDay || this.savedFirstDay; },
    previewChanges() {
      const saved = this.profile.planningWeekChanges;
      if (this.selectedFirstDay === this.savedFirstDay) return saved;
      const history = saved.filter(change => change.effectiveDate <= this.today);
      if (this.selectedFirstDay === planningWeek(this.today, history).firstDay) return history;
      return [...history, {effectiveDate: nextOccurrence(this.selectedFirstDay, this.today), firstDay: this.selectedFirstDay}];
    },
    currentWeek() { return planningWeek(this.today, this.previewChanges); },
    scheduledWeek() {
      const scheduled = this.previewChanges.find(change => change.effectiveDate > this.today);
      if (!scheduled) return null;
      const week = planningWeek(scheduled.effectiveDate, this.previewChanges);
      const prior = planningWeek(dayjs(week.start).subtract(1, 'day'), this.previewChanges);
      return {...scheduled, end: week.end, range: weekdayRange(week.start, week.end), transition: prior.transition ? prior : null};
    },
    typicalCaloriesDays() { return weekdaysFrom(this.currentWeek.firstDay).map(day => ({key: day.toLowerCase(), label: dayLabel(day)})); },
    typicalCaloriesAverage() {
      const values = typicalCaloriesDays.map(day => this.profile.typicalCaloriesPerDay[day]);
      return values.some(value => value === null) ? null : values.reduce((sum, value) => sum + value, 0) / 7;
    }
  },
  async created() {
    if (this.state.user.profile) {
      this.profile = new UserProfile(this.state.user.profile);
      return;
    }
    await this.loadProfile();
  },
  mounted() {
    this.refreshDate();
    document.addEventListener('visibilitychange', this.refreshWhenVisible);
    window.addEventListener('pageshow', this.refreshDate);
  },
  beforeUnmount() {
    clearTimeout(this.midnightTimer);
    document.removeEventListener('visibilitychange', this.refreshWhenVisible);
    window.removeEventListener('pageshow', this.refreshDate);
  },
  methods: {
    refreshWhenVisible() { if (document.visibilityState === 'visible') this.refreshDate(); },
    refreshDate() {
      clearTimeout(this.midnightTimer);
      this.maxDate = new Date();
      const midnight = madridInstant(dayjs(this.today).add(1, 'day').startOf('day').toDate());
      this.midnightTimer = setTimeout(this.refreshDate, Date.parse(midnight) - this.maxDate.getTime());
    },
    formatCalories, periodLabel,
    async loadProfile() {
      this.state.loading = true;
      try {
        const profile = await userProfileService.get();
        this.profile = new UserProfile(profile);
        this.state.user.profile = profile;
      } catch (e) {
        this.handle_error(e);
      } finally {
        this.state.loading = false;
      }
    },
    showPlanningDateChange() {
      this.$toast.add({severity: 'info', summary: 'Planning dates updated', detail: 'Review the new dates and select Save again.', life: 5000});
    },
    async refreshPlanningHistory() {
      try {
        const profile = await userProfileService.get();
        this.state.user.profile = profile;
        this.profile.planningWeekChanges = profile.planningWeekChanges;
        this.refreshDate();
        this.showPlanningDateChange();
      } catch (error) {
        this.handle_error(error);
      }
    },
    async save() {
      const preview = this.scheduledWeek?.effectiveDate;
      this.refreshDate();
      if (this.selectedFirstDay !== this.savedFirstDay && preview !== this.scheduledWeek?.effectiveDate) {
        this.showPlanningDateChange();
        return;
      }
      if (!this.validate()) {
        return;
      }
      const planningChange = this.selectedFirstDay !== this.savedFirstDay ? {
        firstDayOfPlanningWeek: this.selectedFirstDay,
        expectedPlanningWeekEffectiveDate: this.scheduledWeek?.effectiveDate ?? null
      } : undefined;
      this.saving = true;
      try {
        const savedProfile = await userProfileService.save(this.profile, planningChange);
        this.profile = new UserProfile(savedProfile);
        this.state.user.profile = savedProfile;
        this.draftFirstDay = null;
        this.$toast.add({severity:'success', summary: 'Saved', detail: 'Settings updated', life: 3000});
      } catch (e) {
        if (e.status === 409 && planningChange) await this.refreshPlanningHistory();
        else this.handle_error(e);
      } finally {
        this.saving = false;
      }
    },
    cancel() {
      this.draftFirstDay = null;
      this.profile = new UserProfile(this.state.user.profile);
      this.errors = {};
    },
    validate() {
      const errors = {};
      if (!this.profile.birthDate) {
        errors.birthDate = 'Birth date is required';
      }
      if (!this.profile.heightCm) {
        errors.heightCm = 'Height is required';
      }
      if (this.profile.inShapeBodyFatLimit === null) {
        errors.inShapeBodyFatLimit = 'In-shape body-fat limit is required';
      } else if (this.profile.inShapeBodyFatLimit < 0 || this.profile.inShapeBodyFatLimit > 100) {
        errors.inShapeBodyFatLimit = 'In-shape body-fat limit must be between 0 and 100%';
      }
      if (!this.profile.sex) {
        errors.sex = 'Sex is required';
      }
      if (!this.profile.fitnessLevel) {
        errors.fitnessLevel = 'Fitness level is required';
      }
      if (this.profile.weeklyAverageCalorieMaximum === null || this.profile.weeklyAverageCalorieMaximum === undefined) {
        errors.weeklyAverageCalorieMaximum = 'Weekly average calorie maximum is required';
      }
      typicalCaloriesDays.forEach(day => {
        if (this.profile.typicalCaloriesPerDay[day] === null || this.profile.typicalCaloriesPerDay[day] === undefined) {
          errors[day] = 'Typical calories are required';
        }
      });
      calorieShortcutOptions.forEach(shortcut => {
        if (this.profile.calorieShortcuts[shortcut.key] === null || this.profile.calorieShortcuts[shortcut.key] === undefined) {
          errors[`calorieShortcut-${shortcut.key}`] = 'Shortcut calories are required';
        }
      });
      this.errors = errors;
      return Object.keys(errors).length === 0;
    },
    handle_error(e) {
      this.$log.error(e);
      this.$toast.add({severity:'error', summary: 'Failed', detail: e, life: 3000});
    }
  }
}
</script>
