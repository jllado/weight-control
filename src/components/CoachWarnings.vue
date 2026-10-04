<template>
  <div v-if="active.length || has_history || error || loading" class="coach-warnings">
    <span v-if="active.length" class="warning-indicator-wrap">
      <Button v-tooltip.top="warning_tooltip"
            icon="pi pi-exclamation-triangle" class="p-button-outlined p-button-warning warning-indicator" aria-describedby="coach-warnings-tooltip"
            :aria-label="`Current Coach warnings: ${active.length === 1 ? active[0].label : active.length + ' warnings'}`" @focus="tooltip_event($event, 'mouseenter')" @blur="tooltip_event($event, 'mouseleave')" @click="open" />
      <CountBadge :value="active.length" tone="warning" />
    </span>
    <Button v-else-if="has_history" v-tooltip.top="warning_tooltip" aria-label="Coach history" aria-describedby="coach-warnings-tooltip" icon="pi pi-history" class="p-button-outlined p-button-secondary warning-indicator" @focus="tooltip_event($event, 'mouseenter')" @blur="tooltip_event($event, 'mouseleave')" @click="open" />
    <span v-else-if="loading" role="status" class="warning-loading">Checking Coach warnings…</span>
    <div v-if="error" role="alert" class="warning-error">{{ error }} <Button label="Retry" class="p-button-text" @click="refresh" /></div>
    <Dialog v-model:visible="visible" header="Current Coach warnings" modal appendTo="body" :style="{width: '36rem'}" :breakpoints="{'640px': '95vw'}">
      <p v-if="!active.length">No active warnings.</p>
      <article v-for="warning in active" :key="warning.id" class="warning-detail">
        <h3><i class="pi pi-exclamation-triangle" aria-hidden="true"></i> {{ warning.label }}</h3>
        <p>{{ warning.content.explanation }}</p>
        <p><strong>Evidence:</strong> {{ warning.content.evidence }}</p>
        <p><strong>Next step:</strong> {{ warning.content.action }}</p>
        <p v-if="warning.content.onsetDate">Since approximately {{ warning.content.onsetDate }}</p>
        <p class="warning-date">Last reviewed {{ warning.content.reviewedDate }}</p>
        <Button label="Review history" icon="pi pi-history" class="p-button-text" @click="show_revisions(warning)" />
      </article>
      <p>Coach reviews and resolves warnings during coaching sessions.</p>
      <Button v-if="has_history" label="Resolved history" icon="pi pi-history" class="p-button-text" :loading="history_loading" @click="load_history(0)" :disabled="history_loading" />
      <div v-if="history_error" role="alert">{{ history_error }}</div>
      <article v-for="warning in history.items" :key="warning.id" class="warning-detail">
        <h3>{{ warning.label }} · Resolved</h3>
        <p>{{ warning.content.explanation }}</p>
        <p>{{ warning.resolutionRationale }}</p>
        <p class="warning-date">Last reviewed {{ warning.content.reviewedDate }}</p>
        <Button label="Review history" class="p-button-text" @click="show_revisions(warning)" />
      </article>
      <Button v-if="history.hasMore" label="More history" class="p-button-outlined" :loading="history_loading" @click="load_history(history.page + 1)" :disabled="history_loading" />
      <template #footer><div class="action-group"><Button label="Close" class="p-button-secondary" @click="visible = false" /></div></template>
    </Dialog>
    <Dialog v-model:visible="revisions_visible" :header="revision_title" modal appendTo="body" :style="{width: '36rem'}" :breakpoints="{'640px': '95vw'}">
      <p v-if="revisions_error" role="alert">{{ revisions_error }}</p>
      <article v-for="revision in revisions.items" :key="revision.version" class="warning-detail">
        <h3>{{ revision.content.reviewedDate }} · {{ revision.status === 'ACTIVE' ? 'Active' : 'Resolved' }}</h3>
        <p>{{ revision.content.explanation }}</p>
        <p><strong>Evidence:</strong> {{ revision.content.evidence }}</p>
        <p><strong>Next step:</strong> {{ revision.content.action }}</p>
        <p v-if="revision.resolutionRationale">{{ revision.resolutionRationale }}</p>
      </article>
      <Button v-if="revisions.hasMore || revisions_error" label="More reviews" class="p-button-outlined" :loading="revisions_loading" @click="load_revisions(revisions.page + 1)" :disabled="revisions_loading" />
      <template #footer><div class="action-group"><Button label="Close" class="p-button-secondary" @click="revisions_visible = false" /></div></template>
    </Dialog>
  </div>
</template>

