<template>
  <div v-if="visible" class="routine-notification-prompt">
    <div>
      <strong>Enable routine reminders</strong>
      <div>Receive notifications at the reminder times configured for your routines.</div>
    </div>
    <div class="routine-notification-prompt-actions">
      <Button label="Enable" icon="pi pi-bell" class="p-button-sm" @click="enable" :loading="loading" />
      <Button label="Not now" class="p-button-sm p-button-text p-button-secondary" @click="dismiss" :disabled="loading" />
    </div>
  </div>
</template>

<script>
import pushNotificationService from '../services/PushNotificationService';

export default {
  props: {
    hasRoutineReminders: {
      type: Boolean,
      required: true
    }
  },
  data() {
    return {
      status: null,
      dismissed: pushNotificationService.isPromptDismissed(),
      loading: false
    }
  },
  computed: {
    visible() {
      return this.hasRoutineReminders
          && this.status?.config.enabled
          && this.status.supported
          && this.status.permission !== 'denied'
          && !this.status.enabled
          && !this.dismissed;
    }
  },
  async created() {
    try {
      this.status = await pushNotificationService.getStatus();
    } catch (e) {
      this.$log.error(e);
    }
  },
  methods: {
    async enable() {
      this.loading = true;
      try {
        this.status = await pushNotificationService.enable();
        if (this.status.enabled) {
          this.$toast.add({severity: 'success', summary: 'Notifications enabled', life: 3000});
        }
      } catch (e) {
        this.$log.error(e);
        this.$toast.add({severity: 'error', summary: 'Notification failed', detail: e, life: 3000});
      } finally {
        this.loading = false;
      }
    },
    dismiss() {
      pushNotificationService.dismissPrompt();
      this.dismissed = true;
    }
  }
}
</script>

<style scoped>
.routine-notification-prompt {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  margin: 1rem;
  padding: 1rem;
  border: 1px solid #b8d8ff;
  border-radius: 6px;
  background: #eef6ff;
}
.routine-notification-prompt-actions {
  display: flex;
  gap: 0.5rem;
}
@media (max-width: 575px) {
  .routine-notification-prompt {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
