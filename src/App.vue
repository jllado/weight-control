<template>
  <div class="p-mb-5" v-if="this.state.authenticated" >
    <Menubar :model="items" class="app-menubar">
      <template #item="{ item, props, hasSubmenu }">
        <router-link v-if="item.to" v-slot="{ href, navigate, isActive, isExactActive }" :to="item.to" custom>
          <a :href="href" v-bind="props.action" :class="[props.action.class, { 'router-link-active': isActive, 'router-link-active-exact': isExactActive }]" @click="navigate">
            <span v-bind="props.icon" />
            <span v-bind="props.label">{{ item.label }}</span>
          </a>
        </router-link>
        <a v-else v-bind="props.action" :class="[props.action.class, { 'router-link-active': isGroupActive(item) }]">
          <span v-bind="props.icon" />
          <span v-bind="props.label">{{ item.label }}</span>
          <span v-if="hasSubmenu" class="pi pi-angle-down app-menu-chevron" aria-hidden="true" />
        </a>
      </template>
      <template #end>
        <div class="app-header-actions">
          <Button class="p-button-sm p-button-outlined coach-button" label="Open Coach" icon="pi pi-external-link" @click="openCoach()" />
          <NotificationBell />
          <Button
              icon="pi pi-user"
              label="Account"
              class="p-button-rounded p-button-text account-menu-button"
              aria-label="Account"
              aria-haspopup="true"
              aria-controls="account-menu"
              :aria-expanded="accountMenuVisible"
              @click="toggleAccountMenu" />
          <Menu
              id="account-menu"
              ref="accountMenu"
              :model="accountItems"
              :popup="true"
              @show="accountMenuVisible = true"
              @hide="accountMenuVisible = false" />
        </div>
      </template>
    </Menubar>
  </div>
  <div class="app-action-notices" v-if="(this.state.installAvailable && !this.state.installed) || this.state.updateAvailable">
    <Button v-if="this.state.installAvailable && !this.state.installed" class="p-button-sm p-button-outlined app-action-button" icon="pi pi-download" label="Install app" @click="installApp()" />
    <Button v-if="this.state.updateAvailable" class="p-button-sm p-button-outlined p-button-success app-action-button" :icon="this.state.updateRefreshing ? 'pi pi-spin pi-spinner' : 'pi pi-refresh'" :label="this.state.updateRefreshing ? 'Updating...' : 'Update app'" :disabled="this.state.updateRefreshing" @click="updateApp()" />
  </div>
  <Toast position="top-right" :breakpoints="{'575px': {width: 'calc(100% - 2rem)', right: '1rem'}}" />
  <WinCelebration ref="winCelebration" @finished="celebrationFinished" />
  <router-view />
</template>

<script>
import { userState } from './state';
import { get, post } from './services/api';
import userProfileService from './services/UserProfileService';
import NotificationBell from './components/NotificationBell';
import WinCelebration from './components/WinCelebration';
import {onCelebrationRequested} from './services/CelebrationService';
import {openCoach} from './services/CoachService';

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
          label:'Track',
          icon:'pi pi-fw pi-chart-bar',
          items: [
            {label:'Weight', icon:'pi pi-fw pi-chart-bar', to:'/weights'},
            {label:'Progress Photos', icon:'pi pi-fw pi-images', to:'/photos'},
            {label:'Blood Pressure', icon:'pi pi-fw pi-chart-line', to:'/pressures'},
            {label:'Cholesterol', icon:'pi pi-fw pi-chart-line', to:'/cholesterol'},
            {label:'Mood', icon:'pi pi-fw pi-star', to:'/moods'},
            {label:'Nutrition', icon:'pi pi-fw pi-chart-pie', to:'/calories'},
            {label:'Sleep', icon:'pi pi-fw pi-moon', to:'/sleep'},
            {label:'Sickness', icon:'pi pi-fw pi-heart', to:'/sicknesses'},
            {label:'Back Pain', icon:'pi pi-fw pi-chart-line', to:'/back'}
          ]
        },
        {
          label:'Plan',
          icon:'pi pi-fw pi-calendar-plus',
          items: [
            {label:'Goal and plan', icon:'pi pi-fw pi-compass', to:'/plan'},
            {label:'Habits', icon:'pi pi-fw pi-calendar-plus', to:'/habits'},
            {label:'Routines', icon:'pi pi-fw pi-clock', to:'/routines'},
            {label:'Medications', icon:'pi pi-fw pi-bell', to:'/medications'},
            {label:'Workouts', icon:'pi pi-fw pi-bolt', to:'/workouts'}
          ]
        },
        {
          label:'Review',
          icon:'pi pi-fw pi-comment',
          items: [
            {label:'Reflections', icon:'pi pi-fw pi-comment', to:'/reflections'},
            {label:'Personal Records', icon:'pi pi-fw pi-star', to:'/records'}
          ]
        }
      ],
      accountMenuVisible: false,
      state: userState(),
      celebration_queue: [],
      current_celebration: null,
      stop_celebration_listener: null
    };
  },
  computed: {
    accountItems() {
      return [
        {label: 'Settings', icon: 'pi pi-cog', command: () => this.$router.push('/settings')},
        {separator: true},
        {label: 'Log out', icon: 'pi pi-sign-out', command: this.logout}
      ];
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
    openCoach,
    isGroupActive(item) {
      return item.items.some(candidate => candidate.to === this.$route.path);
    },
    toggleAccountMenu(event) {
      this.$refs.accountMenu.toggle(event);
    },
    playNextCelebration() {
      if (this.current_celebration || !this.celebration_queue.length) {
        return;
      }
      this.current_celebration = this.celebration_queue.shift();
      this.$nextTick(() => this.$refs.winCelebration.playRandom(this.current_celebration.type));
    },
    celebrationFinished() {
      this.completeCelebration();
    },
    completeCelebration() {
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
  display: flex;
  align-items: center;
  gap: 0.35rem;
}
.app-menubar .p-menubar-root-list {
  flex-wrap: nowrap;
}
.app-menubar .p-menubar-end {
  margin-left: auto;
}
.app-menu-chevron {
  margin-left: 0.5rem;
  font-size: 0.75rem;
}
.account-menu-button .p-button-label {
  flex: none;
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
  .account-menu-button .p-button-label {
    display: none;
  }
  .coach-button .p-button-label {
    display: none;
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
