<template>
  <Dialog id="mood-form" appendTo="body" header="Mood" v-model:visible="display_modal" :closeOnEscape="false" :closable="false" :modal="true" data-toggle="validator" ref="form">
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
        <Dropdown id="period" v-model="vv.period.$model" :options="mood_period_options" optionLabel="label" optionValue="value" :disabled="!!period" />
        <label for="period">Period</label>
      </span>
      <span class="error">{{ vv.period?.$errors[0]?.$message }}</span>
    </div>
    <div class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <Dropdown id="value" v-model="vv.value.$model" :options="mood_options" optionLabel="label" optionValue="value" />
        <label for="value">Mood</label>
      </span>
      <span class="error">{{ vv.value?.$errors[0]?.$message }}</span>
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
import service from '../services/MoodService';
import { reactive, toRef } from "vue";
import { useVuelidate } from "@vuelidate/core";
import { maxLength, required } from "@vuelidate/validators";
import Mood, {getMoodOptions, getMoodPeriodOptions} from "@/model/Mood";

export default {
  name: "MoodForm",
  emits: ["onSave", "onClose"],
  props: {
    show: Boolean,
    mood: Object,
    initial_date: Date,
    period: String
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
      date: this.initial_date || new Date(),
      period: this.period || null,
      value: null,
      note: ''
    });
    const rules = {
      date: { required },
      period: { required },
      value: { required },
      note: { maxLength: maxLength(500) }
    };
    const vv = useVuelidate(rules, {
      date: toRef(fform, "date"),
      period: toRef(fform, "period"),
      value: toRef(fform, "value"),
      note: toRef(fform, "note")
    });
    return {
      vv,
      fform,
      custom_locale: locale,
      mood_options: getMoodOptions(),
      mood_period_options: getMoodPeriodOptions(),
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
    mood() {
      if (this.display_modal) {
        this.load_form();
      }
    },
    initial_date() {
      if (this.display_modal && !this.mood) {
        this.load_form();
      }
    },
    period() {
      if (this.display_modal) {
        this.load_form();
      }
    }
  },
  methods: {
    load_form() {
      if (this.mood) {
        this.vv.date.$model = this.mood.date;
        this.vv.period.$model = this.period || this.mood.period;
        this.vv.value.$model = this.mood.value;
        this.vv.note.$model = this.mood.note;
        return;
      }
      this.vv.date.$model = this.initial_date || new Date();
      this.vv.period.$model = this.period || null;
      this.vv.value.$model = null;
      this.vv.note.$model = '';
    },
    clear() {
      this.vv.date.$model = this.initial_date || new Date();
      this.vv.period.$model = this.period || null;
      this.vv.value.$model = null;
      this.vv.note.$model = '';
      this.vv.$reset();
    },
    async save() {
      this.vv.$touch();
      if (this.vv.$invalid) {
        return;
      }
      let mood = new Mood();
      mood.id = this.mood ? this.mood.id : null;
      mood.date = this.vv.date.$model;
      mood.period = this.vv.period.$model;
      mood.value = this.vv.value.$model;
      mood.note = this.vv.note.$model || null;
      await service.save(mood.toObject())
          .then(() => {
            this.$emit('onSave');
            this.$toast.add({severity:'success', summary: 'Mood saved', life: 3000});
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
