<template>
  <div class="review-tabs" role="tablist" :aria-label="label">
    <button v-for="tab in tabs" :id="`${id}-tab-${tab.key}`" :key="tab.key" type="button" role="tab" :tabindex="modelValue === tab.key ? 0 : -1" :aria-selected="modelValue === tab.key" :aria-controls="`${id}-panel-${tab.key}`" @click="$emit('update:modelValue', tab.key)" @keydown="navigate($event, tab.key)">{{ tab.label }}</button>
  </div>
</template>
<script>
export default {
  props: {id: {type: String, required: true}, label: {type: String, required: true}, tabs: {type: Array, required: true}, modelValue: {type: String, required: true}},
  emits: ['update:modelValue'],
  methods: {
    navigate(event, key) {
      if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
      event.preventDefault();
      const index = event.key === 'Home' ? 0 : event.key === 'End' ? this.tabs.length - 1 : (this.tabs.findIndex(tab => tab.key === key) + (event.key === 'ArrowRight' ? 1 : this.tabs.length - 1)) % this.tabs.length;
      const next = this.tabs[index].key;
      this.$emit('update:modelValue', next);
      document.getElementById(`${this.id}-tab-${next}`).focus();
    }
  }
};
</script>
<style scoped>
.review-tabs { display: flex; flex-wrap: wrap; gap: .3rem; padding: .65rem 0; border-bottom: 1px solid #dedede; }
.review-tabs button { border: 1px solid transparent; border-radius: 3px; background: transparent; padding: .7rem .8rem; color: #52636f; font: inherit; font-weight: 600; cursor: pointer; }
.review-tabs button[aria-selected="true"] { color: #24679b; background: #eef5fb; border-color: #afcce2; }
.review-tabs button:focus-visible { outline: 2px solid #24679b; outline-offset: 2px; }
@media (max-width: 640px) { .review-tabs button { padding: .6rem .5rem; font-size: .82rem; } }
</style>
