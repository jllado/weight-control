<template>
  <div class="p-mb-5" v-if="this.state.authenticated" >
    <Button class="p-button-danger logout-button" icon="pi pi-sign-out" @click="logout()" />
    <Menubar :model="items" />
  </div>
  <div class="install-banner" v-if="this.state.installAvailable && !this.state.installed">
    <Button v-if="this.state.installAvailable" class="p-button-sm" label="Install app" @click="installApp()" />
  </div>
  <Toast position="top-right" />
  <router-view />
</template>

<script>
import { userState } from './state';
import { get, post } from './services/api';

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
      if (this.$router.currentRoute.value.path === '/login') {
        this.$router.push({ path: '/' });
      }
    } catch {
      this.state.authenticated = false;
      this.state.user.mail = undefined;
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
    async logout() {
      await post('/auth/logout', {});
      this.state.authenticated = false;
      this.state.user.mail = undefined;
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
.install-banner {
  display: flex;
  justify-content: flex-end;
  margin: 12px 16px;
}
@media (max-width: 575px) {
  .install-banner {
    margin: 12px;
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
