<template>
  <div>
    <DataTable :value="this.sleeps" :paginator="true" :rows="10" :loading="this.state.loading" responsiveLayout="scroll"
               paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
               currentPageReportTemplate="{first} to {last} of {totalRecords}">
      <template #header>
        <div class="table-header">
          Sleep
          <CreateSleep @onSave="load_sleeps" />
        </div>
      </template>
      <Column header="Date" headerStyle="width: 110px">
        <template #body="sleep">
          {{ sleep.data.dateFormat }}
        </template>
      </Column>
      <Column header="Total Sleep">
        <template #body="sleep">
          {{ sleep.data.totalSleepDurationFormat() }}
        </template>
      </Column>
      <Column header="Deep / REM / Light" headerClass="mobile-none" bodyClass="mobile-none">
        <template #body="sleep">
          {{ sleep.data.deepSleepDurationFormat() }} / {{ sleep.data.remSleepDurationFormat() }} / {{ sleep.data.lightSleepDurationFormat() }}
        </template>
      </Column>
      <Column header="Awake" headerClass="mobile-none" bodyClass="mobile-none">
        <template #body="sleep">
          {{ sleep.data.awakeTimeFormat() }}
        </template>
      </Column>
      <Column header="HR / HRV" headerClass="mobile-none" bodyClass="mobile-none">
        <template #body="sleep">
          {{ sleep.data.heartRateFormat() }} / {{ sleep.data.hrvFormat() }}
        </template>
      </Column>
      <Column header="Bedtime" headerClass="mobile-none" bodyClass="mobile-none">
        <template #body="sleep">
          {{ sleep.data.bedtimeWindowFormat() }}
        </template>
      </Column>
      <Column headerStyle="width: 100px">
        <template #body="sleep">
          <div style="width: 100px; text-align: center">
            <Button icon="pi pi-pencil" class="p-button-rounded p-button-success p-mr-2" @click="edit(sleep.data)" />
            <Button icon="pi pi-trash" class="p-button-rounded p-button-warning" @click="remove(sleep.data)" />
          </div>
        </template>
      </Column>
    </DataTable>
    <SleepForm @onSave="load_sleeps" @onClose="close_edit" v-model:show="display_edit_modal" v-model:sleep="sleep" />
  </div>
</template>

<script>
import service from '../services/SleepService';
import CreateSleep from "@/components/CreateSleep";
import SleepForm from "@/components/SleepForm";
import { userState } from '../state';

export default {
  components: {CreateSleep, SleepForm},
  data() {
    return {
      sleep: null,
      sleeps: [],
      display_edit_modal: false,
      state: userState()
    }
  },
  async created() {
    await this.load_sleeps();
  },
  methods: {
    async load_sleeps() {
      this.state.loading = true;
      this.sleeps = await service.get_all();
      this.state.loading = false;
    },
    async remove(sleep) {
      if (!confirm('Are you sure you want to delete this?')) {
        return;
      }
      service.delete(sleep)
          .then(() => {
            this.load_sleeps();
          })
          .catch(e => {
            this.handle_error(e)
          });
    },
    async edit(sleep) {
      this.sleep = Object.assign({}, sleep);
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
