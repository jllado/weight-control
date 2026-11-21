<template>
  <div v-if="warning_labels.length || error || loading || visible" class="coach-warnings">
    <span v-if="warning_labels.length" class="warning-indicator-wrap">
      <Button v-tooltip.top="warning_tooltip"
            ref="warning_indicator"
            icon="pi pi-exclamation-triangle" class="p-button-outlined p-button-warning warning-indicator" aria-describedby="coach-warnings-tooltip"
            :aria-label="`Current Coach warnings: ${warning_labels.length} ${warning_labels.length === 1 ? 'warning, ' + warning_labels[0] : 'warnings'}`" @focus="tooltip_event($event, 'mouseenter')" @blur="tooltip_event($event, 'mouseleave')" @click="open" />
      <CountBadge :value="warning_labels.length" tone="warning" />
    </span>
    <span v-else-if="loading" role="status" class="warning-loading">Checking Coach warnings…</span>
    <div v-if="error" role="alert" class="warning-error">{{ error }} <Button label="Retry" class="p-button-text" @click="refresh" /></div>
    <Dialog v-model:visible="visible" header="Current Coach warnings" modal appendTo="body" :style="{width: '36rem'}" :breakpoints="{'640px': '95vw'}" @hide="restore_warning_focus">
      <p v-if="!warning_labels.length">No active warnings.</p>
      <div v-else class="warning-list">
        <OvernightHeartRateWarning v-if="heart_rate_reasons.length" :reasons="heart_rate_reasons" />
        <WarningProgressCard v-for="warning in active" :key="warning.id" :warning="warning" />
      </div>
      <template #footer><div class="action-group"><Button label="Close" class="p-button-secondary" @click="visible = false" /></div></template>
    </Dialog>
  </div>
</template>

<script>
import service from '../services/CoachWarningService';
import Tooltip from 'primevue/tooltip';
import CountBadge from './CountBadge.vue';
import WarningProgressCard from './WarningProgressCard.vue';
import OvernightHeartRateWarning from './OvernightHeartRateWarning.vue';

export default {
  emits: ['active-updated'],
  components: {CountBadge, WarningProgressCard, OvernightHeartRateWarning},
  directives: {tooltip: {
    ...Tooltip,
    beforeMount(el, binding, vnode) {
      Tooltip.beforeMount(el, binding, vnode);
      binding.instance.position_tooltip(el.$instance);
    }
  }},
  computed: {
    warning_labels() {
      const labels = this.active.map(warning => warning.label);
      if (this.heart_rate_reasons.length) labels.unshift('Overnight heart rate');
      return labels;
    },
    warning_tooltip() {
      return {value: this.warning_labels.join(' · '), id: 'coach-warnings-tooltip', class: 'coach-warning-tooltip', autoHide: false, fitContent: false};
    }
  },
  data() {
    return {
      active: [], heart_rate_reasons: [], refresh_after_load: false, loading: false, error: '', visible: false
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
    refreshAfterChange() {
      if (this.loading) this.refresh_after_load = true;
      else return this.refresh();
    },
    async refresh() {
      if (this.loading) return;
      this.loading = true;
      this.error = '';
      try {
        const data = await service.overview();
        // PrimeVue leaves a visible tooltip behind when its bound text changes.
        this.$refs.warning_indicator?.$el.dispatchEvent(new MouseEvent('mouseleave'));
        this.active = data.active;
        this.heart_rate_reasons = data.overnightHeartRateReasons ?? [];
        this.$emit('active-updated', this.active);
      } catch { this.error = 'Could not refresh Coach warnings.'; }
      finally {
        this.loading = false;
        if (this.refresh_after_load) { this.refresh_after_load = false; await this.refresh(); }
      }
    },
    open() { this.visible = true; },
    restore_warning_focus() {
      const indicator = this.$refs.warning_indicator?.$el;
      if (indicator) {
        indicator.focus();
        indicator.dispatchEvent(new MouseEvent('mouseleave'));
      }
    }
  }
};
</script>

<style scoped>
.coach-warnings { display: inline-flex; align-items: center; flex-wrap: wrap; gap: 0.5rem; min-width: 0; max-width: 100%; }
.warning-indicator-wrap { position: relative; display: inline-flex; }
.warning-indicator { flex-shrink: 0; }
.warning-list { display: grid; gap: 1.5rem; }
.warning-loading { color: var(--text-color-secondary, #6c757d); font-size: 0.85rem; }
.warning-error { color: var(--text-color, #495057); font-size: 0.85rem; }
</style>

<style>
/* PrimeVue appends the tooltip to body; allow pointer entry for persistent warning text. */
.coach-warning-tooltip { pointer-events: auto; width: max-content; max-width: min(20rem, calc(100vw - 1rem)); }
</style>
