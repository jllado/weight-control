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
          <Column header="Date"><template #body="record">{{ recordDateLabel(record.data) }}</template></Column>
        </DataTable>
      </TabPanel>
      <TabPanel header="History">
        <DataTable :value="history.items" :loading="loading_history" :lazy="true" :paginator="true" :rows="history.size" :first="history.page * history.size" :totalRecords="history.totalElements" @page="historyPageChanged" responsiveLayout="scroll">
          <template #empty>No record history matches these filters.</template>
          <Column header="Date"><template #body="event">{{ recordDateLabel(event.data) }}</template></Column>
          <Column header="Subject"><template #body="event">{{ event.data.subject.label }}</template></Column>
          <Column header="Record" field="metricLabel" />
          <Column header="Load"><template #body="event">{{ event.data.qualifier?.label || '—' }}</template></Column>
          <Column header="Result"><template #body="event"><span class="history-kind" :class="`history-kind--${event.data.kind.toLowerCase()}`">{{ eventKindLabel(event.data.kind) }}</span></template></Column>
          <Column header="Value"><template #body="event"><strong>{{ formatRecordValue(event.data) }}</strong></template></Column>
        </DataTable>
      </TabPanel>
      <TabPanel header="Settings">
        <p>Choose which numerical extremes appear as personal records. Minimum and maximum are observations, not health judgments.</p>
        <div v-for="group in catalog_groups" :key="group.domain" class="record-settings-group">
          <h3>{{ domainLabel(group.domain) }}</h3>
          <div v-for="metric in group.metrics" :key="metric.key" class="record-setting-row">
            <div>
              <label :for="`record-setting-${metric.key}`"><strong>{{ metric.label }}</strong></label>
              <small>{{ unitLabel(metric.unit) }} · default: {{ modeLabel(metric.defaultMode) }}</small>
            </div>
            <Dropdown :id="`record-setting-${metric.key}`" v-model="settings[metric.key]" :options="mode_options" optionLabel="label" optionValue="value" />
          </div>
        </div>
        <div class="record-settings-actions">
          <Button label="Save" icon="pi pi-check" @click="saveSettings" :loading="saving_settings" :disabled="!settings_changed" />
          <Button label="Reset to defaults" icon="pi pi-refresh" class="p-button-outlined" @click="resetSettings" :disabled="defaults_selected" />
        </div>
      </TabPanel>
    </TabView>
  </div>
</template>

<script>
import personalRecordService, {formatRecordValue} from '../services/PersonalRecordService';
import exerciseService from '../services/WorkoutExerciseService';

export default {
  name: 'PersonalRecords',
  data() {
    return {
      filters: {domain: null, metric: null, exerciseId: null},
      domain_options: [{label: 'Body', value: 'BODY'}, {label: 'Workout', value: 'WORKOUT'}, {label: 'Vitals', value: 'VITALS'}, {label: 'Recovery', value: 'RECOVERY'}, {label: 'Nutrition', value: 'NUTRITION'}, {label: 'Behavior', value: 'BEHAVIOR'}],
      exercises: [],
      catalog: [],
      settings: {},
      saved_settings: {},
      saving_settings: false,
      mode_options: ['DISABLED', 'MINIMUM', 'MAXIMUM', 'BOTH'].map(value => ({value, label: value.charAt(0) + value.slice(1).toLowerCase()})),
      current_records: [],
      history: {items: [], page: 0, size: 25, totalElements: 0, totalPages: 0},
      loading_current: false,
      loading_history: false
    };
  },
  computed: {
    metric_options() {
      return this.catalog.filter(metric => !this.filters.domain || metric.domain === this.filters.domain)
        .flatMap(metric => metric.directions.map(direction => ({value: direction.metric, label: direction.label})));
    },
    catalog_groups() {
      return this.domain_options.map(domain => ({domain: domain.value, metrics: this.catalog.filter(metric => metric.domain === domain.value)}));
    },
    settings_changed() {
      return JSON.stringify(this.settings) !== JSON.stringify(this.saved_settings);
    },
    defaults_selected() {
      return this.catalog.every(metric => this.settings[metric.key] === metric.defaultMode);
    }
  },
  async created() {
    const [exercises, catalog] = await Promise.all([exerciseService.get_all(), personalRecordService.getCatalog()]);
    this.exercises = exercises;
    this.setCatalog(catalog);
    await this.loadRecords();
  },
  methods: {
    formatRecordValue,
    recordDateLabel(record) {
      return record.recordDate || 'Legacy baseline';
    },
    eventKindLabel(kind) {
      return kind === 'TIED' ? 'Tied PR' : 'PR';
    },
    domainLabel(domain) {
      return this.domain_options.find(option => option.value === domain).label;
    },
    modeLabel(mode) {
      return this.mode_options.find(option => option.value === mode).label;
    },
    unitLabel(unit) {
      return {KG: 'kg', PERCENT: '%', REPETITIONS: 'repetitions', SECONDS: 'seconds', KM_PER_HOUR: 'km/h', KM: 'km', LEVEL: 'level', MM_HG: 'mm Hg', MG_PER_DL: 'mg/dL', KCAL: 'kcal', GRAMS: 'g', BPM: 'bpm', MILLISECONDS: 'ms', SCORE_OUT_OF_FIVE: 'score out of 5', COMPLETIONS: 'completions', DAYS: 'days', DECISIONS: 'decisions'}[unit];
    },
    setCatalog(catalog) {
      this.catalog = catalog;
      this.settings = Object.fromEntries(catalog.map(metric => [metric.key, metric.mode]));
      this.saved_settings = {...this.settings};
    },
    resetSettings() {
      this.settings = Object.fromEntries(this.catalog.map(metric => [metric.key, metric.defaultMode]));
    },
    async saveSettings() {
      this.saving_settings = true;
      try {
        const overrides = this.catalog.filter(metric => this.settings[metric.key] !== metric.defaultMode)
          .map(metric => ({metric: metric.key, mode: this.settings[metric.key]}));
        this.setCatalog(await personalRecordService.replaceSettings(overrides));
        this.filters.metric = null;
        this.history.page = 0;
        await this.loadRecords();
        this.$toast.add({severity: 'success', summary: 'Saved', detail: 'Personal record settings updated', life: 3000});
      } catch (e) {
        this.$log.error(e);
        this.$toast.add({severity: 'error', summary: 'Failed', detail: e, life: 3000});
      } finally {
        this.saving_settings = false;
      }
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
.record-settings-group {
  margin: 1.5rem 0;
}
.record-setting-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 12rem;
  align-items: center;
  gap: 1rem;
  border-bottom: 1px solid #dee2e6;
  padding: 0.65rem 0;
}
.record-setting-row small {
  display: block;
  margin-top: 0.2rem;
  color: #6c757d;
}
.record-settings-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
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
  .record-setting-row {
    grid-template-columns: 1fr;
  }
}
</style>
