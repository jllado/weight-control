<template>
  <div>
    <DataTable :value="entries" :loading="loading" :paginator="true" :rows="10" v-model:first="first" class="decision-history"
               paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink"
               currentPageReportTemplate="{first} to {last} of {totalRecords}">
      <template #header><strong>Wins and misses</strong></template>
      <template #empty>No wins or misses recorded.</template>
      <Column header="Date" class="decision-date"><template #body="{data}">{{ data.dateFormat }}</template></Column>
      <Column header="Outcome" class="decision-outcome"><template #body="{data}"><Tag :value="data.outcome" :severity="data.outcome === 'WIN' ? 'success' : 'danger'" /></template></Column>
      <Column header="Reason"><template #body="{data}"><span class="decision-reason">{{ data.reason || 'No reason' }}</span></template></Column>
      <Column header="Actions" class="decision-actions"><template #body="{data}">
        <div class="action-group action-group--compact">
          <CompactAction icon="pi pi-pencil" :disabled="deleting || loading" :aria-label="`Edit reason for ${data.outcome} on ${data.dateFormat}`" @click="entry = data" />
          <CompactAction icon="pi pi-trash" :disabled="deleting || loading" :aria-label="`Delete ${data.outcome} on ${data.dateFormat}`" :action="() => remove(data)" busyLabel="Deleting…" destructive />
        </div>
      </template></Column>
    </DataTable>
    <div v-if="error" class="history-error" role="alert"><span>{{ error }}</span><Button label="Retry" class="p-button-outlined" :disabled="deleting || loading" @click="load_entries" /></div>
    <DecisionOutcomeForm v-if="entry" :entry="entry" @onClose="entry = null" @onSave="load_entries" />
  </div>
</template>

<script>
import service from '../services/DecisionOutcomeService';
import Tag from 'primevue/tag';
import DecisionOutcomeForm from './DecisionOutcomeForm.vue';

export default {
  components: {DecisionOutcomeForm, Tag},
  data() { return {entries: [], first: 0, loading: false, deleting: false, entry: null, error: ''}; },
  created() { this.load_entries(); },
  methods: {
    async remove(entry) {
      if (!confirm(`Delete ${entry.outcome} on ${entry.dateFormat}?`)) return;
      this.deleting = true;
      try {
        await service.delete(entry.id);
        this.entries = this.entries.filter(value => value.id !== entry.id);
        this.clamp_page();
        await this.load_entries();
      } finally { this.deleting = false; }
    },
    clamp_page() {
      this.first = Math.min(this.first, Math.max(0, Math.ceil(this.entries.length / 10) - 1) * 10);
    },
    async load_entries() {
      this.loading = true;
      this.error = '';
      try { this.entries = await service.history(); this.clamp_page(); }
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
.decision-history :deep(.decision-actions) { width: 7rem; }
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
