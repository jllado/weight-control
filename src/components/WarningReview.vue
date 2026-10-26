<template>
  <section class="warning-review" aria-label="Warning progress">
    <h3>Warning progress</h3>
    <p v-if="!review" class="warning-review-empty">Not recorded</p>
    <p v-else-if="!review.items.length" class="warning-review-empty">No active warnings to review on {{ review_date }}</p>
    <div v-else class="warning-progress-grid">
      <WarningProgressCard v-for="warning in review.items" :key="warning.id" :warning="warning" />
    </div>
  </section>
</template>

<script>
import dayjs from 'dayjs';
import WarningProgressCard from './WarningProgressCard.vue';

export default {
  name: 'WarningReview',
  components: {WarningProgressCard},
  props: {review: {type: Object, default: null}},
  computed: {
    review_date() { return dayjs(`${this.review.reviewedDate}T00:00:00`).format('D MMM YYYY'); }
  }
};
</script>

<style scoped>
.warning-review { margin-top: 1rem; }
.warning-review > h3 { margin: 0; font-size: 1.1rem; }
.warning-review-empty { margin: 0.45rem 0 0; color: var(--text-color-secondary, #6c757d); line-height: 1.45; }
.warning-progress-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1.5rem 0.75rem; margin-top: 0.75rem; }
@media (max-width: 760px) { .warning-progress-grid { grid-template-columns: minmax(0, 1fr); } }
</style>
