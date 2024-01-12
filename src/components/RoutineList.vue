<template>
  <div>
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
      <Column header="Start Date" headerStyle="width: 111px" headerClass="mobile-none" bodyClass="mobile-none" >
        <template #body="routine" >
          {{ routine.data.start_date_format }}
        </template>
      </Column>
      <Column header="Routine" field="name" >
        <template #body="routine" >
          {{ routine.data.name }}
        </template>
        <template #filter="{ filterModel, filterCallback }">
          <InputText v-model="filterModel.value" type="text" @input="filterCallback()" class="p-column-filter" placeholder="Search" />
        </template>
      </Column>
      <Column header="Times" headerStyle="width: 111px" headerClass="mobile-none" bodyClass="mobile-none" >
        <template #body="routine" >
          {{ routine.data.times.length }}
        </template>
      </Column>
      <Column header="Strike" headerStyle="width: 111px" >
        <template #body="routine" >
          {{ routine.data.current_strike }}
        </template>
      </Column>
      <Column header="Best Strike" headerStyle="width: 111px" headerClass="mobile-none" bodyClass="mobile-none" >
        <template #body="routine" >
          {{ routine.data.best_strike }}
        </template>
      </Column>
      <Column header="Last Date" headerStyle="width: 111px" headerClass="mobile-none" bodyClass="mobile-none" >
        <template #body="routine" >
          {{ routine.data.last_time_date_format }}
        </template>
      </Column>
      <Column headerStyle="width: 112px" bodyStyle="text-align: center" >
        <template #body="routine">
          <Button icon="pi pi-pencil" class="p-button-rounded p-button-success p-mr-2" @click="edit(routine.data)" />
          <Button icon="pi pi-trash" class="p-button-rounded p-button-warning" @click="remove(routine.data)" />
        </template>
      </Column>
    </DataTable>
    <Dialog id="routine-form" appendTo="body" header="Routine" v-model:visible="display_edit_modal" :closeOnEscape="false" :closable="false" :modal="true" data-toggle="validator" ref="form">
      <br>
      <div class="p-flex-row p-pb-5">
        <span class="p-float-label">
            <InputText id="routine" v-model="vv.name.$model" />
            <label for="routine">Routine</label>
        </span>
        <span class="error">{{ vv.name?.$errors[0]?.$message }}</span>
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
import {reactive, toRef, ref} from "vue";
import {required} from "@vuelidate/validators";
import {useVuelidate} from "@vuelidate/core";
import { FilterMatchMode } from 'primevue/api';


export default {
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
      name: null
    });
    const rules = {
      name: { required }
    };
    const vv = useVuelidate(rules, {
      name: toRef(fform, "name")
    });

    return {
      filters,
      vv,
      fform,
      custom_locale: locale,
      routine: null,
      routines: [],
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
      this.routines = await service.get_all_by(this.state.user.mail);
      this.state.loading = false;
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
      this.display_edit_modal = true;
    },
    create() {
      this.routine = {
        id: null,
        start_date: new Date(),
        last_time_date: null,
        current_strike: 0,
        best_strike: 0,
        times: []
      }
      this.display_edit_modal = true;
    },
    clear() {
      this.vv.name.$model = null;
      this.vv.$reset();
    },
    async save() {
      this.vv.$touch();
      if (this.vv.$invalid) {
        return;
      }
      let routine_state = this.routine;
      let user = this.state.user.mail;
      await service.save(build_routine(this.vv, user, routine_state))
          .then(() => {
            this.$toast.add({severity:'success', summary: 'Routine saved', life: 3000});
            this.close_edit();
          })
          .catch(e => {
            this.handle_error(e)
          });
      this.clear();
      await this.load_routines();

      function build_routine(vv, user, routine_state) {
        let routine = new Routine()
        routine.id = routine_state.id;
        routine.user = user;
        routine.start_date = routine_state.start_date;
        routine.name = vv.name.$model;
        routine.times = routine_state.times;
        routine.current_strike = routine_state.current_strike;
        routine.best_strike = routine_state.best_strike;
        routine.last_time_date = routine_state.last_time_date;
        return routine.toObject();
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
