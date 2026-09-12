<template>
  <Button v-bind="$attrs" :label="pending && label ? busyLabel : label" :loading="pending || loading" :disabled="(pending || loading) || (pending || disabled)" :aria-busy="pending || loading" @click="run" />
</template>

<script>
export default {
  inheritAttrs: false,
  props: {action: {type: Function, required: true}, label: String, busyLabel: {type: String, default: 'Saving…'}, loading: Boolean, disabled: Boolean},
  data() { return {pending: false}; },
  methods: {
    async run(event) {
      if (this.pending) return;
      this.pending = true;
      try { await this.action(event); }
      catch (error) { this.$toast.add({severity: 'error', summary: 'Action failed', detail: error.message, life: 4000}); }
      finally { this.pending = false; }
    }
  }
};
</script>
