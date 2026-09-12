<template>
  <div class="notification-bell">
    <Button
        icon="pi pi-bell"
        class="p-button-rounded p-button-text notification-bell-button"
        :aria-label="bellLabel"
        aria-haspopup="true"
        @click="togglePanel" />
    <span v-if="notifications.length" class="notification-badge" aria-hidden="true">{{ notifications.length }}</span>
    <OverlayPanel ref="panel" class="notification-panel" @show="positionPanel" @hide="stopPositioning">
      <div class="notification-panel-header">
        <strong>Pending notifications</strong>
        <div class="notification-panel-actions">
          <span>{{ notifications.length }}</span>
          <button
              type="button"
              v-if="notifications.length"
              class="notification-dismiss-all"
              :disabled="dismissAllLoading || dismissingId !== null"
              :aria-busy="dismissAllLoading"
              @click="dismissAll">
            {{ dismissAllLoading ? 'Dismissing…' : 'Dismiss all' }}
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
              :loading="dismissingId === notification.id"
              :disabled="(dismissingId === notification.id) || (dismissAllLoading || dismissingId !== null)"
              @click="dismiss(notification, $event.currentTarget.closest('.notification-item'))" />
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
      dismissalAnimation: null,
      refreshVersion: 0,
      unmounted: false,
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
    this.unmounted = true;
    this.dismissalAnimation?.cancel();
    this.stopPositioning();
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
      if (dismiss) this.dismiss(notification, event.currentTarget);
      this.swipe = null;
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
      if (this.dismissingId !== null) return;
      const version = ++this.refreshVersion;
      try {
        const notifications = await notificationService.getPending();
        if (!this.unmounted && version === this.refreshVersion) this.notifications = notifications;
      } catch (e) {
        this.$log.error(e);
      }
    },
    refreshWhenVisible() {
      if (document.visibilityState === 'visible') {
        this.refresh();
      }
    },
    positionPanel() {
      const panel = this.$refs.panel;
      if (!panel.visible) return;
      const bell = this.$el.getBoundingClientRect();
      const header = this.$el.closest('.app-menubar').getBoundingClientRect();
      const rem = parseFloat(getComputedStyle(document.documentElement).fontSize);
      const viewportWidth = document.documentElement.clientWidth;
      const bellCenter = bell.left + bell.width / 2;
      const width = Math.min(viewportWidth - 2 * rem, Math.max(24 * rem, 2 * Math.abs(bellCenter - viewportWidth / 2) + 3 * rem));
      const left = (viewportWidth - width) / 2;
      const top = header.bottom;
      const element = panel.container;
      element.classList.remove('p-overlaypanel-flipped');
      element.removeAttribute('data-p-overlaypanel-flipped');
      Object.assign(element.style, {width: `${width}px`, left: `${left + window.scrollX}px`, top: `${top + window.scrollY}px`});
      element.style.setProperty('--overlayArrowLeft', `${bellCenter - left - 1.25 * rem}px`);
      element.style.setProperty('--notification-panel-height', `${window.innerHeight - top - 10 - rem}px`);
      window.addEventListener('resize', this.positionPanel);
    },
    stopPositioning() {
      window.removeEventListener('resize', this.positionPanel);
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
    async animateDismissal(element, keyframes, duration) {
      const animation = element.animate(keyframes, {
        duration: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : duration,
        easing: 'ease-out',
        fill: 'forwards'
      });
      this.dismissalAnimation = animation;
      await animation.finished.catch(() => {}); // Unmounting cancels the active animation.
    },
    async dismiss(notification, element) {
      if (this.dismissingId !== null || this.dismissAllLoading) return;
      this.dismissingId = notification.id;
      this.refreshVersion++;
      const height = element.getBoundingClientRect().height;
      const transform = getComputedStyle(element).transform;
      const slide = this.animateDismissal(element, [
        {transform, opacity: 1},
        {transform: 'translateX(-100%)', opacity: 0}
      ], 200);
      try {
        await Promise.all([notificationService.dismiss(notification.id), slide]);
        if (this.unmounted) return;
        this.dismissalAnimation.cancel();
        await this.animateDismissal(element, [
          {height: `${height}px`, opacity: 0, overflow: 'hidden'},
          {height: '0px', opacity: 0, overflow: 'hidden'}
        ], 160);
        if (this.unmounted) return;
        this.notifications = this.notifications.filter(candidate => candidate.id !== notification.id);
        if (!this.notifications.length) this.$refs.panel.hide();
      } catch (e) {
        await slide;
        if (this.unmounted) return;
        this.dismissalAnimation.cancel();
        await this.animateDismissal(element, [
          {transform: 'translateX(-100%)', opacity: 0},
          {transform: 'translateX(0)', opacity: 1}
        ], 200);
        if (this.unmounted) return;
        this.$log.error(e);
        this.$toast.add({severity: 'error', summary: 'Notification dismissal failed', detail: e, life: 3000});
      } finally {
        this.dismissalAnimation?.cancel();
        this.dismissalAnimation = null;
        this.dismissingId = null;
        if (!this.unmounted) this.refresh();
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
  flex-shrink: 0;
  gap: .5rem;
  flex-wrap: wrap;
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
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
}
.notification-item {
  box-sizing: border-box;
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

<style>
/* The body-ported notification overlay needs a local class instead of scoped descendant selectors. */
.notification-panel .p-overlaypanel-content {
  display: flex;
  flex-direction: column;
  max-height: var(--notification-panel-height);
}
</style>
