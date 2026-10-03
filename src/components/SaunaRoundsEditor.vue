<template>
  <section class="sauna-rounds" aria-label="Sauna rounds">
    <h3>{{ title }}</h3>
    <div v-for="(minutes, index) in modelValue" :key="index" class="sauna-round">
      <label :for="`${idPrefix}-${index}`">Round {{ index + 1 }} (min)<small v-if="plannedRounds?.[index]"> · Planned {{ plannedRounds[index] }} min</small></label>
      <InputNumber :inputId="`${idPrefix}-${index}`" :inputProps="{'aria-invalid': index === invalidRoundIndex, 'aria-describedby': index === invalidRoundIndex ? errorId : null}" :modelValue="minutes" :min="1" :useGrouping="false" @update:modelValue="updateRound(index, $event)" />
      <CompactAction icon="pi pi-trash" :aria-label="`Remove sauna round ${index + 1}`" destructive @click="removeRound(index)" />
      <span v-if="validationError && index === invalidRoundIndex" :id="errorId" class="error sauna-round-error" data-workout-error :data-error-target="`${idPrefix}-${index}`">{{ validationError }}</span>
    </div>
    <Button label="Add round" icon="pi pi-plus" class="p-button-outlined p-button-sm" @click="$emit('update:modelValue', [...modelValue, null])" />
  </section>
</template>

<script>
export default {
  name: 'SaunaRoundsEditor',
  props: {modelValue: {type: Array, required: true}, plannedRounds: Array, idPrefix: {type: String, required: true}, title: {type: String, default: 'Sauna rounds'}, invalidRoundIndex: Number, errorId: String, validationError: String},
  emits: ['update:modelValue'],
  methods: {
    updateRound(index, minutes) { this.$emit('update:modelValue', this.modelValue.map((value, position) => position === index ? minutes : value)); },
    removeRound(index) { this.$emit('update:modelValue', this.modelValue.filter((_, position) => position !== index)); }
  }
};
</script>

<style scoped>
.sauna-rounds { margin: 0 0 1rem; padding: .75rem; border: 1px solid #d6d6d6; border-radius: 6px; }
.sauna-rounds h3 { font-size: 1rem; margin: 0 0 .75rem; }
.sauna-round { display: grid; grid-template-columns: minmax(0, 1fr) minmax(6rem, 9rem) auto; align-items: center; gap: .5rem; margin-bottom: .5rem; }
.sauna-round-error { grid-column: 1 / -1; }
.sauna-round label { overflow-wrap: anywhere; }
.sauna-round small { color: var(--text-color-secondary); }
.sauna-round :deep(.p-inputnumber), .sauna-round :deep(.p-inputnumber-input) { min-width: 0; width: 100%; }
@media (max-width: 575px) { .sauna-round { grid-template-columns: minmax(0, 1fr) auto; } .sauna-round label { grid-column: 1 / -1; } }
</style>
