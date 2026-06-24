<template>
  <Dialog id="sickness-form" appendTo="body" header="Sickness" v-model:visible="display_modal" :closeOnEscape="false" :closable="false" :modal="true" data-toggle="validator" ref="form">
    <br>
    <div class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <Calendar v-model="vv.date.$model" dateFormat="dd/mm/yy" appendTo="body" v-model:locale="custom_locale" :maxDate="max_date" />
        <label>Date</label>
      </span>
      <span class="error">{{ vv.date?.$errors[0]?.$message }}</span>
    </div>
    <div class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <Dropdown id="type" v-model="vv.type.$model" :options="type_options" optionLabel="label" optionValue="value" />
        <label for="type">Type</label>
      </span>
      <span class="error">{{ vv.type?.$errors[0]?.$message }}</span>
    </div>
    <div class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <Dropdown id="severity" v-model="vv.severity.$model" :options="severity_options" optionLabel="label" optionValue="value" />
        <label for="severity">Severity</label>
      </span>
      <span class="error">{{ vv.severity?.$errors[0]?.$message }}</span>
    </div>
    <div class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <InputText id="note" v-model="vv.note.$model" maxlength="500" />
        <label for="note">Note</label>
      </span>
      <span class="error">{{ vv.note?.$errors[0]?.$message }}</span>
    </div>
    <template #footer>
      <Button label="Save" icon="pi pi-check" @click="save" />
      <Button label="Cancel" icon="pi pi-times" @click="close_modal" class="p-button-secondary" />
    </template>
  </Dialog>
</template>

<script>
import service from '../services/SicknessService';
import { reactive, toRef } from "vue";
import { useVuelidate } from "@vuelidate/core";
import { maxLength, required } from "@vuelidate/validators";
import Sickness, {getSicknessSeverityOptions, getSicknessTypeOptions} from "@/model/Sickness";

export default {
  name: "SicknessForm",
  emits: ["onSave", "onClose"],
  props: {
    show: Boolean,
    sickness: Object
  },
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
      date: new Date(),
      type: null,
      severity: null,
      note: ''
    });
    const rules = {
      date: { required },
      type: { required },
      severity: { required },
      note: { maxLength: maxLength(500) }
    };
    const vv = useVuelidate(rules, {
      date: toRef(fform, "date"),
      type: toRef(fform, "type"),
      severity: toRef(fform, "severity"),
      note: toRef(fform, "note")
    });
    return {
      vv,
      fform,
      custom_locale: locale,
      type_options: getSicknessTypeOptions(),
      severity_options: getSicknessSeverityOptions(),
      display_modal: this.show,
      max_date: new Date()
    }
  },
  watch: {
    show(value) {
      this.display_modal = value;
      if (value) {
        this.load_form();
      }
    },
    sickness() {
      if (this.display_modal) {
        this.load_form();
      }
    }
  },
  methods: {
    load_form() {
      if (this.sickness) {
        this.vv.date.$model = this.sickness.date;
        this.vv.type.$model = this.sickness.type;
        this.vv.severity.$model = this.sickness.severity;
        this.vv.note.$model = this.sickness.note;
        this.vv.$reset();
        return;
      }
      this.clear();
    },
    clear() {
      this.vv.date.$model = new Date();
      this.vv.type.$model = null;
      this.vv.severity.$model = null;
      this.vv.note.$model = '';
      this.vv.$reset();
    },
    async save() {
      this.vv.$touch();
      if (this.vv.$invalid) {
        return;
      }
      let sickness = new Sickness();
      sickness.id = this.sickness ? this.sickness.id : null;
      sickness.date = this.vv.date.$model;
      sickness.type = this.vv.type.$model;
      sickness.severity = this.vv.severity.$model;
      sickness.note = this.vv.note.$model || null;
      await service.save(sickness.toObject())
          .then(() => {
            this.$emit('onSave');
            this.$toast.add({severity:'success', summary: 'Sickness saved', life: 3000});
            this.close_modal();
          })
          .catch(e => {
            this.handle_error(e)
          });
      this.clear();
    },
    close_modal() {
      this.clear();
      this.$emit('onClose');
    },
    handle_error(e) {
      this.$log.error(e);
      this.$toast.add({severity:'error', summary: 'Failed', detail: e, life: 3000});
    }
  }
}
</script>
