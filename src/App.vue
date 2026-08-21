<template>
  <div class="p-mb-5" v-if="this.state.authenticated" >
    <div class="app-header-actions">
      <NotificationBell />
      <Button class="p-button-danger logout-button" icon="pi pi-sign-out" aria-label="Log out" @click="logout()" />
    </div>
    <Menubar :model="items">
      <template #item="{ item, props }">
        <router-link v-slot="{ href, navigate, isActive, isExactActive }" :to="item.to" custom>
          <a :href="href" v-bind="props.action" :class="[props.action.class, { 'router-link-active': isActive, 'router-link-active-exact': isExactActive }]" @click="navigate">
            <span v-bind="props.icon" />
            <span v-bind="props.label">{{ item.label }}</span>
          </a>
        </router-link>
      </template>
    </Menubar>
  </div>
  <div class="app-action-notices" v-if="(this.state.installAvailable && !this.state.installed) || this.state.updateAvailable">
    <Button v-if="this.state.installAvailable && !this.state.installed" class="p-button-sm p-button-outlined app-action-button" icon="pi pi-download" label="Install app" @click="installApp()" />
    <Button v-if="this.state.updateAvailable" class="p-button-sm p-button-outlined p-button-success app-action-button" :icon="this.state.updateRefreshing ? 'pi pi-spin pi-spinner' : 'pi pi-refresh'" :label="this.state.updateRefreshing ? 'Updating...' : 'Update app'" :disabled="this.state.updateRefreshing" @click="updateApp()" />
  </div>
  <Toast position="top-right" :breakpoints="{'575px': {width: 'calc(100% - 2rem)', right: '1rem'}}" />
  <WinCelebration ref="winCelebration" @finished="celebrationFinished" />
  <Dialog appendTo="body" header="Personal records" v-model:visible="record_dialog_visible" :modal="true" :style="{width: 'min(560px, 96vw)'}" @hide="completeCelebration">
    <div v-if="current_celebration" class="record-achievement-list">
      <div v-for="achievement in current_celebration.achievements" :key="`${achievement.metric}-${achievement.subject.id}-${achievement.qualifier?.loadKg ?? ''}`" class="record-achievement-item">
        <div><strong>{{ achievement.metricLabel }}</strong> · {{ achievement.subject.label }}</div>
        <div v-if="achievement.qualifier" class="record-achievement-qualifier">{{ achievement.qualifier.label }}</div>
        <div class="record-achievement-value">{{ formatRecordValue(achievement) }}<span v-if="achievement.previousValue !== null"> · previous {{ formatPreviousRecordValue(achievement) }}</span></div>
      </div>
    </div>
    <template #footer><Button label="Great" icon="pi pi-check" @click="record_dialog_visible = false" /></template>
  </Dialog>
  <router-view />
</template>

<script>
import { userState } from './state';
import { get, post } from './services/api';
import userProfileService from './services/UserProfileService';
import NotificationBell from './components/NotificationBell';
import WinCelebration from './components/WinCelebration';
import {onCelebrationRequested} from './services/CelebrationService';
import {formatRecordValue} from './services/PersonalRecordService';

