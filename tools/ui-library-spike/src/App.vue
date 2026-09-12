<template>
  <main>
    <h1>Weight Control</h1>
    <p>UI library evaluation · Synthetic data</p>
    <TabView v-if="reference" :activeIndex="sections.indexOf(section)" @update:activeIndex="section = sections[$event]"><TabPanel header="History" /><TabPanel header="Workout" /><TabPanel header="Charts" /></TabView>
    <Tabs v-else v-model:value="section"><TabList><Tab value="history">History</Tab><Tab value="workout">Workout</Tab><Tab value="chart">Charts</Tab></TabList></Tabs>
    <nav class="actions" aria-label="Actions">
      <Button aria-haspopup="true" aria-controls="actions-menu" @click="$refs.menu.toggle($event)">More</Button>
    </nav>
    <Menu id="actions-menu" ref="menu" :model="menu" :popup="true" />
    <Toast />
    <section v-if="section === 'history'" aria-label="Weight history">
      <h2>Weight</h2>
      <div class="actions">
        <Button ref="newButton" @click="openForm">New</Button>
        <label for="filter">Filter history</label><InputText id="filter" v-model="filter" />
        <Button aria-label="Show history information" @click="$refs.info.toggle($event)">Information</Button>
      </div>
      <InfoOverlay ref="info"><p>Measurements use local calendar dates and kilograms.</p></InfoOverlay>
      <DataTable :value="filteredRows" dataKey="id" :paginator="true" :rows="5" responsiveLayout="scroll" :tableStyle="{minWidth: '34rem'}">
        <Column field="date" header="Date" sortable :style="{minWidth: '8rem', whiteSpace: 'nowrap'}" /><Column field="weight" header="Weight (kg)" sortable :style="{minWidth: '8rem'}" />
        <Column field="note" header="Notes" /><template #empty>No measurements found.</template>
      </DataTable>
    </section>
    <section v-if="section === 'workout'" aria-label="Workout selection">
      <h2>Workout</h2><p>Select exercises and preserve their order.</p>
      <PickList v-model="exercises" dataKey="id" breakpoint="640px" :sourceHeader="'Available'" :targetHeader="'Selected'" :showSourceControls="false" :showTargetControls="true">
        <template #item="{item}">{{ item.name }}</template>
        <template #option="{option}">{{ option.name }}</template>
      </PickList>
      <p role="status">Selected: {{ exercises[1].map(item => item.name).join(', ') }}</p>
    </section>
    <section v-if="section === 'chart'" aria-label="Weight chart"><h2>Progress</h2><TrendChart /></section>
    <Dialog v-model:visible="visible" header="Weight" modal appendTo="body" :style="{width: '32rem'}" :breakpoints="{'575px': '94vw'}" :closable="false" :closeOnEscape="false" @hide="restoreFocus">
      <form id="measurement" @submit.prevent="save">
        <SaveFields :saving="saving">
          <div class="fields">
            <div><label for="date">Date</label><DateInput inputId="date" v-model="draft.date" dateFormat="dd/mm/yy" showIcon :disabled="saving" /></div>
            <div><label for="weight">Weight (kg)</label><InputNumber inputId="weight" v-model="draft.weight" :minFractionDigits="2" :maxFractionDigits="2" :disabled="saving" /></div>
            <div><label for="period">Period</label><SelectInput inputId="period" v-model="draft.period" :options="periods" :disabled="saving" /></div>
            <div><label for="tags">Tags</label><MultiSelect inputId="tags" v-model="draft.tags" :options="tags" :disabled="saving" /></div>
          </div>
          <p v-if="validation" role="alert">{{ validation }}</p>
          <div class="photo">
            <FileUpload mode="basic" chooseLabel="Choose Front Photo" accept="image/*" :auto="true" :customUpload="true" :disabled="saving" @uploader="selectPhoto" />
            <img v-if="photo" :src="photo" alt="Selected front photo" width="64" height="80" />
          </div>
          <label class="failure"><input type="checkbox" v-model="failSave"> Simulate a failed save</label>
        </SaveFields>
      </form>
      <p v-if="error" role="alert">{{ error }}</p>
      <template #footer><div class="actions footer"><Button :disabled="saving" class="p-button-secondary" @click="visible = false">Cancel</Button><Button type="submit" form="measurement" :disabled="saving" :aria-busy="saving"><i v-if="saving" class="pi pi-spin pi-spinner" aria-hidden="true" />{{ saving ? 'Saving…' : 'Save' }}</Button></div></template>
    </Dialog>
    <p class="result" role="status">Saved measurements: {{ saves }}</p>
  </main>
