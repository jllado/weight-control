<template><figure><div class="chart"><canvas ref="canvas" role="img" aria-label="Weight decreased from 81 to 80 kilograms across three measurements" /></div><figcaption>Weight: 81 → 80.5 → 80 kg</figcaption></figure></template>
<script>
import Chart from 'chart.js/auto';
export default {
  mounted() {
    this.chart = new Chart(this.$refs.canvas, {type: 'line', data: {labels: ['Sep 8', 'Sep 9', 'Sep 10'], datasets: [{label: 'Weight (kg)', data: [81, 80.5, 80], borderColor: '#007ad9', backgroundColor: '#007ad9'}]}, options: {responsive: true, maintainAspectRatio: false, animation: false}});
  },
  beforeUnmount() { this.chart.destroy(); }
};
</script>
<style scoped>
figure { margin: 0; } .chart { height: 220px; position: relative; } figcaption { margin-top: .5rem; }
</style>
