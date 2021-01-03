<template>
  <Dialog id="weight-form" appendTo="body" header="Weight" v-model:visible="display_modal" :closeOnEscape="false" :closable="false" :modal="true" data-toggle="validator" ref="form">
    <br>
    <div class="p-flex-row p-pb-5">
        <span class="p-float-label">
            <Calendar v-model="vv.date.$model" dateFormat="yy-mm-dd" appendTo="body" />
            <label for="weight">Date</label>
        </span>
      <span class="error">{{ vv.date?.$errors[0]?.$message }}</span>
    </div>
    <div class="p-flex-row p-pb-5">
        <span class="p-float-label">
            <InputNumber id="weight" v-model="vv.weight.$model" mode="decimal" :minFractionDigits="2" :maxFractionDigits="2" />
            <label for="weight">Weight</label>
        </span>
      <span class="error">{{ vv.weight?.$errors[0]?.$message }}</span>
    </div>
    <div class="p-flex-row p-pb-5">
        <span class="p-float-label">
            <InputNumber id="fat-percentage" v-model="vv.fat_percentage.$model" mode="decimal" :minFractionDigits="2" :maxFractionDigits="2" />
            <label for="fat-percentage">Fat Percentage</label>
            %
        </span>
      <span class="error">{{ vv.fat_percentage?.$errors[0]?.$message }}</span>
    </div>
    <div class="p-flex-row p-pb-5">
        <span class="p-float-label">
            <InputNumber id="muscle" v-model="vv.muscle.$model" mode="decimal" :minFractionDigits="2" :maxFractionDigits="2" />
            <label for="muscle">Muscle</label>
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
      let weightId = this.weight ? this.weight.id : null;
      await service.save(build_weight(this.vv, weightId, this.state.user.mail))
          .then(() => {
            this.$emit('onSave');
            this.$toast.add({severity:'success', summary: 'Weight saved', life: 3000});
            this.close_modal();
          })
          .catch(e => {
            this.handle_error(e)
          });
      this.clear();

      function build_weight(vv, id, user) {
        let weight = {}
        weight.id = id;
        weight.user = user;
        weight.date = vv.date.$model;
        weight.weight = vv.weight.$model;
        weight.fat_percentage = vv.fat_percentage.$model;
        weight.muscle = vv.muscle.$model;
        weight.fat = (vv.fat_percentage.$model * vv.weight.$model) / 100;
        weight.muscle_percentage = (vv.muscle.$model * 100) / vv.weight.$model;
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
