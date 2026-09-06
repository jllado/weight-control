<template>
  <div>
    <p v-if="loading" role="status">Loading meal…</p>
    <div v-else-if="error"><p role="alert">{{ error }}</p><Button label="Back" class="p-button-outlined" @click="leave" /></div>
    <MealForm v-else-if="state.user.profile" ref="form" :meal="meal" :meals="meals" :fasting_periods="fasting_periods" :initial_date="initial_date" :fixed_date="fixed_date" @onSave="leave" @onClose="leave" />
  </div>
</template>

<script>
import MealForm from './MealForm.vue';
import mealService from '../services/MealService';
import fastingService from '../services/FastingPeriodService';
import {userState} from '../state';
import dayjs from 'dayjs';

export default {
  name: 'MealEditor',
  components: {MealForm},
  data() { return {state: userState(), loading: true, error: '', meal: null, meals: [], fasting_periods: []}; },
  computed: {
    initial_date() { return this.$route.query.date ? dayjs(this.$route.query.date).toDate() : new Date(); },
    fixed_date() { return this.$route.query.from === 'dashboard'; }
  },
  watch: {
    'state.user.profile': {immediate: true, handler(profile) { if (profile) this.load(); }}
  },
  mounted() { window.addEventListener('beforeunload', this.before_unload); },
  beforeUnmount() { window.removeEventListener('beforeunload', this.before_unload); },
  beforeRouteLeave() { return !this.$refs.form?.dirty || window.confirm('Discard unsaved meal changes?'); },
  methods: {
    async load() {
      try {
        [this.meals, this.fasting_periods] = await Promise.all([mealService.get_all(), fastingService.get_all()]);
        if (this.$route.params.id) {
          this.meal = this.meals.find(meal => meal.id === Number(this.$route.params.id));
          if (!this.meal) this.error = 'This meal no longer exists or is unavailable.';
        }
      } catch { this.error = 'Unable to load meals. Please try again.'; }
      finally { this.loading = false; }
    },
    leave() { this.$router.push(this.fixed_date ? {path: '/', query: {tab: 'calories'}} : {path: '/calories', query: {tab: 'meals'}}); },
    before_unload(event) { if (this.$refs.form?.dirty) { event.preventDefault(); event.returnValue = ''; } }
  }
};
</script>
