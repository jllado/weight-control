<template>
  <div>
    <DataTable :value="this.weights" :paginator="true" :rows="10" :loading="this.state.loading" responsiveLayout="scroll"
               paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
               currentPageReportTemplate="{first} to {last} of {totalRecords}" >
      <template #header>
        <div class="table-header">
          Weights
          <CreateWeight @onSave="load_weights" />
        </div>
      </template>
      <Column header="Date" headerStyle="width: 111px">
        <template #body="weight" >
          {{ weight.data.dateFormat }}
        </template>
      </Column>
      <Column header="Weight" >
        <template #body="weight" >
          {{ weight.data.weight }}kg <span class="extra_info" v-bind:class="{'bad': weight.data.lost_weight > 0, 'good': weight.data.lost_weight <= 0}">{{ weight.data.lost_weight > 0 ? '+' : '' }}{{ weight.data.lost_weight }}kg</span>
        </template>
      </Column>
      <Column header="Fat" >
        <template #body="weight" >
          {{ weight.data.fat }}kg <span class="extra_info">{{ weight.data.fat_percentage }}%</span> <span class="extra_info" v-bind:class="{'bad': weight.data.lost_fat > 0, 'good': weight.data.lost_fat <= 0}">{{ weight.data.lost_fat > 0 ? '+' : '' }}{{ weight.data.lost_fat }}kg</span>
        </template>
      </Column>
      <Column header="Muscle" headerClass="mobile-none" bodyClass="mobile-none">
        <template #body="weight">
          {{ weight.data.muscle }}kg <span class="extra_info">{{ weight.data.muscle_percentage }}%</span> <span class="extra_info" v-bind:class="{'good': weight.data.lost_muscle >= 0, 'bad': weight.data.lost_muscle < 0}">{{ weight.data.lost_muscle > 0 ? '+' : '' }}{{ weight.data.lost_muscle }}kg</span>
        </template>
      </Column>
      <Column header="Status" headerClass="mobile-none" bodyClass="mobile-none">
        <template #body="weight">
          <span :style="{color: weight.data.status().color}">{{ weight.data.status().name }}</span>
        </template>
      </Column>
      <Column header="BMI" headerClass="mobile-none" bodyClass="mobile-none">
        <template #body="weight">
          <span :style="{color: weight.data.bmi().status().color}">{{ weight.data.bmi().status().name }}</span>
        </template>
      </Column>
      <Column headerStyle="width: 100px" >
        <template #body="weight">
          <div style="width: 100px; text-align: center">
            <Button icon="pi pi-pencil" class="p-button-rounded p-button-success p-mr-2" @click="edit(weight.data)" />
            <Button icon="pi pi-trash" class="p-button-rounded p-button-warning" @click="remove(weight.data)" />
          </div>
        </template>
      </Column>
    </DataTable>
    <WeightForm @onSave="load_weights" @onClose="close_edit" v-model:show="display_edit_modal" v-model:weight="weight" />

  </div>
</template>

<script>
import service from '../services/WeightService';
import CreateWeight from "@/components/CreateWeight";
import WeightForm from "@/components/WeightForm";
import { userState } from '../state';

export default {
  components: {CreateWeight, WeightForm},
  data() {
    return {
      weight: null,
      weights: [],
      display_edit_modal: false,
      state: userState()
    }
  },
  async created () {
    await this.load_weights();
  },
  methods: {
    async load_weights() {
      this.state.loading = true;
      this.weights = await service.get_all_by(this.state.user.mail);
      this.state.loading = false;
    },
    async remove(weight) {
      if (!confirm('Are you sure you want to delete this?')) {
        return;
      }
      service.delete(weight)
          .then(() => {
            this.load_weights();
          })
          .catch(e => {
            this.handle_error(e)
          });
    },
    async edit(weight) {
      this.weight = Object.assign({}, weight);
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
