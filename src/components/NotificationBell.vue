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
              :disabled="dismissAllLoading || dismissingId !== null"
              @click="dismissAll">
            Dismiss all
          </button>
        </div>
      </div>
      <div v-if="notifications.length" class="notification-list">
        <div v-for="notification in notifications" :key="notification.id" class="notification-item"
             :class="{'notification-item-dragging': swipe && swipe.id === notification.id}"
             :style="{transform: swipe && swipe.id === notification.id ? `translateX(${swipe.offset}px)` : ''}"
             @pointerdown="startSwipe($event, notification)"
             @pointermove="moveSwipe"
             @pointerup="endSwipe($event, notification)"
             @pointercancel="cancelSwipe"
             @lostpointercapture.self="cancelSwipe"
             @click.capture="suppressSwipeClick($event, notification)">
          <button type="button" class="notification-content" :disabled="dismissAllLoading || dismissingId !== null" @click="openNotification(notification)">
            <span class="notification-title">{{ notification.title }}</span>
            <span class="notification-message">{{ notification.message }}</span>
            <span class="notification-time">{{ formatTime(notification.availableAt) }}</span>
          </button>
          <Button
              icon="pi pi-times"
              class="p-button-rounded p-button-text p-button-secondary notification-dismiss"
              :aria-label="`Dismiss ${notification.title}`"
              :disabled="dismissAllLoading || dismissingId !== null"
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
      dismissingId: null,
      swipe: null,
      swipedId: null,
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
    startSwipe(event, notification) {
      if (!event.isPrimary || event.button !== 0 || this.dismissAllLoading || this.dismissingId !== null) return;
      this.swipedId = null;
      this.swipe = {id: notification.id, pointerId: event.pointerId, x: event.clientX, y: event.clientY, offset: 0, direction: null};
    },
    moveSwipe(event) {
      if (!this.swipe || event.pointerId !== this.swipe.pointerId) return;
      const dx = event.clientX - this.swipe.x;
      const dy = event.clientY - this.swipe.y;
      if (!this.swipe.direction && Math.max(Math.abs(dx), Math.abs(dy)) >= 10) {
        this.swipe.direction = Math.abs(dx) > Math.abs(dy) ? 'horizontal' : 'vertical';
        this.swipedId = this.swipe.id;
        if (this.swipe.direction === 'horizontal') event.currentTarget.setPointerCapture(event.pointerId);
      }
      if (this.swipe.direction === 'horizontal') this.swipe.offset = Math.min(0, dx);
    },
    endSwipe(event, notification) {
      if (!this.swipe || event.pointerId !== this.swipe.pointerId) return;
      const dismiss = this.swipe.direction === 'horizontal' && this.swipe.offset <= -60;
      this.swipe = null;
      if (dismiss) this.dismiss(notification);
    },
    cancelSwipe() {
      this.swipe = null;
    },
    suppressSwipeClick(event, notification) {
      if (this.swipedId === notification.id && event.detail !== 0) {
        event.preventDefault();
        event.stopPropagation();
        this.swipedId = null;
      }
    },
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
      if (['PERSONAL_RECORD', 'GPT_ACTION'].includes(notification.type)) {
        await notificationService.dismiss(notification.id);
        this.notifications = this.notifications.filter(candidate => candidate.id !== notification.id);
      }
      await this.$router.push(notification.actionUrl);
    },
    async dismiss(notification) {
      if (this.dismissingId !== null || this.dismissAllLoading) return;
      this.dismissingId = notification.id;
      try {
        await notificationService.dismiss(notification.id);
        this.notifications = this.notifications.filter(candidate => candidate.id !== notification.id);
        if (!this.notifications.length) this.$refs.panel.hide();
      } catch (e) {
        this.$log.error(e);
        this.$toast.add({severity: 'error', summary: 'Notification dismissal failed', detail: e, life: 3000});
      } finally {
        this.dismissingId = null;
      }
    },
    async dismissAll() {
      this.dismissAllLoading = true;
      try {
        await notificationService.dismissAll();
        this.notifications = [];
        this.$refs.panel.hide();
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
  overflow-x: hidden;
}
.notification-item {
  display: flex;
  align-items: center;
  border-bottom: 1px solid #edf0f2;
  touch-action: pan-y;
  transition: transform 150ms ease-out;
}
.notification-item-dragging {
  transition: none;
  user-select: none;
}
@media (prefers-reduced-motion: reduce) {
  .notification-item {
    transition: none;
  }
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
