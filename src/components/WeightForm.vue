<template>
  <Dialog id="weight-form" appendTo="body" header="Weight" v-model:visible="display_modal" :closeOnEscape="false" :closable="false" :modal="true" data-toggle="validator" ref="form">
    <br>
    <div class="p-flex-row p-pb-5">
        <span class="p-float-label">
            <Calendar v-model="vv.date.$model" dateFormat="dd/mm/yy" appendTo="body" v-model:locale="custom_locale" />
            <label for="weight">Date</label>
        </span>
      <span class="error">{{ vv.date?.$errors[0]?.$message }}</span>
    </div>
    <div class="p-flex-row p-pb-5">
        <span class="p-float-label">
            <InputNumber id="weight" v-model="vv.weight.$model" mode="decimal" :minFractionDigits="2" :maxFractionDigits="2" />
            <label for="weight">Weight</label>
            kg
        </span>
      <span class="error">{{ vv.weight?.$errors[0]?.$message }}</span>
    </div>
    <div class="p-flex-row p-pb-5">
        <span class="p-float-label">
            <InputNumber id="fat-percentage" v-model="vv.fat_percentage.$model" mode="decimal" :minFractionDigits="2" :maxFractionDigits="2" />
            <label for="fat-percentage">Fat</label>
            %
        </span>
      <span class="error">{{ vv.fat_percentage?.$errors[0]?.$message }}</span>
    </div>
    <div class="p-flex-row p-pb-5">
        <span class="p-float-label">
            <InputNumber id="muscle" v-model="vv.muscle.$model" mode="decimal" :minFractionDigits="2" :maxFractionDigits="2" />
            <label for="muscle">Muscle</label>
            kg
        </span>
      <span class="error">{{ vv.muscle?.$errors[0]?.$message }}</span>
    </div>
    <template #footer>
      <Button label="Save" icon="pi pi-check" @click="save" />
      <Button label="Cancel" icon="pi pi-times" @click="close_modal" class="p-button-secondary" />
    </template>
  </Dialog>
</template>

<script>
import service from '../services/WeightService';
import { reactive, toRef } from "vue";
import { useVuelidate } from "@vuelidate/core";
import { required } from "@vuelidate/validators";
import { useState } from '../state';

export default {
  name: "WeightForm",
  emits: ["onSave", "onClose"],
  props: {
    show: Boolean,
    weight: Object
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
      weight: null,
      fat_percentage: null,
      muscle: null
    });
    const rules = {
      date: {},
      weight: { required },
      fat_percentage: { required },
      muscle: { required }
    };
    const vv = useVuelidate(rules, {
      date: toRef(fform, "date"),
      weight: toRef(fform, "weight"),
      fat_percentage: toRef(fform, "fat_percentage"),
      muscle: toRef(fform, "muscle")
    });
    return {
      vv,
      fform,
      custom_locale: locale,
      state: useState(),
      display_modal: this.show
    }
  },
  updated() {
    this.display_modal = this.show;
    if (this.weight) {
      this.vv.date.$model = this.weight.date;
      this.vv.weight.$model = this.weight.weight;
      this.vv.fat_percentage.$model = this.weight.fat_percentage;
      this.vv.muscle.$model = this.weight.muscle;
    }
  },
  methods: {
    clear() {
      this.vv.date.$model = new Date();
      this.vv.weight.$model = null;
      this.vv.fat_percentage.$model = null;
      this.vv.muscle.$model = null;
      this.vv.$reset();
    },
    async save() {
      this.vv.$touch();
      if (this.vv.$invalid) {
        return;
      }
      let weight_id = this.weight ? this.weight.id : null;
      let user = this.state.user.mail;
      let previous_weight = await service.get_last(user);
      await service.save(build_weight(this.vv, weight_id, user, previous_weight))
          .then(() => {
            this.$emit('onSave');
            this.$toast.add({severity:'success', summary: 'Weight saved', life: 3000});
            this.close_modal();
          })
          .catch(e => {
            this.handle_error(e)
          });
      this.clear();

      function build_weight(vv, id, user, previous_weight) {
        let weight = {}
        weight.id = id;
        weight.user = user;
        weight.date = vv.date.$model;
        weight.weight = vv.weight.$model;
        weight.fat_percentage = vv.fat_percentage.$model;
        weight.fat = (vv.fat_percentage.$model * vv.weight.$model) / 100;
        weight.muscle = vv.muscle.$model;
        weight.muscle_percentage = (vv.muscle.$model * 100) / vv.weight.$model;
        weight.lost_weight =  weight.weight - previous_weight.weight;
        weight.lost_fat =  weight.fat - previous_weight.fat;
        weight.lost_muscle =  weight.muscle - previous_weight.muscle;
        return weight;
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
