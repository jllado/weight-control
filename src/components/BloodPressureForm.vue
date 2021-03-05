<template>
  <Dialog id="blood-pressure-form" appendTo="body" header="Blood Preasure" v-model:visible="display_modal" :closeOnEscape="false" :closable="false" :modal="true" data-toggle="validator" ref="form">
    <br>
    <div class="p-flex-row p-pb-5">
        <span class="p-float-label">
            <Calendar v-model="vv.date.$model" dateFormat="dd/mm/yy" appendTo="body" v-model:locale="custom_locale" :showTime="true" />
            <label>Date</label>
        </span>
      <span class="error">{{ vv.date?.$errors[0]?.$message }}</span>
    </div>
    <div class="p-flex-row p-pb-5">
        <span class="p-float-label">
            <InputNumber id="upper" v-model="vv.upper.$model" mode="decimal" :minFractionDigits="0" :maxFractionDigits="0" />
            <label for="upper">Upper</label>
            mm Hg
        </span>
      <span class="error">{{ vv.upper?.$errors[0]?.$message }}</span>
    </div>
    <div class="p-flex-row p-pb-5">
        <span class="p-float-label">
            <InputNumber id="lower" v-model="vv.lower.$model" mode="decimal" :minFractionDigits="0" :maxFractionDigits="0" />
            <label for="lower">Lower</label>
            mm Hg
        </span>
      <span class="error">{{ vv.lower?.$errors[0]?.$message }}</span>
    </div>
    <template #footer>
      <Button label="Save" icon="pi pi-check" @click="save" />
      <Button label="Cancel" icon="pi pi-times" @click="close_modal" class="p-button-secondary" />
    </template>
  </Dialog>
</template>

<script>
import service from '../services/BloodPressureService';
import { reactive, toRef } from "vue";
import { useVuelidate } from "@vuelidate/core";
import { required } from "@vuelidate/validators";
import { userState } from '../state';
import BloodPressure from "@/model/BloodPressure";

export default {
  name: "BloodPressureForm",
  emits: ["onSave", "onClose"],
  props: {
    show: Boolean,
    blood_pressure: Object
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
      upper: null,
      lower: null,
    });
    const rules = {
      date: {},
      upper: { required },
      lower: { required }
    };
    const vv = useVuelidate(rules, {
      date: toRef(fform, "date"),
      upper: toRef(fform, "upper"),
      lower: toRef(fform, "lower")
    });
    return {
      vv,
      fform,
      custom_locale: locale,
      state: userState(),
      display_modal: this.show
    }
  },
  updated() {
    this.display_modal = this.show;
    if (this.blood_pressure) {
      this.vv.date.$model = this.blood_pressure.date;
      this.vv.upper.$model = this.blood_pressure.upper;
      this.vv.lower.$model = this.blood_pressure.lower;
    }
  },
  methods: {
    clear() {
      this.vv.date.$model = new Date();
      this.vv.upper.$model = null;
      this.vv.lower.$model = null;
      this.vv.$reset();
    },
    async save() {
      this.vv.$touch();
      if (this.vv.$invalid) {
        return;
      }
      let blood_pressure_id = this.blood_pressure ? this.blood_pressure.id : null;
      let user = this.state.user.mail;
      let previous_blood_pressure = await service.get_last(user);
      await service.save(build_blood_pressure(this.vv, blood_pressure_id, user, previous_blood_pressure))
          .then(() => {
            this.$emit('onSave');
            this.$toast.add({severity:'success', summary: 'Blood Pressure saved', life: 3000});
            this.close_modal();
          })
          .catch(e => {
            this.handle_error(e)
          });
      this.clear();

      function build_blood_pressure(vv, id, user, previous_blood_pressure) {
        let blood_pressure = new BloodPressure()
        blood_pressure.id = id;
        blood_pressure.user = user;
        blood_pressure.date = vv.date.$model;
        blood_pressure.upper = vv.upper.$model;
        blood_pressure.lower = vv.lower.$model;
        console.log(previous_blood_pressure);
        blood_pressure.load_lost(previous_blood_pressure)
        return blood_pressure.toObject();
      }
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
