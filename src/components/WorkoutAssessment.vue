<template>
  <RatingDetails title="Training day assessment" :label="`Training day assessment, ${date}: goal ${assessment.goalAlignmentScore}, demand ${assessment.estimatedTrainingDemandScore}`">
    <template #score>
      <template v-if="compact">Goal {{ assessment.goalAlignmentScore }} · Demand {{ assessment.estimatedTrainingDemandScore }}</template>
      <template v-else>Goal alignment: <strong>{{ assessment.goalAlignmentScore }}/10</strong> · Training demand: <strong>{{ assessment.estimatedTrainingDemandScore }}/10</strong></template>
    </template>
    <p><strong>Training day:</strong> {{ date }}</p>
    <p><strong>Goal:</strong> {{ assessment.goalSnapshot }}</p>
    <p><strong>Goal alignment:</strong> {{ assessment.goalAlignmentScore }}/10</p>
    <p><strong>Estimated training demand:</strong> {{ assessment.estimatedTrainingDemandScore }}/10</p>
    <p><strong>{{ assessment.automatic ? 'Plan alignment' : 'Rationale' }}:</strong> {{ assessment.rationale }}</p>
    <p><strong>{{ assessment.automatic ? 'Recorded training' : 'Strength' }}:</strong> {{ assessment.strength }}</p>
    <p><strong>{{ assessment.automatic ? 'Prior comparison' : 'Improvement' }}:</strong> {{ assessment.improvement }}</p>
    <p><strong>Next workout:</strong> {{ assessment.nextWorkoutAction }}</p>
  </RatingDetails>
</template>

<script>
import RatingDetails from './RatingDetails.vue';

export default {
  components: {RatingDetails},
  props: {assessment: {type: Object, required: true}, date: {type: String, required: true}, compact: Boolean}
};
</script>
