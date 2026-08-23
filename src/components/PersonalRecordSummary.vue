<template>
  <div v-if="records.length" class="personal-record-summary">
    <strong class="personal-record-summary__heading">All-time Records</strong>
    <div v-if="layout === 'table'" class="personal-record-summary__table-wrapper">
      <table class="personal-record-summary__table" aria-label="All-time records">
        <colgroup>
          <col class="personal-record-summary__record-column">
          <col class="personal-record-summary__value-column">
          <col class="personal-record-summary__date-column">
        </colgroup>
        <thead>
          <tr>
            <th scope="col">Record</th>
            <th scope="col">Value</th>
            <th scope="col">Date</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="record in records" :key="record.metric">
            <th scope="row">{{ record.metricLabel }}</th>
            <td><strong>{{ formatRecordValue(record) }}</strong></td>
            <td>{{ record.recordDate || 'Legacy baseline' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-else v-for="record in records" :key="record.metric" class="personal-record-summary__row">
      <span>{{ record.metricLabel }}</span>
      <span><strong>{{ formatRecordValue(record) }}</strong> · {{ record.recordDate || 'Legacy baseline' }}</span>
    </div>
  </div>
</template>

<script>
import {formatRecordValue} from '@/services/PersonalRecordService';

export default {
  name: 'PersonalRecordSummary',
  props: {
    records: {type: Array, required: true},
    layout: {type: String, default: 'compact'}
  },
  methods: {formatRecordValue}
};
</script>

<style scoped>
.personal-record-summary { margin-top: 1rem; border-top: 1px solid #dee2e6; padding-top: 0.75rem; }
.personal-record-summary__heading { display: block; margin-bottom: 0.35rem; }
.personal-record-summary__row { display: flex; justify-content: space-between; gap: 1rem; padding: 0.2rem 0; }
.personal-record-summary__row span:last-child { text-align: right; }
.personal-record-summary__table { width: 100%; border-collapse: collapse; table-layout: fixed; }
.personal-record-summary__record-column { width: 46%; }
.personal-record-summary__value-column { width: 31%; }
.personal-record-summary__date-column { width: 23%; }
.personal-record-summary__table th, .personal-record-summary__table td { overflow-wrap: anywhere; padding: 0.45rem 0.5rem; text-align: left; vertical-align: top; }
.personal-record-summary__table thead th { border-bottom: 2px solid #dee2e6; color: #495057; font-size: 0.875rem; }
.personal-record-summary__table tbody th { font-weight: 400; }
.personal-record-summary__table tbody td { border-bottom: 1px solid #dee2e6; }
.personal-record-summary__table tbody tr:last-child th, .personal-record-summary__table tbody tr:last-child td { border-bottom: 0; }
</style>
