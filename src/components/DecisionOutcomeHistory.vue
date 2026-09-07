<template>
  <div>
    <DataTable :value="entries" :loading="loading" :paginator="true" :rows="10" class="decision-history"
               paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink"
               currentPageReportTemplate="{first} to {last} of {totalRecords}">
      <template #header><strong>Wins and misses</strong></template>
      <template #empty>No wins or misses recorded.</template>
      <Column header="Date" class="decision-date"><template #body="{data}">{{ data.dateFormat }}</template></Column>
      <Column header="Outcome" class="decision-outcome"><template #body="{data}"><Tag :value="data.outcome" :severity="data.outcome === 'WIN' ? 'success' : 'danger'" /></template></Column>
      <Column header="Reason"><template #body="{data}"><span class="decision-reason">{{ data.reason || 'No reason' }}</span></template></Column>
      <Column header="Actions" class="decision-actions"><template #body="{data}"><Button icon="pi pi-pencil" class="p-button-rounded p-button-outlined" :aria-label="`Edit reason for ${data.outcome} on ${data.dateFormat}`" @click="entry = data" /></template></Column>
    </DataTable>
    <div v-if="error" class="history-error" role="alert"><span>{{ error }}</span><Button label="Retry" class="p-button-outlined" @click="load_entries" /></div>
    <DecisionOutcomeForm v-if="entry" :entry="entry" @onClose="entry = null" @onSave="load_entries" />
  </div>
</template>

<script>
import service from '../services/DecisionOutcomeService';
import Tag from 'primevue/tag';
import DecisionOutcomeForm from './DecisionOutcomeForm.vue';

export default {
  components: {DecisionOutcomeForm, Tag},
  data() { return {entries: [], loading: false, entry: null, error: ''}; },
  created() { this.load_entries(); },
  methods: {
    async load_entries() {
      this.loading = true;
      this.error = '';
      try { this.entries = await service.history(); }
      catch (error) { this.error = error.message; }
      finally { this.loading = false; }
    }
  }
}
</script>

<style scoped>
.decision-history :deep(table) { table-layout: fixed; width: 100%; }
.decision-history :deep(.decision-date) { width: 7rem; }
.decision-history :deep(.decision-outcome) { width: 6rem; }
.decision-history :deep(.decision-actions) { width: 5rem; }
.decision-reason { white-space: pre-wrap; overflow-wrap: anywhere; }
.history-error { display: flex; align-items: center; flex-wrap: wrap; gap: .5rem; margin-top: 1rem; overflow-wrap: anywhere; }
@media (max-width: 640px) {
  .decision-history :deep(.p-datatable-thead) { display: none; }
  .decision-history :deep(.p-datatable-tbody > tr) { display: flex; flex-wrap: wrap; padding: .5rem; border-bottom: 1px solid #ddd; }
  .decision-history :deep(.p-datatable-tbody > tr > td) { display: block; border: 0; padding: .5rem; }
  .decision-history :deep(.p-datatable-tbody > tr > td:nth-child(3)) { width: 100%; order: 1; }
  .decision-history :deep(.decision-actions) { margin-left: auto; }
}
</style>
