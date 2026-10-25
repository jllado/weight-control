<template>
  <article class="warning-progress-card" :aria-label="`${label} warning progress`">
    <div class="warning-progress-heading">
      <strong v-if="showTitle" class="warning-title">{{ label }}</strong>
      <div class="warning-progress-labels">
        <span class="warning-direction" :class="`direction-${progress.direction.toLowerCase().replaceAll('_', '-')}`">{{ direction_label }}</span>
        <span class="warning-status">{{ status_label }}</span>
      </div>
    </div>
    <p class="warning-progress-date">{{ warning.version === progress.sourceVersion ? 'Reviewed' : 'Last DAILY assessment' }} {{ format_date(progress.reviewedDate) }}</p>
    <p>{{ progress.assessment }}</p>
    <p class="warning-comparison">
      <strong>Recent {{ format_range(progress.recentStart, progress.recentEnd) }}</strong>
      <template v-if="progress.priorStart"> vs prior {{ format_range(progress.priorStart, progress.priorEnd) }}</template>
    </p>
    <p><strong>Toward resolution:</strong> {{ progress.criteriaProgress }}</p>
    <p><strong>Resolution criteria:</strong> {{ progress.resolutionCriteria }}</p>
    <p><strong>Next action:</strong> {{ progress.nextAction }}</p>
    <details>
      <summary>Evidence and criteria changes</summary>
      <p><strong>Evidence:</strong> {{ progress.evidence }}</p>
      <p v-if="progress.criteriaChangeReason"><strong>Criteria changed:</strong> {{ progress.criteriaChangeReason }}</p>
      <p v-if="progress.resolutionRationale"><strong>Resolution:</strong> {{ progress.resolutionRationale }}</p>
    </details>
  </article>
</template>

<script>
import dayjs from 'dayjs';
import {warningLabel} from '@/model/CoachWarning';

const directions = {
  IMPROVING: 'Improving', UNCHANGED: 'Unchanged', WORSENING: 'Worsening',
  RESOLVED: 'Resolved', INSUFFICIENT_EVIDENCE: 'Insufficient evidence'
};

export default {
  name: 'WarningProgressCard',
  props: {
    warning: {type: Object, required: true},
    showTitle: {type: Boolean, default: true}
  },
  computed: {
    progress() { return this.warning.progress; },
    label() { return warningLabel(this.warning.type); },
    direction_label() {
      return this.progress.initialReview ? `Initial review · ${directions[this.progress.direction]}` : directions[this.progress.direction];
    },
    status_label() { return this.warning.status === 'ACTIVE' ? 'Active' : 'Resolved'; }
  },
  methods: {
    format_date(date) { return dayjs(`${date}T00:00:00`).format('D MMM YYYY'); },
    format_range(start, end) { return `${this.format_date(start)}–${this.format_date(end)}`; }
  }
};
</script>

<style scoped>
.warning-progress-card {
  min-width: 0;
  padding: 0.85rem;
  border: 1px solid var(--line, var(--surface-border, #dee2e6));
  border-radius: 0.65rem;
  background: var(--paper, var(--surface-ground, #f8f9fa));
  overflow-wrap: anywhere;
}
.warning-progress-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 0.5rem 1rem; flex-wrap: wrap; }
.warning-title { font-size: 1rem; }
.warning-progress-labels { display: flex; gap: 0.35rem; flex-wrap: wrap; }
.warning-progress-labels span { padding: 0.18rem 0.45rem; border-radius: 999px; font-size: 0.78rem; font-weight: 700; }
.direction-improving { background: #e4eee8; color: #285a43; }
.direction-unchanged { background: #e8edf1; color: #43525c; }
.direction-worsening { background: #fae5db; color: #7a3418; }
.direction-resolved { background: #dceff0; color: #1e5c64; }
.direction-insufficient-evidence { background: #e7eaec; color: #49545d; }
.warning-status { background: #e8edf1; color: #315165; }
.warning-progress-card p { margin: 0.45rem 0 0; line-height: 1.45; white-space: pre-wrap; }
.warning-progress-date { color: var(--muted, var(--text-color-secondary, #6c757d)); font-size: 0.85rem; }
.warning-comparison { color: var(--ink, var(--text-color, #495057)); }
.warning-progress-card details { margin-top: 0.6rem; }
.warning-progress-card summary { color: #315f78; cursor: pointer; font-weight: 600; }
.warning-progress-card summary:focus-visible { outline: 2px solid #315f78; outline-offset: 3px; }
</style>
