<template>
  <Panel header="Overnight heart rate" class="p-mt-3">
    <p v-if="loading" role="status">Loading personal alert threshold…</p>
    <div v-else-if="error">
      <p role="alert">{{ error }}</p>
      <ActionButton label="Retry" busyLabel="Loading…" :action="load" />
    </div>
    <p v-else-if="setting.bpm == null">Your personal alert threshold will be set 5 bpm above the median of your first 30 nights with a recorded heart rate.</p>
    <template v-else>
      <SaveFields :saving="saving">
        <div class="p-field threshold-field">
          <label for="overnight-heart-rate-limit">Personal alert threshold (bpm)</label>
          <InputNumber inputId="overnight-heart-rate-limit" v-model="draft" @input="draft = $event.value" :useGrouping="false" :min="0.001" :max="9999.999" :maxFractionDigits="3" :inputProps="{'aria-describedby': 'overnight-threshold-description'}" />
        </div>
        <p id="overnight-threshold-description">Two consecutive nights above this value trigger a dashboard warning. Changes apply from today.</p>
        <p aria-live="polite">Current threshold: {{ setting.bpm }} bpm since {{ setting.effectiveFrom }}.</p>
      </SaveFields>
      <ActionButton label="Save threshold" icon="pi pi-check" :action="save" :loading="saving" :disabled="draft == null || draft === setting.bpm" />
    </template>
  </Panel>
</template>

<script>
import sleepService from '../services/SleepService';

export default {
  data() {
    return {setting: null, draft: null, loading: true, saving: false, error: null};
  },
  created() { this.load(); },
  methods: {
    async load() {
      this.loading = true;
      this.error = null;
      try {
        this.setting = await sleepService.getHeartRateLimit();
        this.draft = this.setting.bpm;
      } catch (error) {
        this.error = `Could not load the personal alert threshold: ${error.message}`;
      } finally { this.loading = false; }
    },
    async save() {
      this.saving = true;
      try {
        this.setting = await sleepService.saveHeartRateLimit(this.draft);
        this.draft = this.setting.bpm;
        this.$toast.add({severity: 'success', summary: 'Saved', detail: 'Personal alert threshold updated', life: 3000});
      } finally { this.saving = false; }
    }
  }
};
</script>

<style scoped>
.threshold-field { display: grid; gap: .5rem; max-width: 20rem; }
.threshold-field :deep(input) { width: 100%; min-width: 0; }
</style>
