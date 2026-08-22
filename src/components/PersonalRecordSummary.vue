<template>
  <div v-if="records.length" class="personal-record-summary">
    <strong class="personal-record-summary__heading">All-time Records</strong>
    <div v-for="record in records" :key="record.metric" class="personal-record-summary__row">
      <span>{{ record.metricLabel }}</span>
      <span><strong>{{ formatRecordValue(record) }}</strong> · {{ record.recordDate }}</span>
    </div>
  </div>
</template>

<script>
import {formatRecordValue} from '@/services/PersonalRecordService';

export default {
  name: 'PersonalRecordSummary',
  props: {records: {type: Array, required: true}},
  methods: {formatRecordValue}
};
</script>

<style scoped>
.personal-record-summary { margin-top: 1rem; border-top: 1px solid #dee2e6; padding-top: 0.75rem; }
.personal-record-summary__heading { display: block; margin-bottom: 0.35rem; }
.personal-record-summary__row { display: flex; justify-content: space-between; gap: 1rem; padding: 0.2rem 0; }
.personal-record-summary__row span:last-child { text-align: right; }
</style>
