<template>
  <Button :icon="button_icon" :label="button_label" @click="create" />
  <BloodPressureForm :initial_date="initial_date" :blood_pressure="blood_pressure" :fixed_date="fixed_date" @onSave="save" @onClose="close_modal" v-model:show="display_modal" />
</template>

<script>
import BloodPressureForm from "@/components/BloodPressureForm";

export default {
  name: "CreateWeight",
  components: {BloodPressureForm},
  emits: ["onSave"],
  props: {
    initial_date: Date,
    blood_pressure: Object,
    fixed_date: Boolean
  },
  computed: {
    button_icon() {
      return this.blood_pressure ? 'pi pi-pencil' : 'pi pi-plus';
    },
    button_label() {
      return this.blood_pressure ? 'Edit' : 'New';
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
