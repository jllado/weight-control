<template>
  <div>
    <DataTable :value="this.blood_pressures" :paginator="true" :rows="10" :loading="this.state.loading"
               paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
               currentPageReportTemplate="{first} to {last} of {totalRecords}" >
      <template #header>
        <div class="table-header">
          Blood Pressures
          <CreateBloodPressure @onSave="load_blood_pressures" />
        </div>
      </template>
      <Column header="Date" headerStyle="width: 111px">
        <template #body="blood_pressure" >
          {{ blood_pressure.data.dateFormat }}
        </template>
      </Column>
      <Column header="Status" headerStyle="width: 111px">
        <template #body="blood_pressure" >
          <span :style="{color: blood_pressure.data.stage().color}">{{ blood_pressure.data.stage().name }}</span>
        </template>
      </Column>
      <Column header="Upper" >
        <template #body="blood_pressure" >
          {{ blood_pressure.data.upper }} mm Hg <span class="extra_info" v-bind:class="{'bad': blood_pressure.data.lost_upper > 0, 'good': blood_pressure.data.lost_upper <= 0}">{{ blood_pressure.data.lost_upper >= 0 ? '+' : '' }}{{ blood_pressure.data.lost_upper }} mm Hg</span>
        </template>
      </Column>
      <Column header="Lower" >
        <template #body="blood_pressure" >
          {{ blood_pressure.data.lower }} mm Hg <span class="extra_info" v-bind:class="{'bad': blood_pressure.data.lost_lower > 0, 'good': blood_pressure.data.lost_lower <= 0}">{{ blood_pressure.data.lost_lower >= 0 ? '+' : '' }}{{ blood_pressure.data.lost_lower }} mm Hg</span>
        </template>
      </Column>
      <Column bodyStyle="text-align: center" headerClass="button-column" >
        <template #body="blood_pressure">
          <Button icon="pi pi-pencil" class="p-button-rounded p-button-success p-mr-2" @click="edit(blood_pressure.data)" />
          <Button icon="pi pi-trash" class="p-button-rounded p-button-warning trash-button" @click="remove(blood_pressure.data)" />
        </template>
      </Column>
    </DataTable>
    <BloodPressureForm @onSave="load_blood_pressures" @onClose="close_edit" v-model:show="display_edit_modal" v-model:blood_pressure="blood_pressure" />

  </div>
</template>

<script>
import service from '../services/BloodPressureService';
import CreateBloodPressure from "@/components/CreateBloodPressure";
import BloodPressureForm from "@/components/BloodPressureForm";
import { userState } from '../state';

export default {
  components: {CreateBloodPressure, BloodPressureForm},
  data() {
    return {
      blood_pressure: null,
      blood_pressures: [],
      display_edit_modal: false,
      state: userState()
    }
  },
  async created () {
    await this.load_blood_pressures();
  },
  methods: {
    async load_blood_pressures() {
      this.state.loading = true;
      this.blood_pressures = await service.get_all_by(this.state.user.mail);
      this.state.loading = false;
    },
    async remove(blood_pressure) {
      if (!confirm('Are you sure you want to delete this?')) {
        return;
      }
      service.delete(blood_pressure)
          .then(() => {
            this.load_blood_pressures();
          })
          .catch(e => {
            this.handle_error(e)
          });
    },
    async edit(blood_pressure) {
      this.blood_pressure = Object.assign({}, blood_pressure);
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

<style>
@media ( min-width: 650px ) {
  .button-column {
    width: 112px;
  }
}
@media ( max-width: 650px ) {
  .trash-button {
    margin-top: 5px !important;
  }
}
</style>
