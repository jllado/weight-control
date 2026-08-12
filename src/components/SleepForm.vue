<template>
  <Dialog id="sleep-form" appendTo="body" header="Sleep" v-model:visible="display_modal" :closeOnEscape="false" :closable="false" :modal="true" data-toggle="validator" ref="form">
    <br>
    <div v-if="!fixed_date" class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <Calendar :modelValue="vv.date.$model" @update:modelValue="setDate" dateFormat="dd/mm/yy" appendTo="body" v-model:locale="custom_locale" :maxDate="max_date" />
        <label>Date</label>
      </span>
      <span class="error">{{ vv.date?.$errors[0]?.$message }}</span>
    </div>
    <div class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <Calendar :modelValue="vv.bedtimeStart.$model" @update:modelValue="setBedtimeStart" dateFormat="dd/mm/yy" appendTo="body" v-model:locale="custom_locale" :maxDate="bedtime_max_datetime" :showTime="true" hourFormat="24" :stepMinute="5" />
        <label>Bedtime Start</label>
      </span>
      <span class="error">{{ vv.bedtimeStart?.$errors[0]?.$message }}</span>
    </div>
    <div class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <Calendar :key="bedtime_end_calendar_key" :modelValue="vv.bedtimeEnd.$model" @update:modelValue="setBedtimeEnd" dateFormat="dd/mm/yy" appendTo="body" v-model:locale="custom_locale" :minDate="bedtime_end_min_datetime" :maxDate="bedtime_max_datetime" :showTime="true" hourFormat="24" :stepMinute="5" />
        <label>Bedtime End</label>
      </span>
      <span class="error">{{ vv.bedtimeEnd?.$errors[0]?.$message }}</span>
    </div>
    <div class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <Calendar id="deepSleepDuration" :modelValue="vv.deepSleepDuration.$model" @update:modelValue="value => setDuration('deepSleepDuration', value)" appendTo="body" :timeOnly="true" hourFormat="24" :stepMinute="5" />
        <label for="deepSleepDuration">Deep Sleep</label>
      </span>
      <span class="error">{{ vv.deepSleepDuration?.$errors[0]?.$message }}</span>
    </div>
    <div class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <Calendar id="remSleepDuration" :modelValue="vv.remSleepDuration.$model" @update:modelValue="value => setDuration('remSleepDuration', value)" appendTo="body" :timeOnly="true" hourFormat="24" :stepMinute="5" />
        <label for="remSleepDuration">REM Sleep</label>
      </span>
      <span class="error">{{ vv.remSleepDuration?.$errors[0]?.$message }}</span>
    </div>
    <div class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <Calendar id="awakeTime" :modelValue="vv.awakeTime.$model" @update:modelValue="value => setDuration('awakeTime', value)" appendTo="body" :timeOnly="true" hourFormat="24" :stepMinute="5" />
        <label for="awakeTime">Awake Time</label>
      </span>
      <span class="error">{{ vv.awakeTime?.$errors[0]?.$message }}</span>
    </div>
    <div class="p-flex-row p-pb-5">
      <div class="sleep-derived-panel">
        <div><strong>Time in bed:</strong> {{ formatDurationClock(time_in_bed_seconds) }}</div>
        <div><strong>Total sleep:</strong> {{ formatDurationClock(total_sleep_seconds) }}</div>
        <div><strong>Light sleep:</strong> {{ formatDurationClock(light_sleep_seconds) }}</div>
      </div>
    </div>
    <div class="p-flex-row p-pb-5" v-if="bedtime_error">
      <span class="error">{{ bedtime_error }}</span>
    </div>
    <div class="p-flex-row p-pb-5" v-if="awake_error">
      <span class="error">{{ awake_error }}</span>
    </div>
    <div class="p-flex-row p-pb-5" v-if="sleep_stage_error">
      <span class="error">{{ sleep_stage_error }}</span>
    </div>
    <div class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <InputNumber id="averageHeartRate" v-model="vv.averageHeartRate.$model" mode="decimal" :min="0" :minFractionDigits="0" :maxFractionDigits="2" />
        <label for="averageHeartRate">Average Heart Rate</label>
      </span>
      <span class="error">{{ vv.averageHeartRate?.$errors[0]?.$message }}</span>
    </div>
    <div class="p-flex-row p-pb-5">
      <span class="p-float-label">
        <InputNumber id="averageHrv" v-model="vv.averageHrv.$model" mode="decimal" :min="0" :minFractionDigits="0" :maxFractionDigits="0" />
        <label for="averageHrv">Average HRV</label>
      </span>
      <span class="error">{{ vv.averageHrv?.$errors[0]?.$message }}</span>
    </div>
    <template #footer>
      <Button label="Save" icon="pi pi-check" @click="save" />
      <Button label="Cancel" icon="pi pi-times" @click="close_modal" class="p-button-secondary" />
    </template>
  </Dialog>
