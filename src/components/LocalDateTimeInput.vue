<template>
  <span class="local-datetime-input p-inputwrapper" :class="{'p-inputwrapper-filled': modelValue}">
    <Calendar :inputId="`${inputId}-date`" :modelValue="date_value" @update:modelValue="setDate" dateFormat="dd/mm/yy" appendTo="body" :minDate="min_date" :maxDate="max_date" :aria-label="`${label} date`" />
    <ClockCalendar :key="time_calendar_key" :inputId="`${inputId}-time`" :modelValue="time_value" @update:modelValue="setTime" :timeOnly="true" hourFormat="24" :stepMinute="5" appendTo="body" :minDate="min_time" :maxDate="max_time" :aria-label="`${label} time`" />
  </span>
</template>

<script>
import Calendar from 'primevue/calendar';

// PrimeVue parses typed time-only values on today, which can have a DST gap.
const ClockCalendar = {
  extends: Calendar,
  methods: {
    parseDateTime(text) {
      const date = new Date(2000, 0, 1);
      this.populateTime(date, text);
      return date;
    }
  }
};

export default {
  name: 'LocalDateTimeInput',
  components: {ClockCalendar},
  emits: ['update:modelValue'],
  props: {
    modelValue: String,
    inputId: {type: String, required: true},
    label: {type: String, required: true},
    min: String,
    max: String
  },
  computed: {
    date_value() { return calendarDate(this.modelValue); },
    time_value() { return calendarTime(this.modelValue); },
    time_calendar_key() { return `${this.min_time?.getTime() ?? 'none'}-${this.max_time?.getTime() ?? 'none'}`; },
    min_date() { return calendarDate(this.min); },
    max_date() { return calendarDate(this.max); },
    min_time() { return this.modelValue && this.min?.slice(0, 10) === this.modelValue.slice(0, 10) ? calendarTime(this.min) : null; },
    max_time() { return this.modelValue && this.max?.slice(0, 10) === this.modelValue.slice(0, 10) ? calendarTime(this.max) : null; }
  },
  methods: {
    setDate(value) {
      this.$emit('update:modelValue', value ? `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}T${this.modelValue?.slice(11, 16) || '00:00'}` : null);
    },
    setTime(value) {
      if (!this.modelValue) return;
      this.$emit('update:modelValue', value ? `${this.modelValue.slice(0, 10)}T${pad(value.getHours())}:${pad(value.getMinutes())}` : null);
    }
  }
};

function calendarDate(value) {
  return value ? new Date(Number(value.slice(0, 4)), Number(value.slice(5, 7)) - 1, Number(value.slice(8, 10)), 12) : null;
}

function calendarTime(value) {
  // The clock picker uses an independent day, so a gap on the selected date cannot shift its time.
  return value ? new Date(2000, 0, 1, Number(value.slice(11, 13)), Number(value.slice(14, 16))) : null;
}

function pad(value) { return String(value).padStart(2, '0'); }
</script>

<style scoped>
.local-datetime-input {
  display: inline-flex;
  gap: 0.5rem;
  width: 14rem;
  max-width: 100%;
}
.local-datetime-input > :first-child { flex: 1; min-width: 0; }
.local-datetime-input > :last-child { width: 4.5rem; flex: none; }
.local-datetime-input :deep(input) { width: 100%; min-width: 0; }
</style>
