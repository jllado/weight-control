<template>
  <TabView class="nutrition-tabs" :activeIndex="$route.query.tab === 'meals' ? 1 : 0">
    <TabPanel header="Daily summaries">
      <DataTable :value="daily_summaries" :paginator="true" :rows="10" :loading="state.loading" responsiveLayout="scroll"
                 paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
                 currentPageReportTemplate="{first} to {last} of {totalRecords}">
        <Column header="Date" headerStyle="width: 111px">
          <template #body="row">{{ row.data.dateFormat }}</template>
        </Column>
        <Column header="Calories"><template #body="row">{{ row.data.calories }} kcal</template></Column>
        <Column header="Protein"><template #body="row">{{ format_macro(row.data.proteinGrams, row.data, 4) }}</template></Column>
        <Column header="Carbohydrates"><template #body="row">{{ format_macro(row.data.carbohydrateGrams, row.data, 4) }}</template></Column>
        <Column header="Fat"><template #body="row">{{ format_macro(row.data.fatGrams, row.data, 9) }}</template></Column>
        <Column header="Macros"><template #body="row">{{ row.data.macrosComplete ? 'Complete' : 'Incomplete' }}</template></Column>
      </DataTable>
    </TabPanel>
    <TabPanel header="Meals">
      <DataTable :value="meals" :paginator="true" :rows="10" :loading="state.loading" responsiveLayout="scroll"
                 paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
                 currentPageReportTemplate="{first} to {last} of {totalRecords}">
        <template #header>
          <div class="table-header">Meals<CreateMeal :meals="meals" :fasting_periods="fasting_periods" @onSave="load_meals" /></div>
        </template>
        <Column header="Date" headerStyle="width: 111px"><template #body="row">{{ row.data.dateFormat }}</template></Column>
        <Column header="Meal"><template #body="row">{{ row.data.label() }}</template></Column>
        <Column header="Start time"><template #body="row">{{ row.data.mealTimeFormat() }}</template></Column>
        <Column header="Duration"><template #body="row">{{ row.data.durationMinutes == null ? '—' : `${row.data.durationMinutes} min` }}</template></Column>
        <Column header="Calories"><template #body="row">{{ row.data.calories }} kcal</template></Column>
        <Column header="Protein"><template #body="row">{{ format_macro(row.data.proteinGrams, row.data, 4) }}</template></Column>
        <Column header="Carbohydrates"><template #body="row">{{ format_macro(row.data.carbohydrateGrams, row.data, 4) }}</template></Column>
        <Column header="Fat"><template #body="row">{{ format_macro(row.data.fatGrams, row.data, 9) }}</template></Column>
        <Column header="Dishes"><template #body="row"><div v-for="dish in row.data.dishes" :key="dish.id">{{ dish.name }} · {{ dish.calories }} kcal</div><span v-if="!row.data.dishes.length">—</span></template></Column>
        <Column header="Source"><template #body="row">{{ row.data.sourceLabel() }}</template></Column>
        <Column header="Notes"><template #body="row">{{ row.data.notes || '—' }}</template></Column>
        <Column headerStyle="width: 100px">
          <template #body="row">
            <div class="nutrition-row-actions">
              <Button icon="pi pi-pencil" aria-label="Edit meal" class="p-button-rounded p-button-success p-mr-2" @click="edit_meal(row.data)" />
              <Button icon="pi pi-trash" aria-label="Delete meal" class="p-button-rounded p-button-warning" @click="remove_meal(row.data)" />
            </div>
          </template>
        </Column>
      </DataTable>
    </TabPanel>
    <TabPanel header="Fasting periods">
      <DataTable :value="automatic_fasting_periods" :paginator="true" :rows="10" :loading="state.loading" responsiveLayout="scroll"
                 paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
                 currentPageReportTemplate="{first} to {last} of {totalRecords}">
        <template #header>
          <div class="table-header">Automatically calculated fasting periods</div>
        </template>
        <Column header="Start"><template #body="row">{{ row.data.startTimeFormat }}</template></Column>
        <Column header="End"><template #body="row">{{ row.data.endTimeFormat }}</template></Column>
        <Column header="Duration"><template #body="row">{{ row.data.durationFormat(now) }}</template></Column>
      </DataTable>
      <DataTable :value="manual_fasting_periods" :paginator="true" :rows="10" :loading="state.loading" responsiveLayout="scroll"
                 paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
                 currentPageReportTemplate="{first} to {last} of {totalRecords}">
        <template #header>
          <div class="table-header">Manual fasting periods<CreateFastingPeriod @onSave="load_fasting_periods" /></div>
        </template>
        <Column header="Start"><template #body="row">{{ row.data.startTimeFormat }}</template></Column>
        <Column header="End"><template #body="row">{{ row.data.endTimeFormat }}</template></Column>
        <Column header="Duration"><template #body="row">{{ row.data.durationFormat(now) }}</template></Column>
        <Column header="Notes"><template #body="row">{{ row.data.notes || '—' }}</template></Column>
        <Column headerStyle="width: 100px">
          <template #body="row">
            <div class="nutrition-row-actions">
              <Button icon="pi pi-pencil" aria-label="Edit fasting period" class="p-button-rounded p-button-success p-mr-2" @click="edit_fasting_period(row.data)" />
              <Button icon="pi pi-trash" aria-label="Delete fasting period" class="p-button-rounded p-button-warning" @click="remove_fasting_period(row.data)" />
            </div>
          </template>
        </Column>
      </DataTable>
    </TabPanel>
  </TabView>
  <FastingPeriodForm @onSave="load_fasting_periods" @onClose="close_fasting_period_edit" v-model:show="display_fasting_period_modal" :fasting_period="fasting_period" />
