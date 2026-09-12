<template>
  <span v-if="src" class="exercise-picture">
    <button type="button" class="exercise-picture-button" :aria-label="`View picture of ${name}`" @click="visible = true">
      <img v-if="!failed" :src="src" :alt="`${name} demonstration`" loading="lazy" @error="failed = true" />
      <span v-else>Picture unavailable</span>
    </button>
    <Dialog appendTo="body" v-model:visible="visible" :header="name" :modal="true" :style="{width: 'min(860px, 96vw)'}">
      <img v-if="!failed" class="exercise-picture-large" :src="src" :alt="`${name} demonstration`" @error="failed = true" />
      <p v-else role="status">Picture unavailable.</p>
      <p v-if="description" class="exercise-picture-description">{{ description }}</p>
      <template #footer><div class="action-group"><Button label="Close" class="p-button-secondary" @click="visible = false" /></div></template>
    </Dialog>
  </span>
</template>

<script>
export default {
  name: 'ExercisePicture',
  props: {src: String, name: String, description: String},
  data() { return {visible: false, failed: false}; },
  watch: {src() { this.failed = false; }}
};
</script>

<style scoped>
.exercise-picture { display: inline-flex; flex-shrink: 0; vertical-align: middle; margin-right: .5rem; }
.exercise-picture-button { width: 64px; height: 64px; padding: 2px; border: 1px solid #d6d6d6; border-radius: 4px; background: white; cursor: pointer; font: inherit; font-size: .7rem; }
.exercise-picture-button:focus-visible { outline: 2px solid #007ad9; outline-offset: 2px; }
.exercise-picture-button img { width: 100%; height: 100%; object-fit: contain; }
.exercise-picture-large { display: block; width: 100%; max-height: 65vh; object-fit: contain; }
.exercise-picture-description { overflow-wrap: anywhere; }
</style>
