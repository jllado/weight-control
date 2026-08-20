<template>
  <Panel header="Settings">
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
        <InputNumber :id="day.key" v-model="profile.typicalCaloriesPerDay[day.key]" suffix=" kcal" :min="0" />
        <span class="error">{{ errors[day.key] }}</span>
      </div>
    </div>
    <Button label="Save" icon="pi pi-check" @click="save" :loading="saving" />
  </Panel>
  <HealthConstraintSettings />
  <PushNotificationSettings />
  <WeeklySummarySettings />
</template>

<script>
import {userState} from '../state';
import UserProfile, {calorieShortcutOptions, medicationOptions, typicalCaloriesDays, userFitnessLevelOptions, userSexOptions} from '../model/UserProfile';
import userProfileService from '../services/UserProfileService';
import PushNotificationSettings from './PushNotificationSettings.vue';
import WeeklySummarySettings from './WeeklySummarySettings.vue';
import HealthConstraintSettings from './HealthConstraintSettings.vue';

export default {
  components: {HealthConstraintSettings, PushNotificationSettings, WeeklySummarySettings},
  data() {
    return {
      profile: new UserProfile({takesMedication: false}),
      errors: {},
      maxDate: new Date(),
      medicationOptions,
      fitnessOptions: userFitnessLevelOptions,
      sexOptions: userSexOptions,
      calorieShortcutOptions,
      typicalCaloriesDays: typicalCaloriesDays.map(day => ({
        key: day,
        label: day.charAt(0).toUpperCase() + day.slice(1)
      })),
      saving: false,
      state: userState()
    }
  },
  async created() {
    if (this.state.user.profile) {
      this.profile = new UserProfile(this.state.user.profile);
      return;
    }
    await this.loadProfile();
  },
  methods: {
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
    async save() {
      if (!this.validate()) {
        return;
      }
      this.saving = true;
      try {
        const savedProfile = await userProfileService.save(this.profile);
        this.profile = new UserProfile(savedProfile);
        this.state.user.profile = savedProfile;
        this.$toast.add({severity:'success', summary: 'Saved', detail: 'Settings updated', life: 3000});
      } catch (e) {
        this.handle_error(e);
      } finally {
        this.saving = false;
      }
    },
    validate() {
      const errors = {};
      if (!this.profile.birthDate) {
        errors.birthDate = 'Birth date is required';
      }
      if (!this.profile.heightCm) {
        errors.heightCm = 'Height is required';
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
