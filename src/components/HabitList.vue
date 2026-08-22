<template>
  <div>
    <DataTable :value="this.habits" :paginator="true" :rows="10" :loading="this.state.loading" responsiveLayout="scroll"
               paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
               currentPageReportTemplate="{first} to {last} of {totalRecords}" >
      <template #header>
        <div class="table-header">
          Habits
          <Button icon="pi pi-plus" label="New" @click="create" />
        </div>
      </template>
      <Column header="Start Date" headerStyle="width: 111px" >
        <template #body="habit" >
          {{ habit.data.start_date_format }}
        </template>
      </Column>
      <Column header="Status" >
        <template #body="habit" >
          {{ habit.data.getStatus() }}
        </template>
      </Column>
      <Column header="Habit" >
        <template #body="habit" >
          {{ habit.data.name }}
          <small v-if="habit.data.legacy_baseline" class="legacy-baseline-note">Includes a legacy baseline{{ habit.data.legacy_baseline.lastDate ? ` through ${habit.data.legacy_baseline.lastDate}` : ' with no recorded date' }}.</small>
          <PersonalRecordSummary :records="recordsForHabit(habit.data.id)" />
        </template>
      </Column>
      <Column header="Times" headerStyle="width: 111px" >
        <template #body="habit" >
          {{ habit.data.times }}
        </template>
      </Column>
      <Column header="Duration" headerStyle="width: 111px" >
        <template #body="habit" >
          {{ habit.data.duration }} days
        </template>
      </Column>
      <Column header="Strike" headerStyle="width: 111px" >
        <template #body="habit" >
          {{ habit.data.current_strike }}
        </template>
      </Column>
      <Column header="Best Strike" headerStyle="width: 111px" >
        <template #body="habit" >
          {{ habit.data.best_strike }}
        </template>
      </Column>
      <Column header="Last Date" headerStyle="width: 111px" >
        <template #body="habit" >
          {{ habit.data.last_time_date_format }}
        </template>
      </Column>
      <Column headerStyle="width: 100px" >
        <template #body="habit">
          <div style="width: 100px; text-align: center">
            <Button icon="pi pi-pencil" class="p-button-rounded p-button-success p-mr-2" @click="edit(habit.data)" />
            <Button icon="pi pi-trash" class="p-button-rounded p-button-warning" @click="remove(habit.data)" />
          </div>
        </template>
      </Column>
      <Column header="Today" headerStyle="width: 80px" bodyStyle="text-align: center">
        <template #body="habit">
          <Button v-if="completedToday(habit.data)" icon="pi pi-undo" class="p-button-rounded p-button-warning" aria-label="Undo today" @click="undoToday(habit.data)" :loading="pending_habit_id === habit.data.id" />
          <Button v-else icon="pi pi-check" class="p-button-rounded p-button-success" aria-label="Complete today" @click="completeToday(habit.data)" :loading="pending_habit_id === habit.data.id" />
        </template>
      </Column>
    </DataTable>
    <Dialog id="habit-form" appendTo="body" header="Habit" v-model:visible="display_edit_modal" :closeOnEscape="false" :closable="false" :modal="true" data-toggle="validator" ref="form">
      <br>
      <div class="p-flex-row p-pb-5">
        <span class="p-float-label">
            <InputText id="habit" v-model="vv.name.$model" />
            <label for="habit">Habit</label>
        </span>
        <span class="error">{{ vv.name?.$errors[0]?.$message }}</span>
      </div>
      <div class="p-flex-row p-pb-5">
        <span class="p-float-label">
            <InputNumber id="duration" v-model="vv.duration.$model" />
            <label for="duration">Duration</label>
            days
        </span>
        <span class="error">{{ vv.duration?.$errors[0]?.$message }}</span>
      </div>
      <template #footer>
        <Button label="Save" icon="pi pi-check" @click="save" />
        <Button label="Cancel" icon="pi pi-times" @click="close_edit" class="p-button-secondary" />
      </template>
    </Dialog>
  </div>
</template>

