<template>
  <Dialog id="back-status-form" appendTo="body" header="Back Status" v-model:visible="display_modal" :closeOnEscape="false" :closable="false" :modal="true" :style="{width: '52rem'}" :breakpoints="{'960px': '75vw', '640px': '95vw'}" data-toggle="validator" ref="form">
    <br>
    <div class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <Calendar v-model="vv.date.$model" dateFormat="dd/mm/yy" appendTo="body" v-model:locale="custom_locale" :maxDate="max_date" />
        <label>Date</label>
      </span>
      <span class="error">{{ vv.date?.$errors[0]?.$message }}</span>
    </div>
    <p class="back-status-scale-help">Use 0 for none and 10 for extreme.</p>
    <div class="back-status-regions">
      <div v-for="region in regions" :key="region.key" class="back-status-region">
        <h3>{{ region.label }}</h3>
        <div v-for="metric in metrics" :key="metric.key" class="back-status-score-field">
          <label :for="`${region.key}-${metric.key}`">{{ metric.label }} (0–10)</label>
          <InputNumber :id="`${region.key}-${metric.key}`" v-model="vv[region.key][metric.key].$model" :min="0" :max="10" :showButtons="true" />
          <span class="error">{{ vv[region.key][metric.key]?.$errors[0]?.$message }}</span>
        </div>
      </div>
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
import service from '../services/BackStatusService';
import {reactive} from "vue";
import {useVuelidate} from "@vuelidate/core";
import {maxLength, maxValue, minValue, required} from "@vuelidate/validators";
import BackStatus, {BACK_METRICS, BACK_REGIONS} from "@/model/BackStatus";

function emptyRegion() {
  return {pain: 0, stiffness: 0, activityLimitation: 0};
}

function regionRules() {
  return {
    pain: {required, minValue: minValue(0), maxValue: maxValue(10)},
    stiffness: {required, minValue: minValue(0), maxValue: maxValue(10)},
    activityLimitation: {required, minValue: minValue(0), maxValue: maxValue(10)}
  };
}

export default {
  name: "BackStatusForm",
  emits: ["onSave", "onClose"],
  props: {
    show: Boolean,
    back_status: Object,
    initial_date: Date
  },
  data() {
    const locale = {
      firstDayOfWeek: 1,
      dayNames: ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"],
      dayNamesShort: ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"],
      dayNamesMin: ["Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"],
      monthNames: ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"],
      monthNamesShort: ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"],
      today: 'Today',
      clear: 'Clear',
      dateFormat: 'mm/dd/yy',
      weekHeader: 'Wk'
    };
    const fform = reactive({
      date: this.initial_date || new Date(),
      lower: emptyRegion(),
      middle: emptyRegion(),
      upper: emptyRegion(),
      note: ''
    });
    const rules = {
      date: {required},
      lower: regionRules(),
      middle: regionRules(),
      upper: regionRules(),
      note: {maxLength: maxLength(500)}
    };
    return {
      vv: useVuelidate(rules, fform),
      fform,
      regions: BACK_REGIONS,
      metrics: BACK_METRICS,
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
    back_status() {
      if (this.display_modal) {
        this.load_form();
      }
    },
    initial_date() {
      if (this.display_modal && !this.back_status) {
        this.load_form();
      }
    }
  },
  methods: {
    load_form() {
      if (this.back_status) {
        this.vv.date.$model = this.back_status.date;
        this.load_region('lower', this.back_status.lower);
        this.load_region('middle', this.back_status.middle);
        this.load_region('upper', this.back_status.upper);
        this.vv.note.$model = this.back_status.note;
        this.vv.$reset();
        return;
      }
      this.clear();
    },
    load_region(region, values) {
      this.vv[region].pain.$model = values.pain;
      this.vv[region].stiffness.$model = values.stiffness;
      this.vv[region].activityLimitation.$model = values.activityLimitation;
    },
    clear() {
      this.vv.date.$model = this.initial_date || new Date();
      this.load_region('lower', emptyRegion());
      this.load_region('middle', emptyRegion());
      this.load_region('upper', emptyRegion());
      this.vv.note.$model = '';
      this.vv.$reset();
    },
    async save() {
      this.vv.$touch();
      if (this.vv.$invalid) {
        return;
      }
      let status = new BackStatus();
      status.id = this.back_status ? this.back_status.id : null;
      status.date = this.vv.date.$model;
      status.lower = {...this.fform.lower};
      status.middle = {...this.fform.middle};
      status.upper = {...this.fform.upper};
      status.note = this.vv.note.$model || null;
      await service.save(status.toObject())
          .then(() => {
            this.$emit('onSave');
            this.$toast.add({severity: 'success', summary: 'Back status saved', life: 3000});
            this.close_modal();
          })
          .catch(e => this.handle_error(e));
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

<style scoped>
.back-status-regions {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1rem;
  margin-bottom: 2rem;
}
.back-status-scale-help {
  margin: 0 0 1rem;
  color: #666;
}
.back-status-region {
  padding: 1rem;
  border: 1px solid #dce4ea;
  border-radius: 0.5rem;
}
.back-status-region h3 {
  margin-top: 0;
}
.back-status-score-field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  margin-bottom: 1rem;
}
@media (max-width: 640px) {
  .back-status-regions {
    grid-template-columns: 1fr;
  }
}
</style>
