<template>
  <section class="back-pain-trend-summary" aria-label="Back pain trends">
    <div v-for="row in trends.rows" :key="row.value" class="back-pain-trend-row p-grid">
      <div class="p-col-5">Trend {{ row.label }}:</div>
      <div class="p-col-7 back-pain-trend-result">
        <span class="trend-average">{{ number(row.current.average) }}<template v-if="row.current.average !== null"> / 4</template></span>
        <span class="trend-change" :class="changeClass(row.delta)" aria-hidden="true">{{ delta(row.delta) }}</span>
        <span class="sr-only">{{ description(row) }}</span>
      </div>
    </div>
  </section>
</template>

<script>
import {buildBackPainTrends, backPainTrendNumber, backPainTrendDelta} from '../model/BackPainTrends';

export default {
  props: {episodes: {type: Array, required: true}, date: {type: [String, Date], required: true}},
  computed: {
    trends() { return buildBackPainTrends(this.episodes, this.date); }
  },
  methods: {
    number: backPainTrendNumber,
    delta: backPainTrendDelta,
    changeClass(delta) { return delta === null || this.number(delta) === '0' ? '' : delta < 0 ? 'good' : 'bad'; },
    description(row) {
      const {start, end, previousStart, previousEnd} = this.trends;
      return `Current 30 days, ${start} to ${end}: ${row.current.days} observed ${row.current.days === 1 ? 'day' : 'days'}. Previous 30 days, ${previousStart} to ${previousEnd}: ${row.previous.days} observed ${row.previous.days === 1 ? 'day' : 'days'}. Change versus previous 30 days: ${row.delta === null ? 'unavailable' : this.delta(row.delta)}.`;
    }
  }
};
</script>

<style scoped>
.back-pain-trend-summary { margin-bottom: 1rem; }
.back-pain-trend-row { margin: 0 -.5rem; }
.back-pain-trend-row > * { overflow-wrap: anywhere; min-width: 0; }
.back-pain-trend-result { padding-inline-end: .75rem; }
.trend-change { margin-inline-start: .25rem; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
</style>
