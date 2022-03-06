<template>
  <div>
    <DataTable :value="this.habits" :paginator="true" :rows="10" :loading="this.state.loading"
               paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
               currentPageReportTemplate="{first} to {last} of {totalRecords}" >
      <template #header>
        <div class="table-header">
          Habits
          <Button icon="pi pi-plus" label="New" @click="create" />
        </div>
      </template>
      <Column header="Start Date" headerStyle="width: 111px" headerClass="mobile-none" bodyClass="mobile-none" >
        <template #body="habit" >
          {{ habit.data.start_date_format }}
        </template>
      </Column>
      <Column header="Habit" headerStyle="width: 100%" >
        <template #body="habit" >
          {{ habit.data.name }}
        </template>
      </Column>
      <Column header="Times" headerStyle="width: 111px" headerClass="mobile-none" bodyClass="mobile-none" >
        <template #body="habit" >
          {{ habit.data.times }}
        </template>
      </Column>
      <Column header="Daily Times" headerStyle="width: 111px" headerClass="mobile-none" bodyClass="mobile-none" >
        <template #body="habit" >
          {{ habit.data.daily_times }}
        </template>
      </Column>
      <Column header="Duration" headerStyle="width: 111px" headerClass="mobile-none" bodyClass="mobile-none" >
        <template #body="habit" >
          {{ habit.data.duration }} days
        </template>
      </Column>
      <Column header="Daily Strike" headerStyle="width: 111px" headerClass="mobile-none" bodyClass="mobile-none" >
        <template #body="habit" >
          {{ habit.data.current_daily_strike }}
        </template>
      </Column>
      <Column header="Strike" headerStyle="width: 111px" >
        <template #body="habit" >
          {{ habit.data.current_strike }}
        </template>
      </Column>
      <Column header="Best Strike" headerStyle="width: 111px" headerClass="mobile-none" bodyClass="mobile-none" >
        <template #body="habit" >
          {{ habit.data.best_strike }}
        </template>
      </Column>
      <Column header="Last Date" headerStyle="width: 111px" headerClass="mobile-none" bodyClass="mobile-none" >
        <template #body="habit" >
          {{ habit.data.last_time_date_format }}
        </template>
      </Column>
      <Column headerStyle="width: 112px" bodyStyle="text-align: center" >
        <template #body="habit">
          <Button icon="pi pi-pencil" class="p-button-rounded p-button-success p-mr-2" @click="edit(habit.data)" />
          <Button icon="pi pi-trash" class="p-button-rounded p-button-warning" @click="remove(habit.data)" />
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
            <InputNumber id="daily_times" v-model="vv.daily_times.$model" />
            <label for="duration">Daily Times</label>
        </span>
        <span class="error">{{ vv.duration?.$errors[0]?.$message }}</span>
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

export default {
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
      daily_times: null,
      duration: null,
      name: null
    });
    const rules = {
      daily_times: { required },
      duration: { required },
      name: { required }
    };
    const vv = useVuelidate(rules, {
      daily_times: toRef(fform, "daily_times"),
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
      state: userState()
    }
  },
  async created () {
    await this.load_habits();
  },
  methods: {
    async load_habits() {
      this.state.loading = true;
      this.habits = await service.get_all_by(this.state.user.mail);
      this.state.loading = false;
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
      this.vv.daily_times.$model = this.habit.daily_times;
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
      this.vv.daily_times.$model = null;
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
      this.load_habits();

      function build_habit(vv, user, habit_state) {
        let habit = new Habit()
        habit.id = habit_state.id;
        habit.user = user;
        habit.start_date = habit_state.start_date;
        habit.daily_times = vv.daily_times.$model;
        habit.duration = vv.duration.$model;
        habit.name = vv.name.$model;
        habit.times = habit_state.times;
        habit.current_daily_strike = habit_state.current_daily_strike;
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
