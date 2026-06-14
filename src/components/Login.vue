<template>
  <div class="login-center p-mt-6">
    <img alt="Weight Control" style="width: 120px" src="../assets/logo.png">
    <GoogleSignInButton @success="login" @error="loginError"></GoogleSignInButton>
  </div>
</template>

<script>
import { post } from '../services/api';
import { userState } from '../state';

export default {
  data() {
    return {
      state: userState()
    }
  },
  methods: {
    async login(response) {
      const { credential } = response;
      const authUser = await post('/auth/google', { credential });
      this.state.authenticated = true;
      this.state.user.mail = authUser.email;
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
