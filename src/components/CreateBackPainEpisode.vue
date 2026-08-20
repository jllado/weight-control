<template>
  <Button :icon="button_icon" :label="button_label" @click="create" />
  <BackPainEpisodeForm :initial_date="initial_date" :period="period" :episode="episode" :fixed_date="fixed_date" @onSave="save" @onSaveAndContinue="save_and_continue" @onClose="close_modal" v-model:show="display_modal" />
</template>

<script>
import BackPainEpisodeForm from '@/components/BackPainEpisodeForm';

export default {
  name: 'CreateBackPainEpisode',
  components: {BackPainEpisodeForm},
  emits: ['onSave'],
  props: {
    initial_date: Date,
    period: String,
    episode: Object,
    fixed_date: Boolean
  },
  computed: {
    button_icon() {
      return this.episode ? 'pi pi-pencil' : 'pi pi-plus';
    },
    button_label() {
      return this.episode ? 'Edit' : 'Add Episode';
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
    save_and_continue() {
      this.$emit('onSave');
    },
    close_modal() {
      this.display_modal = false;
    }
  }
};
</script>
