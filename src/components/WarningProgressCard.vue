<template>
  <article class="warning-progress-card" :aria-label="`${label} warning progress`">
    <header class="warning-progress-heading">
      <h3 class="warning-title"><i class="pi pi-exclamation-triangle" aria-hidden="true"></i>{{ label }}</h3>
      <span class="warning-goal-status" :class="`goal-${goal_status.toLowerCase()}`">
        <i :class="status_icon" aria-hidden="true"></i>{{ status_label }}
      </span>
    </header>
    <div class="warning-dates">
      <p>Triggered <time v-if="warning.createdAt" :datetime="warning.createdAt">{{ trigger_date }}</time><span v-else>Not recorded</span></p>
      <p v-if="review" :aria-label="assessment_label" :title="assessment_label">Last {{ review.windowDays }} days</p>
    </div>
    <div class="warning-metrics">
      <table v-if="review" class="warning-goal-table" :aria-label="`${label} current metrics and goals`">
        <thead><tr><th scope="col">{{ metric_heading }}</th><th scope="col">Current</th><th scope="col">Goal</th></tr></thead>
        <tbody>
          <tr v-for="metric in review.metrics" :key="metric.key">
            <th scope="row">{{ metric.label }}<small v-if="!is_seconds(metric.unit)">{{ metric.unit }}</small></th>
            <td class="warning-current" :class="`goal-${metric_status(metric).toLowerCase()}`" :aria-label="metric_accessible_label(metric)">{{ format_value(metric.value, metric.unit) }}</td>
            <td class="warning-goal">{{ format_goal(metric) }}</td>
          </tr>
        </tbody>
      </table>
      <p class="warning-short-status">{{ review ? review.shortStatus : 'Progress not recorded.' }}</p>
    </div>
  </article>
</template>

<script>
import dayjs from 'dayjs';
import {warningLabel} from '@/model/CoachWarning';

