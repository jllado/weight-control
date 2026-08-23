<template>
  <div>
    <TabView :lazy="true">
      <TabPanel header="Manage">
        <DataTable :value="this.routines" :paginator="true" :rows="10" :loading="this.state.loading" responsiveLayout="scroll"
                   v-model:filters="filters" filterDisplay="row"
                   paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
                   currentPageReportTemplate="{first} to {last} of {totalRecords}" >
          <template #header>
            <div class="table-header">
              Routines
              <Button icon="pi pi-plus" label="New" @click="create" />
            </div>
          </template>
          <Column header="Start Date" headerStyle="width: 111px" >
            <template #body="routine" >
              {{ routine.data.start_date_format }}
            </template>
          </Column>
          <Column header="Routine" field="name" headerStyle="min-width: 250px" >
            <template #body="routine" >
              {{ routine.data.name }}
            </template>
            <template #filter="{ filterModel, filterCallback }">
              <InputText v-model="filterModel.value" type="text" @input="filterCallback()" class="p-column-filter" placeholder="Search" />
            </template>
          </Column>
          <Column header="Type" headerStyle="width: 250px" >
            <template #body="routine" >
              {{ routine.data.typeValues() }}
            </template>
          </Column>
          <Column header="Reminder" headerStyle="width: 111px" >
            <template #body="routine" >
              {{ format_reminder_times(routine.data.reminders) }}
            </template>
          </Column>
          <Column header="Times" headerStyle="width: 111px" >
            <template #body="routine" >
              {{ routine.data.times.length }}
            </template>
          </Column>
          <Column header="Streak" headerStyle="width: 111px" >
            <template #body="routine" >
              {{ routine.data.current_strike }}
            </template>
          </Column>
          <Column header="Best Streak" headerStyle="width: 111px" >
            <template #body="routine" >
              {{ routine.data.best_strike }}
            </template>
          </Column>
          <Column header="Last Date" headerStyle="width: 111px" >
            <template #body="routine" >
              {{ routine.data.last_time_date_format }}
            </template>
          </Column>
          <Column headerStyle="width: 100px" >
            <template #body="routine">
              <div style="width: 100px; text-align: center">
                <Button icon="pi pi-pencil" class="p-button-rounded p-button-success p-mr-2" @click="edit(routine.data)" />
                <Button icon="pi pi-trash" class="p-button-rounded p-button-warning" @click="remove(routine.data)" />
              </div>
            </template>
          </Column>
        </DataTable>
      </TabPanel>
      <TabPanel header="Analytics">
        <div v-if="this.state.loading" class="routine-tab-message"><i class="pi pi-spin pi-spinner"></i> Loading routines...</div>
        <div v-else-if="this.routines.length === 0" class="routine-tab-message">No routines yet. Create one in the Manage tab to start tracking progress.</div>
        <div v-else class="routine-analytics-content">
          <Dropdown
              v-model="selected_routine_id"
              :options="get_routine_options()"
              optionLabel="label"
              optionValue="id"
              placeholder="Select routine"
              filter
              class="routine-selector"
          />
          <RoutineAnalyticsCard :key="selected_routine.id" :routine="selected_routine" />
        </div>
      </TabPanel>
      <TabPanel header="Scheduled">
        <div v-if="this.state.loading" class="routine-tab-message"><i class="pi pi-spin pi-spinner"></i> Loading routines...</div>
        <div v-else-if="this.scheduled_reminders.length === 0" class="routine-tab-message">No scheduled routines. Add a reminder time in the Manage tab.</div>
        <DataTable v-else :value="this.scheduled_reminders" responsiveLayout="scroll">
          <template #header>
            <div class="table-header">Scheduled routines</div>
          </template>
          <Column header="Reminder (Europe/Madrid)" headerStyle="width: 220px">
            <template #body="scheduled">
              {{ format_reminder_time(scheduled.data.reminder.time) }}
            </template>
          </Column>
          <Column header="Routine" field="routine.name" headerStyle="min-width: 250px" />
          <Column header="Type" headerStyle="width: 250px">
            <template #body="scheduled">
              {{ scheduled.data.routine.typeValues() }}
            </template>
          </Column>
          <Column headerStyle="width: 70px">
            <template #body="scheduled">
              <Button icon="pi pi-pencil" aria-label="Edit routine" class="p-button-rounded p-button-success" @click="edit(scheduled.data.routine)" />
            </template>
          </Column>
        </DataTable>
      </TabPanel>
    </TabView>
    <Dialog id="routine-form" appendTo="body" header="Routine" v-model:visible="display_edit_modal" :closeOnEscape="false" :closable="false" :modal="true" data-toggle="validator" ref="form">
      <br>
      <div class="p-flex-row p-pb-5">
        <span class="p-float-label">
            <InputText id="routine" v-model="vv.name.$model" />
            <label for="routine">Routine</label>
        </span>
        <span class="error">{{ vv.name?.$errors[0]?.$message }}</span>
      </div>
      <div class="p-flex-row p-pb-5">
        <MultiSelect v-model="vv.types.$model" :options="types()" optionLabel="name" placeholder="Select types" class="w-full" />
        <span class="error">{{ vv.types?.$errors[0]?.$message }}</span>
      </div>
      <div class="p-pb-5 routine-reminder-field">
        <label>Reminder times</label>
        <div v-for="(reminderTime, index) in fform.reminder_times" :key="index" class="routine-reminder-row">
          <Calendar :id="`routine-reminder-time-${index}`" v-model="fform.reminder_times[index]" :timeOnly="true" hourFormat="24" :stepMinute="5" :manualInput="false" showIcon class="routine-reminder-input" />
          <Button icon="pi pi-trash" :aria-label="`Remove reminder ${index + 1}`" class="p-button-rounded p-button-text p-button-danger" @click="remove_reminder_time(index)" />
        </div>
        <Button label="Add reminder" icon="pi pi-plus" class="p-button-outlined" @click="add_reminder_time" />
        <span v-if="has_duplicate_reminder_times()" class="error">Reminder times must be unique.</span>
        <small>Europe/Madrid time. Leave the list empty to disable this routine's reminders.</small>
      </div>
      <template #footer>
        <Button label="Save" icon="pi pi-check" @click="save" />
        <Button label="Cancel" icon="pi pi-times" @click="close_edit" class="p-button-secondary" />
      </template>
    </Dialog>
  </div>
