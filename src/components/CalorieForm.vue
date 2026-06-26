<template>
  <Dialog id="calorie-form" appendTo="body" header="Calories" v-model:visible="display_modal" :closeOnEscape="false" :closable="false" :modal="true" data-toggle="validator" ref="form">
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
        <InputNumber id="calories" v-model="vv.calories.$model" :min="0" />
        <label for="calories">Calories</label>
      </span>
      <span class="error">{{ vv.calories?.$errors[0]?.$message }}</span>
    </div>
    <template #footer>
      <Button label="Save" icon="pi pi-check" @click="save" />
      <Button label="Cancel" icon="pi pi-times" @click="close_modal" class="p-button-secondary" />
    </template>
  </Dialog>
</template>

<script>
import service from '../services/CalorieService';
import { reactive, toRef } from "vue";
import { useVuelidate } from "@vuelidate/core";
import { minValue, required } from "@vuelidate/validators";
import Calorie from "@/model/Calorie";

export default {
  name: "CalorieForm",
  emits: ["onSave", "onClose"],
  props: {
    show: Boolean,
    calorie: Object,
    initial_date: Date
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
      calories: null
    });
    const rules = {
      date: { required },
      calories: { required, minValue: minValue(0) }
    };
    const vv = useVuelidate(rules, {
      date: toRef(fform, "date"),
      calories: toRef(fform, "calories")
    });
    return {
      vv,
      fform,
      custom_locale: locale,
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
    calorie() {
      if (this.display_modal) {
        this.load_form();
      }
    },
    initial_date() {
      if (this.display_modal && !this.calorie) {
        this.load_form();
      }
    }
  },
  methods: {
    load_form() {
      if (this.calorie) {
        this.vv.date.$model = this.calorie.date;
        this.vv.calories.$model = this.calorie.calories;
        return;
      }
      this.vv.date.$model = this.initial_date || new Date();
      this.vv.calories.$model = null;
    },
    clear() {
      this.vv.date.$model = this.initial_date || new Date();
      this.vv.calories.$model = null;
      this.vv.$reset();
    },
    async save() {
      this.vv.$touch();
      if (this.vv.$invalid) {
        return;
      }
      let calorie = new Calorie();
      calorie.id = this.calorie ? this.calorie.id : null;
      calorie.date = this.vv.date.$model;
      calorie.calories = this.vv.calories.$model;
      await service.save(calorie.toObject())
          .then(() => {
            this.$emit('onSave');
            this.$toast.add({severity:'success', summary: 'Calories saved', life: 3000});
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
