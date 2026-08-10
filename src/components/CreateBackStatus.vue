<template>
  <Button :icon="button_icon" :label="button_label" @click="create" />
  <BackStatusForm :initial_date="initial_date" :back_status="back_status" @onSave="save" @onClose="close_modal" v-model:show="display_modal" />
</template>

<script>
import BackStatusForm from "@/components/BackStatusForm";

export default {
  name: "CreateBackStatus",
  components: {BackStatusForm},
  emits: ["onSave"],
  props: {
    initial_date: Date,
    back_status: Object
  },
  computed: {
    button_icon() {
      return this.back_status ? 'pi pi-pencil' : 'pi pi-plus';
    },
    button_label() {
      return this.back_status ? 'Edit' : 'New';
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
};
</script>
