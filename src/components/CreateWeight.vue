<template>
  <Button :icon="button_icon" :label="button_label" @click="create" />
  <WeightForm :initial_date="initial_date" :weight="weight" :fixed_date="fixed_date" @onSave="save" @onClose="close_modal" v-model:show="display_modal" />
</template>

<script>
import WeightForm from "@/components/WeightForm";

export default {
  name: "CreateWeight",
  components: {WeightForm},
  emits: ["onSave"],
  props: {
    initial_date: Date,
    weight: Object,
    fixed_date: Boolean
  },
  computed: {
    button_icon() {
      return this.weight ? 'pi pi-pencil' : 'pi pi-plus';
    },
    button_label() {
      return this.weight ? 'Edit' : 'New';
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
