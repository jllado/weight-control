<template>
  <div class="medications-page">
    <TabView>
      <TabPanel header="Manage">
        <DataTable :value="medications" :loading="loading" responsiveLayout="scroll">
          <template #header>
            <div class="table-header">
              Medications
              <Button label="New" icon="pi pi-plus" @click="createMedication" />
            </div>
          </template>
          <Column header="Medication" field="name" headerStyle="min-width: 180px" />
          <Column header="Dose" headerStyle="min-width: 120px">
            <template #body="row">{{ formatDose(row.data) }}</template>
          </Column>
          <Column header="Schedule" headerStyle="min-width: 240px">
            <template #body="row">{{ formatSchedule(row.data) }}</template>
          </Column>
          <Column header="Dates" headerStyle="min-width: 190px">
            <template #body="row">{{ formatDate(row.data.startDate) }} – {{ formatDate(row.data.endDate) }}</template>
          </Column>
          <Column header="Status" headerStyle="width: 100px">
            <template #body="row">{{ row.data.active ? 'Active' : 'Inactive' }}</template>
          </Column>
          <Column headerStyle="min-width: 190px">
            <template #body="row">
              <div class="medication-row-actions">
                <Button label="Log now" icon="pi pi-check" class="p-button-sm medication-log-button" @click="logDose(row.data)" />
                <Button icon="pi pi-pencil" aria-label="Edit medication" class="p-button-rounded p-button-success" @click="editMedication(row.data)" />
                <Button icon="pi pi-trash" aria-label="Delete medication" class="p-button-rounded p-button-warning" @click="removeMedication(row.data)" />
              </div>
            </template>
          </Column>
        </DataTable>
      </TabPanel>
      <TabPanel header="Dose log">
        <DataTable :value="doses" :loading="loadingDoses" :paginator="true" :rows="20" responsiveLayout="scroll"
                   currentPageReportTemplate="{first} to {last} of {totalRecords}">
          <template #empty>No medication doses recorded in the last 30 days.</template>
          <Column header="Scheduled" headerStyle="min-width: 180px">
            <template #body="row">{{ formatDateTime(row.data.scheduledAt) }}</template>
          </Column>
          <Column header="Medication" field="medicationName" headerStyle="min-width: 180px" />
          <Column header="Dose" headerStyle="min-width: 120px">
            <template #body="row">{{ formatDose(row.data) }}</template>
          </Column>
          <Column header="Status" headerStyle="min-width: 110px">
            <template #body="row">{{ statusLabel(row.data.status) }}</template>
          </Column>
          <Column header="Taken" headerStyle="min-width: 180px">
            <template #body="row">{{ row.data.takenAt ? formatDateTime(row.data.takenAt) : '—' }}</template>
          </Column>
          <Column header="Source" headerStyle="min-width: 100px">
            <template #body="row">{{ row.data.source === 'MANUAL' ? 'Manual' : 'Scheduled' }}</template>
          </Column>
        </DataTable>
      </TabPanel>
    </TabView>

    <Dialog appendTo="body" header="Medication" v-model:visible="formVisible" :closeOnEscape="false" :closable="false" :modal="true" class="medication-form-dialog">
      <div class="medication-form">
        <div class="medication-field medication-field-wide">
          <label for="medication-name">Medication name</label>
          <InputText id="medication-name" v-model.trim="form.name" />
        </div>
        <div class="medication-field">
          <label for="medication-dose">Dose</label>
          <InputNumber inputId="medication-dose" v-model="form.doseAmount" :min="0.001" :maxFractionDigits="3" />
        </div>
        <div class="medication-field">
          <label for="medication-unit">Unit</label>
          <InputText id="medication-unit" v-model.trim="form.doseUnit" placeholder="tablet, mg, mL…" />
        </div>
        <div class="medication-field">
          <label for="medication-start">Start date</label>
          <Calendar inputId="medication-start" v-model="form.startDate" dateFormat="dd/mm/yy" :manualInput="false" showIcon />
        </div>
        <div class="medication-field">
          <label for="medication-end">End date</label>
          <Calendar inputId="medication-end" v-model="form.endDate" dateFormat="dd/mm/yy" :manualInput="false" showIcon />
        </div>
        <div class="medication-field">
          <label for="medication-repeat-every">Repeat every</label>
          <InputNumber inputId="medication-repeat-every" v-model="form.repeatEvery" :min="1" :useGrouping="false" />
        </div>
        <div class="medication-field">
          <label for="medication-repeat-unit">Repeat unit</label>
          <Dropdown inputId="medication-repeat-unit" v-model="form.repeatUnit" :options="repeatUnits" optionLabel="label" optionValue="value" />
        </div>
        <div class="medication-field medication-field-wide">
          <label>Exact reminder times (Europe/Madrid)</label>
          <div v-for="(time, index) in form.reminderTimeValues" :key="index" class="medication-reminder-row">
            <Calendar :inputId="`medication-reminder-${index}`" v-model="form.reminderTimeValues[index]" :timeOnly="true" hourFormat="24" :stepMinute="5" :manualInput="false" showIcon />
            <Button icon="pi pi-trash" :aria-label="`Remove reminder ${index + 1}`" class="p-button-rounded p-button-text p-button-danger" @click="removeReminder(index)" />
          </div>
          <Button label="Add reminder" icon="pi pi-plus" class="p-button-outlined" @click="addReminder" />
          <small v-if="hasDuplicateTimes" class="error">Reminder times must be unique.</small>
        </div>
        <div class="medication-field medication-field-wide">
          <label for="medication-notes">Notes</label>
          <textarea id="medication-notes" v-model.trim="form.notes" class="p-inputtext p-component" rows="3" />
        </div>
        <label class="medication-active-field">
          <input type="checkbox" v-model="form.active">
          Active
        </label>
      </div>
      <template #footer>
        <Button label="Save" icon="pi pi-check" :disabled="!formValid" :loading="saving" @click="saveMedication" />
        <Button label="Cancel" icon="pi pi-times" class="p-button-secondary" :disabled="saving" @click="formVisible = false" />
      </template>
    </Dialog>
  </div>
