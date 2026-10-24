<template>
  <main class="actions-page">
    <header class="actions-heading"><h1>Actions</h1><Button ref="create" label="New action" icon="pi pi-plus" @click="openForm()" /></header>
    <ActionActivationForm v-if="form" :key="formKey" :suggestion="suggestion" :attempt="editing" :restart="restarting" @saved="saved" @cancel="closeForm" />
    <Message v-if="draftError" severity="error" :closable="false">{{ draftError }}</Message>
    <div class="actions-tabs" role="tablist" aria-label="Action views">
      <button v-for="view in ['ACTIVE', 'SUGGESTIONS', 'HISTORY']" :id="'action-tab-' + view" :key="view" role="tab" :tabindex="tab === view ? 0 : -1" :aria-selected="tab === view" :aria-controls="'action-panel-' + view" @click="selectTab(view)" @keydown="tabKey($event, view)">{{ {ACTIVE: 'Active', SUGGESTIONS: 'Suggestions', HISTORY: 'History'}[view] }}</button>
    </div>
    <section v-for="view in ['ACTIVE', 'SUGGESTIONS', 'HISTORY']" :id="'action-panel-' + view" :key="view" role="tabpanel" :hidden="tab !== view" :aria-labelledby="'action-tab-' + view">
      <template v-if="tab === view">
      <div v-if="tab !== 'SUGGESTIONS'" class="actions-date"><label for="actions-date">Progress date</label><InputText id="actions-date" v-model="date" type="date" :max="today" required /></div>
      <p v-if="loading" role="status">Loading actions…</p>
      <Message v-else-if="error" severity="error" :closable="false">{{ error }} <Button label="Retry" class="p-button-text" @click="load" /></Message>
      <template v-else-if="tab === 'SUGGESTIONS'">
        <p v-if="!suggestions.length">No suggestions yet. Add a custom action or ask the Coach to reflect on your latest week.</p>
        <article v-for="row in suggestions" :key="row.sourceKey" class="suggestion">
          <h2>{{ row.wording }}</h2><p>{{ row.sourceLabel }} · {{ row.provenance.join(', ') }}</p>
          <Button v-if="row.existingAttemptId" label="View existing action" class="p-button-outlined" @click="viewExisting(row.existingAttemptId)" />
          <Button v-else label="Activate action" class="p-button-outlined" @click="openForm(row)" />
        </article>
      </template>
      <template v-else>
        <p v-if="!attempts.length">{{ tab === 'HISTORY' ? 'Completed and stopped attempts will appear here.' : 'No active actions for this date.' }}</p>
        <ActionCard v-for="attempt in attempts" :key="attempt.id" :attempt="attempt" :date="date" @changed="load" @edit="edit" @restart="restart" />
      </template>
      </template>
    </section>
    <p class="actions-help">A missed day resets the streak. Your action stays active until its target is reached or you stop it.</p>
  </main>
</template>

<script>
import {nextTick} from 'vue';
import ActionActivationForm from './ActionActivationForm.vue';
import ActionCard from './ActionCard.vue';
import actionService, {actionToday} from '../services/ActionService';
export default {
  components: {ActionActivationForm, ActionCard},
  data() { return {
    today: actionToday(), date: this.$route.query.date || actionToday(),
    tab: this.$route.query.tab === 'suggestions' ? 'SUGGESTIONS' : 'ACTIVE',
    attempts: [], suggestions: [], loading: false, error: null, draftError: null, generation: 0,
    form: false, formKey: 0, suggestion: null, editing: null, restarting: false
  }; },
  watch: {
    date() { if (this.date) this.load(); },
    '$route.query.source'() { this.resolveSource(); }
  },
  async mounted() { await this.load(); await this.resolveSource(); },
  beforeUnmount() { this.generation++; },
  methods: {
    async load() {
      const generation = ++this.generation;
      this.loading = true; this.error = null; this.attempts = []; this.suggestions = [];
      try {
        if (this.tab === 'SUGGESTIONS') {
          const rows = await actionService.suggestions(); if (generation === this.generation) this.suggestions = rows;
        } else {
          const rows = await actionService.list(this.date, this.tab); if (generation === this.generation) this.attempts = rows;
        }
      } catch (error) { if (generation === this.generation) this.error = error.message; }
      finally { if (generation === this.generation) this.loading = false; }
    },
    async resolveSource() {
      const source = this.$route.query.source;
      if (typeof source !== 'string') return;
      this.draftError = null;
      try {
        const row = await actionService.resolve(source);
        if (source !== this.$route.query.source) return;
        if (row.existingAttemptId) await this.viewExisting(row.existingAttemptId);
        else this.openForm(row);
      } catch (error) { if (source === this.$route.query.source) this.draftError = error.message; }
    },
    openForm(suggestion = null) { this.suggestion = suggestion; this.editing = null; this.restarting = false; this.formKey++; this.form = true; },
    edit(attempt) { this.openForm(); this.editing = attempt; },
    restart(attempt) { this.edit(attempt); this.restarting = true; },
    async closeForm() {
      this.form = false;
      if (this.$route.query.source) { const query = {...this.$route.query}; delete query.source; await this.$router.replace({path: '/actions', query}); }
      await nextTick(); this.$refs.create.$el.focus();
    },
    async saved() { await this.closeForm(); this.tab = 'ACTIVE'; this.date = this.today; await this.load(); },
    async viewExisting(id) {
      const attempt = await actionService.find(id, this.today);
      this.tab = ['COMPLETED', 'STOPPED'].includes(attempt.status) ? 'HISTORY' : 'ACTIVE';
      this.date = this.today; await this.load();
    },
    selectTab(tab) { this.tab = tab; this.load(); },
    tabKey(event, view) {
      if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
      event.preventDefault();
      const tabs = ['ACTIVE', 'SUGGESTIONS', 'HISTORY'];
      const index = event.key === 'Home' ? 0 : event.key === 'End' ? 2 : (tabs.indexOf(view) + (event.key === 'ArrowRight' ? 1 : 2)) % 3;
      this.selectTab(tabs[index]); document.getElementById('action-tab-' + tabs[index]).focus();
    }
  }
};
</script>

<style scoped>
.actions-page { max-width: 56rem; margin: 0 auto; padding: 1rem 0; }
.actions-heading { display: flex; align-items: center; justify-content: space-between; gap: 1rem; margin-bottom: 1rem; }
.actions-heading h1 { margin: 0; font-size: 1.6rem; }
.actions-tabs { display: flex; gap: .25rem; border-bottom: 1px solid #dedede; margin-bottom: 1rem; }
.actions-tabs button { padding: .75rem; border: 0; border-bottom: 3px solid transparent; background: transparent; cursor: pointer; color: #63717b; }
.actions-tabs button[aria-selected="true"] { color: #245b83; border-bottom-color: #245b83; }
.actions-tabs button:focus-visible { outline: 2px solid #245b83; outline-offset: 2px; }
.actions-date { display: flex; align-items: center; flex-wrap: wrap; gap: .5rem; margin-bottom: 1rem; }
.suggestion { padding: 1rem; border: 1px solid #dedede; background: white; border-radius: 4px; margin-bottom: .75rem; overflow-wrap: anywhere; }
.suggestion h2 { margin: 0; font-size: 1rem; }
.suggestion p, .actions-help { color: #63717b; font-size: .85rem; line-height: 1.5; }
</style>
