<template>
  <div class="login-center p-mt-6">
    <img alt="Weight Control" style="width: 120px" src="../assets/logo.png">
    <GoogleSignInButton @success="login" @error="loginError"></GoogleSignInButton>
    <Message v-if="loginFailed" severity="error" :closable="false">Unable to sign in. Please try again.</Message>
  </div>
</template>

<script>
import { post } from '../services/api';
import { userState } from '../state';
import userProfileService from '../services/UserProfileService';

export default {
  data() {
    return {
      state: userState(),
      loginFailed: false
    }
  },
  methods: {
    async login(response) {
      this.loginFailed = false;
      try {
        const { credential } = response;
        const authUser = await post('/auth/google', { credential });
        const profile = await userProfileService.get();
        this.state.authenticated = true;
        this.state.user.mail = authUser.email;
        this.state.user.profile = profile;
        await this.$router.push({ path: '/' });
      } catch (error) {
        this.failLogin(error);
      }
    },
    loginError() {
      this.failLogin(new Error('Google login failed'));
    },
    failLogin(error) {
      this.state.authenticated = false;
      this.state.user.mail = undefined;
      this.state.user.profile = null;
      this.loginFailed = true;
      this.$log.error(error);
    }
  }
}
</script>

<style>
.login-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  margin-left: auto;
  margin-right: auto;
  width: min(20rem, calc(100% - 2rem));
}
</style>
