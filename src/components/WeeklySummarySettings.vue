<template>
  <Panel header="Weekly email summary" class="p-mt-3">
    <p v-if="config">A Saturday–Friday progress summary is emailed every {{ deliverySchedule }}<span v-if="config.recipientEmail"> to {{ config.recipientEmail }}</span>.</p>
    <Message v-if="config && !config.enabled" severity="warn" :closable="false">Weekly email summaries are not configured for this environment.</Message>
    <Message v-else-if="sent" severity="success" :closable="false">The weekly summary was sent.</Message>
    <div class="weekly-summary-actions action-group">
      <Button v-if="config && config.enabled && config.canSend" label="Send weekly summary now" icon="pi pi-send" class="p-button-outlined" @click="send" :loading="sending" :disabled="sending" />
      <RouterLink to="/weekly-summaries" class="p-button p-component p-button-outlined"><span class="p-button-label">View weekly summaries</span></RouterLink>
    </div>
  </Panel>
</template>

<script>
import weeklySummaryService from '../services/WeeklySummaryService';

export default {
  data() {
    return {
      config: null,
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
    } catch (e) {
      this.handleError(e);
    }
  },
  methods: {
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
      this.$toast.add({severity: 'error', summary: 'Weekly summary failed', detail: e, life: 3000});
    }
  }
}
</script>

<style scoped>
.weekly-summary-actions { margin-top: 0.5rem; }
</style>
