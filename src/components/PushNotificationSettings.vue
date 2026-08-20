<template>
  <Panel header="Notifications" class="p-mt-3">
    <p>Receive daily Mood and Back reminders, weekly Weight and Blood Pressure reminders, routine reminders, and notifications when a new app update is available. Notifications use {{ timeZone }} time.</p>
    <p>Weekly Weight and Blood Pressure reminders are sent on Saturday at 05:00 and 05:15.</p>
    <div v-if="reminderSettings" class="daily-reminder-settings">
      <h3>Daily check-in schedule</h3>
      <p>A separate Mood and Back reminder is sent at each time.</p>
      <div class="daily-reminder-times">
        <div v-for="period in reminderPeriods" :key="period.key" class="daily-reminder-time">
          <label :for="`${period.key}-reminder-time`">{{ period.label }}</label>
          <Calendar :inputId="`${period.key}-reminder-time`" v-model="reminderTimes[period.key]" :timeOnly="true" hourFormat="24" :stepMinute="5" :manualInput="false" showIcon />
        </div>
      </div>
      <Button label="Save reminder times" icon="pi pi-check" class="p-button-outlined" @click="saveReminderSettings" :loading="savingReminderSettings" />
    </div>
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
      loading: false,
      reminderSettings: null,
      reminderTimes: {morning: null, midday: null, evening: null},
      reminderPeriods: [
        {key: 'morning', label: 'Morning'},
        {key: 'midday', label: 'Midday'},
        {key: 'evening', label: 'Evening'}
      ],
      savingReminderSettings: false
    }
  },
  computed: {
    timeZone() {
      return this.status?.config.timeZone || 'Europe/Madrid';
    }
  },
  async created() {
    await this.loadStatus();
    await this.loadReminderSettings();
  },
  methods: {
    async loadStatus() {
      try {
        this.status = await pushNotificationService.getStatus();
      } catch (e) {
        this.handleError(e);
      }
    },
    async loadReminderSettings() {
      try {
        this.reminderSettings = await pushNotificationService.getReminderSettings();
        this.reminderTimes.morning = this.parseTime(this.reminderSettings.morningTime);
        this.reminderTimes.midday = this.parseTime(this.reminderSettings.middayTime);
        this.reminderTimes.evening = this.parseTime(this.reminderSettings.eveningTime);
      } catch (e) {
        this.handleError(e);
      }
    },
    async saveReminderSettings() {
      const settings = {
        morningTime: this.serializeTime(this.reminderTimes.morning),
        middayTime: this.serializeTime(this.reminderTimes.midday),
        eveningTime: this.serializeTime(this.reminderTimes.evening)
      };
      if (!(settings.morningTime < settings.middayTime && settings.middayTime < settings.eveningTime)) {
        this.$toast.add({severity: 'error', summary: 'Invalid reminder times', detail: 'Use chronological morning, midday, and evening times.', life: 3000});
        return;
      }
      this.savingReminderSettings = true;
      try {
        this.reminderSettings = await pushNotificationService.saveReminderSettings(settings);
        this.$toast.add({severity: 'success', summary: 'Reminder times saved', life: 3000});
      } catch (e) {
        this.handleError(e);
      } finally {
        this.savingReminderSettings = false;
      }
    },
    parseTime(value) {
      const [hours, minutes] = value.split(':').map(Number);
      const time = new Date();
      time.setHours(hours, minutes, 0, 0);
      return time;
    },
    serializeTime(value) {
      return `${String(value.getHours()).padStart(2, '0')}:${String(value.getMinutes()).padStart(2, '0')}`;
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
.daily-reminder-settings {
  margin-bottom: 1.5rem;
}
.daily-reminder-times {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1rem;
  margin-bottom: 1rem;
}
.daily-reminder-time {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}
@media (max-width: 640px) {
  .daily-reminder-times {
    grid-template-columns: 1fr;
  }
}
</style>
