<template>
  <Button :icon="button_icon" :label="button_label" @click="create" />
  <WorkoutForm :initial_date="initial_date" :workout="workout" :workouts="workouts" @onSave="save" @onClose="close_modal" v-model:show="display_modal" />
</template>

<script>
import WorkoutForm from "@/components/WorkoutForm";

export default {
  name: "CreateWorkout",
  components: {WorkoutForm},
  emits: ["onSave"],
  props: {
    initial_date: Date,
    workout: Object,
    workouts: {
      type: Array,
      default: () => []
    }
  },
  computed: {
    button_icon() {
      return this.workout ? 'pi pi-pencil' : 'pi pi-plus';
    },
    button_label() {
      return this.workout ? 'Edit' : 'New';
    }
  },
  data() {
    return {
      display_modal: false
    };
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
