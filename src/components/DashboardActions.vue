<template>
  <Panel class="dashboard-actions">
    <template #header><div class="actions-panel-heading"><strong>Actions ({{ attempts.length }})</strong><router-link :to="{path: '/actions', query: {date}}">Manage ›</router-link></div></template>
    <p class="actions-date">{{ displayDate }} · {{ attempts.filter(row => row.checked).length }}/{{ attempts.length }} checked</p>
    <p v-if="loading" role="status">Loading actions…</p>
    <Message v-else-if="error" severity="error" :closable="false">{{ error }} <Button label="Retry" class="p-button-text" @click="load" /></Message>
    <template v-else>
      <ActionCard v-for="attempt in attempts.slice(0, 3)" :key="attempt.id" :attempt="attempt" :date="date" compact @changed="load" />
      <p v-if="!attempts.length">No active actions. <router-link to="/actions?tab=suggestions">Browse suggestions</router-link></p>
      <router-link v-if="attempts.length > 3" :to="{path: '/actions', query: {date}}">+{{ attempts.length - 3 }} more actions</router-link>
    </template>
  </Panel>
</template>
<script>
import ActionCard from './ActionCard.vue';
import actionService from '../services/ActionService';
import dayjs from 'dayjs';
export default {
  components: {ActionCard},
  props: {date: {type: String, required: true}},
  data() { return {attempts: [], loading: false, error: null, generation: 0}; },
  computed: {displayDate() { return dayjs(this.date).format('DD/MM/YYYY'); }},
  watch: {date: {immediate: true, handler() { this.load(); }}},
  beforeUnmount() { this.generation++; },
  methods: {
    async load() {
      const generation = ++this.generation;
      this.loading = true; this.error = null; this.attempts = [];
      try { const attempts = await actionService.list(this.date); if (generation === this.generation) this.attempts = attempts; }
      catch (error) { if (generation === this.generation) this.error = error.message; }
      finally { if (generation === this.generation) this.loading = false; }
    }
  }
};
</script>
<style scoped>
.dashboard-actions { margin-bottom: 1rem; }
.actions-panel-heading { display: flex; align-items: center; justify-content: space-between; gap: .75rem; width: 100%; }
.actions-panel-heading a { font-size: .85rem; }
.actions-date { margin: 0; color: #63717b; font-size: .85rem; }
</style>
