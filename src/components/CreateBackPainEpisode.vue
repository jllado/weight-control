<template>
  <Button :icon="button_icon" :label="button_label" @click="create" />
  <BackPainEpisodeForm :initial_date="initial_date" :episode="episode" :fixed_date="fixed_date" @onSave="save" @onClose="close_modal" v-model:show="display_modal" />
</template>

<script>
import BackPainEpisodeForm from '@/components/BackPainEpisodeForm';

export default {
  name: 'CreateBackPainEpisode',
  components: {BackPainEpisodeForm},
  emits: ['onSave'],
  props: {
    initial_date: Date,
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
    close_modal() {
      this.display_modal = false;
    }
  }
};
</script>
