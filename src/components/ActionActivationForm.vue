<template>
  <section class="action-form" aria-labelledby="action-form-title">
    <h2 id="action-form-title">{{ attempt ? restart ? 'Start again' : 'Edit action' : 'Activate action' }}</h2>
    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
    <form @submit.prevent="save">
      <SaveFields :saving="saving">
        <div class="p-fluid p-formgrid p-grid">
          <div class="p-field p-col-12">
            <label for="action-wording">Action</label>
            <InputText id="action-wording" ref="wording" v-model="draft.wording" required maxlength="255" />
          </div>
          <div class="p-field p-col-12 p-md-6">
            <label for="action-start">Start date</label>
            <InputText id="action-start" v-model="draft.startDate" type="date" required :disabled="locked" />
          </div>
          <div class="p-field p-col-12 p-md-6">
            <label for="action-target">Target consecutive days</label>
            <InputText id="action-target" v-model.number="draft.targetDays" type="number" min="1" step="1" required :disabled="locked" />
          </div>
        </div>
        <p>Check off each successful day. A missed day resets your streak; the action stays active.</p>
        <p v-if="locked">Start date and target are locked after the first check-in.</p>
        <p v-if="sourceLabel" class="source-label">From {{ sourceLabel }}</p>
      </SaveFields>
      <div class="action-group form-actions">
        <Button type="submit" label="Save" :loading="saving" :disabled="saving" :aria-busy="saving" />
        <Button label="Cancel" class="p-button-text" :disabled="saving" @click="$emit('cancel')" />
      </div>
    </form>
  </section>
</template>

<script>
import actionService, {actionToday} from '../services/ActionService';
export default {
  props: {suggestion: Object, attempt: Object, restart: Boolean},
  emits: ['saved', 'cancel'],
  data() {
    return {saving: false, error: null, draft: {
      wording: this.attempt?.wording || this.suggestion?.wording || '',
      startDate: this.attempt && !this.restart ? this.attempt.startDate : actionToday(),
      targetDays: this.attempt?.targetDays || 14,
      sourceKey: this.attempt?.sourceKey || this.suggestion?.sourceKey || null
    }};
  },
  computed: {
    locked() { return this.attempt?.goalLocked && !this.restart; },
    sourceLabel() { return this.attempt?.sourceLabel || this.suggestion?.sourceLabel; }
  },
  mounted() { this.$refs.wording.$el.focus(); },
  methods: {
    async save() {
      this.saving = true; this.error = null;
      try {
        const row = this.attempt ? this.restart ? await actionService.restart(this.attempt.id, this.draft)
          : await actionService.update(this.attempt.id, this.draft) : await actionService.create(this.draft);
        this.$emit('saved', row);
      } catch (error) { this.error = error.message; }
      finally { this.saving = false; }
    }
  }
};
</script>

<style scoped>
.action-form { padding: 1rem; margin-bottom: 1rem; border: 1px solid #d6d6d6; border-radius: 4px; background: white; }
.action-form h2 { margin-top: 0; font-size: 1.2rem; }
.source-label { color: #63717b; overflow-wrap: anywhere; }
.form-actions { max-width: 20rem; }
</style>
