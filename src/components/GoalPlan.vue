<template>
  <div class="goal-plan-page">
    <h1>Goal and plan</h1>
    <CoachingPlanSettings />
    <HealthConstraintSettings />
  </div>
</template>

<script>
import CoachingPlanSettings from './CoachingPlanSettings.vue';
import HealthConstraintSettings from './HealthConstraintSettings.vue';

export default {
  name: 'GoalPlan',
  components: {CoachingPlanSettings, HealthConstraintSettings}
};
</script>
