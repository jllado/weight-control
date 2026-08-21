<template>
  <Dialog id="fasting-period-form" appendTo="body" header="Fasting Period" v-model:visible="display_modal" :closeOnEscape="false" :closable="false" :modal="true" data-toggle="validator" ref="form">
    <br>
    <div class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <Calendar inputId="fasting-start-time" v-model="vv.startTime.$model" dateFormat="dd/mm/yy" appendTo="body" :maxDate="max_datetime" :showTime="true" hourFormat="24" :stepMinute="5" />
        <label for="fasting-start-time">Start</label>
      </span>
      <span class="error">{{ vv.startTime?.$errors[0]?.$message }}</span>
    </div>
    <div class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <Calendar inputId="fasting-end-time" v-model="vv.endTime.$model" dateFormat="dd/mm/yy" appendTo="body" :minDate="vv.startTime.$model" :maxDate="max_datetime" :showTime="true" hourFormat="24" :stepMinute="5" />
        <label for="fasting-end-time">End</label>
      </span>
      <span class="error">{{ vv.endTime?.$errors[0]?.$message }}</span>
    </div>
    <div v-if="time_error" class="p-flex-row p-pb-5">
      <span class="error">{{ time_error }}</span>
    </div>
    <div class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <InputText id="fasting-notes" v-model="vv.notes.$model" />
        <label for="fasting-notes">Notes (optional)</label>
      </span>
    </div>
    <template #footer>
      <Button label="Save" icon="pi pi-check" @click="save" />
      <Button label="Cancel" icon="pi pi-times" @click="close_modal" class="p-button-secondary" />
    </template>
  </Dialog>
</template>

<script>
import {reactive} from 'vue';
import {useVuelidate} from '@vuelidate/core';
import {required} from '@vuelidate/validators';
import service from '../services/FastingPeriodService';
import FastingPeriod from '@/model/FastingPeriod';

export default {
  name: 'FastingPeriodForm',
  emits: ['onSave', 'onClose'],
  props: {
    show: Boolean,
    fasting_period: Object
  },
  data() {
    const defaults = defaultPeriod();
    const fform = reactive({
      startTime: defaults.startTime,
      endTime: defaults.endTime,
      notes: ''
    });
    return {
      vv: useVuelidate({startTime: {required}, endTime: {required}, notes: {}}, fform),
      fform,
      display_modal: this.show,
      max_datetime: new Date()
    };
  },
  computed: {
    time_error() {
      if (!this.vv.startTime.$model || !this.vv.endTime.$model) {
        return null;
      }
      if (this.vv.startTime.$model >= this.vv.endTime.$model) {
        return 'Fasting period end must be after the start';
      }
      if (this.vv.endTime.$model > this.max_datetime) {
        return 'Fasting period end cannot be in the future';
      }
      return null;
    }
  },
  watch: {
    show(value) {
      this.display_modal = value;
      if (value) {
        this.load_form();
      }
    },
    fasting_period() {
      if (this.display_modal) {
        this.load_form();
      }
    }
  },
  methods: {
    load_form() {
      if (this.fasting_period) {
        this.vv.startTime.$model = new Date(this.fasting_period.startTime);
        this.vv.endTime.$model = new Date(this.fasting_period.endTime);
        this.vv.notes.$model = this.fasting_period.notes || '';
      } else {
        const defaults = defaultPeriod();
        this.vv.startTime.$model = defaults.startTime;
        this.vv.endTime.$model = defaults.endTime;
        this.vv.notes.$model = '';
      }
      this.max_datetime = new Date();
      this.vv.$reset();
    },
    async save() {
      this.vv.$touch();
      if (this.vv.$invalid || this.time_error) {
        return;
      }
      const period = new FastingPeriod();
      period.id = this.fasting_period?.id || null;
      period.startTime = this.vv.startTime.$model;
      period.endTime = this.vv.endTime.$model;
      period.notes = this.vv.notes.$model || null;
      try {
        await service.save(period.toObject());
        this.$emit('onSave');
        this.$toast.add({severity: 'success', summary: 'Fasting period saved', life: 3000});
        this.close_modal();
      } catch (error) {
        this.$log.error(error);
        this.$toast.add({severity: 'error', summary: 'Failed', detail: error, life: 3000});
      }
    },
    close_modal() {
      this.$emit('onClose');
    }
  }
};

function defaultPeriod() {
  const endTime = roundDownToFiveMinutes(new Date());
  return {startTime: new Date(endTime.getTime() - 16 * 60 * 60 * 1000), endTime};
}

function roundDownToFiveMinutes(value) {
  const date = new Date(value);
  date.setMinutes(Math.floor(date.getMinutes() / 5) * 5, 0, 0);
  return date;
}
</script>
