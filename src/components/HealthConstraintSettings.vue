<template>
  <Panel header="Health constraints" class="p-mt-3">
    <p>Record limitations and clinician guidance that the Coach should consider before giving advice.</p>
    <DataTable :value="constraints" :loading="loading" responsiveLayout="scroll" emptyMessage="No health constraints recorded">
      <template #header>
        <div class="table-header">
          <span>Constraints</span>
          <Button label="New" icon="pi pi-plus" @click="create" />
        </div>
      </template>
      <Column header="Status" headerStyle="width: 100px">
        <template #body="row">{{ row.data.status() }}</template>
      </Column>
      <Column header="Title" field="title" headerStyle="min-width: 180px" />
      <Column header="Type" headerStyle="min-width: 150px">
        <template #body="row">{{ row.data.typeLabel() }}</template>
      </Column>
      <Column header="Source" headerStyle="min-width: 140px">
        <template #body="row">{{ row.data.sourceLabel() }}</template>
      </Column>
      <Column header="Dates" headerStyle="min-width: 190px">
        <template #body="row">{{ row.data.dateRange() }}</template>
      </Column>
      <Column header="Details" field="details" headerStyle="min-width: 240px" />
      <Column headerStyle="min-width: 260px">
        <template #body="row">
          <div class="constraint-actions">
            <Button label="Edit" icon="pi pi-pencil" class="p-button-sm p-button-outlined" @click="edit(row.data)" />
            <Button :label="row.data.active ? 'Deactivate' : 'Reactivate'" :icon="row.data.active ? 'pi pi-times' : 'pi pi-check'" class="p-button-sm p-button-outlined p-button-warning" @click="toggle(row.data)" />
            <Button label="Delete" icon="pi pi-trash" class="p-button-sm p-button-outlined p-button-danger" @click="remove(row.data)" />
          </div>
        </template>
      </Column>
    </DataTable>
    <HealthConstraintForm :show="formVisible" :constraint="selected" @onSave="saved" @onClose="closeForm" />
  </Panel>
</template>

<script>
import HealthConstraint from '../model/HealthConstraint';
import healthConstraintService from '../services/HealthConstraintService';
import HealthConstraintForm from './HealthConstraintForm.vue';

export default {
  name: 'HealthConstraintSettings',
  components: {HealthConstraintForm},
  data() {
    return {constraints: [], selected: null, formVisible: false, loading: false};
  },
  async created() {
    await this.load();
  },
  methods: {
    async load() {
      this.loading = true;
      try {
        this.constraints = await healthConstraintService.getAll();
      } catch (e) {
        this.handleError(e);
      } finally {
        this.loading = false;
      }
    },
    create() {
      this.selected = null;
      this.formVisible = true;
    },
    edit(constraint) {
      this.selected = new HealthConstraint(constraint.toObject());
      this.formVisible = true;
    },
    async toggle(constraint) {
      const updated = new HealthConstraint(constraint.toObject());
      updated.active = !updated.active;
      try {
        await healthConstraintService.save(updated);
        await this.load();
      } catch (e) {
        this.handleError(e);
      }
    },
    async remove(constraint) {
      if (!confirm(`Are you sure you want to delete “${constraint.title}”?`)) {
        return;
      }
      try {
        await healthConstraintService.delete(constraint);
        await this.load();
      } catch (e) {
        this.handleError(e);
      }
    },
    async saved() {
      await this.load();
    },
    closeForm() {
      this.formVisible = false;
      this.selected = null;
    },
    handleError(e) {
      this.$log.error(e);
      this.$toast.add({severity: 'error', summary: 'Health constraints failed', detail: e, life: 3000});
    }
  }
};
</script>

<style scoped>
.constraint-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}
</style>