</template>
<script>
import {defineAsyncComponent} from 'vue';
import SaveFields from '../../../src/components/SaveFields.vue';
export default {
  inject: ['reference'],
  components: {SaveFields, TrendChart: defineAsyncComponent(() => import('./TrendChart.vue'))},
  data() {
    return {
      sections: ['history', 'workout', 'chart'], section: 'history', filter: '', visible: false, saving: false, validation: '', error: '', photo: '', failSave: false, saves: 0,
      draft: {date: new Date(2026, 8, 10, 12), weight: 80, period: 'Morning', tags: []}, periods: ['Morning', 'Evening'], tags: ['At home', 'After exercise', 'A deliberately long label for checking wrapping'],
      rows: [{id: 1, date: '10/09/2026', weight: 80, note: 'Morning'}, {id: 2, date: '09/09/2026', weight: 80.5, note: 'After exercise'}, {id: 3, date: '08/09/2026', weight: 81, note: 'A deliberately long measurement note that must wrap inside the table'}],
      exercises: [[{id: 1, name: 'Walk'}], [{id: 2, name: 'Squat'}, {id: 3, name: 'Stretch'}]],
      menu: [{label: 'Show saved count', command: () => this.$toast.add({severity: 'info', summary: 'Saved measurements', detail: String(this.saves), life: 3000})}]
    };
  },
  computed: {filteredRows() { return this.rows.filter(row => Object.values(row).join(' ').toLowerCase().includes(this.filter.toLowerCase())); }},
  methods: {
    openForm() { this.error = ''; this.validation = ''; this.visible = true; },
    restoreFocus() { this.$refs.newButton.$el.focus(); },
    selectPhoto(event) { if (this.photo) URL.revokeObjectURL(this.photo); this.photo = URL.createObjectURL(event.files[0]); },
    async save() {
      this.validation = !this.draft.date || !this.draft.weight || this.draft.weight <= 0 || !this.draft.period || !this.draft.tags.length ? 'Enter a date, a positive weight, a period, and at least one tag.' : '';
      if (this.validation) return;
      this.saving = true; this.error = '';
      try {
        const response = await fetch('/simulation/measurements', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({...this.draft, fail: this.failSave})});
        if (!response.ok) throw new Error('Could not save. Your draft has been retained.');
        this.saves++; this.visible = false;
        this.$toast.add({severity: 'success', summary: 'Weight saved', life: 3000});
      } catch (error) { this.error = error.message; }
      finally { this.saving = false; }
    }
  },
  beforeUnmount() { if (this.photo) URL.revokeObjectURL(this.photo); }
};
</script>
<style>
:root { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; color: #333; background: #fff; }
* { box-sizing: border-box; }
body { margin: 0; } main { padding: 1rem; max-width: 1100px; margin: auto; }
h1 { font-size: 1.5rem; } h2 { font-size: 1.25rem; }
section { margin-top: 1rem; padding: 1rem; border: 1px solid #c8c8c8; border-radius: 3px; min-width: 0; }
.actions { display: flex; gap: .5rem; align-items: center; flex-wrap: wrap; margin-bottom: 1rem; }
.fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem; }
.p-multiselect-overlay { max-width: calc(100vw - 1rem); } .p-multiselect-option-label { white-space: normal; overflow-wrap: anywhere; }
.fields input { min-width: 0; } .fields > div { min-width: 0; } .fields label { display: block; margin-bottom: .4rem; }
.fields .p-inputtext, .fields .p-inputnumber, .fields .p-calendar, .fields .p-datepicker, .fields .p-select, .fields .p-dropdown, .fields .p-multiselect { width: 100%; }
.photo { display: flex; align-items: center; flex-wrap: wrap; gap: .75rem; margin: 1rem 0; } .photo img { object-fit: contain; }
.failure { display: flex; align-items: center; gap: .5rem; } .footer { justify-content: flex-end; margin: 0; }
.p-button { gap: .5rem; } .p-datatable td { overflow-wrap: anywhere; } .result { font-size: .875rem; }
@media(max-width: 575px) { .fields { grid-template-columns: minmax(0, 1fr); } section { padding: .5rem; } }
@media(prefers-reduced-motion: reduce) { *, *::before, *::after { animation-duration: .001ms !important; transition-duration: .001ms !important; } }
</style>
