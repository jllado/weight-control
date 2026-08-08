<template>
  <div v-if="visible" class="routine-notification-prompt">
    <div>
      <strong>Enable routine reminders</strong>
      <div>Receive a daily notification at {{ status.config.reminderTime }} with your remaining routine count.</div>
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
  data() {
    return {
      status: null,
      visible: false,
      loading: false
    }
  },
  async created() {
    try {
      this.status = await pushNotificationService.getStatus();
      this.visible = this.status.config.enabled && this.status.supported && this.status.permission !== 'denied' && !this.status.enabled && !pushNotificationService.isPromptDismissed();
    } catch (e) {
      this.$log.error(e);
    }
  },
  methods: {
    async enable() {
      this.loading = true;
      try {
        this.status = await pushNotificationService.enable();
        this.visible = !this.status.enabled && this.status.permission !== 'denied';
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
      this.visible = false;
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
