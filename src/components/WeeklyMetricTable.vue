<template>
  <div class="metrics-table" :class="{'metrics-table--compact': compactCurrent}" role="table" :aria-label="label">
    <div class="metrics-row metrics-row--heading" role="row"><span role="columnheader">{{ compactCurrent ? 'Nutrient' : 'Metric' }}</span><template v-if="compactCurrent"><span role="columnheader">Daily average</span><span role="columnheader">Target</span><span role="columnheader">Status</span></template><span v-else role="columnheader">{{ periods[0].label }}<small>{{ periods[0].dates }}</small></span></div>
    <div role="rowgroup" :aria-label="group || undefined">
      <div v-for="row in rows" :key="row.label" class="metrics-row" role="row">
        <strong role="rowheader">{{ row.label }}<small v-if="row.note">{{ row.note }}</small></strong>
        <template v-if="compactCurrent">
          <div role="cell" data-period="Daily average">
            <span class="metric-value" :style="{color: row.cells[0].status?.color}">{{ row.cells[0].value }}<span v-if="row.cells[0].unit" class="metric-unit"> {{ ' ' + row.cells[0].unit }}</span></span>
            <small v-if="row.cells[0].detail">{{ row.cells[0].detail }}</small>
          </div>
          <div role="cell" data-period="Target"><slot name="target" :row="row">{{ row.cells[0].target || 'Not available' }}</slot></div>
          <div role="cell" data-period="Status"><span class="metric-status" :style="{color: row.cells[0].status?.color}">{{ row.cells[0].status?.label || 'Not available' }}</span></div>
        </template>
        <div v-else role="cell" :data-period="periods[0].description">
          <div v-for="(cell, index) in row.cells" :key="index" role="group" :aria-label="periods[index].description" class="metric-period" :class="{'metric-period--comparison': index > 0}">
            <div v-if="index" class="metric-comparison-line"><span v-if="cell.delta" class="metric-delta" :style="{color: cell.delta.color}">{{ cell.delta.text }} vs </span><span>{{ cell.delta ? periods[index].label.toLowerCase() : periods[index].label }} · </span><span class="metric-baseline-value" :style="{color: cell.status?.color}">{{ cell.value }}<span v-if="cell.unit"> {{ ' ' + cell.unit }}</span></span></div>
            <span v-else class="metric-value" :style="{color: cell.status?.color}">{{ cell.value }}<span v-if="cell.unit" class="metric-unit"> {{ ' ' + cell.unit }}</span></span>
            <small v-if="index" class="metric-period-dates">{{ periods[index].dates }}</small>
            <div v-if="cell.status || cell.target || cell.detail" class="metric-evidence"><small v-if="cell.status" class="metric-status" :style="{color: cell.status.color}">{{ cell.status.label }}</small><small v-if="cell.target">{{ cell.target }}</small><small v-if="cell.detail">{{ cell.detail }}</small></div>
            <slot name="cell" :row="row" :index="index" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
<script>
export default {props: {label: {type: String, required: true}, periods: {type: Array, required: true}, rows: {type: Array, required: true}, group: {type: String, default: ''}, compactCurrent: {type: Boolean, default: false}}};
</script>
<style scoped>
.metrics-table { width: 100%; border: 1px solid #dce6ee; border-radius: 4px; overflow: hidden; }
.metrics-row { display: grid; grid-template-columns: minmax(120px, 1fr) minmax(0, 3fr); border-bottom: 1px solid #e3e8ec; }
.metrics-table--compact .metrics-row { grid-template-columns: minmax(120px, 1fr) repeat(3, minmax(0, 1fr)); }
.metrics-row > * { min-width: 0; padding: .8rem .55rem; line-height: 1.4; overflow-wrap: anywhere; }
.metrics-row--heading { background: #eef5fb; color: #63717b; font-size: .8rem; font-weight: 600; }
.metrics-row small { display: block; color: #63717b; font-size: .76rem; font-weight: 400; margin-top: .2rem; }
.metric-value { font-size: 1.02rem; font-variant-numeric: tabular-nums; font-weight: 700; }
.metric-unit { font-size: .8rem; font-weight: 400; }
.metrics-row .metric-status { display: inline-block; width: fit-content; padding: .15rem .4rem; border-radius: 3px; font-size: .75rem; font-weight: 600; background: color-mix(in srgb, currentColor 9%, white); }
.metrics-row [role="cell"]:nth-child(2) { background: #f7faff; }
[role="rowgroup"] .metrics-row:last-child { border-bottom: 0; }
.metric-period--comparison { margin-top: .65rem; }
.metric-comparison-line { font-size: .84rem; color: #63717b; font-variant-numeric: tabular-nums; }
.metric-evidence { display: flex; flex-wrap: wrap; align-items: center; gap: .15rem .65rem; }
@media (max-width: 760px) {
  .metrics-row--heading { display: none; }
  .metrics-row { margin: 0 .6rem; grid-template-columns: minmax(0, 1fr); gap: .3rem .6rem; padding: .5rem 0; }
  .metrics-row > * { padding: .3rem .25rem; }
  .metrics-table--compact .metrics-row:not(.metrics-row--heading) { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .3rem .5rem; }
  .metrics-table--compact .metrics-row > strong, .metrics-table--compact .metrics-row > :last-child { grid-column: 1 / -1; }
  .metrics-table--compact .metrics-row > :last-child::before { display: inline; content: 'Status: '; }
  .metrics-row > [role="cell"]::before { content: attr(data-period); display: block; color: #63717b; font-size: .72rem; margin-bottom: .2rem; }
}
</style>
