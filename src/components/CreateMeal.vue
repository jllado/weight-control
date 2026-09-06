<template>
  <Button :icon="button_icon" :label="icon_only ? null : button_label" :aria-label="button_label" :class="{'p-button-rounded p-button-sm': icon_only}" @click="create" />
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
    fixed_date: Boolean,
    icon_only: Boolean
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
