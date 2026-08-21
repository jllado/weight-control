<template>
  <span v-if="badges.length" class="workout-record-badges">
    <span v-for="badge in badges" :key="badge.kind" class="workout-record-badge" :class="`workout-record-badge--${badge.kind.toLowerCase()}`" :title="badge.title">{{ badge.label }}</span>
  </span>
</template>

<script>
export default {
  name: 'WorkoutRecordBadges',
  props: {
    events: {
      type: Array,
      default: () => []
    }
  },
  computed: {
    badges() {
      const recordEvents = this.events.filter(event => event.kind === 'FIRST' || event.kind === 'IMPROVED');
      const tiedEvents = this.events.filter(event => event.kind === 'TIED');
      return [
        recordEvents.length ? {kind: 'PR', label: 'PR', title: recordEvents.map(event => event.metricLabel).join(', ')} : null,
        tiedEvents.length ? {kind: 'TIED', label: 'Tied PR', title: tiedEvents.map(event => event.metricLabel).join(', ')} : null
      ].filter(Boolean);
    }
  }
}
</script>

<style scoped>
.workout-record-badges {
  display: inline-flex;
  gap: 0.25rem;
  margin-left: 0.4rem;
}
.workout-record-badge {
  border-radius: 999px;
  padding: 0.1rem 0.4rem;
  font-size: 0.72rem;
  font-weight: 700;
}
.workout-record-badge--pr {
  color: #075f46;
  background: #d1fae5;
}
.workout-record-badge--tied {
  color: #7c4a03;
  background: #fef3c7;
}
</style>
