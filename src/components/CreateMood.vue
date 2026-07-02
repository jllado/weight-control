<template>
  <Button icon="pi pi-plus" label="New" @click="create" />
  <MoodForm :initial_date="initial_date" @onSave="save" @onClose="close_modal" v-model:show="display_modal" />
</template>

<script>
import MoodForm from "@/components/MoodForm";

export default {
  name: "CreateMood",
  components: {MoodForm},
  emits: ["onSave"],
  props: {
    initial_date: Date
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
