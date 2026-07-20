<template>
  <Button :icon="button_icon" :label="button_label" @click="create" />
  <MoodForm :initial_date="initial_date" :mood="mood" @onSave="save" @onClose="close_modal" v-model:show="display_modal" />
</template>

<script>
import MoodForm from "@/components/MoodForm";

export default {
  name: "CreateMood",
  components: {MoodForm},
  emits: ["onSave"],
  props: {
    initial_date: Date,
    mood: Object
  },
  computed: {
    button_icon() {
      return this.mood ? 'pi pi-pencil' : 'pi pi-plus';
    },
    button_label() {
      return this.mood ? 'Edit' : 'New';
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