export default {
  name: "app",
  components: {NotificationBell, WinCelebration},
  data() {
    return {
      items: [
        {
          label:'Home',
          icon:'pi pi-fw pi-home',
          to: '/'
        },
        {
          label:'Reflections',
          icon:'pi pi-fw pi-comment',
          to: '/reflections'
        },
        {
          label:'Weights',
          icon:'pi pi-fw pi-chart-bar',
          to: '/weights'
        },
        {
          label:'Photos',
          icon:'pi pi-fw pi-images',
          to: '/photos'
        },
        {
          label:'Pressures',
          icon:'pi pi-fw pi-chart-line',
          to: '/pressures'
        },
        {
          label:'Cholesterol',
          icon:'pi pi-fw pi-chart-line',
          to: '/cholesterol'
        },
        {
          label:'Moods',
          icon:'pi pi-fw pi-star',
          to: '/moods'
        },
        {
          label:'Calories',
          icon:'pi pi-fw pi-chart-pie',
          to: '/calories'
        },
        {
          label:'Sleep',
          icon:'pi pi-fw pi-moon',
          to: '/sleep'
        },
        {
          label:'Sickness',
          icon:'pi pi-fw pi-heart',
          to: '/sicknesses'
        },
        {
          label:'Back',
          icon:'pi pi-fw pi-chart-line',
          to: '/back'
        },
        {
          label:'Habits',
          icon:'pi pi-fw pi-calendar-plus',
          to: '/habits'
        },
        {
          label:'Routines',
          icon:'pi pi-fw pi-clock',
          to: '/routines'
        },
        {
          label:'Workouts',
          icon:'pi pi-fw pi-bolt',
          to: '/workouts'
        },
        {
          label:'Records',
          icon:'pi pi-fw pi-trophy',
          to: '/records'
        },
        {
          label:'Settings',
          icon:'pi pi-fw pi-cog',
          to: '/settings'
        },
        {
          label:'Backup',
          icon:'pi pi-fw pi-upload',
          to: '/backup'
        }
      ],
      state: userState(),
      celebration_queue: [],
      current_celebration: null,
      record_dialog_visible: false,
      stop_celebration_listener: null
    }
  },
  mounted() {
    this.state.installed = window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone === true;
    if (this.state.installed) {
      return;
    }

    window.addEventListener('beforeinstallprompt', this.handleBeforeInstallPrompt);
    window.addEventListener('appinstalled', this.handleAppInstalled);
  },
  beforeUnmount() {
    window.removeEventListener('beforeinstallprompt', this.handleBeforeInstallPrompt);
    window.removeEventListener('appinstalled', this.handleAppInstalled);
    this.stop_celebration_listener?.();
  },
    async created() {
      this.stop_celebration_listener = onCelebrationRequested(request => {
        this.celebration_queue.push(request);
        this.playNextCelebration();
      });
      try {
        const authUser = await get('/auth/me');
        this.state.authenticated = authUser.authenticated;
        this.state.user.mail = authUser.email;
        this.state.user.profile = await userProfileService.get();
        if (this.$router.currentRoute.value.path === '/login') {
          this.$router.push({path: '/', query: this.$router.currentRoute.value.query});
        }
      } catch {
        this.state.authenticated = false;
        this.state.user.mail = undefined;
        this.state.user.profile = null;
        if (this.$router.currentRoute.value.path !== '/login') {
          this.$router.push({path: '/login', query: this.$router.currentRoute.value.query});
        }
    }
  },
  methods: {
    formatRecordValue,
    formatPreviousRecordValue(achievement) {
      return formatRecordValue({...achievement, value: achievement.previousValue});
    },
    playNextCelebration() {
      if (this.current_celebration || !this.celebration_queue.length) {
        return;
      }
      this.current_celebration = this.celebration_queue.shift();
      this.$nextTick(() => this.$refs.winCelebration.playRandom());
    },
    celebrationFinished() {
      if (this.current_celebration.type === 'PERSONAL_RECORDS') {
        this.record_dialog_visible = true;
      } else {
        this.completeCelebration();
      }
    },
    completeCelebration() {
      if (this.record_dialog_visible) {
        return;
      }
      this.current_celebration = null;
      this.playNextCelebration();
    },
    handleBeforeInstallPrompt(event) {
      event.preventDefault();
      this.state.deferredInstallPrompt = event;
      this.state.installAvailable = true;
    },
    handleAppInstalled() {
      this.state.deferredInstallPrompt = undefined;
      this.state.installAvailable = false;
      this.state.installed = true;
    },
    async installApp() {
      await this.state.deferredInstallPrompt.prompt();
      await this.state.deferredInstallPrompt.userChoice;
      this.state.deferredInstallPrompt = undefined;
      this.state.installAvailable = false;
    },
    updateApp() {
      if (!this.state.updateRegistration?.waiting) {
        return;
      }
      this.state.updateRefreshing = true;
      this.state.updateRegistration.waiting.postMessage({type: 'SKIP_WAITING'});
    },
    async logout() {
      await post('/auth/logout', {});
      this.state.authenticated = false;
      this.state.user.mail = undefined;
      this.state.user.profile = null;
      this.$router.push({ path: '/login' });
    }
  }
};
</script>

<style>
.anychart-credits {
  display: none;
}
.error {
  color: red;
  font-size: smaller;
  font-style: italic;
  font-weight: 500;
  margin-top: 4px;
}
.table-header {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.app-action-notices {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  justify-content: flex-end;
  width: fit-content;
  margin: 0.75rem 1rem 0.75rem auto;
  padding: 0.625rem;
  border: 1px solid #dce4ea;
  border-radius: 0.625rem;
  background: #f8fafc;
  box-shadow: 0 0.25rem 0.75rem rgba(35, 52, 70, 0.08);
}
.app-action-button {
  white-space: nowrap;
}
.app-header-actions {
  position: absolute;
  top: 20px;
  right: 20px;
  z-index: 1000;
  display: flex;
  align-items: center;
  gap: 0.35rem;
}
.record-achievement-list {
  display: grid;
  gap: 0.75rem;
}
.record-achievement-item {
  padding: 0.75rem;
  border: 1px solid #dce4ea;
  border-radius: 0.5rem;
  background: #f8fafc;
}
.record-achievement-qualifier {
  color: #64748b;
  font-size: 0.85rem;
}
.record-achievement-value {
  margin-top: 0.25rem;
  color: #087f5b;
  font-size: 1.1rem;
  font-weight: 700;
}
@media (max-width: 575px) {
  .app-action-notices {
    width: auto;
    margin: 0.75rem;
  }
  .app-action-button {
    flex: 1;
    justify-content: center;
  }
  .mobile-none {
    display: none;
  }
  .p-datatable th, td {
    padding-left: 2px !important;
  }
  .app-header-actions {
    top: 16px;
    right: 16px;
  }
}
@media (min-width: 575px) {
  span.extra_info:before {
    content: '(';
  }
  span.extra_info:after {
    content: ')';
  }
}
span.perfect {
  color: green;
}
span.good {
  color: #8ce30f;
}
span.bad {
  color: red;
}
span.normal {
  color: blue;
}
span.fail {
  color: orange;
}
.p-panel-content:has(.p-datatable) {
  padding: 0 !important;
}
</style>
