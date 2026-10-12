<template>
  <div class="workout-source-picker">
    <div v-for="source in sources" :key="source.kind" class="p-field p-mb-4">
      <label :for="`${idPrefix}-${source.kind}`" class="p-d-block p-mb-2">{{ source.label }}</label>
      <Dropdown :inputId="`${idPrefix}-${source.kind}`" :aria-label="source.label" :inputProps="{'aria-describedby': `${idPrefix}-${source.kind}-help`}" :modelValue="modelValue?.kind === source.kind ? modelValue.id : null" :options="source.options" optionLabel="label" optionValue="id" :placeholder="`Select a ${source.kind === 'previous' ? 'previous' : 'planned'} workout`" :class="source.kind === 'previous' ? 'workout-preload' : 'planned-workout-preload'" :disabled="disabled || source.loading || !!source.error || !source.options.length" showClear :panelStyle="{maxWidth: 'calc(100vw - 2rem)'}" @update:modelValue="select(source, $event)" @before-show="$emit('overlay-change', {kind: source.kind, visible: true})" @hide="$emit('overlay-change', {kind: source.kind, visible: false})">
        <template #option="{option}"><span class="workout-source-option">{{ option.label }}</span></template>
        <template #clearicon="{onClick}"><button type="button" class="p-dropdown-clear-icon p-link" :aria-label="`Clear ${source.label.toLowerCase()}`" @click.stop="clear(source, onClick, $event)"><i class="pi pi-times" aria-hidden="true" /></button></template>
      </Dropdown>
      <span :id="`${idPrefix}-${source.kind}-help`" class="p-hidden-accessible">Optional. Clear with Delete or the clear button.</span>
      <p v-if="source.loading" role="status">Loading {{ source.label.toLowerCase() }}s…</p>
      <p v-else-if="source.error" role="alert" class="error">{{ source.error }} <Button label="Retry" :aria-label="`Retry ${source.label.toLowerCase()}s`" class="p-button-text" :disabled="disabled" @click="$emit('retry', source.kind)" /></p>
      <p v-else-if="!source.options.length">{{ source.empty }}</p>
    </div>
  </div>
</template>
<script>
export default {
  name: 'WorkoutSourcePicker',
  props: {idPrefix: {type: String, required: true}, modelValue: Object, previousOptions: {type: Array, required: true}, plannedOptions: {type: Array, required: true}, previousLoading: Boolean, plannedLoading: Boolean, previousError: String, plannedError: String, disabled: Boolean},
  emits: ['update:modelValue', 'retry', 'overlay-change'],
  computed: {
    sources() { return [
      {kind: 'previous', label: 'Previous workout', options: this.previousOptions, loading: this.previousLoading, error: this.previousError, empty: 'No previous workouts through this date.'},
      {kind: 'planned', label: 'Planned workout', options: this.plannedOptions, loading: this.plannedLoading, error: this.plannedError, empty: 'No planned workouts in the current plan.'}
    ]; }
  },
  methods: {
    clear(source, onClick, event) { onClick(event); document.getElementById(`${this.idPrefix}-${source.kind}`).focus(); },
    select(source, id) { this.$emit('update:modelValue', id === null ? null : {...source.options.find(option => option.id === id), kind: source.kind}); }
  }
};
</script>
<style scoped>
.workout-source-picker { min-width: 0; }
.workout-source-picker :deep(.p-dropdown) { width: 100%; min-width: 0; }
.workout-source-picker :deep(.p-dropdown-label), .workout-source-option { white-space: normal; overflow-wrap: anywhere; }
.workout-source-picker :deep(.p-dropdown-clearable .p-dropdown-label) { padding-right: 2rem; }
.workout-source-picker .p-dropdown-clear-icon { display: flex; align-items: center; justify-content: center; width: 1.5rem; height: 1.5rem; margin-top: -.75rem; padding: 0; }
.workout-source-picker .p-dropdown-clear-icon .pi { font-size: 1rem; }
</style>
