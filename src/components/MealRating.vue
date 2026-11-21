<template>
  <RatingDetails v-if="meal.ratingExplanation" title="Meal rating" :label="`${meal.label()} rating details: ${meal.rating}/10`">
    <template #score>{{ meal.rating }}/10</template>
    <p><strong>{{ meal.label() }} · {{ meal.dateFormat }} · {{ meal.rating }}/10</strong></p>
    <p>{{ meal.ratingExplanation }}</p>
  </RatingDetails>
  <span v-else>{{ meal.rating === null ? emptyLabel : `${meal.rating}/10` }}</span>
</template>

<script>
import RatingDetails from './RatingDetails.vue';

export default {
  components: {RatingDetails},
  props: {meal: {type: Object, required: true}, emptyLabel: {type: String, default: 'Not rated'}}
};
</script>
