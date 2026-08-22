<template>
  <div class="notification-bell">
    <Button
        icon="pi pi-bell"
        class="p-button-rounded p-button-text notification-bell-button"
        :aria-label="bellLabel"
        aria-haspopup="true"
        @click="togglePanel" />
    <span v-if="notifications.length" class="notification-badge" aria-hidden="true">{{ notifications.length }}</span>
    <OverlayPanel ref="panel" class="notification-panel" style="width: min(24rem, calc(100vw - 2rem))">
      <div class="notification-panel-header">
        <strong>Pending notifications</strong>
        <div class="notification-panel-actions">
          <span>{{ notifications.length }}</span>
          <button
              type="button"
              v-if="notifications.length"
              class="notification-dismiss-all"
              :disabled="dismissAllLoading"
              @click="dismissAll">
            Dismiss all
          </button>
        </div>
      </div>
      <div v-if="notifications.length" class="notification-list">
        <div v-for="notification in notifications" :key="notification.id" class="notification-item">
          <button type="button" class="notification-content" @click="openNotification(notification)">
            <span class="notification-title">{{ notification.title }}</span>
            <span class="notification-message">{{ notification.message }}</span>
            <span class="notification-time">{{ formatTime(notification.availableAt) }}</span>
          </button>
          <Button
              icon="pi pi-times"
              class="p-button-rounded p-button-text p-button-secondary notification-dismiss"
              :aria-label="`Dismiss ${notification.title}`"
              @click="dismiss(notification)" />
        </div>
      </div>
      <p v-else class="notification-empty">No pending notifications.</p>
    </OverlayPanel>
  </div>
</template>

<script>
import notificationService, {onNotificationsChanged} from '../services/InAppNotificationService';

const timeFormatter = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'Europe/Madrid',
  hour: '2-digit',
  minute: '2-digit'
});

export default {
  name: 'NotificationBell',
  data() {
    return {
      notifications: [],
      dismissAllLoading: false,
      poller: null,
      unsubscribe: null
    };
  },
  computed: {
    bellLabel() {
      return this.notifications.length === 1
          ? '1 pending notification'
          : `${this.notifications.length} pending notifications`;
    }
  },
  mounted() {
    this.refresh();
    this.unsubscribe = onNotificationsChanged(this.refresh);
    this.poller = window.setInterval(this.refresh, 60000);
    window.addEventListener('focus', this.refresh);
    document.addEventListener('visibilitychange', this.refreshWhenVisible);
  },
  beforeUnmount() {
    this.unsubscribe();
    window.clearInterval(this.poller);
    window.removeEventListener('focus', this.refresh);
    document.removeEventListener('visibilitychange', this.refreshWhenVisible);
  },
  methods: {
    async refresh() {
      try {
        this.notifications = await notificationService.getPending();
      } catch (e) {
        this.$log.error(e);
      }
    },
    refreshWhenVisible() {
      if (document.visibilityState === 'visible') {
        this.refresh();
      }
    },
    togglePanel(event) {
      this.$refs.panel.toggle(event);
      this.refresh();
    },
    async openNotification(notification) {
      this.$refs.panel.hide();
      await this.$router.push(notification.actionUrl);
    },
    async dismiss(notification) {
      try {
        await notificationService.dismiss(notification.id);
        this.notifications = this.notifications.filter(candidate => candidate.id !== notification.id);
      } catch (e) {
        this.$log.error(e);
        this.$toast.add({severity: 'error', summary: 'Notification dismissal failed', detail: e, life: 3000});
      }
    },
    async dismissAll() {
      this.dismissAllLoading = true;
      try {
        await notificationService.dismissAll();
        this.notifications = [];
        this.$toast.add({severity: 'success', summary: 'Notifications dismissed', life: 3000});
      } catch (e) {
        this.$log.error(e);
        this.$toast.add({severity: 'error', summary: 'Notification dismissal failed', detail: e, life: 3000});
      } finally {
        this.dismissAllLoading = false;
      }
    },
    formatTime(value) {
      return timeFormatter.format(new Date(value));
    }
  }
};
</script>

<style scoped>
.notification-bell {
  position: relative;
}
.notification-bell-button {
  color: #495057;
}
.notification-badge {
  position: absolute;
  top: -0.2rem;
  right: -0.2rem;
  min-width: 1.25rem;
  height: 1.25rem;
  padding: 0 0.3rem;
  border: 2px solid #fff;
  border-radius: 0.75rem;
  color: #fff;
  background: #dc3545;
  font-size: 0.7rem;
  font-weight: 700;
  line-height: 1rem;
  text-align: center;
  pointer-events: none;
}
.notification-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid #e3e7eb;
}
.notification-panel-actions {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}
.notification-dismiss-all {
  padding: 0.25rem 0.4rem;
  border: 0;
  color: #6c757d;
  background: transparent;
  font-size: 0.85rem;
  cursor: pointer;
}
.notification-dismiss-all:hover {
  color: #495057;
}
.notification-list {
  max-height: 24rem;
  overflow-y: auto;
}
.notification-item {
  display: flex;
  align-items: center;
  border-bottom: 1px solid #edf0f2;
}
.notification-item:last-child {
  border-bottom: 0;
}
.notification-content {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.2rem;
  min-width: 0;
  padding: 0.85rem 0.5rem 0.85rem 0;
  border: 0;
  color: inherit;
  background: transparent;
  text-align: left;
  cursor: pointer;
}
.notification-content:hover .notification-title {
  color: #1976d2;
}
.notification-title {
  font-weight: 600;
}
.notification-message {
  overflow: hidden;
  color: #5f6872;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.notification-time {
  color: #7b8490;
  font-size: 0.8rem;
}
.notification-dismiss {
  flex: none;
}
.notification-empty {
  margin: 1rem 0 0.25rem;
  color: #68727d;
  text-align: center;
}
</style>