</template>

<script>
import service from '../services/SleepService';
import { reactive, toRef } from "vue";
import { useVuelidate } from "@vuelidate/core";
import { minValue, required } from "@vuelidate/validators";
import Sleep from "@/model/Sleep";

export default {
  name: "SleepForm",
  emits: ["onSave", "onClose"],
  props: {
    show: Boolean,
    sleep: Object,
    initial_date: Date,
    fixed_date: Boolean
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
      bedtimeStart: new Date(),
      bedtimeEnd: new Date(),
      deepSleepDuration: null,
      remSleepDuration: null,
      awakeTime: null,
      averageHeartRate: null,
      averageHrv: null
    });
    const rules = {
      date: { required },
      bedtimeStart: { required },
      bedtimeEnd: { required },
      deepSleepDuration: { required },
      remSleepDuration: { required },
      awakeTime: { required },
      averageHeartRate: { required, minValue: minValue(0) },
      averageHrv: { required, minValue: minValue(0) }
    };
    const vv = useVuelidate(rules, {
      date: toRef(fform, "date"),
      bedtimeStart: toRef(fform, "bedtimeStart"),
      bedtimeEnd: toRef(fform, "bedtimeEnd"),
      deepSleepDuration: toRef(fform, "deepSleepDuration"),
      remSleepDuration: toRef(fform, "remSleepDuration"),
      awakeTime: toRef(fform, "awakeTime"),
      averageHeartRate: toRef(fform, "averageHeartRate"),
      averageHrv: toRef(fform, "averageHrv")
    });
    return {
      vv,
      fform,
      custom_locale: locale,
      display_modal: this.show,
      max_date: new Date(),
      max_datetime: new Date()
    }
  },
  computed: {
    time_in_bed_seconds() {
      if (!this.vv.bedtimeStart.$model || !this.vv.bedtimeEnd.$model) {
        return null;
      }
      return differenceInSeconds(this.vv.bedtimeStart.$model, this.vv.bedtimeEnd.$model);
    },
    total_sleep_seconds() {
      if (this.time_in_bed_seconds === null || !this.vv.awakeTime.$model) {
        return null;
      }
      return this.time_in_bed_seconds - durationDateToSeconds(this.vv.awakeTime.$model);
    },
    light_sleep_seconds() {
      if (this.total_sleep_seconds === null || !this.vv.deepSleepDuration.$model || !this.vv.remSleepDuration.$model) {
        return null;
      }
      return this.total_sleep_seconds
          - durationDateToSeconds(this.vv.deepSleepDuration.$model)
          - durationDateToSeconds(this.vv.remSleepDuration.$model);
    },
    bedtime_error() {
      if (!this.vv.bedtimeStart.$model || !this.vv.bedtimeEnd.$model) {
        return null;
      }
      if (this.time_in_bed_seconds <= 0) {
        return 'Bedtime end must be after bedtime start';
      }
      return null;
    },
    awake_error() {
      if (this.time_in_bed_seconds === null || !this.vv.awakeTime.$model) {
        return null;
      }
      if (this.total_sleep_seconds < 0) {
        return 'Awake time cannot be greater than time in bed';
      }
      return null;
    },
    sleep_stage_error() {
      if (this.total_sleep_seconds === null || !this.vv.deepSleepDuration.$model || !this.vv.remSleepDuration.$model) {
        return null;
      }
      if (this.light_sleep_seconds < 0) {
        return 'Deep sleep plus REM sleep cannot be greater than total sleep';
      }
      return null;
    },
    bedtime_max_datetime() {
      return buildBedtimeMaxDate(this.vv.date.$model, this.max_datetime);
    },
    bedtime_end_min_datetime() {
      if (!this.vv.bedtimeStart.$model) {
        return null;
      }
      return addMinutes(this.vv.bedtimeStart.$model, 5);
    },
    bedtime_end_calendar_key() {
      const min = this.bedtime_end_min_datetime ? this.bedtime_end_min_datetime.getTime() : 'none';
      return `${min}-${this.bedtime_max_datetime.getTime()}`;
    }
  },
  watch: {
    show(value) {
      this.display_modal = value;
      if (value) {
        this.load_form();
      }
    },
    sleep() {
      if (this.display_modal) {
        this.load_form();
      }
    },
    initial_date() {
      if (this.display_modal && !this.sleep) {
        this.load_form();
      }
    }
  },
  methods: {
    setDate(value) {
      this.vv.date.$model = cloneDate(value);
      this.alignBedtimesWithDate();
    },
    setBedtimeStart(value) {
      this.vv.bedtimeStart.$model = cloneDate(value);
      if (this.vv.bedtimeEnd.$model && this.vv.bedtimeEnd.$model <= this.vv.bedtimeStart.$model) {
        this.vv.bedtimeEnd.$model = addMinutes(this.vv.bedtimeStart.$model, 5);
      }
      this.clampBedtimeEnd();
    },
    setBedtimeEnd(value) {
      this.vv.bedtimeEnd.$model = cloneDate(value);
      this.clampBedtimeEnd();
    },
    setDuration(field, value) {
      this.vv[field].$model = cloneDate(value);
    },
    load_form() {
      if (this.sleep) {
        this.vv.date.$model = this.sleep.date;
        this.vv.bedtimeStart.$model = normalizeDateToFiveMinutes(this.sleep.bedtimeStart);
        this.vv.bedtimeEnd.$model = normalizeDateToFiveMinutes(this.sleep.bedtimeEnd);
        this.vv.deepSleepDuration.$model = secondsToDurationDate(this.sleep.deepSleepDuration);
        this.vv.remSleepDuration.$model = secondsToDurationDate(this.sleep.remSleepDuration);
        this.vv.awakeTime.$model = secondsToDurationDate(this.sleep.awakeTime);
        this.vv.averageHeartRate.$model = this.sleep.averageHeartRate;
        this.vv.averageHrv.$model = this.sleep.averageHrv;
        this.vv.$reset();
        return;
      }
      this.clear();
    },
    clear() {
      const date = this.initial_date || new Date();
      const bedtimeStart = buildDefaultBedtimeStart(date);
      const bedtimeEnd = buildDefaultBedtimeEnd(date);
      this.vv.date.$model = date;
      this.vv.bedtimeStart.$model = bedtimeStart;
      this.vv.bedtimeEnd.$model = bedtimeEnd;
      this.vv.deepSleepDuration.$model = secondsToDurationDate(0);
      this.vv.remSleepDuration.$model = secondsToDurationDate(0);
      this.vv.awakeTime.$model = secondsToDurationDate(0);
      this.vv.averageHeartRate.$model = null;
      this.vv.averageHrv.$model = null;
      this.vv.$reset();
    },
    alignBedtimesWithDate() {
      if (!this.vv.date.$model) {
        return;
      }
      const bedtimeMax = this.bedtime_max_datetime;
      if (this.vv.bedtimeStart.$model > bedtimeMax || this.vv.bedtimeEnd.$model > bedtimeMax) {
        this.vv.bedtimeStart.$model = buildDefaultBedtimeStart(this.vv.date.$model);
        this.vv.bedtimeEnd.$model = buildDefaultBedtimeEnd(this.vv.date.$model);
      }
      this.clampBedtimeEnd();
    },
    clampBedtimeEnd() {
      if (!this.vv.bedtimeEnd.$model) {
        return;
      }
      if (this.vv.bedtimeEnd.$model > this.bedtime_max_datetime) {
        this.vv.bedtimeEnd.$model = cloneDate(this.bedtime_max_datetime);
      }
    },
    async save() {
      this.vv.$touch();
      if (this.vv.$invalid || this.bedtime_error || this.awake_error || this.sleep_stage_error) {
        return;
      }
      let sleep = new Sleep();
      sleep.id = this.sleep ? this.sleep.id : null;
      sleep.date = this.vv.date.$model;
      sleep.bedtimeStart = normalizeDateToFiveMinutes(this.vv.bedtimeStart.$model);
      sleep.bedtimeEnd = normalizeDateToFiveMinutes(this.vv.bedtimeEnd.$model);
      sleep.totalSleepDuration = normalizeSecondsToFiveMinutes(this.total_sleep_seconds);
      sleep.deepSleepDuration = durationDateToSeconds(this.vv.deepSleepDuration.$model);
      sleep.remSleepDuration = durationDateToSeconds(this.vv.remSleepDuration.$model);
      sleep.lightSleepDuration = normalizeSecondsToFiveMinutes(this.light_sleep_seconds);
      sleep.awakeTime = durationDateToSeconds(this.vv.awakeTime.$model);
      sleep.averageHeartRate = this.vv.averageHeartRate.$model;
      sleep.averageHrv = this.vv.averageHrv.$model;
      await service.save(sleep.toObject())
          .then(() => {
            this.$emit('onSave');
            this.$toast.add({severity:'success', summary: 'Sleep saved', life: 3000});
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
    },
    formatDurationClock(seconds) {
      return formatDurationClock(seconds);
    }
  }
}

function buildDefaultBedtimeStart(date) {
  const bedtimeStart = normalizeDateToFiveMinutes(date);
  bedtimeStart.setDate(bedtimeStart.getDate() - 1);
  bedtimeStart.setHours(23, 0, 0, 0);
  return bedtimeStart;
}

function buildDefaultBedtimeEnd(date) {
  const bedtimeEnd = normalizeDateToFiveMinutes(date);
  bedtimeEnd.setHours(7, 0, 0, 0);
  return bedtimeEnd;
}

function buildBedtimeMaxDate(date, maxDateTime) {
  const bedtimeMax = normalizeDateToFiveMinutes(date || maxDateTime);
  bedtimeMax.setHours(23, 55, 0, 0);
  return bedtimeMax.getTime() > maxDateTime.getTime()
      ? normalizeDateToFiveMinutes(maxDateTime)
      : bedtimeMax;
}

function addMinutes(value, minutes) {
  const date = cloneDate(value);
  date.setMinutes(date.getMinutes() + minutes);
  return date;
}

function cloneDate(value) {
  return value ? new Date(value) : value;
}

function differenceInSeconds(start, end) {
  return Math.round((end.getTime() - start.getTime()) / 1000);
}

function durationDateToSeconds(value) {
  return normalizeSecondsToFiveMinutes((value.getHours() * 3600) + (value.getMinutes() * 60));
}

function secondsToDurationDate(seconds) {
  const normalizedSeconds = normalizeSecondsToFiveMinutes(seconds);
  const duration = new Date();
  duration.setHours(0, 0, 0, 0);
  duration.setHours(Math.floor(normalizedSeconds / 3600), Math.floor((normalizedSeconds % 3600) / 60), 0, 0);
  return duration;
}

function normalizeDateToFiveMinutes(value) {
  const date = new Date(value);
  date.setSeconds(0, 0);
  const minutes = Math.round(date.getMinutes() / 5) * 5;
  date.setMinutes(minutes);
  return date;
}

function normalizeSecondsToFiveMinutes(seconds) {
  return Math.round(seconds / 300) * 300;
}

function formatDurationClock(seconds) {
  if (seconds === null || seconds === undefined) {
    return '-';
  }
  const normalizedSeconds = Math.max(0, normalizeSecondsToFiveMinutes(seconds));
  const hours = Math.floor(normalizedSeconds / 3600);
  const minutes = Math.floor((normalizedSeconds % 3600) / 60);
  return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`;
}
</script>

<style scoped>
.sleep-derived-panel {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
</style>
