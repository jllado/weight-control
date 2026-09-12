<template>
  <div>
    <p v-if="refresh_error" role="alert">{{ refresh_error }} <Button label="Retry" class="p-button-text" @click="load_blood_pressures" /></p>
    <DataTable :value="this.blood_pressures" :paginator="true" :rows="10" :loading="this.state.loading" responsiveLayout="scroll"
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
      <Column headerStyle="width: 100px" >
        <template #body="blood_pressure">
          <div class="action-group action-group--compact">
            <CompactAction icon="pi pi-pencil" @click="edit(blood_pressure.data)" aria-label="Edit" />
            <CompactAction icon="pi pi-trash" :action="() => remove(blood_pressure.data)" busyLabel="Deleting…" aria-label="Delete" destructive />
          </div>
        </template>
      </Column>
    </DataTable>
    <BloodPressureForm @onSave="load_blood_pressures" @onClose="close_edit" v-model:show="display_edit_modal" v-model:blood_pressure="blood_pressure" />

  </div>
</template>

<script>
import service from '../services/BloodPressureService';
import CreateBloodPressure from "@/components/CreateBloodPressure.vue";
import BloodPressureForm from "@/components/BloodPressureForm.vue";
import { userState } from '../state';

export default {
  components: {CreateBloodPressure, BloodPressureForm},
  data() {
    return {
      blood_pressure: null,
      blood_pressures: [],
      display_edit_modal: false,
      refresh_error: '',
      state: userState()
    }
  },
  async created () {
    await this.load_blood_pressures();
  },
  methods: {
    async load_blood_pressures() {
      this.state.loading = true;
      this.refresh_error = '';
      try {
        this.blood_pressures = await service.get_all_by(this.state.user.mail);
      } catch (error) {
        this.refresh_error = 'Unable to refresh entries. ' + error.message;
      } finally {
        this.state.loading = false;
      }
    },
    async remove(blood_pressure) {
      if (!confirm('Are you sure you want to delete this?')) {
        return;
      }
      await service.delete(blood_pressure)
          .then(async () => {
            await this.load_blood_pressures();
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
@media ( max-width: 650px ) {
  .trash-button {
    margin-top: 5px !important;
  }
}
</style>
