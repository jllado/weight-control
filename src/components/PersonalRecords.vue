<template>
  <div class="personal-records-page">
    <div class="personal-record-filters p-mb-3">
      <Dropdown v-model="filters.domain" :options="domain_options" optionLabel="label" optionValue="value" placeholder="All domains" showClear @change="filtersChanged" />
      <Dropdown v-model="filters.metric" :options="metric_options" optionLabel="label" optionValue="value" placeholder="All metrics" showClear @change="filtersChanged" />
      <Dropdown v-model="filters.exerciseId" :options="exercises" optionLabel="name" optionValue="id" placeholder="All exercises" showClear @change="filtersChanged" />
    </div>
    <TabView>
      <TabPanel header="Current">
        <DataTable :value="current_records" :loading="loading_current" responsiveLayout="scroll" rowGroupMode="subheader" groupRowsBy="groupLabel" sortField="groupLabel" :sortOrder="1">
          <template #groupheader="record"><strong>{{ record.data.groupLabel }}</strong></template>
          <template #empty>No current records match these filters.</template>
          <Column header="Record" field="metricLabel" />
          <Column header="Load"><template #body="record">{{ record.data.qualifier?.label || '—' }}</template></Column>
          <Column header="Value"><template #body="record"><strong>{{ formatRecordValue(record.data) }}</strong></template></Column>
          <Column header="Date" field="recordDate" />
        </DataTable>
      </TabPanel>
      <TabPanel header="History">
        <DataTable :value="history.items" :loading="loading_history" :lazy="true" :paginator="true" :rows="history.size" :first="history.page * history.size" :totalRecords="history.totalElements" @page="historyPageChanged" responsiveLayout="scroll">
          <template #empty>No record history matches these filters.</template>
          <Column header="Date" field="recordDate" />
          <Column header="Subject"><template #body="event">{{ event.data.subject.label }}</template></Column>
          <Column header="Record" field="metricLabel" />
          <Column header="Load"><template #body="event">{{ event.data.qualifier?.label || '—' }}</template></Column>
          <Column header="Result"><template #body="event"><span class="history-kind" :class="`history-kind--${event.data.kind.toLowerCase()}`">{{ eventKindLabel(event.data.kind) }}</span></template></Column>
          <Column header="Value"><template #body="event"><strong>{{ formatRecordValue(event.data) }}</strong></template></Column>
        </DataTable>
      </TabPanel>
    </TabView>
  </div>
</template>

<script>
import personalRecordService, {formatRecordValue} from '../services/PersonalRecordService';
import exerciseService from '../services/WorkoutExerciseService';

const METRICS = [
  ['BODY_WEIGHT', 'Lowest weight', 'BODY'], ['BODY_FAT_MASS', 'Lowest fat mass', 'BODY'], ['BODY_FAT_PERCENTAGE', 'Lowest fat percentage', 'BODY'],
  ['BODY_MUSCLE_MASS', 'Highest muscle mass', 'BODY'], ['BODY_MUSCLE_PERCENTAGE', 'Highest muscle percentage', 'BODY'],
  ['WORKOUT_HEAVIEST_LOAD', 'Heaviest load', 'WORKOUT'], ['WORKOUT_REPETITIONS', 'Most repetitions', 'WORKOUT'], ['WORKOUT_DURATION', 'Longest duration', 'WORKOUT'],
  ['CARDIO_DURATION', 'Longest interval', 'WORKOUT'], ['CARDIO_SPEED', 'Highest speed', 'WORKOUT'], ['CARDIO_DISTANCE', 'Longest distance', 'WORKOUT'],
  ['CARDIO_INCLINE', 'Highest incline', 'WORKOUT'], ['CARDIO_RESISTANCE', 'Highest resistance', 'WORKOUT'],
  ['BLOOD_PRESSURE_SYSTOLIC_MINIMUM', 'Lowest systolic pressure', 'VITALS'], ['BLOOD_PRESSURE_SYSTOLIC_MAXIMUM', 'Highest systolic pressure', 'VITALS'],
  ['BLOOD_PRESSURE_DIASTOLIC_MINIMUM', 'Lowest diastolic pressure', 'VITALS'], ['BLOOD_PRESSURE_DIASTOLIC_MAXIMUM', 'Highest diastolic pressure', 'VITALS'],
  ['LIPID_TOTAL_CHOLESTEROL_MINIMUM', 'Lowest total cholesterol', 'VITALS'], ['LIPID_HDL_MAXIMUM', 'Highest HDL', 'VITALS'], ['LIPID_LDL_MINIMUM', 'Lowest LDL', 'VITALS'], ['LIPID_TRIGLYCERIDES_MINIMUM', 'Lowest triglycerides', 'VITALS'],
  ['MOOD_MAXIMUM', 'Highest mood', 'RECOVERY'], ['SLEEP_TOTAL_DURATION_MAXIMUM', 'Longest total sleep', 'RECOVERY'], ['SLEEP_DEEP_DURATION_MAXIMUM', 'Longest deep sleep', 'RECOVERY'],
  ['SLEEP_REM_DURATION_MAXIMUM', 'Longest REM sleep', 'RECOVERY'], ['SLEEP_LIGHT_DURATION_MAXIMUM', 'Longest light sleep', 'RECOVERY'], ['SLEEP_AWAKE_TIME_MINIMUM', 'Shortest awake time', 'RECOVERY'],
  ['SLEEP_AVERAGE_HEART_RATE_MINIMUM', 'Lowest sleep heart rate', 'RECOVERY'], ['SLEEP_AVERAGE_HRV_MAXIMUM', 'Highest HRV', 'RECOVERY'],
  ...['CALORIES', 'PROTEIN', 'CARBOHYDRATES', 'FAT'].flatMap(nutrient => ['MINIMUM', 'MAXIMUM'].flatMap(direction => [
    [`MEAL_${nutrient}_${direction}`, `${direction === 'MINIMUM' ? 'Lowest' : 'Highest'} meal ${nutrient.toLowerCase()}`, 'NUTRITION'],
    [`DAILY_${nutrient}_${direction}`, `${direction === 'MINIMUM' ? 'Lowest' : 'Highest'} daily ${nutrient.toLowerCase()}`, 'NUTRITION']
  ]))
];

