<template>
  <div class="weekly-reminder-fields">
    <div class="weekly-reminder-field">
      <label :id="`${idPrefix}-day-label`" :for="`${idPrefix}-day`">{{ label }} day</label>
      <Dropdown :inputId="`${idPrefix}-day`" :aria-labelledby="`${idPrefix}-day-label`" :modelValue="day" @update:modelValue="$emit('update:day', $event)" :options="weekdays" optionLabel="label" optionValue="value" :disabled="disabled" />
    </div>
    <div class="weekly-reminder-field">
      <label :for="`${idPrefix}-time`">{{ label }} time</label>
      <Calendar :inputId="`${idPrefix}-time`" :modelValue="time" @update:modelValue="$emit('update:time', $event)" :timeOnly="true" hourFormat="24" :stepMinute="5" :manualInput="false" showIcon :disabled="disabled" />
    </div>
  </div>
</template>

<script>
export default {
  props: {
    idPrefix: {type: String, required: true},
    label: {type: String, required: true},
    day: {type: String, required: true},
    time: {type: Date, required: true},
    disabled: Boolean
  },
  emits: ['update:day', 'update:time'],
  data() {
    return {weekdays: ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'].map(label => ({label, value: label.toUpperCase()}))};
  }
};
</script>

<style scoped>
.weekly-reminder-fields {display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem;}
.weekly-reminder-field {display: flex; flex-direction: column; gap: .35rem; min-width: 0;}
.weekly-reminder-field :deep(.p-inputtext) {min-width: 0; width: 100%;}
@media (max-width: 640px) {.weekly-reminder-fields {grid-template-columns: 1fr;}}
</style>
