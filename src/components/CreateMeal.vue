<template>
  <CompactAction v-if="meal" :icon="button_icon" :aria-label="button_label" @click="create" />
  <Button v-else :icon="button_icon" :label="button_label" @click="create" />
</template>

<script>
import dayjs from 'dayjs';

export default {
  name: "CreateMeal",
  emits: ["onSave"],
  props: {
    initial_date: Date,
    meal: Object,
    meals: {
      type: Array,
      default: () => []
    },
    fasting_periods: {
      type: Array,
      default: () => []
    },
    fixed_date: Boolean
  },
  computed: {
    button_icon() {
      return this.meal ? 'pi pi-pencil' : 'pi pi-plus';
    },
    button_label() {
      return this.meal ? 'Edit' : 'New';
    }
  },
  methods: {
    create() {
      this.$router.push({path: this.meal ? `/meals/${this.meal.id}/edit` : '/meals/new', query: {from: this.fixed_date ? 'dashboard' : 'history', ...(this.initial_date ? {date: dayjs(this.initial_date).format('YYYY-MM-DD')} : {})}});
    }
  }
}
</script>
