<template>
  <button ref="trigger" type="button" class="rating-value" aria-haspopup="dialog" :aria-label="label" @click="visible = true"><slot name="score" /></button>
  <Dialog v-if="visible" :visible="true" appendTo="body" :header="title" modal :style="{width: 'min(36rem, 95vw)'}" @update:visible="close">
    <div class="rating-details"><slot /></div>
    <template #footer>
      <div class="action-group"><Button label="Close" icon="pi pi-times" class="p-button-secondary" @click="close" /></div>
    </template>
  </Dialog>
</template>

<script>
export default {
  props: {title: {type: String, required: true}, label: {type: String, required: true}},
  data() { return {visible: false}; },
  methods: {
    close() {
      this.visible = false;
      this.$nextTick(() => this.$refs.trigger.focus());
    }
  }
};
</script>

<style scoped>
.rating-value { font: inherit; padding: 0; border: 0; background: none; color: inherit; cursor: pointer; text-align: left; text-decoration: underline dotted; text-underline-offset: .2em; }
.rating-value:focus-visible { outline: 2px solid var(--primary-color); outline-offset: 3px; }
.rating-details { overflow-wrap: anywhere; }
.rating-details :deep(p) { margin: 0 0 .75rem; }
</style>
