<template>
  <Dialog appendTo="body" :header="entry.id ? 'Edit reason' : `Record ${entry.outcome}`" :visible="true" :modal="true" :closable="!saving" :closeOnEscape="!saving" :style="{width: 'min(30rem, calc(100vw - 2rem))'}" @update:visible="$emit('onClose')">
    <p class="decision-context">{{ date_label }} · {{ entry.outcome }}</p>
    <div class="decision-reason-field">
      <label for="decision-reason">Reason (optional)</label>
      <Textarea id="decision-reason" v-model="reason" rows="4" maxlength="500" :disabled="saving" aria-describedby="decision-reason-limit" />
      <small id="decision-reason-limit">{{ reason.length }}/500 characters</small>
      <small v-if="error" class="p-error" role="alert">{{ error }}</small>
    </div>
    <template #footer>
      <div class="action-group"><Button :label="saving ? 'Saving…' : 'Save'" icon="pi pi-check" :loading="saving" :disabled="saving" @click="save" :aria-busy="saving" />
      <Button label="Cancel" icon="pi pi-times" class="p-button-secondary" :disabled="saving" @click="$emit('onClose')" />
    </div></template>
  </Dialog>
</template>

<script>
import Textarea from 'primevue/textarea';
import dayjs from 'dayjs';
import service from '../services/DecisionOutcomeService';

export default {
  components: {Textarea},
  props: {entry: {type: Object, required: true}, saveEntry: Function},
  emits: ['onSave', 'onClose'],
  data() {
    return {reason: this.entry.reason || '', saving: false, error: ''};
  },
  computed: {
    date_label() { return dayjs(this.entry.date).format('DD/MM/YYYY'); }
  },
  methods: {
    async save() {
      if (this.saving) return;
      this.saving = true;
      this.error = '';
      try {
        const reason = this.reason.trim() || null;
        if (this.saveEntry) {
          await this.saveEntry(reason);
        } else if (this.entry.id) {
          await service.updateReason(this.entry.id, reason);
        } else {
          await service.create(this.entry.date, this.entry.outcome, reason);
        }
        this.$toast.add({severity: 'success', summary: this.entry.id ? 'Reason saved' : `${this.entry.outcome} recorded`, life: 3000});
        this.$emit('onSave');
        this.$emit('onClose');
      } catch (error) {
        this.error = error.message;
      } finally {
        this.saving = false;
      }
    }
  }
}
</script>

<style scoped>
.decision-context { margin-top: 0; }
.decision-reason-field { display: flex; flex-direction: column; gap: .5rem; }
.decision-reason-field textarea { width: 100%; resize: vertical; }
.decision-reason-field .p-error { overflow-wrap: anywhere; }
</style>