</template>

<script>
import dayjs from 'dayjs';
import Medication from '../model/Medication';
import medicationService from '../services/MedicationService';

function initialForm(medication = new Medication()) {
  return {
    id: medication.id,
    name: medication.name,
    doseAmount: medication.doseAmount,
    doseUnit: medication.doseUnit,
    notes: medication.notes,
    startDate: new Date(medication.startDate),
    endDate: new Date(medication.endDate),
    repeatEvery: medication.repeatEvery,
    repeatUnit: medication.repeatUnit,
    reminderTimeValues: medication.reminderTimes.map(time => parseTime(time)),
    active: medication.active
  };
}

function parseTime(time) {
  const [hours, minutes] = time.split(':').map(Number);
  const value = new Date();
  value.setHours(hours, minutes, 0, 0);
  return value;
}

export default {
  data() {
    return {
      medications: [],
      doses: [],
      form: initialForm(),
      formVisible: false,
      loading: false,
      loadingDoses: false,
      saving: false,
      repeatUnits: [
        {label: 'Days', value: 'DAY'},
        {label: 'Weeks', value: 'WEEK'}
      ]
    };
  },
  computed: {
    serializedReminderTimes() {
      return this.form.reminderTimeValues.map(value => this.serializeTime(value));
    },
    hasDuplicateTimes() {
      return new Set(this.serializedReminderTimes).size !== this.serializedReminderTimes.length;
    },
    formValid() {
      return this.form.name && this.form.doseAmount > 0 && this.form.doseUnit && this.form.startDate && this.form.endDate
          && !dayjs(this.form.startDate).isAfter(this.form.endDate, 'day') && this.form.repeatEvery >= 1
          && this.form.reminderTimeValues.length > 0 && !this.hasDuplicateTimes;
    }
  },
  async created() {
    await Promise.all([this.loadMedications(), this.loadDoses()]);
  },
  methods: {
    newForm(medication = new Medication()) {
      return initialForm(medication);
    },
    async loadMedications() {
      this.loading = true;
      try {
        this.medications = await medicationService.getAll();
      } catch (e) {
        this.handleError(e);
      } finally {
        this.loading = false;
      }
    },
    async loadDoses() {
      this.loadingDoses = true;
      try {
        this.doses = await medicationService.getDoses(dayjs().subtract(30, 'day'), new Date());
      } catch (e) {
        this.handleError(e);
      } finally {
        this.loadingDoses = false;
      }
    },
    createMedication() {
      this.form = this.newForm();
      this.addReminder();
      this.formVisible = true;
    },
    editMedication(medication) {
      this.form = this.newForm(medication);
      this.formVisible = true;
    },
    addReminder() {
      const value = new Date();
      value.setHours(8, 0, 0, 0);
      this.form.reminderTimeValues.push(value);
    },
    removeReminder(index) {
      this.form.reminderTimeValues.splice(index, 1);
    },
    async saveMedication() {
      this.saving = true;
      try {
        const medication = new Medication({...this.form, reminderTimes: this.serializedReminderTimes});
        await medicationService.save(medication);
        this.formVisible = false;
        await this.loadMedications();
        this.$toast.add({severity: 'success', summary: 'Medication saved', life: 3000});
      } catch (e) {
        this.handleError(e);
      } finally {
        this.saving = false;
      }
    },
    async removeMedication(medication) {
      if (!confirm(`Delete ${medication.name} and its dose history?`)) {
        return;
      }
      try {
        await medicationService.delete(medication);
        await Promise.all([this.loadMedications(), this.loadDoses()]);
      } catch (e) {
        this.handleError(e);
      }
    },
    async logDose(medication) {
      try {
        await medicationService.logDose(medication.id);
        await this.loadDoses();
        this.$toast.add({severity: 'success', summary: `${medication.name} dose recorded`, life: 3000});
      } catch (e) {
        this.handleError(e);
      }
    },
    serializeTime(value) {
      return `${String(value.getHours()).padStart(2, '0')}:${String(value.getMinutes()).padStart(2, '0')}`;
    },
    formatDose(value) {
      return `${Number(value.doseAmount)} ${value.doseUnit}`;
    },
    formatSchedule(medication) {
      const interval = medication.repeatEvery === 1
          ? `Every ${medication.repeatUnit === 'DAY' ? 'day' : 'week'}`
          : `Every ${medication.repeatEvery} ${medication.repeatUnit === 'DAY' ? 'days' : 'weeks'}`;
      return `${interval} at ${medication.reminderTimes.map(time => time.slice(0, 5)).join(', ')}`;
    },
    formatDate(value) {
      return dayjs(value).format('DD/MM/YYYY');
    },
    formatDateTime(value) {
      return new Intl.DateTimeFormat('en-GB', {
        timeZone: 'Europe/Madrid', dateStyle: 'medium', timeStyle: 'short'
      }).format(new Date(value));
    },
    statusLabel(status) {
      return {PENDING: 'Scheduled', SNOOZED: 'Snoozed', TAKEN: 'Taken', MISSED: 'Missed'}[status];
    },
    handleError(error) {
      this.$log.error(error);
      this.$toast.add({severity: 'error', summary: 'Failed', detail: error.message || error, life: 3000});
    }
  }
};
</script>

<style scoped>
.medications-page {
  width: min(100%, 90rem);
  margin: 0 auto;
}
.medication-row-actions,
.medication-reminder-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.medication-row-actions {
  flex-wrap: wrap;
}
.medication-log-button {
  white-space: nowrap;
}
.medication-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
  width: min(42rem, 90vw);
}
.medication-field {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  min-width: 0;
}
.medication-field-wide,
.medication-active-field {
  grid-column: 1 / -1;
}
.medication-reminder-row + .medication-reminder-row {
  margin-top: 0.4rem;
}
.medication-active-field {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.error {
  color: #e91224;
}
@media (max-width: 600px) {
  .medication-form {
    grid-template-columns: minmax(0, 1fr);
  }
  .medication-field-wide,
  .medication-active-field {
    grid-column: 1;
  }
}
</style>
