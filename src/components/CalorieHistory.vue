<template>
  <div>
    <DataTable :value="meals" :paginator="true" :rows="10" :loading="state.loading" responsiveLayout="scroll"
               paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
               currentPageReportTemplate="{first} to {last} of {totalRecords}">
      <template #header>
        <div class="table-header">
          Meals
          <CreateMeal :meals="meals" @onSave="load_meals" />
        </div>
      </template>
      <Column header="Date" headerStyle="width: 111px">
        <template #body="row">{{ row.data.dateFormat }}</template>
      </Column>
      <Column header="Meal">
        <template #body="row">{{ row.data.label() }}</template>
      </Column>
      <Column header="Calories">
        <template #body="row">{{ row.data.calories }} kcal</template>
      </Column>
      <Column header="Protein">
        <template #body="row">{{ format_macro(row.data.proteinGrams) }}</template>
      </Column>
      <Column header="Carbohydrates">
        <template #body="row">{{ format_macro(row.data.carbohydrateGrams) }}</template>
      </Column>
      <Column header="Fat">
        <template #body="row">{{ format_macro(row.data.fatGrams) }}</template>
      </Column>
      <Column headerStyle="width: 100px">
        <template #body="row">
          <div style="width: 100px; text-align: center">
            <Button icon="pi pi-pencil" aria-label="Edit meal" class="p-button-rounded p-button-success p-mr-2" @click="edit(row.data)" />
            <Button icon="pi pi-trash" aria-label="Delete meal" class="p-button-rounded p-button-warning" @click="remove(row.data)" />
          </div>
        </template>
      </Column>
    </DataTable>
    <MealForm @onSave="load_meals" @onClose="close_edit" v-model:show="display_edit_modal" :meal="meal" :meals="meals" />
  </div>
</template>

<script>
import service from '../services/MealService';
import CreateMeal from "@/components/CreateMeal";
import MealForm from "@/components/MealForm";
import {userState} from '../state';

export default {
  components: {CreateMeal, MealForm},
  data() {
    return {
      meal: null,
      meals: [],
      display_edit_modal: false,
      state: userState()
    }
  },
  async created() {
    await this.load_meals();
  },
  methods: {
    async load_meals() {
      this.state.loading = true;
      this.meals = await service.get_all();
      this.state.loading = false;
    },
    async remove(meal) {
      if (!confirm('Are you sure you want to delete this meal?')) {
        return;
      }
      try {
        await service.delete(meal);
        await this.load_meals();
      } catch (e) {
        this.handle_error(e);
      }
    },
    edit(meal) {
      this.meal = Object.assign({}, meal);
      this.display_edit_modal = true;
    },
    close_edit() {
      this.display_edit_modal = false;
      this.meal = null;
    },
    format_macro(value) {
      return value === null ? '—' : `${value} g`;
    },
    handle_error(e) {
      this.$log.error(e);
      this.$toast.add({severity:'error', summary: 'Failed', detail: e, life: 3000});
    }
  }
}
</script>