const statusLabels = {MET: 'Goals met', NOT_MET: 'Goals not met', UNKNOWN: 'Unknown'};
const metricStatusLabels = {MET: 'Goal met', NOT_MET: 'Goal not met', UNKNOWN: 'Unknown'};
const madridDate = new Intl.DateTimeFormat('en-GB', {timeZone: 'Europe/Madrid', day: 'numeric', month: 'short', year: 'numeric'});
const madridTimestamp = new Intl.DateTimeFormat('en-GB', {timeZone: 'Europe/Madrid', day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit'});
const number = new Intl.NumberFormat('en-GB', {maximumFractionDigits: 2});

export default {
  name: 'WarningProgressCard',
  props: {warning: {type: Object, required: true}},
  computed: {
    progress() { return this.warning.progress; },
    review() { return this.progress?.metricsReview; },
    label() { return warningLabel(this.warning.type); },
    trigger_date() { return this.warning.createdAt ? madridDate.format(new Date(this.warning.createdAt)) : null; },
    goal_status() { return this.review ? this.progress.goalStatus : 'UNKNOWN'; },
    status_label() { return statusLabels[this.goal_status]; },
    status_icon() { return {MET: 'pi pi-check', NOT_MET: 'pi pi-exclamation-circle', UNKNOWN: 'pi pi-question-circle'}[this.goal_status]; },
    metric_heading() {
      const aggregations = new Set(this.review.metrics.map(metric => metric.aggregation));
      return aggregations.size === 1 ? {SUM: 'Total', MEAN: 'Daily average', COUNT: 'Count', LATEST: 'Latest'}[this.review.metrics[0].aggregation] : 'Metric';
    },
    assessment_label() {
      const end = dayjs(`${this.review.windowEnd}T00:00:00`);
      const start = end.subtract(this.review.windowDays - 1, 'day');
      const assessed = this.progress.reviewedAt ? `${madridTimestamp.format(new Date(this.progress.reviewedAt))} Europe/Madrid` : dayjs(`${this.progress.reviewedDate}T00:00:00`).format('D MMM YYYY');
      return `${start.format('D MMM YYYY')} – ${end.format('D MMM YYYY')}. Assessed ${assessed}.`;
    }
  },
  methods: {
    is_seconds(unit) { return unit.toLowerCase() === 'seconds'; },
    format_value(value, unit) {
      if (value == null) return 'Not recorded';
      if (!this.is_seconds(unit)) return number.format(value);
      const hours = Math.floor(value / 3600);
      const minutes = Math.floor(value % 3600 / 60);
      const seconds = value % 60;
      return [hours ? `${hours}h` : '', minutes ? `${minutes}m` : '', seconds || !hours && !minutes ? `${number.format(seconds)}s` : ''].filter(Boolean).join(' ');
    },
    format_goal(metric) {
      const {minimum, maximum, unit} = metric;
      if (minimum == null && maximum == null) return 'Not recorded';
      if (minimum != null && maximum != null) return `${this.format_value(minimum, unit)}–${this.format_value(maximum, unit)}`;
      return minimum != null ? `≥${this.format_value(minimum, unit)}` : `≤${this.format_value(maximum, unit)}`;
    },
    metric_status(metric) {
      if (metric.value == null || metric.coveredDays !== this.review.windowDays || metric.minimum == null && metric.maximum == null) return 'UNKNOWN';
      return (metric.minimum == null || metric.value >= metric.minimum) && (metric.maximum == null || metric.value <= metric.maximum) ? 'MET' : 'NOT_MET';
    },
    metric_accessible_label(metric) {
      return `Current ${metric.label}: ${metric.value == null ? 'Not recorded' : `${metric.value} ${metric.unit}`}. ${metricStatusLabels[this.metric_status(metric)]}. ${metric.coveredDays} of ${this.review.windowDays} days recorded.`;
    }
  }
};
</script>

<style scoped>
.warning-progress-card { min-width: 0; overflow-wrap: anywhere; }
.warning-progress-heading { display: flex; align-items: baseline; justify-content: space-between; gap: 0.5rem; flex-wrap: wrap; }
.warning-title { display: flex; align-items: baseline; gap: 0.5rem; margin: 0; font-size: 1rem; font-weight: 600; }
.warning-title i { flex-shrink: 0; }
.warning-goal-status { display: inline-flex; align-items: center; gap: 0.3rem; font-size: 0.78rem; }
.warning-goal-status i { font-size: 0.78rem; }
.warning-dates { margin: 0.45rem 0 0.75rem; color: #6c757d; font-size: 0.8rem; line-height: 1.6; }
.warning-dates p { margin: 0; }
.warning-metrics { padding: 1rem; border: 1px solid var(--surface-border, #d4d9dc); border-radius: 0.55rem; background: var(--surface-ground, #f8f9fa); }
.warning-goal-table { width: 100%; border-collapse: collapse; table-layout: fixed; font-variant-numeric: tabular-nums; }
.warning-goal-table th, .warning-goal-table td { text-align: right; vertical-align: baseline; padding: 0.4rem 0 0.4rem 0.4rem; font-weight: 400; }
.warning-goal-table thead th { font-size: 0.78rem; color: #6c757d; padding-top: 0; }
.warning-goal-table th:first-child { width: 34%; padding-left: 0; text-align: left; }
.warning-goal-table th:nth-child(2) { width: 38%; }
.warning-goal-table th:nth-child(3) { width: 28%; }
.warning-goal-table tbody th { font-size: 0.9rem; }
.warning-goal-table small { display: block; margin-top: 0.2rem; color: #6c757d; font-size: 0.75rem; }
.warning-current { font-size: 1.2rem; }
.warning-goal { font-size: 0.9rem; }
.goal-met { color: #2f855a; }
.goal-not_met { color: #c05621; }
.goal-unknown { color: #6c757d; }
.warning-short-status { margin: 0.7rem 0 0; line-height: 1.45; }
.warning-short-status:first-child { margin-top: 0; }
@media (max-width: 640px) {
  .warning-metrics { padding: 0.75rem; }
  .warning-current { font-size: 1rem; }
  .warning-goal, .warning-goal-table tbody th { font-size: 0.8rem; }
}
</style>
