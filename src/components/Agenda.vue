<template>
  <div class="agenda-page">
    <Panel header="Agenda">
      <div v-if="agenda" class="agenda-header">
        <div>
          <p class="agenda-date">{{ formatDate(agenda.date) }}</p>
          <p class="agenda-description">Your reminders in {{ agenda.timeZone }}.</p>
        </div>
        <div class="agenda-current-time" aria-live="polite">
          <i class="pi pi-clock" aria-hidden="true"></i>
          <span>Now {{ currentTime }}</span>
        </div>
      </div>

      <div v-if="agenda" class="agenda-summary" aria-label="Agenda summary">
        <span><i class="pi pi-check-circle" aria-hidden="true"></i>{{ completedCount }} completed</span>
        <span><i class="pi pi-clock" aria-hidden="true"></i>{{ pendingCount }} pending</span>
        <span><i class="pi pi-heart" aria-hidden="true"></i>{{ noIssueCount }} no back-pain issues</span>
      </div>

      <p v-if="loading" class="agenda-empty">Loading agenda…</p>
      <p v-else-if="agenda && !agenda.entries.length" class="agenda-empty">No push notifications are scheduled for today.</p>

      <ol v-else-if="agenda" class="agenda-timeline">
        <template v-for="(entry, index) in agenda.entries" :key="`${entry.type}-${entry.title}-${entry.scheduledTime}-${index}`">
          <li v-if="index === nowDividerIndex" class="agenda-now" aria-label="Current time">
            <span></span><strong>Now</strong><span></span>
          </li>
          <li class="agenda-entry" :class="`agenda-entry-${entry.status.toLowerCase()}`">
            <time :datetime="entry.scheduledTime">{{ formatTime(entry.scheduledTime) }}</time>
            <span class="agenda-entry-icon" aria-hidden="true"><i :class="entryIcon(entry.type)"></i></span>
            <div class="agenda-entry-content">
              <strong>{{ entry.title }}</strong>
              <span v-if="entry.details">{{ entry.details }}</span>
            </div>
            <span class="agenda-status" :class="`agenda-status-${entry.status.toLowerCase()}`">
              <i :class="statusIcon(entry.status)" aria-hidden="true"></i>{{ statusLabel(entry.status) }}
            </span>
          </li>
        </template>
        <li v-if="nowDividerIndex === agenda.entries.length" class="agenda-now" aria-label="Current time">
          <span></span><strong>Now</strong><span></span>
        </li>
      </ol>
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
      loading: false,
      currentDateTime: new Date(),
      clockInterval: null
    };
  },
  computed: {
    currentTime() {
      if (!this.agenda) {
        return '—';
      }
      return new Intl.DateTimeFormat('en-GB', {
        timeZone: this.agenda.timeZone,
        hour: '2-digit',
        minute: '2-digit',
        hourCycle: 'h23'
      }).format(this.currentDateTime);
    },
    nowDividerIndex() {
      const nextEntryIndex = this.agenda.entries.findIndex(entry => this.formatTime(entry.scheduledTime) > this.currentTime);
      return nextEntryIndex === -1 ? this.agenda.entries.length : nextEntryIndex;
    },
    completedCount() {
      return this.agenda.entries.filter(entry => entry.status === 'COMPLETED' || entry.status === 'RECORDED').length;
    },
    pendingCount() {
      return this.agenda.entries.filter(entry => entry.status === 'PENDING' || entry.status === 'MISSED').length;
    },
    noIssueCount() {
      return this.agenda.entries.filter(entry => entry.status === 'NO_ISSUE').length;
    }
  },
  async created() {
    this.clockInterval = window.setInterval(() => {
      this.currentDateTime = new Date();
    }, 60000);
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
  beforeUnmount() {
    window.clearInterval(this.clockInterval);
  },
  methods: {
    formatDate(date) {
      return date ? dayjs(date).format('dddd, D MMMM') : 'today';
    },
    formatTime(time) {
      return time?.slice(0, 5) || '—';
    },
    entryIcon(type) {
      return {
        MOOD: 'pi pi-smile',
        BACK_PAIN: 'pi pi-heart',
        WEIGHT: 'pi pi-chart-line',
        BLOOD_PRESSURE: 'pi pi-heart-fill',
        ROUTINE: 'pi pi-check-square',
        MEDICATION: 'pi pi-plus-circle'
      }[type];
    },
    statusIcon(status) {
      return {
        COMPLETED: 'pi pi-check-circle',
        PENDING: 'pi pi-clock',
        MISSED: 'pi pi-times-circle',
        RECORDED: 'pi pi-info-circle',
        NO_ISSUE: 'pi pi-check-circle'
      }[status];
    },
    statusLabel(status) {
      return {
        COMPLETED: 'Completed',
        PENDING: 'Pending',
        MISSED: 'Missed',
        RECORDED: 'Recorded',
        NO_ISSUE: 'No issue'
      }[status];
    }
  }
};
</script>

<style scoped>
.agenda-page { margin: 1rem; }
.agenda-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem; margin-bottom: 1rem; }
.agenda-date, .agenda-description { margin: 0; }
.agenda-date { font-size: 1.15rem; font-weight: 600; }
.agenda-description { margin-top: .25rem; color: #6c757d; }
.agenda-current-time, .agenda-summary, .agenda-status { display: flex; align-items: center; gap: .4rem; }
.agenda-current-time { flex: 0 0 auto; padding: .5rem .75rem; border-radius: 4px; background: #eef5ff; color: #1f5d9e; font-weight: 600; }
.agenda-summary { flex-wrap: wrap; gap: .75rem 1.25rem; margin-bottom: 1.25rem; color: #495057; font-size: .9rem; }
.agenda-summary span { display: inline-flex; align-items: center; gap: .35rem; }
.agenda-empty { margin: 0; color: #6c757d; }
.agenda-timeline { margin: 0; padding: 0; list-style: none; }
.agenda-entry { display: grid; grid-template-columns: 4.5rem 2.25rem minmax(0, 1fr) auto; align-items: center; gap: .75rem; min-height: 4.25rem; padding: .75rem 0; border-top: 1px solid #e9ecef; }
.agenda-entry time { color: #495057; font-variant-numeric: tabular-nums; font-weight: 600; }
.agenda-entry-icon { display: grid; width: 2.25rem; height: 2.25rem; place-items: center; border-radius: 50%; background: #eef5ff; color: #1f5d9e; }
.agenda-entry-content { display: grid; gap: .2rem; }
.agenda-entry-content span { color: #6c757d; font-size: .9rem; }
.agenda-status { justify-content: center; white-space: nowrap; font-size: .85rem; font-weight: 600; }
.agenda-status-completed, .agenda-status-no_issue { color: #2e7d32; }
.agenda-status-recorded { color: #1f5d9e; }
.agenda-status-pending { color: #8a5a00; }
.agenda-status-missed { color: #c62828; }
.agenda-entry-completed, .agenda-entry-no_issue { opacity: .68; }
.agenda-now { display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; gap: .75rem; margin: .25rem 0; color: #1f5d9e; font-size: .85rem; }
.agenda-now span { height: 1px; background: #8bb8e8; }
@media (max-width: 575px) {
  .agenda-page { margin: .5rem; }
  .agenda-header { align-items: stretch; flex-direction: column; }
  .agenda-current-time { align-self: flex-start; }
  .agenda-entry { grid-template-columns: 3.75rem 2.25rem minmax(0, 1fr); gap: .5rem; }
  .agenda-status { grid-column: 3; justify-content: flex-start; }
}
</style>
