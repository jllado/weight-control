<template>
  <Button :icon="button_icon" :label="button_label" @click="create" />
  <MealForm :initial_date="initial_date" :meal="meal" :meals="meals" :fixed_date="fixed_date" @onSave="save" @onClose="close_modal" v-model:show="display_modal" />
</template>

<script>
import MealForm from "@/components/MealForm";

export default {
  name: "CreateMeal",
  components: {MealForm},
  emits: ["onSave"],
  props: {
    initial_date: Date,
    meal: Object,
    meals: {
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
  data() {
    return {
      display_modal: false
    }
  },
  methods: {
    create() {
      this.display_modal = true;
    },
    save() {
      this.$emit('onSave');
      this.close_modal();
    },
    close_modal() {
      this.display_modal = false;
    }
  }
}
</script>
