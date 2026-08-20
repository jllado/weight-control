<template>
  <div>
    <DataTable :value="lipid_panels" :paginator="true" :rows="10" :loading="state.loading" responsiveLayout="scroll"
               paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
               currentPageReportTemplate="{first} to {last} of {totalRecords}">
      <template #header>
        <div class="table-header">
          Cholesterol
          <CreateLipidPanel @onSave="load_lipid_panels" />
        </div>
      </template>
      <Column header="Date" headerStyle="width: 111px">
        <template #body="panel">{{ panel.data.dateFormat }}</template>
      </Column>
      <Column v-for="column in columns" :key="column.key" :header="column.label" headerStyle="min-width: 155px">
        <template #body="panel">
          {{ panel.data[column.key] }} mg/dL
          <span :class="panel.data.metricStatus(column.key, state.user.profile.sex).className">{{ panel.data.metricStatus(column.key, state.user.profile.sex).label }}</span>
          <span class="extra_info" :class="panel.data.changeClass(column.key, panel.data[column.changeKey])">{{ panel.data.formatChange(panel.data[column.changeKey]) }}</span>
        </template>
      </Column>
      <Column headerStyle="width: 100px">
        <template #body="panel">
          <div style="width: 100px; text-align: center">
            <Button icon="pi pi-pencil" class="p-button-rounded p-button-success p-mr-2" @click="edit(panel.data)" />
            <Button icon="pi pi-trash" class="p-button-rounded p-button-warning" @click="remove(panel.data)" />
          </div>
        </template>
      </Column>
    </DataTable>
    <LipidPanelForm @onSave="load_lipid_panels" @onClose="close_edit" v-model:show="display_edit_modal" v-model:lipid_panel="lipid_panel" />
  </div>
</template>

<script>
import service from '../services/LipidPanelService';
import CreateLipidPanel from '@/components/CreateLipidPanel';
import LipidPanelForm from '@/components/LipidPanelForm';
import {userState} from '../state';

export default {
  components: {CreateLipidPanel, LipidPanelForm},
  data() {
    return {
      columns: [
        {key: 'totalCholesterol', changeKey: 'totalChange', label: 'Total'},
        {key: 'hdlCholesterol', changeKey: 'hdlChange', label: 'HDL'},
        {key: 'ldlCholesterol', changeKey: 'ldlChange', label: 'LDL'},
        {key: 'triglycerides', changeKey: 'triglyceridesChange', label: 'Triglycerides'}
      ],
      lipid_panel: null,
      lipid_panels: [],
      display_edit_modal: false,
      state: userState()
    };
  },
  async created() {
    await this.load_lipid_panels();
  },
  methods: {
    async load_lipid_panels() {
      this.state.loading = true;
      this.lipid_panels = await service.get_all();
      this.state.loading = false;
    },
    async remove(panel) {
      if (!confirm('Are you sure you want to delete this lipid panel?')) {
        return;
      }
      try {
        await service.delete(panel);
        await this.load_lipid_panels();
      } catch (e) {
        this.handle_error(e);
      }
    },
    edit(panel) {
      this.lipid_panel = Object.assign({}, panel);
      this.display_edit_modal = true;
    },
    close_edit() {
      this.display_edit_modal = false;
    },
    handle_error(e) {
      this.$log.error(e);
      this.$toast.add({severity: 'error', summary: 'Failed', detail: e, life: 3000});
    }
  }
};
</script>
