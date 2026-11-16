<template>
  <div class="meal-preload-option"><strong>{{ option.title }}</strong><div><small>{{ option.dateFormat }}</small><span>{{ option.calories.toLocaleString('en-US') }} kcal</span></div></div>
</template>
<script>
export default {props: {option: {type: Object, required: true}}};
</script>
<style scoped>
.meal-preload-option { display: flex; flex-direction: column; gap: .35rem; white-space: normal; overflow-wrap: anywhere; min-width: 0; width: 100%; }
.meal-preload-option > div { display: flex; justify-content: space-between; align-items: baseline; gap: 1rem; }
.meal-preload-option span { white-space: nowrap; }
</style>
