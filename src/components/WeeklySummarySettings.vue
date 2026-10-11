<template>
  <Panel header="Weekly email summary" class="p-mt-3">
    <p>Progress summary: Saturday–Friday.</p>
    <p v-if="config">Emailed every {{ deliverySchedule }}<span v-if="config.recipientEmail"> to {{ config.recipientEmail }}</span>.</p>
    <div v-if="config && config.canSend" class="weekly-summary-schedule">
      <SaveFields :saving="saving">
        <WeeklyReminderFields idPrefix="weekly-summary-delivery" label="Delivery" v-model:day="deliveryDay" v-model:time="deliveryTime" :disabled="saving" />
        <p>Times use {{ config.timeZone }}. Changes apply to future emails; saved summaries stay unchanged.</p>
      </SaveFields>
      <ActionButton label="Save email schedule" icon="pi pi-check" :action="saveSchedule" :loading="saving" :disabled="sending" />
    </div>
    <Message v-if="config && !config.enabled" severity="warn" :closable="false">Weekly email summaries are not configured for this environment.</Message>
    <Message v-else-if="sent" severity="success" :closable="false">The weekly summary was sent.</Message>
    <div class="weekly-summary-actions action-group">
      <Button v-if="config && config.enabled && config.canSend" label="Send weekly summary now" icon="pi pi-send" class="p-button-outlined" @click="send" :loading="sending" :disabled="sending || saving" />
      <RouterLink to="/weekly-summaries" class="p-button p-component p-button-outlined"><span class="p-button-label">View weekly summaries</span></RouterLink>
    </div>
  </Panel>
</template>

<script>
import weeklySummaryService from '../services/WeeklySummaryService';
import WeeklyReminderFields from './WeeklyReminderFields.vue';

export default {
  components: {WeeklyReminderFields},
  data() {
    return {
      config: null,
      deliveryDay: null,
      deliveryTime: null,
      saving: false,
      sending: false,
      sent: false
    }
  },
  computed: {
    deliverySchedule() {
      const day = this.config.deliveryDay.charAt(0) + this.config.deliveryDay.slice(1).toLowerCase();
      return `${day} at ${this.config.deliveryTime.slice(0, 5)} ${this.config.timeZone}`;
    }
  },
  async created() {
    try {
      this.config = await weeklySummaryService.getConfig();
      this.deliveryDay = this.config.deliveryDay;
      const [hours, minutes] = this.config.deliveryTime.split(':').map(Number);
      this.deliveryTime = new Date();
      this.deliveryTime.setHours(hours, minutes, 0, 0);
    } catch (e) {
      this.handleError(e);
    }
  },
  methods: {
    async saveSchedule() {
      this.saving = true;
      try {
        const deliveryTime = `${String(this.deliveryTime.getHours()).padStart(2, '0')}:${String(this.deliveryTime.getMinutes()).padStart(2, '0')}`;
        this.config = await weeklySummaryService.saveConfig({deliveryDay: this.deliveryDay, deliveryTime});
        this.$toast.add({severity: 'success', summary: 'Email schedule saved', life: 3000});
      } catch (e) {
        this.handleError(e);
      } finally {
        this.saving = false;
      }
    },
    async send() {
      this.sending = true;
      this.sent = false;
      try {
        await weeklySummaryService.send();
        this.sent = true;
        this.$toast.add({severity: 'success', summary: 'Weekly summary sent', life: 3000});
      } catch (e) {
        this.handleError(e);
      } finally {
        this.sending = false;
      }
    },
    handleError(e) {
      this.$log.error(e);
      this.$toast.add({severity: 'error', summary: 'Weekly summary failed', detail: e.message, life: 3000});
    }
  }
}
</script>

<style scoped>
.weekly-summary-actions { margin-top: 0.5rem; }
.weekly-summary-schedule {margin-bottom: 1rem;}
</style>
