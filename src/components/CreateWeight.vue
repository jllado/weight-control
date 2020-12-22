<template>
  <Button icon="pi pi-plus" label="New" @click="create" />
  <WeightForm @onSave="save" @onClose="close_modal" v-model:show="display_modal" />
</template>

<script>
export default {
  name: "CreateWeight",
  emits: ["onSave"],
  data() {
    return {
      display_modal: false,
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
