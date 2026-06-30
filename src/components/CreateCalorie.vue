<template>
  <Button icon="pi pi-plus" label="New" @click="create" />
  <CalorieForm :initial_date="initial_date" @onSave="save" @onClose="close_modal" v-model:show="display_modal" />
</template>

<script>
import CalorieForm from "@/components/CalorieForm";

export default {
  name: "CreateCalorie",
  components: {CalorieForm},
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
