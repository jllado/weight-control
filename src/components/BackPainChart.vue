<template>
  <section class="back-pain-chart" aria-label="Back pain chart">
    <div class="back-pain-chart-filters">
      <div class="back-pain-chart-field">
        <label id="back-pain-chart-area-label" for="back-pain-chart-area">Back area</label>
        <Dropdown inputId="back-pain-chart-area" aria-labelledby="back-pain-chart-area-label" v-model="area" :options="areas" optionLabel="label" optionValue="value" />
      </div>
      <div class="back-pain-chart-field">
        <label id="back-pain-chart-period-label" for="back-pain-chart-period">Time of day</label>
        <Dropdown inputId="back-pain-chart-period" aria-labelledby="back-pain-chart-period-label" v-model="period" :options="periods" optionLabel="label" optionValue="value" />
      </div>
    </div>
    <p v-if="loading" role="status">Loading back check-ins…</p>
    <p v-else-if="error" role="alert">{{ error }} <Button label="Retry" class="p-button-text" @click="$emit('retry')" /></p>
    <template v-else>
      <template v-if="chart.hasData">
        <div class="back-pain-chart-canvas">
          <Chart type="line" :data="chart.data" :options="chart.options" :canvasProps="{role: 'img', 'aria-label': 'Daily average back pain', 'aria-describedby': 'back-pain-chart-description'}" />
        </div>
        <p id="back-pain-chart-description" class="sr-only">{{ chart.description }}</p>
      </template>
      <p v-else>No back check-ins for these filters in the selected period.</p>
      <p v-if="chartType !== 'all' && !chart.hasPrevious">No previous-year data for these filters.</p>
    </template>
  </section>
</template>

<script>
import {BACK_PAIN_AREAS} from '../model/BackPainDaily';
import {getMoodPeriodOptions} from '../model/Mood';
import {buildBackPainChart} from '../model/BackPainChart';

export default {
  props: {
    episodes: {type: Array, required: true}, chartType: {type: String, required: true}, today: {type: String, required: true},
    loading: {type: Boolean, required: true}, error: {type: String, required: true}
  },
  emits: ['retry'],
  data() {
    return {
      area: 'ALL', period: 'ALL',
      areas: [{label: 'All', value: 'ALL'}, ...BACK_PAIN_AREAS],
      periods: [{label: 'All', value: 'ALL'}, ...getMoodPeriodOptions()]
    };
  },
  computed: {
    chart() { return buildBackPainChart(this.episodes, {chartType: this.chartType, today: this.today, area: this.area, period: this.period}); }
  }
};
</script>

<style scoped>
.back-pain-chart { min-width: 0; }
.back-pain-chart-filters { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .5rem; margin-bottom: 1rem; text-align: left; }
.back-pain-chart-field { display: flex; flex-direction: column; gap: .5rem; min-width: 0; }
.back-pain-chart-canvas { height: 16rem; position: relative; }
.back-pain-chart-canvas :deep(.p-chart) { width: 100%; height: 100%; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
@media (max-width: 640px) {
  .back-pain-chart-filters { grid-template-columns: minmax(0, 1fr); }
}
</style>