</template>

<script>
import service from '../services/RoutineService';
import { userState } from '../state';
import Routine from "@/model/Routine";
import { RoutineType } from "@/model/Routine";
import { reactive, toRef, ref } from "vue";
import { required } from "@vuelidate/validators";
import { useVuelidate } from "@vuelidate/core";
import { FilterMatchMode } from 'primevue/api';
import RoutineAnalyticsCard from '@/components/RoutineAnalyticsCard';


export default {
  components: {RoutineAnalyticsCard},
  computed: {
    scheduled_reminders() {
      return this.routines
          .flatMap(routine => routine.reminders.map(reminder => ({routine, reminder})))
          .sort((first, second) => first.reminder.time.localeCompare(second.reminder.time) || first.routine.name.localeCompare(second.routine.name));
    },
    selected_routine() {
      return this.routines.find(routine => routine.id === this.selected_routine_id);
    }
  },
  data() {
    const filters = ref({
      name: { value: null, matchMode: FilterMatchMode.CONTAINS }
    });
    const locale = {
      firstDayOfWeek: 1,
      dayNames: ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"],
      dayNamesShort: ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"],
      dayNamesMin: ["Su","Mo","Tu","We","Th","Fr","Sa"],
      monthNames: [ "January","February","March","April","May","June","July","August","September","October","November","December" ],
      monthNamesShort: [ "Jan", "Feb", "Mar", "Apr", "May", "Jun","Jul", "Aug", "Sep", "Oct", "Nov", "Dec" ],
      today: 'Today',
      clear: 'Clear',
      dateFormat: 'mm/dd/yy',
      weekHeader: 'Wk'
    };
    const fform = reactive({
      name: null,
      types: null,
      reminder_times: []
    });
    const rules = {
      name: { required },
      types: { required }
    };
    const vv = useVuelidate(rules, {
      name: toRef(fform, "name"),
      types: toRef(fform, "types")
    });

    return {
      filters,
      vv,
      fform,
      custom_locale: locale,
      routine: null,
      routines: [],
      selected_routine_id: undefined,
      display_edit_modal: false,
      state: userState()
    }
  },
  async created () {
    await this.load_routines();
  },
  methods: {
    async load_routines() {
      this.state.loading = true;
      try {
        this.routines = await service.get_all_by(this.state.user.mail);
        this.sync_selected_routine();
      } catch (e) {
        this.handle_error(e);
      } finally {
        this.state.loading = false;
      }
    },
    get_routine_options() {
      return this.routines.map(routine => ({
        id: routine.id,
        label: `${routine.name} (${routine.typeValues()})`
      }));
    },
    sync_selected_routine() {
      if (this.routines.length === 0) {
        this.selected_routine_id = undefined;
        return;
      }
      if (!this.selected_routine) {
        this.selected_routine_id = this.routines[0].id;
      }
    },
    async remove(routine) {
      if (!confirm('Are you sure you want to delete this?')) {
        return;
      }
      service.delete(routine)
          .then(() => {
            this.load_routines();
          })
          .catch(e => {
            this.handle_error(e)
          });
    },
    async edit(routine) {
      this.routine = Object.assign({}, routine);
      this.vv.name.$model = this.routine.name;
      this.vv.types.$model = this.routine.types;
      this.fform.reminder_times = this.routine.reminders.map(reminder => this.parse_reminder_time(reminder.time));
      this.display_edit_modal = true;
    },
    create() {
      this.fform.reminder_times = [];
      this.routine = {
        id: null,
        start_date: new Date(),
        last_time_date: null,
        current_strike: 0,
        best_strike: 0,
        reminders: [],
        times: []
      }
      this.display_edit_modal = true;
    },
    clear() {
      this.vv.name.$model = null;
      this.vv.types.$model = null;
      this.fform.reminder_times = [];
      this.vv.$reset();
    },
    types() {
      return [RoutineType.WEIGHT, RoutineType.BLOOD_PRESSURE, RoutineType.FLEXIBILITY, RoutineType.MIND];
    },
    format_reminder_time(reminder_time) {
      return reminder_time?.slice(0, 5) || '—';
    },
    format_reminder_times(reminders) {
      return reminders.length ? reminders.map(reminder => this.format_reminder_time(reminder.time)).join(', ') : '—';
    },
    parse_reminder_time(reminder_time) {
      if (!reminder_time) {
        return null;
      }
      const [hours, minutes] = reminder_time.split(':').map(Number);
      const value = new Date();
      value.setHours(hours, minutes, 0, 0);
      return value;
    },
    serialize_reminder_time(reminder_time) {
      return `${String(reminder_time.getHours()).padStart(2, '0')}:${String(reminder_time.getMinutes()).padStart(2, '0')}`;
    },
    add_reminder_time() {
      const reminderTime = new Date();
      reminderTime.setHours(8, 0, 0, 0);
      this.fform.reminder_times.push(reminderTime);
    },
    remove_reminder_time(index) {
      this.fform.reminder_times.splice(index, 1);
    },
    has_duplicate_reminder_times() {
      const reminderTimes = this.fform.reminder_times.map(this.serialize_reminder_time);
      return new Set(reminderTimes).size !== reminderTimes.length;
    },
    async save() {
      this.vv.$touch();
      if (this.vv.$invalid || this.has_duplicate_reminder_times()) {
        return;
      }
      let routine_state = this.routine;
      let user = this.state.user.mail;
      const reminder_times = this.fform.reminder_times.map(this.serialize_reminder_time).sort();
      await service.save(build_routine(this.vv, reminder_times, user, routine_state))
          .then(() => {
            this.$toast.add({severity:'success', summary: 'Routine saved', life: 3000});
            this.close_edit();
          })
          .catch(e => {
            this.handle_error(e)
          });
      this.clear();
      await this.load_routines();

      function build_routine(vv, reminder_times, user, routine_state) {
        let routine = new Routine()
        routine.id = routine_state.id;
        routine.user = user;
        routine.start_date = routine_state.start_date;
        routine.name = vv.name.$model;
        routine.reminders = reminder_times.map(time => ({time}));
        routine.types = vv.types.$model;
        routine.times = routine_state.times;
        routine.current_strike = routine_state.current_strike;
        routine.best_strike = routine_state.best_strike;
        routine.last_time_date = routine_state.last_time_date;
        return routine;
      }
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

<style scoped>
.routine-analytics-content {
  width: min(100%, 64rem);
  margin: 0 auto;
}
.routine-selector {
  width: 100%;
  margin-bottom: 1rem;
}
.routine-reminder-field {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.5rem;
}
.routine-reminder-input {
  width: min(100%, 14rem);
}
.routine-reminder-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.routine-tab-message {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 0.5rem;
  min-height: 10rem;
  padding: 1.5rem;
  border: 1px solid #dce4ea;
  border-radius: 0.625rem;
  color: #666;
  background: #f8fafc;
  text-align: center;
}
</style>
