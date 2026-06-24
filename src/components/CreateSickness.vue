<template>
  <Button icon="pi pi-plus" label="New" @click="create" />
  <SicknessForm @onSave="save" @onClose="close_modal" v-model:show="display_modal" />
</template>

<script>
import SicknessForm from "@/components/SicknessForm";

export default {
  name: "CreateSickness",
  components: {SicknessForm},
  emits: ["onSave"],
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
