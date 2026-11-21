<template>
  <article class="heart-rate-warning" aria-label="Overnight heart rate warning">
    <h3><i class="pi pi-exclamation-triangle" aria-hidden="true"></i> Overnight heart rate</h3>
    <section v-for="reason in reasons" :key="reason.code" class="heart-rate-reason">
      <h4>{{ reason.label }}</h4>
      <p class="warning-date">Active since {{ date(reason.since) }}</p>
      <ul>
        <li v-for="row in reason.evidence" :key="row.label + row.from">
          <strong>{{ range(row.from, row.through) }} · {{ bpm(row.bpm) }} bpm</strong>
          <div v-if="reason.code === 'PERSONAL_LIMIT'">
            Personal alert threshold {{ bpm(row.thresholdBpm) }} bpm, effective {{ date(row.referenceFrom) }}.
          </div>
          <div v-else-if="reason.code === 'RECENT_ELEVATION'">
            Prior median {{ bpm(row.referenceBpm) }} bpm ({{ range(row.referenceFrom, row.referenceThrough) }}).
            Alert above {{ bpm(row.thresholdBpm) }} bpm.
          </div>
          <template v-else>
            <div>{{ row.label }} · {{ row.readings.length }} recorded nights<span v-if="row.thresholdBpm != null"> · alert at {{ bpm(row.thresholdBpm) }} bpm or higher</span></div>
            <details>
              <summary>Nightly readings</summary>
              <ul><li v-for="night in row.readings" :key="night.date">{{ date(night.date) }} · {{ bpm(night.bpm) }} bpm</li></ul>
            </details>
          </template>
        </li>
      </ul>
    </section>
  </article>
</template>

<script>
import dayjs from 'dayjs';

const rate = new Intl.NumberFormat('en-GB', {maximumFractionDigits: 3});

export default {
  props: {reasons: {type: Array, required: true}},
  methods: {
    date(value) { return dayjs(value).format('DD/MM/YYYY'); },
    range(from, through) { return from === through ? this.date(from) : this.date(from) + ' – ' + this.date(through); },
    bpm(value) { return rate.format(value); }
  }
};
</script>

<style scoped>
.heart-rate-warning { min-width: 0; overflow-wrap: anywhere; }
.heart-rate-warning h3 { margin: 0; font-size: 1rem; font-weight: 600; }
.heart-rate-warning h3 i { margin-right: 0.3rem; }
.heart-rate-reason { margin-top: 1rem; }
.heart-rate-reason h4 { margin: 0; font-size: 0.95rem; }
.warning-date { margin: 0.25rem 0; color: inherit; }
.heart-rate-reason ul { display: grid; gap: 0.5rem; margin: 0.5rem 0; padding-left: 1.25rem; }
.heart-rate-reason details { margin-top: 0.25rem; }
.heart-rate-reason summary { cursor: pointer; }
</style>
