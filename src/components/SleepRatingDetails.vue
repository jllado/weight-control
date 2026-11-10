<template>
  <details class="sleep-rating-details">
    <summary>Why this rating?</summary>
    <p>One point per criterion across {{ days }} days. Duration goals are two of the four criteria.</p>
    <ul aria-label="Sleep rating criteria">
      <li v-for="criterion in status.criteria" :key="criterion.key">
        <strong>{{ criterion.label }}</strong>
        <span>{{ format_value(criterion.value, criterion.unit) }} · Goal {{ criterion.comparison === '>=' ? '≥' : '&lt;' }} {{ format_value(criterion.threshold, criterion.unit, true) }}</span>
        <span class="criterion-result" :class="criterion.met ? 'criterion-met' : 'criterion-missed'">{{ criterion.met ? 'Met' : 'Not met' }}</span>
      </li>
    </ul>
    <p>Efficiency is the average of each night’s sleep percentage. Consistency measures how much the midpoint between your recorded local bedtime and wake time varies; lower is more consistent.</p>
  </details>
</template>

<script>
export default {
  props: {status: {type: Object, required: true}, days: {type: Number, required: true}},
  methods: {
    format_value(value, unit, threshold = false) {
      if (unit === 's') {
        const seconds = Math.floor(value);
        return [`${Math.floor(seconds / 3600)} h`, seconds % 3600 >= 60 ? `${Math.floor(seconds % 3600 / 60)} min` : '', seconds % 60 ? `${seconds % 60} s` : ''].filter(Boolean).join(' ');
      }
      const number = threshold ? value : (Math.floor(value * 100) / 100).toFixed(2);
      return `${number}${unit === '%' ? '%' : ' min'}`;
    }
  }
};
</script>

<style scoped>
.sleep-rating-details { border-top: 1px solid #dce6ee; padding-top: .75rem; font-size: .9rem; }
summary { cursor: pointer; color: #317db6; font-weight: 600; }
summary:focus-visible { outline: 2px solid #317db6; outline-offset: 3px; }
p { color: #486277; line-height: 1.5; margin: .65rem 0; }
ul { list-style: none; padding: 0; margin: .75rem 0; }
li { display: flex; flex-wrap: wrap; gap: .3rem .75rem; padding: .5rem 0; border-bottom: 1px solid #e3e8ec; }
li strong { flex-basis: 100%; }
.criterion-result { font-weight: 600; }
.criterion-met { color: #2d6a4f; }
.criterion-missed { color: #bc4749; }
</style>
