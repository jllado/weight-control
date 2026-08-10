<template>
  <div>
    <DataTable :value="back_statuses" :paginator="true" :rows="10" :loading="state.loading" responsiveLayout="scroll"
               paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
               currentPageReportTemplate="{first} to {last} of {totalRecords}">
      <template #header>
        <div class="table-header">
          Back Status
          <CreateBackStatus @onSave="load_back_statuses" />
        </div>
      </template>
      <Column header="Date" headerStyle="width: 110px">
        <template #body="status">
          {{ status.data.dateFormat }}
        </template>
      </Column>
      <Column v-for="region in regions" :key="region.key" :header="region.label" headerStyle="min-width: 190px">
        <template #body="status">
          <div v-for="metric in metrics" :key="metric.key" class="back-history-score">
            <span>{{ metric.label }}:</span>
            <span :class="score_band(status.data[region.key][metric.key]).className">{{ format_score(status.data[region.key][metric.key]) }}</span>
          </div>
        </template>
      </Column>
      <Column header="Note" headerStyle="min-width: 180px">
        <template #body="status">
          {{ status.data.note }}
        </template>
      </Column>
      <Column headerStyle="width: 100px">
        <template #body="status">
          <div style="width: 100px; text-align: center">
            <Button icon="pi pi-pencil" class="p-button-rounded p-button-success p-mr-2" @click="edit(status.data)" />
            <Button icon="pi pi-trash" class="p-button-rounded p-button-warning" @click="remove(status.data)" />
          </div>
        </template>
      </Column>
    </DataTable>
    <BackStatusForm @onSave="load_back_statuses" @onClose="close_edit" v-model:show="display_edit_modal" v-model:back_status="back_status" />
  </div>
</template>

<script>
import service from '../services/BackStatusService';
import CreateBackStatus from "@/components/CreateBackStatus";
import BackStatusForm from "@/components/BackStatusForm";
import {BACK_METRICS, BACK_REGIONS, formatBackScore, getBackScoreBand} from "@/model/BackStatus";
import {userState} from '../state';

export default {
  name: "BackStatusHistory",
  components: {CreateBackStatus, BackStatusForm},
  data() {
    return {
      back_status: null,
      back_statuses: [],
      display_edit_modal: false,
      regions: BACK_REGIONS,
      metrics: BACK_METRICS,
      state: userState()
    };
  },
  async created() {
    await this.load_back_statuses();
  },
  methods: {
    async load_back_statuses() {
      this.state.loading = true;
      this.back_statuses = await service.get_all();
      this.state.loading = false;
    },
    async remove(status) {
      if (!confirm('Are you sure you want to delete this?')) {
        return;
      }
      service.delete(status)
          .then(() => this.load_back_statuses())
          .catch(e => this.handle_error(e));
    },
    edit(status) {
      this.back_status = Object.assign({}, status);
      this.display_edit_modal = true;
    },
    close_edit() {
      this.display_edit_modal = false;
    },
    score_band(value) {
      return getBackScoreBand(value);
    },
    format_score(value) {
      return formatBackScore(value);
    },
    handle_error(e) {
      this.$log.error(e);
      this.$toast.add({severity: 'error', summary: 'Failed', detail: e, life: 3000});
    }
  }
};
</script>

<style scoped>
.back-history-score {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  line-height: 1.5;
}
</style>
