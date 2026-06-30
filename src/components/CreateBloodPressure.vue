<template>
  <Button icon="pi pi-plus" label="New" @click="create" />
  <BloodPressureForm :initial_date="initial_date" @onSave="save" @onClose="close_modal" v-model:show="display_modal" />
</template>

<script>
import BloodPressureForm from "@/components/BloodPressureForm";

export default {
  name: "CreateWeight",
  components: {BloodPressureForm},
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
