<template>
  <RatingDetails v-if="meal.ratingExplanation" title="Meal rating" :label="`${meal.label()} rating details: ${scoreLabel}`">
    <template #score>{{ scoreLabel }}</template>
    <p><strong>{{ meal.label() }} · {{ meal.dateFormat }} · {{ scoreLabel }}</strong></p>
    <p v-for="(line, index) in meal.ratingExplanation.split('\n')" :key="index">{{ line }}</p>
  </RatingDetails>
  <span v-else>{{ meal.rating === null ? emptyLabel : scoreLabel }}</span>
</template>

<script>
import RatingDetails from './RatingDetails.vue';

export default {
  components: {RatingDetails},
  props: {meal: {type: Object, required: true}, emptyLabel: {type: String, default: 'Not rated'}},
  computed: {
    scoreLabel() { return this.meal.rating === null ? 'Not rated' : `${this.meal.rating}/10`; }
  }
};
</script>
