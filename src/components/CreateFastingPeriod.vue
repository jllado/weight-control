<template>
  <Button icon="pi pi-plus" label="New" @click="create" />
  <FastingPeriodForm v-model:show="display_modal" @onSave="save" @onClose="close_modal" />
</template>

<script>
import FastingPeriodForm from '@/components/FastingPeriodForm';

export default {
  name: 'CreateFastingPeriod',
  components: {FastingPeriodForm},
  emits: ['onSave'],
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
