<template>
  <div>
    <DataTable :value="this.sicknesses" :paginator="true" :rows="10" :loading="this.state.loading" responsiveLayout="scroll"
               paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
               currentPageReportTemplate="{first} to {last} of {totalRecords}">
      <template #header>
        <div class="table-header">
          Sickness
          <CreateSickness @onSave="load_sicknesses" />
        </div>
      </template>
      <Column header="Date" headerStyle="width: 110px">
        <template #body="sickness">
          {{ sickness.data.dateFormat }}
        </template>
      </Column>
      <Column header="Type" headerStyle="width: 180px">
        <template #body="sickness">
          {{ sickness.data.typeLabel() }}
        </template>
      </Column>
      <Column header="Severity" headerStyle="width: 120px">
        <template #body="sickness">
          {{ sickness.data.severityLabel() }}
        </template>
      </Column>
      <Column header="Note">
        <template #body="sickness">
          {{ sickness.data.note }}
        </template>
      </Column>
      <Column headerStyle="width: 100px">
        <template #body="sickness">
          <div style="width: 100px; text-align: center">
            <Button icon="pi pi-pencil" class="p-button-rounded p-button-success p-mr-2" @click="edit(sickness.data)" />
            <Button icon="pi pi-trash" class="p-button-rounded p-button-warning" @click="remove(sickness.data)" />
          </div>
        </template>
      </Column>
    </DataTable>
    <SicknessForm @onSave="load_sicknesses" @onClose="close_edit" v-model:show="display_edit_modal" v-model:sickness="sickness" />
  </div>
</template>

<script>
import service from '../services/SicknessService';
import CreateSickness from "@/components/CreateSickness";
import SicknessForm from "@/components/SicknessForm";
import { userState } from '../state';

export default {
  components: {CreateSickness, SicknessForm},
  data() {
    return {
      sickness: null,
      sicknesses: [],
      display_edit_modal: false,
      state: userState()
    }
  },
  async created() {
    await this.load_sicknesses();
  },
  methods: {
    async load_sicknesses() {
      this.state.loading = true;
      this.sicknesses = await service.get_all();
      this.state.loading = false;
    },
    async remove(sickness) {
      if (!confirm('Are you sure you want to delete this?')) {
        return;
      }
      service.delete(sickness)
          .then(() => {
            this.load_sicknesses();
          })
          .catch(e => {
            this.handle_error(e)
          });
    },
    async edit(sickness) {
      this.sickness = Object.assign({}, sickness);
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
