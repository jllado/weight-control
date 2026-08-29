<template>
  <div class="agenda-page">
    <Panel header="Agenda">
      <p class="agenda-description">Scheduled push notifications for {{ formatDate(agenda?.date) }} in {{ agenda?.timeZone }}.</p>
      <DataTable :value="agenda?.entries" :loading="loading" responsiveLayout="scroll">
        <template #empty>No push notifications are scheduled for today.</template>
        <Column header="Time" headerStyle="width: 110px">
          <template #body="row">{{ formatTime(row.data.scheduledTime) }}</template>
        </Column>
        <Column header="Notification" field="title" headerStyle="min-width: 220px" />
        <Column header="Details" headerStyle="min-width: 180px">
          <template #body="row">{{ row.data.details || '—' }}</template>
        </Column>
      </DataTable>
    </Panel>
  </div>
</template>

<script>
import dayjs from 'dayjs';
import pushNotificationService from '../services/PushNotificationService';

export default {
  data() {
    return {
      agenda: null,
      loading: false
    };
  },
  async created() {
    this.loading = true;
    try {
      this.agenda = await pushNotificationService.getAgenda();
    } catch (e) {
      this.$log.error(e);
      this.$toast.add({severity: 'error', summary: 'Agenda failed to load', detail: e, life: 3000});
    } finally {
      this.loading = false;
    }
  },
  methods: {
    formatDate(date) {
      return date ? dayjs(date).format('dddd, D MMMM') : 'today';
    },
    formatTime(time) {
      return time?.slice(0, 5) || '—';
    }
  }
};
</script>

<style scoped>
.agenda-page {
  margin: 1rem;
}
.agenda-description {
  margin-top: 0;
}
</style>
