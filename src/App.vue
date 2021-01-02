<template>
  <div class="p-mb-5" v-if="this.state.authenticated" >
    <Button class="p-button-danger logout-button" icon="pi pi-sign-out" @click="signOut()" />
    <Menubar :model="items" />
  </div>
  <Toast />
  <router-view />
</template>

<script>
/* global gapi */
import { useState } from './state';

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
          label:'History',
          icon:'pi pi-fw pi-chart-bar',
          to: '/history'
        },
        {
          label:'Backup',
          icon:'pi pi-fw pi-upload',
          to: '/backup'
        }
      ],
      state: useState()
    }
  },
  mounted() {
    gapi.load('auth2', function() {
      gapi.auth2.init();
    });
  },
  created() {
    if (!this.state.authenticated) {
      this.$router.push({ path: '/login' });
    }
  },
  methods: {
    signOut() {
      let auth2 = gapi.auth2.getAuthInstance();
      auth2.signOut().then(this.logout);
    },
    logout() {
      document.cookie = "login= ; expires = Thu, 01 Jan 1970 00:00:00 GMT"
      this.state.authenticated = false;
      this.$router.push({ path: '/login' });
    }
  }
};
</script>

<style>
@media ( max-width: 650px ) { /* Default 35em */
  .p-filter-column, .non-responsive-column {
    display: none;
  }
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
@media (max-width: 575px) {
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
    z-index: 10000;
  }
}
@media (min-width: 575px) {
  .logout-button {
    position: absolute !important;
    top: 20px;
    right: 20px;
  }
  span.percentage:before {
    content: '(';
  }
  span.percentage:after {
    content: ')';
  }
}
</style>