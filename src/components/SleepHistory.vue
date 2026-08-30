<template>
  <div>
    <Panel>
      <template #header>
        <div class="table-header">
          Sleep Trend
        </div>
      </template>
      <div v-if="trend_summary" class="sleep-trend-summary">
        <div v-for="metric in trend_metrics" :key="metric.label" class="sleep-trend-summary-item">
          <div class="sleep-trend-summary-label">{{ metric.label }}</div>
          <div class="sleep-trend-summary-value">{{ metric.format(metric.value) }}</div>
          <div class="sleep-trend-summary-change" :class="trend_change_class(metric.change, metric.improves_when_increased)">
            {{ metric.formatChange(metric.change) }}
          </div>
        </div>
      </div>
      <div v-else>No sleep trend data yet.</div>
    </Panel>
    <DataTable :value="this.sleeps" :paginator="true" :rows="10" :loading="this.state.loading" responsiveLayout="scroll"
               paginatorTemplate="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
               currentPageReportTemplate="{first} to {last} of {totalRecords}">
      <template #header>
        <div class="table-header">
          Sleep
          <CreateSleep @onSave="load_sleeps" />
        </div>
      </template>
      <Column header="Date" headerStyle="width: 110px">
        <template #body="sleep">
          {{ sleep.data.dateFormat }}
        </template>
      </Column>
      <Column header="Total Sleep">
        <template #body="sleep">
          {{ sleep.data.totalSleepDurationFormat() }}
        </template>
      </Column>
      <Column header="Deep / REM / Light" headerClass="mobile-none" bodyClass="mobile-none">
        <template #body="sleep">
          {{ sleep.data.deepSleepDurationFormat() }} / {{ sleep.data.remSleepDurationFormat() }} / {{ sleep.data.lightSleepDurationFormat() }}
        </template>
      </Column>
      <Column header="Awake" headerClass="mobile-none" bodyClass="mobile-none">
        <template #body="sleep">
          {{ sleep.data.awakeTimeFormat() }}
        </template>
      </Column>
      <Column header="HR / HRV" headerClass="mobile-none" bodyClass="mobile-none">
        <template #body="sleep">
          {{ sleep.data.heartRateFormat() }} / {{ sleep.data.hrvFormat() }}
        </template>
      </Column>
      <Column header="Bedtime" headerClass="mobile-none" bodyClass="mobile-none">
        <template #body="sleep">
          {{ sleep.data.bedtimeWindowFormat() }}
        </template>
      </Column>
      <Column headerStyle="width: 100px">
        <template #body="sleep">
          <div style="width: 100px; text-align: center">
            <Button icon="pi pi-pencil" class="p-button-rounded p-button-success p-mr-2" @click="edit(sleep.data)" />
            <Button icon="pi pi-trash" class="p-button-rounded p-button-warning" @click="remove(sleep.data)" />
          </div>
        </template>
      </Column>
    </DataTable>
    <SleepForm @onSave="load_sleeps" @onClose="close_edit" v-model:show="display_edit_modal" v-model:sleep="sleep" />
  </div>
</template>

<script>
import service from '../services/SleepService';
import CreateSleep from "@/components/CreateSleep";
import SleepForm from "@/components/SleepForm";
import summaryService from "@/services/MeasuresSummaryService";
import { formatDuration } from "@/model/Sleep";
import { userState } from '../state';

export default {
  components: {CreateSleep, SleepForm},
  data() {
    return {
      sleep: null,
      sleeps: [],
      trend_summary: null,
      display_edit_modal: false,
      state: userState()
    }
  },
  computed: {
    trend_metrics() {
      if (!this.trend_summary) {
        return [];
      }
      return [
        this.duration_trend_metric('Total sleep', 'totalSleepDuration', 'lostTotalSleepDuration'),
        this.duration_trend_metric('Deep sleep', 'deepSleepDuration', 'lostDeepSleepDuration'),
        this.duration_trend_metric('REM sleep', 'remSleepDuration', 'lostRemSleepDuration'),
        this.duration_trend_metric('Light sleep', 'lightSleepDuration', 'lostLightSleepDuration'),
        this.duration_trend_metric('Awake time', 'awakeTime', 'lostAwakeTime', false),
        this.numeric_trend_metric('Average heart rate', 'averageHeartRate', 'lostAverageHeartRate', 'bpm', false),
        this.numeric_trend_metric('Average HRV', 'averageHrv', 'lostAverageHrv', 'ms')
      ];
    }
  },
  async created() {
    await this.load_sleeps();
  },
  methods: {
    async load_sleeps() {
      this.state.loading = true;
      this.sleeps = await service.get_all();
      this.trend_summary = this.sleeps.length > 0 ? summaryService.get_sleep_trend(this.sleeps) : null;
      this.state.loading = false;
    },
    formatDuration,
    duration_trend_metric(label, value_key, change_key, improves_when_increased = true) {
      return {
        label,
        value: this.trend_summary[value_key],
        change: this.trend_summary[change_key],
        improves_when_increased,
        format: formatDuration,
        formatChange: this.formatTrendDuration
      };
    },
    numeric_trend_metric(label, value_key, change_key, unit, improves_when_increased = true) {
      return {
        label,
        value: this.trend_summary[value_key],
        change: this.trend_summary[change_key],
        improves_when_increased,
        format: value => `${value} ${unit}`,
        formatChange: value => this.formatTrendMetric(value, unit)
      };
    },
    formatTrendDuration(seconds) {
      if (seconds === null || seconds === undefined) {
        return '-';
      }
      const sign = seconds > 0 ? '+' : seconds < 0 ? '-' : '';
      return `${sign}${formatDuration(Math.abs(seconds))}`;
    },
    formatTrendMetric(value, unit) {
      if (value === null || value === undefined) {
        return '-';
      }
      const sign = value > 0 ? '+' : value < 0 ? '-' : '';
      return `${sign}${Math.abs(value)} ${unit}`;
    },
    trend_change_class(change, improves_when_increased) {
      if (change === 0) {
        return '';
      }
      return (change > 0) === improves_when_increased ? 'positive' : 'negative';
    },
    async remove(sleep) {
      if (!confirm('Are you sure you want to delete this?')) {
        return;
      }
      service.delete(sleep)
          .then(() => {
            this.load_sleeps();
          })
          .catch(e => {
            this.handle_error(e)
          });
    },
    async edit(sleep) {
      this.sleep = Object.assign({}, sleep);
      this.display_edit_modal = true;
    },
    close_edit() {
      this.display_edit_modal = false;
    },
    handle_error(e) {
      this.$log.error(e);
      this.$toast.add({severity:'error', summary: 'Failed', detail: e, life: 3000});
    }
  }
}
</script>

<style scoped>
.sleep-trend-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 1rem;
  margin-bottom: 1rem;
}

.sleep-trend-summary-item {
  min-width: 0;
}

.sleep-trend-summary-label {
  color: #6b7280;
  font-size: 0.9rem;
}

.sleep-trend-summary-value {
  font-size: 1.2rem;
  font-weight: 600;
}

.sleep-trend-summary-change {
  font-size: 0.9rem;
}

.positive {
  color: #2d6a4f;
}

.negative {
  color: #bc4749;
}
</style>
