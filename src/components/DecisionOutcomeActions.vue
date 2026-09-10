<template>
  <div class="decision-outcome-actions">
    <Button label="WIN" icon="pi pi-check" class="p-button-success decision-outcome-button" :disabled="disabled" @click="$emit('select', 'WIN')" />
    <Button label="MISS" icon="pi pi-times" class="p-button-danger decision-outcome-button" :disabled="disabled" @click="$emit('select', 'MISS')" />
  </div>
</template>

<script>
export default {
  props: {disabled: Boolean},
  emits: ['select']
};
</script>

<style scoped>
.decision-outcome-actions { display: flex; flex-wrap: wrap; justify-content: center; gap: .5rem; }
.decision-outcome-button { width: 7rem; }
</style>
