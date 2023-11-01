<template>
  <div class="login-center p-mt-6">
    <img alt="Weight Control" style="width: 120px" src="../assets/logo.png">
    <GoogleSignInButton @success="login" @error="loginError"></GoogleSignInButton>
  </div>
</template>

<script>
import { decodeCredential } from "vue3-google-signin";
import { userState, saveCookie } from '../state';

export default {
  data() {
    return {
      state: userState()
    }
  },
  methods: {
    login(response) {
      const { credential } = response;
      const profile = decodeCredential(credential);
      let email = profile.email;
      saveCookie(credential, email);
      this.state.token = credential;
      this.state.authenticated = true;
      this.state.user.mail = email;
      this.$router.push({ path: '/' })
    },
    loginError() {
      console.error("Login failed");
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