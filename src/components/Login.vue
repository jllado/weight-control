<template>
  <div class="login-center p-mt-6">
    <img alt="Weight Control" style="width: 120px" src="../assets/logo.png">
    <div id="google-signin-button" />
  </div>
</template>

<script>
/* global gapi */
import { userState } from '../state';

export default {
  data() {
    return {
      state: userState()
    }
  },
  mounted() {
    gapi.signin2.render('google-signin-button', {
      onsuccess: this.login
    });
  },
  methods: {
    login(googleUser) {
      let profile = googleUser.getBasicProfile();
      let id_token = googleUser.getAuthResponse().id_token;
      let email = profile.getEmail();
      document.cookie = 'wc-login=' +  id_token + ":" + email;
      this.state.authenticated = true;
      this.state.user.mail = email;
      this.$router.push({ path: '/' })
    }
  }
}
</script>

<style>
.login-center {
  display: block;
  margin-left: auto;
  margin-right: auto;
  width: 120px;
}
</style>