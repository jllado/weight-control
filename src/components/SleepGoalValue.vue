<template>
  <span v-if="goal_met === null">Not recorded</span>
  <span v-else class="sleep-goal-result" :class="goal_met ? 'sleep-goal-met' : 'sleep-goal-missed'">{{ value }}<template v-if="deficit">{{ ' ' }}<span role="note" :aria-label="deficit_label">{{ deficit }}</span></template></span>
</template>

<script>
import Sleep from '@/model/Sleep';

export default {
  props: {
    sleep: {type: Sleep, default: null},
    inBed: Boolean
  },
  computed: {
    goal_met() {
      if (!this.sleep) return null;
      return this.inBed ? this.sleep.meetsTimeInBedGoal() : this.sleep.meetsTotalSleepGoal();
    },
    value() {
      return this.inBed ? this.sleep.totalBedtimeFormat() : this.sleep.totalSleepDurationFormat();
    },
    deficit() {
      return this.inBed ? this.sleep.timeInBedGoalDeficitFormat() : this.sleep.totalSleepGoalDeficitFormat();
    },
    deficit_label() {
      return this.inBed ? this.sleep.timeInBedGoalDeficitAccessibleLabel() : this.sleep.totalSleepGoalDeficitAccessibleLabel();
    }
  }
};
</script>

<style scoped>
.sleep-goal-result { font-weight: 600; }
.sleep-goal-met { color: #2d6a4f; }
.sleep-goal-missed { color: #bc4749; }
</style>
