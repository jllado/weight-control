<template>
  <div>
    <DataTable :value="this.calories" :paginator="true" :rows="10" :loading="this.state.loading" responsiveLayout="scroll"
               paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
               currentPageReportTemplate="{first} to {last} of {totalRecords}" >
      <template #header>
        <div class="table-header">
          Calories
          <CreateCalorie @onSave="load_calories" />
        </div>
      </template>
      <Column header="Date" headerStyle="width: 111px">
        <template #body="calorie" >
          {{ calorie.data.dateFormat }}
        </template>
      </Column>
      <Column header="Calories">
        <template #body="calorie" >
          {{ calorie.data.calories }} kcal
        </template>
      </Column>
      <Column headerStyle="width: 100px" >
        <template #body="calorie">
          <div style="width: 100px; text-align: center">
            <Button icon="pi pi-pencil" class="p-button-rounded p-button-success p-mr-2" @click="edit(calorie.data)" />
            <Button icon="pi pi-trash" class="p-button-rounded p-button-warning" @click="remove(calorie.data)" />
          </div>
        </template>
      </Column>
    </DataTable>
    <CalorieForm @onSave="load_calories" @onClose="close_edit" v-model:show="display_edit_modal" v-model:calorie="calorie" />
  </div>
</template>

<script>
import service from '../services/CalorieService';
import CreateCalorie from "@/components/CreateCalorie";
import CalorieForm from "@/components/CalorieForm";
import { userState } from '../state';

export default {
  components: {CreateCalorie, CalorieForm},
  data() {
    return {
      calorie: null,
      calories: [],
      display_edit_modal: false,
      state: userState()
    }
  },
  async created () {
    await this.load_calories();
  },
  methods: {
    async load_calories() {
      this.state.loading = true;
      this.calories = await service.get_all();
      this.state.loading = false;
    },
    async remove(calorie) {
      if (!confirm('Are you sure you want to delete this?')) {
        return;
      }
      service.delete(calorie)
          .then(() => {
            this.load_calories();
          })
          .catch(e => {
            this.handle_error(e)
          });
    },
    async edit(calorie) {
      this.calorie = Object.assign({}, calorie);
      this.display_edit_modal = true;
    },
    close_edit() {
      this.display_edit_modal = false;
    },
    handle_error(e) {
      this.$log.error(e);
      this.$toast.add({severity:'error', summary: 'Failed', detail: e, life: 3000});
    }
  }
}
</script>
