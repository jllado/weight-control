<template>
  <Panel header="Notifications" class="p-mt-3">
    <p>Receive routine reminders and notifications when a new app update is available. Routine reminders use {{ timeZone }} time.</p>
    <Message v-if="status && !status.config.enabled" severity="warn" :closable="false">Notifications are not configured for this environment.</Message>
    <Message v-else-if="status && !status.supported" severity="warn" :closable="false">Push notifications are not available in this browser. On iPhone or iPad, add Weight Control to the Home Screen first.</Message>
    <Message v-else-if="status && status.permission === 'denied'" severity="warn" :closable="false">Notifications are blocked. Allow them in your browser settings to enable notifications.</Message>
    <Message v-else-if="status && status.enabled" severity="success" :closable="false">Notifications are enabled on this device.</Message>
    <Message v-else-if="status" severity="info" :closable="false">Notifications are disabled on this device.</Message>
    <div v-if="status && status.config.enabled && status.supported" class="notification-actions">
      <Button v-if="!status.enabled" label="Enable on this device" icon="pi pi-bell" @click="enable" :loading="loading" :disabled="status.permission === 'denied'" />
      <template v-else>
        <Button label="Send test notification" icon="pi pi-send" class="p-button-outlined" @click="sendTest" :loading="loading" />
        <Button label="Disable on this device" icon="pi pi-bell-slash" class="p-button-outlined p-button-warning" @click="disable" :loading="loading" />
      </template>
    </div>
  </Panel>
</template>

<script>
import pushNotificationService from '../services/PushNotificationService';

export default {
  data() {
    return {
      status: null,
      loading: false
    }
  },
  computed: {
    timeZone() {
      return this.status?.config.timeZone || 'Europe/Madrid';
    }
  },
  async created() {
    await this.loadStatus();
  },
  methods: {
    async loadStatus() {
      try {
        this.status = await pushNotificationService.getStatus();
      } catch (e) {
        this.handleError(e);
      }
    },
    async enable() {
      this.loading = true;
      try {
        this.status = await pushNotificationService.enable();
        if (this.status.enabled) {
          this.$toast.add({severity: 'success', summary: 'Notifications enabled', life: 3000});
        }
      } catch (e) {
        this.handleError(e);
      } finally {
        this.loading = false;
      }
    },
    async disable() {
      this.loading = true;
      try {
        this.status = await pushNotificationService.disable();
        this.$toast.add({severity: 'success', summary: 'Notifications disabled', life: 3000});
      } catch (e) {
        this.handleError(e);
      } finally {
        this.loading = false;
      }
    },
    async sendTest() {
      this.loading = true;
      try {
        await pushNotificationService.sendTest();
        this.$toast.add({severity: 'success', summary: 'Test notification sent', life: 3000});
      } catch (e) {
        this.handleError(e);
      } finally {
        this.loading = false;
      }
    },
    handleError(e) {
      this.$log.error(e);
      this.$toast.add({severity: 'error', summary: 'Notification failed', detail: e, life: 3000});
    }
  }
}
</script>

<style scoped>
.notification-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 1rem;
}
</style>
