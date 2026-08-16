<template>
  <Dialog id="lipid-panel-form" appendTo="body" header="Lipid Panel" v-model:visible="display_modal" :closeOnEscape="false" :closable="false" :modal="true" data-toggle="validator" ref="form">
    <br>
    <div class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <Calendar inputId="lipid-panel-date" v-model="vv.date.$model" dateFormat="dd/mm/yy" appendTo="body" v-model:locale="custom_locale" :maxDate="max_date" />
        <label for="lipid-panel-date">Date</label>
      </span>
      <span class="error">{{ vv.date?.$errors[0]?.$message }}</span>
    </div>
    <div v-for="field in fields" :key="field.key" class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <InputNumber :inputId="field.key" v-model="vv[field.key].$model" mode="decimal" :min="1" :minFractionDigits="0" :maxFractionDigits="0" />
        <label :for="field.key">{{ field.label }}</label>
        mg/dL
      </span>
      <span class="error">{{ vv[field.key]?.$errors[0]?.$message }}</span>
    </div>
    <template #footer>
      <Button label="Save" icon="pi pi-check" @click="save" />
      <Button label="Cancel" icon="pi pi-times" @click="close_modal" class="p-button-secondary" />
    </template>
  </Dialog>
</template>

<script>
import service from '../services/LipidPanelService';
import {reactive, toRef} from 'vue';
import {useVuelidate} from '@vuelidate/core';
import {minValue, required} from '@vuelidate/validators';
import LipidPanel from '@/model/LipidPanel';

const fields = [
  {key: 'totalCholesterol', label: 'Total Cholesterol'},
  {key: 'hdlCholesterol', label: 'HDL Cholesterol'},
  {key: 'ldlCholesterol', label: 'LDL Cholesterol'},
  {key: 'triglycerides', label: 'Triglycerides'}
];

export default {
  name: 'LipidPanelForm',
  emits: ['onSave', 'onClose'],
  props: {
    show: Boolean,
    lipid_panel: Object
  },
  data() {
    const locale = {
      firstDayOfWeek: 1,
      dayNames: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'],
      dayNamesShort: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
      dayNamesMin: ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'],
      monthNames: ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'],
      monthNamesShort: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
      today: 'Today',
      clear: 'Clear',
      dateFormat: 'mm/dd/yy',
      weekHeader: 'Wk'
    };
    const fform = reactive({
      date: new Date(),
      totalCholesterol: null,
      hdlCholesterol: null,
      ldlCholesterol: null,
      triglycerides: null
    });
    const positiveRequired = {required, minValue: minValue(1)};
    const rules = {
      date: {required},
      totalCholesterol: positiveRequired,
      hdlCholesterol: positiveRequired,
      ldlCholesterol: positiveRequired,
      triglycerides: positiveRequired
    };
    const vv = useVuelidate(rules, Object.fromEntries(Object.keys(rules).map(key => [key, toRef(fform, key)])));
    return {
      vv,
      fform,
      fields,
      custom_locale: locale,
      display_modal: this.show,
      max_date: new Date()
    };
  },
  watch: {
    show(value) {
      this.display_modal = value;
      if (value) {
        this.load_form();
      }
    },
    lipid_panel() {
      if (this.display_modal) {
        this.load_form();
      }
    }
  },
  methods: {
    load_form() {
      if (!this.lipid_panel) {
        this.clear();
        return;
      }
      this.vv.date.$model = this.lipid_panel.date;
      fields.forEach(field => this.vv[field.key].$model = this.lipid_panel[field.key]);
    },
    clear() {
      this.vv.date.$model = new Date();
      fields.forEach(field => this.vv[field.key].$model = null);
      this.vv.$reset();
    },
    async save() {
      this.vv.$touch();
      if (this.vv.$invalid) {
        return;
      }
      const panel = new LipidPanel();
      panel.id = this.lipid_panel ? this.lipid_panel.id : null;
      panel.date = this.vv.date.$model;
      fields.forEach(field => panel[field.key] = this.vv[field.key].$model);
      try {
        await service.save(panel.toObject());
        this.$emit('onSave');
        this.$toast.add({severity: 'success', summary: 'Lipid panel saved', life: 3000});
        this.close_modal();
      } catch (e) {
        this.handle_error(e);
      }
    },
    close_modal() {
      this.clear();
      this.$emit('onClose');
    },
    handle_error(e) {
      this.$log.error(e);
      this.$toast.add({severity: 'error', summary: 'Failed', detail: e, life: 3000});
    }
  }
};
</script>
