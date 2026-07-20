<template>
  <Button :icon="button_icon" :label="button_label" @click="create" />
  <SleepForm :initial_date="initial_date" :sleep="sleep" @onSave="save" @onClose="close_modal" v-model:show="display_modal" />
</template>

<script>
import SleepForm from "@/components/SleepForm";

export default {
  name: "CreateSleep",
  components: {SleepForm},
  emits: ["onSave"],
  props: {
    initial_date: Date,
    sleep: Object
  },
  computed: {
    button_icon() {
      return this.sleep ? 'pi pi-pencil' : 'pi pi-plus';
    },
    button_label() {
      return this.sleep ? 'Edit' : 'New';
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
