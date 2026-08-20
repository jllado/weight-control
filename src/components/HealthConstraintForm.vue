<template>
  <Dialog appendTo="body" header="Health constraint" v-model:visible="displayModal" :closeOnEscape="false" :closable="false" :modal="true">
    <div class="constraint-form">
      <div class="p-field">
        <label for="health-constraint-type">Type</label>
        <Dropdown id="health-constraint-type" v-model="vv.type.$model" :options="typeOptions" optionLabel="label" optionValue="value" placeholder="Select type" />
        <span class="error">{{ vv.type?.$errors[0]?.$message }}</span>
      </div>
      <div class="p-field">
        <label for="health-constraint-title">Title</label>
        <InputText id="health-constraint-title" v-model="vv.title.$model" maxlength="255" />
        <span class="error">{{ vv.title?.$errors[0]?.$message }}</span>
      </div>
      <div class="p-field">
        <label for="health-constraint-details">Details</label>
        <textarea id="health-constraint-details" v-model="vv.details.$model" class="p-inputtext p-component constraint-details" rows="5" />
        <span class="error">{{ vv.details?.$errors[0]?.$message }}</span>
      </div>
      <div class="p-field">
        <label for="health-constraint-source">Source</label>
        <Dropdown id="health-constraint-source" v-model="vv.source.$model" :options="sourceOptions" optionLabel="label" optionValue="value" placeholder="Select source" />
        <span class="error">{{ vv.source?.$errors[0]?.$message }}</span>
      </div>
      <div class="constraint-dates">
        <div class="p-field">
          <label for="health-constraint-start-date">Start date</label>
          <Calendar inputId="health-constraint-start-date" v-model="vv.startDate.$model" dateFormat="dd/mm/yy" appendTo="body" />
          <span class="error">{{ vv.startDate?.$errors[0]?.$message }}</span>
        </div>
        <div class="p-field">
          <label for="health-constraint-end-date">End date (optional)</label>
          <Calendar inputId="health-constraint-end-date" v-model="vv.endDate.$model" dateFormat="dd/mm/yy" appendTo="body" showButtonBar />
          <span class="error">{{ dateError }}</span>
        </div>
      </div>
    </div>
    <template #footer>
      <Button label="Save" icon="pi pi-check" @click="save" :loading="saving" />
      <Button label="Cancel" icon="pi pi-times" @click="close" class="p-button-secondary" />
    </template>
  </Dialog>
</template>

<script>
import dayjs from 'dayjs';
import {reactive, toRef} from 'vue';
import {useVuelidate} from '@vuelidate/core';
import {maxLength, required} from '@vuelidate/validators';
import HealthConstraint, {getHealthConstraintSourceOptions, getHealthConstraintTypeOptions} from '../model/HealthConstraint';
import healthConstraintService from '../services/HealthConstraintService';

export default {
  name: 'HealthConstraintForm',
  emits: ['onSave', 'onClose'],
  props: {
    show: Boolean,
    constraint: Object
  },
  data() {
    const form = reactive({type: null, title: '', details: '', source: null, startDate: new Date(), endDate: null});
    const rules = {
      type: {required},
      title: {required, maxLength: maxLength(255)},
      details: {required},
      source: {required},
      startDate: {required},
      endDate: {}
    };
    return {
      vv: useVuelidate(rules, Object.fromEntries(Object.keys(rules).map(key => [key, toRef(form, key)]))),
      form,
      typeOptions: getHealthConstraintTypeOptions(),
      sourceOptions: getHealthConstraintSourceOptions(),
      displayModal: this.show,
      dateError: '',
      saving: false
    };
  },
  watch: {
    show(value) {
      this.displayModal = value;
      if (value) {
        this.loadForm();
      }
    },
    constraint() {
      if (this.displayModal) {
        this.loadForm();
      }
    }
  },
  methods: {
    loadForm() {
      this.vv.type.$model = this.constraint?.type || null;
      this.vv.title.$model = this.constraint?.title || '';
      this.vv.details.$model = this.constraint?.details || '';
      this.vv.source.$model = this.constraint?.source || null;
      this.vv.startDate.$model = this.constraint?.startDate || new Date();
      this.vv.endDate.$model = this.constraint?.endDate || null;
      this.dateError = '';
      this.vv.$reset();
    },
    async save() {
      this.vv.$touch();
      this.dateError = this.vv.endDate.$model && dayjs(this.vv.startDate.$model).isAfter(this.vv.endDate.$model, 'day')
          ? 'End date must not be before the start date'
          : '';
      if (this.vv.$invalid || this.dateError) {
        return;
      }
      const value = new HealthConstraint();
      value.id = this.constraint?.id;
      value.type = this.vv.type.$model;
      value.title = this.vv.title.$model;
      value.details = this.vv.details.$model;
      value.source = this.vv.source.$model;
      value.startDate = this.vv.startDate.$model;
      value.endDate = this.vv.endDate.$model;
      value.active = this.constraint?.active ?? true;
      this.saving = true;
      try {
        await healthConstraintService.save(value);
        this.$emit('onSave');
        this.$toast.add({severity: 'success', summary: 'Health constraint saved', life: 3000});
        this.close();
      } catch (e) {
        this.handleError(e);
      } finally {
        this.saving = false;
      }
    },
    close() {
      this.$emit('onClose');
    },
    handleError(e) {
      this.$log.error(e);
      this.$toast.add({severity: 'error', summary: 'Failed', detail: e, life: 3000});
    }
  }
};
</script>

<style scoped>
.constraint-form {
  min-width: min(32rem, 75vw);
}
.constraint-form .p-field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}
.constraint-details {
  resize: vertical;
  width: 100%;
}
.constraint-dates {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
}
@media (max-width: 640px) {
  .constraint-dates {
    grid-template-columns: 1fr;
  }
}
</style>
