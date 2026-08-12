<template>
  <Button :icon="button_icon" :label="button_label" @click="create" />
  <CalorieForm :initial_date="initial_date" :calorie="calorie" :fixed_date="fixed_date" @onSave="save" @onClose="close_modal" v-model:show="display_modal" />
</template>

<script>
import CalorieForm from "@/components/CalorieForm";

export default {
  name: "CreateCalorie",
  components: {CalorieForm},
  emits: ["onSave"],
  props: {
    initial_date: Date,
    calorie: Object,
    fixed_date: Boolean
  },
  computed: {
    button_icon() {
      return this.calorie ? 'pi pi-pencil' : 'pi pi-plus';
    },
    button_label() {
      return this.calorie ? 'Edit' : 'New';
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