<script>
import service from '../services/CoachWarningService';
import Tooltip from 'primevue/tooltip';
import CountBadge from './CountBadge.vue';

export default {
  emits: ['active-updated'],
  components: {CountBadge},
  directives: {tooltip: {
    ...Tooltip,
    beforeMount(el, binding, vnode) {
      Tooltip.beforeMount(el, binding, vnode);
      binding.instance.position_tooltip(el.$instance);
    }
  }},
  computed: {
    warning_tooltip() {
      return {value: this.active.length ? this.active.map(warning => warning.label).join(' · ') : 'Coach history', id: 'coach-warnings-tooltip', class: 'coach-warning-tooltip', autoHide: false, fitContent: false};
    }
  },
  data() {
    return {
      active: [], has_history: false, loading: false, error: '', visible: false,
      history: {items: [], page: -1, hasMore: false}, history_loading: false, history_error: '',
      revisions: {items: [], page: -1, hasMore: false}, revisions_loading: false, revisions_error: '',
      revisions_visible: false, revision_id: null, revision_title: ''
    };
  },
  mounted() {
    this.refresh();
    window.addEventListener('focus', this.refresh);
    document.addEventListener('visibilitychange', this.on_visibility);
  },
  beforeUnmount() {
    window.removeEventListener('focus', this.refresh);
    document.removeEventListener('visibilitychange', this.on_visibility);
  },
  methods: {
    position_tooltip(instance) {
      instance.align = function(el) {
        this.alignTop(el);
        const tooltip = this.getTooltipElement(el);
        tooltip.style.display = 'inline-block';
        const box = tooltip.getBoundingClientRect();
        const left = Math.max(8, Math.min(box.left, document.documentElement.clientWidth - box.width - 8));
        tooltip.style.left = `${left + window.scrollX}px`;
        tooltip.style.top = `${Math.max(window.scrollY + 8, parseFloat(tooltip.style.top))}px`;
        tooltip.querySelector('.p-tooltip-arrow').style.left = `${el.getBoundingClientRect().left + el.offsetWidth / 2 - left}px`;
      };
    },
    tooltip_event(event, type) { event.currentTarget.dispatchEvent(new MouseEvent(type)); },
    on_visibility() { if (document.visibilityState === 'visible') this.refresh(); },
    async refresh() {
      if (this.loading) return;
      this.loading = true;
      this.error = '';
      try {
        const data = await service.overview();
        this.active = data.active;
        this.has_history = data.hasHistory;
        this.$emit('active-updated', this.active);
      } catch { this.error = 'Could not refresh Coach warnings.'; }
      finally { this.loading = false; }
    },
    open() { this.visible = true; this.history = {items: [], page: -1, hasMore: false}; this.history_error = ''; },
    async load_history(page) {
      this.history_loading = true;
      this.history_error = '';
      try {
        const data = await service.history(page);
        this.history = {...data, items: page ? [...this.history.items, ...data.items] : data.items};
      } catch { this.history_error = 'Could not load resolved history. Try again.'; }
      finally { this.history_loading = false; }
    },
    show_revisions(warning) {
      this.revision_id = warning.id;
      this.revision_title = `${warning.label} · Reviews`;
      this.revisions = {items: [], page: -1, hasMore: false};
      this.revisions_visible = true;
      this.load_revisions(0);
    },
    async load_revisions(page) {
      this.revisions_loading = true;
      this.revisions_error = '';
      try {
        const data = await service.revisions(this.revision_id, page);
        this.revisions = {...data, items: page ? [...this.revisions.items, ...data.items] : data.items};
      } catch { this.revisions_error = 'Could not load reviews. Try again.'; }
      finally { this.revisions_loading = false; }
    }
  }
};
</script>

<style scoped>
.coach-warnings { display: inline-flex; align-items: center; flex-wrap: wrap; gap: 0.5rem; min-width: 0; max-width: 100%; }
.warning-indicator-wrap { position: relative; display: inline-flex; }
.warning-indicator { flex-shrink: 0; }
.warning-detail { border-bottom: 1px solid var(--surface-border, #dee2e6); padding: 0.5rem 0; overflow-wrap: anywhere; }
.warning-detail h3 { font-size: 1rem; }
.warning-detail p { white-space: pre-wrap; }
.warning-date, .warning-loading { color: var(--text-color-secondary, #6c757d); font-size: 0.85rem; }
.warning-error { color: var(--text-color, #495057); font-size: 0.85rem; }
</style>

<style>
/* PrimeVue appends the tooltip to body; allow pointer entry for persistent warning text. */
.coach-warning-tooltip { pointer-events: auto; width: max-content; max-width: min(20rem, calc(100vw - 1rem)); }
</style>
