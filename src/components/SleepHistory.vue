<template>
  <div>
    <Panel>
      <template #header>
        <div class="table-header">
          Sleep Trend
        </div>
      </template>
      <div v-if="trend_summary" class="sleep-trend-summary">
        <div class="sleep-trend-summary-item">
          <div class="sleep-trend-summary-label">Latest 30 days avg</div>
          <div class="sleep-trend-summary-value">{{ formatDuration(trend_summary.totalSleepDuration) }}</div>
        </div>
        <div class="sleep-trend-summary-item">
          <div class="sleep-trend-summary-label">Change</div>
          <div class="sleep-trend-summary-value" :class="trend_change_class">
            {{ formatTrendDuration(trend_summary.lostTotalSleepDuration) }}
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
    trend_change_class() {
      if (!this.trend_summary || this.trend_summary.lostTotalSleepDuration === 0) {
        return '';
      }
      return this.trend_summary.lostTotalSleepDuration > 0 ? 'positive' : 'negative';
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
    formatTrendDuration(seconds) {
      if (seconds === null || seconds === undefined) {
        return '-';
      }
      const sign = seconds > 0 ? '+' : seconds < 0 ? '-' : '';
      return `${sign}${formatDuration(Math.abs(seconds))}`;
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
  display: flex;
  gap: 1rem;
  margin-bottom: 1rem;
  flex-wrap: wrap;
}

.sleep-trend-summary-item {
  min-width: 180px;
}

.sleep-trend-summary-label {
  color: #6b7280;
  font-size: 0.9rem;
}

.sleep-trend-summary-value {
  font-size: 1.2rem;
  font-weight: 600;
}

.positive {
  color: #2d6a4f;
}

.negative {
  color: #bc4749;
}
</style>