</template>

<script>
import mealService from '../services/MealService';
import fastingPeriodService from '../services/FastingPeriodService';
import nutritionService from '../services/NutritionService';
import CreateMeal from '@/components/CreateMeal';
import CreateFastingPeriod from '@/components/CreateFastingPeriod';
import FastingPeriodForm from '@/components/FastingPeriodForm';
import {userState} from '../state';

export default {
  components: {CreateMeal, CreateFastingPeriod, FastingPeriodForm},
  data() {
    return {
      daily_summaries: [],
      meals: [],
      fasting_periods: [],
      fasting_period: null,
      now: new Date(),
      duration_timer: null,
      display_fasting_period_modal: false,
      state: userState()
    };
  },
  computed: {
    automatic_fasting_periods() {
      return this.fasting_periods.filter(period => period.source === 'AUTOMATIC');
    },
    manual_fasting_periods() {
      return this.fasting_periods.filter(period => period.source !== 'AUTOMATIC');
    }
  },
  async created() {
    await this.load_all();
  },
  mounted() {
    this.duration_timer = setInterval(() => {
      this.now = new Date();
    }, 60000);
  },
  beforeUnmount() {
    clearInterval(this.duration_timer);
  },
  methods: {
    async load_all() {
      this.state.loading = true;
      [this.daily_summaries, this.meals, this.fasting_periods] = await Promise.all([
        nutritionService.get_daily_summaries(), mealService.get_all(), fastingPeriodService.get_all()
      ]);
      this.state.loading = false;
    },
    async load_meals() {
      this.state.loading = true;
      [this.daily_summaries, this.meals, this.fasting_periods] = await Promise.all([nutritionService.get_daily_summaries(), mealService.get_all(), fastingPeriodService.get_all()]);
      this.state.loading = false;
    },
    async load_fasting_periods() {
      this.state.loading = true;
      this.fasting_periods = await fastingPeriodService.get_all();
      this.state.loading = false;
    },
    async remove_meal(meal) {
      if (!confirm('Are you sure you want to delete this meal?')) {
        return;
      }
      try {
        await mealService.delete(meal);
        await this.load_meals();
      } catch (error) {
        this.handle_error(error);
      }
    },
    async remove_fasting_period(period) {
      if (!confirm('Are you sure you want to delete this fasting period?')) {
        return;
      }
      try {
        await fastingPeriodService.delete(period);
        await this.load_fasting_periods();
      } catch (error) {
        this.handle_error(error);
      }
    },
    edit_meal(meal) {
      this.$router.push({path: `/meals/${meal.id}/edit`, query: {from: 'history'}});
    },
    edit_fasting_period(period) {
      this.fasting_period = Object.assign({}, period);
      this.display_fasting_period_modal = true;
    },
    close_fasting_period_edit() {
      this.display_fasting_period_modal = false;
      this.fasting_period = null;
    },
    format_macro(value, nutrition, calories_per_gram) {
      if (value === null) {
        return '—';
      }
      if (nutrition.proteinGrams === null || nutrition.carbohydrateGrams === null || nutrition.fatGrams === null) {
        return `${value} g`;
      }
      const total_macro_calories = nutrition.proteinGrams * 4 + nutrition.carbohydrateGrams * 4 + nutrition.fatGrams * 9;
      const percentage = total_macro_calories === 0 ? 0 : Math.round(value * calories_per_gram * 100 / total_macro_calories);
      return `${value} g · ${percentage}%`;
    },
    handle_error(error) {
      this.$log.error(error);
      this.$toast.add({severity: 'error', summary: 'Failed', detail: error, life: 3000});
    }
  }
};
</script>

<style scoped>
.nutrition-row-actions {
  width: 100px;
  text-align: center;
}
</style>
