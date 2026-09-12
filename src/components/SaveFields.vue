<template>
  <fieldset class="save-fields" :disabled="saving" :inert="saving ? '' : null" :aria-busy="saving"><slot /></fieldset>
</template>

<script>
export default {props: {saving: Boolean}};
</script>

<style scoped>
.save-fields { border: 0; margin: 0; padding: 0; min-width: 0; }
</style>
