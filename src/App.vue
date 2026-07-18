<template>
  <div class="p-mb-5" v-if="this.state.authenticated" >
    <Button class="p-button-danger logout-button" icon="pi pi-sign-out" @click="logout()" />
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
  <Toast position="top-right" />
  <router-view />
</template>

<script>
import { userState } from './state';
import { get, post } from './services/api';
import userProfileService from './services/UserProfileService';

export default {
  name: "app",
  data() {
    return {
      items: [
        {
          label:'Home',
          icon:'pi pi-fw pi-home',
          to: '/'
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
      state: userState()
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
  },
    async created() {
      try {
        const authUser = await get('/auth/me');
        this.state.authenticated = authUser.authenticated;
        this.state.user.mail = authUser.email;
        this.state.user.profile = await userProfileService.get();
        if (this.$router.currentRoute.value.path === '/login') {
          this.$router.push({ path: '/' });
        }
      } catch {
        this.state.authenticated = false;
        this.state.user.mail = undefined;
        this.state.user.profile = null;
        if (this.$router.currentRoute.value.path !== '/login') {
          this.$router.push({ path: '/login' });
        }
    }
  },
  methods: {
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
  .logout-button {
    position: absolute !important;
    top: 16px;
    right: 16px;
    z-index: 1000;
  }
}
@media (min-width: 575px) {
  .logout-button {
    position: absolute !important;
    top: 20px;
    right: 20px;
  }
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
