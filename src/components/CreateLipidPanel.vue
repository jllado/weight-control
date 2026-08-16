<template>
  <Button :icon="button_icon" :label="button_label" @click="create" />
  <LipidPanelForm :lipid_panel="lipid_panel" @onSave="save" @onClose="close_modal" v-model:show="display_modal" />
</template>

<script>
import LipidPanelForm from '@/components/LipidPanelForm';

export default {
  name: 'CreateLipidPanel',
  components: {LipidPanelForm},
  emits: ['onSave'],
  props: {
    lipid_panel: Object
  },
  computed: {
    button_icon() {
      return this.lipid_panel ? 'pi pi-pencil' : 'pi pi-plus';
    },
    button_label() {
      return this.lipid_panel ? 'Edit' : 'New';
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