export default {
  name: 'PersonalRecords',
  data() {
    return {
      filters: {domain: null, metric: null, exerciseId: null},
      domain_options: [{label: 'Body', value: 'BODY'}, {label: 'Workout', value: 'WORKOUT'}, {label: 'Vitals', value: 'VITALS'}, {label: 'Recovery', value: 'RECOVERY'}, {label: 'Nutrition', value: 'NUTRITION'}],
      exercises: [],
      current_records: [],
      history: {items: [], page: 0, size: 25, totalElements: 0, totalPages: 0},
      loading_current: false,
      loading_history: false
    };
  },
  computed: {
    metric_options() {
      return METRICS.filter(([, , domain]) => !this.filters.domain || domain === this.filters.domain).map(([value, label]) => ({value, label}));
    }
  },
  async created() {
    this.exercises = await exerciseService.get_all();
    await this.loadRecords();
  },
  methods: {
    formatRecordValue,
    eventKindLabel(kind) {
      return kind === 'TIED' ? 'Tied PR' : 'PR';
    },
    requestFilters() {
      return {...this.filters};
    },
    async filtersChanged() {
      if (this.filters.metric && !this.metric_options.some(option => option.value === this.filters.metric)) {
        this.filters.metric = null;
      }
      this.history.page = 0;
      await this.loadRecords();
    },
    async loadRecords() {
      await Promise.all([this.loadCurrent(), this.loadHistory()]);
    },
    async loadCurrent() {
      this.loading_current = true;
      const records = await personalRecordService.getCurrent(this.requestFilters());
      this.current_records = records.map(record => ({...record, groupLabel: record.subject.label}));
      this.loading_current = false;
    },
    async loadHistory() {
      this.loading_history = true;
      this.history = await personalRecordService.getHistory({...this.requestFilters(), page: this.history.page, size: this.history.size});
      this.loading_history = false;
    },
    async historyPageChanged(event) {
      this.history.page = event.page;
      this.history.size = event.rows;
      await this.loadHistory();
    }
  }
}
</script>

<style scoped>
.personal-records-page {
  padding: 0 1rem 1rem;
}
.personal-record-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}
.personal-record-filters > * {
  min-width: 12rem;
}
.history-kind {
  border-radius: 999px;
  padding: 0.15rem 0.5rem;
  font-weight: 700;
}
.history-kind--first, .history-kind--improved {
  color: #075f46;
  background: #d1fae5;
}
.history-kind--tied {
  color: #7c4a03;
  background: #fef3c7;
}
@media (max-width: 575px) {
  .personal-record-filters > * {
    width: 100%;
  }
}
</style>