<script>
import service from '../services/HabitService';
import { userState } from '../state';
import Habit from "@/model/Habit";
import {reactive, toRef} from "vue";
import {required} from "@vuelidate/validators";
import {useVuelidate} from "@vuelidate/core";
import dayjs from 'dayjs';
import PersonalRecordSummary from '@/components/PersonalRecordSummary';
import personalRecordService from '@/services/PersonalRecordService';

export default {
  components: {PersonalRecordSummary},
  data() {
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
      duration: null,
      name: null
    });
    const rules = {
      duration: { required },
      name: { required }
    };
    const vv = useVuelidate(rules, {
      duration: toRef(fform, "duration"),
      name: toRef(fform, "name")
    });

    return {
      vv,
      fform,
      custom_locale: locale,
      habit: null,
      habits: [],
      display_edit_modal: false,
      pending_habit_id: null,
      personal_records: [],
      state: userState()
    }
  },
  async created () {
    await Promise.all([this.load_habits(), this.load_records()]);
  },
  methods: {
    async load_habits() {
      this.state.loading = true;
      this.habits = await service.get_all_by(this.state.user.mail);
      this.state.loading = false;
    },
    async load_records() {
      this.personal_records = await personalRecordService.getCurrent({domain: 'BEHAVIOR'});
    },
    recordsForHabit(id) {
      return this.personal_records.filter(record => record.subject.type === 'HABIT' && record.subject.id === id);
    },
    completedToday(habit) {
      const today = dayjs().format('YYYY-MM-DD');
      return habit.checkins.includes(today);
    },
    async completeToday(habit) {
      this.pending_habit_id = habit.id;
      try {
        const updated = await service.complete(habit.id, new Date());
        this.habits = this.habits.map(candidate => candidate.id === updated.id ? updated : candidate);
        await this.load_records();
        this.$toast.add({severity:'success', summary: 'Habit completed', life: 3000});
      } catch (e) {
        this.handle_error(e);
      } finally {
        this.pending_habit_id = null;
      }
    },
    async undoToday(habit) {
      this.pending_habit_id = habit.id;
      try {
        const updated = await service.undo(habit.id, new Date());
        this.habits = this.habits.map(candidate => candidate.id === updated.id ? updated : candidate);
        await this.load_records();
        this.$toast.add({severity:'success', summary: 'Habit completion undone', life: 3000});
      } catch (e) {
        this.handle_error(e);
      } finally {
        this.pending_habit_id = null;
      }
    },
    async remove(habit) {
      if (!confirm('Are you sure you want to delete this?')) {
        return;
      }
      service.delete(habit)
          .then(() => {
            this.load_habits();
          })
          .catch(e => {
            this.handle_error(e)
          });
    },
    async edit(habit) {
      this.habit = Object.assign({}, habit);
      this.vv.name.$model = this.habit.name;
      this.vv.duration.$model = this.habit.duration;
      this.display_edit_modal = true;
    },
    create() {
      this.habit = {
        id: null,
        start_date: new Date(),
        last_time_date: null,
        current_strike: 0,
        best_strike: 0,
        current_daily_strike: 0,
        times: 0
      }
      this.display_edit_modal = true;
    },
    clear() {
      this.vv.duration.$model = null;
      this.vv.name.$model = null;
      this.vv.$reset();
    },
    async save() {
      this.vv.$touch();
      if (this.vv.$invalid) {
        return;
      }
      let habit_state = this.habit;
      let user = this.state.user.mail;
      await service.save(build_habit(this.vv, user, habit_state))
          .then(() => {
            this.$toast.add({severity:'success', summary: 'Habit saved', life: 3000});
            this.close_edit();
          })
          .catch(e => {
            this.handle_error(e)
          });
      this.clear();
      await this.load_habits();

      function build_habit(vv, user, habit_state) {
        let habit = new Habit()
        habit.id = habit_state.id;
        habit.user = user;
        habit.start_date = habit_state.start_date;
        habit.duration = vv.duration.$model;
        habit.name = vv.name.$model;
        habit.times = habit_state.times;
        habit.current_strike = habit_state.current_strike;
        habit.best_strike = habit_state.best_strike;
        habit.last_time_date = habit_state.last_time_date;
        return habit.toObject();
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
.legacy-baseline-note { display: block; margin-top: 0.25rem; color: #6c757d; }
</style>
