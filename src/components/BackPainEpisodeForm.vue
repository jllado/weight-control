<template>
  <Dialog id="back-pain-episode-form" appendTo="body" header="Back Pain Episode" v-model:visible="display_modal" :closeOnEscape="false" :closable="false" :modal="true" :style="{width: '42rem'}" :breakpoints="{'960px': '75vw', '640px': '95vw'}" data-toggle="validator" ref="form">
    <p class="back-pain-date"><strong>Date:</strong> {{ date_label }}</p>
    <p class="back-pain-help">Choose where you feel pain. Left and right refer to your body.</p>
    <div class="back-pain-location-grid" role="group" aria-label="Pain location">
      <div></div>
      <div v-for="side in sides" :key="side.value" class="back-pain-column-label">{{ side.label }}</div>
      <template v-for="region in regions" :key="region.value">
        <div class="back-pain-row-label">{{ region.label }}</div>
        <button v-for="side in sides" :key="side.value" type="button" class="back-pain-location" :class="{selected: is_selected(region.value, side.value)}" :aria-pressed="is_selected(region.value, side.value)" @click="select_location(region.value, side.value)">
          {{ region.label }} {{ side.label }}
        </button>
      </template>
    </div>
    <span v-if="vv.region.$error || vv.side.$error" class="error">Choose one pain location.</span>
    <Message v-if="has_unknown_location" severity="warn" :closable="false">This migrated entry has no recorded side. Choose an exact location before saving.</Message>
    <div class="back-pain-field">
      <label for="pain">Pain (1–10)</label>
      <InputNumber id="pain" v-model="vv.pain.$model" :min="1" :max="10" :showButtons="true" />
      <span class="error">{{ vv.pain?.$errors[0]?.$message }}</span>
    </div>
    <div class="back-pain-field">
      <span class="p-float-label">
        <InputText id="note" v-model="vv.note.$model" maxlength="500" />
        <label for="note">Note (optional)</label>
      </span>
      <span class="error">{{ vv.note?.$errors[0]?.$message }}</span>
    </div>
    <template #footer>
      <Button label="Save" icon="pi pi-check" @click="save" />
      <Button label="Cancel" icon="pi pi-times" @click="close_modal" class="p-button-secondary" />
    </template>
  </Dialog>
</template>

<script>
import {reactive} from 'vue';
import {useVuelidate} from '@vuelidate/core';
import {maxLength, maxValue, minValue, required} from '@vuelidate/validators';
import dayjs from 'dayjs';
import service from '../services/BackPainEpisodeService';
import BackPainEpisode, {BACK_REGIONS, BACK_SIDES} from '@/model/BackPainEpisode';

export default {
  name: 'BackPainEpisodeForm',
  emits: ['onSave', 'onClose'],
  props: {
    show: Boolean,
    episode: Object,
    initial_date: Date
  },
  data() {
    const fform = reactive({
      date: this.initial_date || new Date(),
      region: null,
      side: null,
      pain: null,
      note: ''
    });
    const rules = {
      date: {required},
      region: {required},
      side: {required},
      pain: {required, minValue: minValue(1), maxValue: maxValue(10)},
      note: {maxLength: maxLength(500)}
    };
    return {
      vv: useVuelidate(rules, fform),
      fform,
      regions: BACK_REGIONS,
      sides: BACK_SIDES,
      display_modal: this.show
    };
  },
  computed: {
    date_label() {
      return dayjs(this.fform.date).format('DD/MM/YYYY');
    },
    has_unknown_location() {
      return Boolean(this.episode && !this.episode.side && !this.fform.side);
    }
  },
  watch: {
    show(value) {
      this.display_modal = value;
      if (value) {
        this.load_form();
      }
    },
    episode() {
      if (this.display_modal) {
        this.load_form();
      }
    },
    initial_date() {
      if (this.display_modal && !this.episode) {
        this.load_form();
      }
    }
  },
  methods: {
    load_form() {
      if (this.episode) {
        this.vv.date.$model = this.episode.date;
        this.vv.region.$model = this.episode.region;
        this.vv.side.$model = this.episode.side;
        this.vv.pain.$model = this.episode.pain;
        this.vv.note.$model = this.episode.note || '';
        this.vv.$reset();
        return;
      }
      this.clear();
    },
    clear() {
      this.vv.date.$model = this.initial_date || new Date();
      this.vv.region.$model = null;
      this.vv.side.$model = null;
      this.vv.pain.$model = null;
      this.vv.note.$model = '';
      this.vv.$reset();
    },
    is_selected(region, side) {
      return this.fform.region === region && this.fform.side === side;
    },
    select_location(region, side) {
      this.vv.region.$model = region;
      this.vv.side.$model = side;
    },
    async save() {
      this.vv.$touch();
      if (this.vv.$invalid) {
        return;
      }
      const episode = new BackPainEpisode();
      episode.id = this.episode ? this.episode.id : null;
      episode.date = this.vv.date.$model;
      episode.region = this.vv.region.$model;
      episode.side = this.vv.side.$model;
      episode.pain = this.vv.pain.$model;
      episode.note = this.vv.note.$model || null;
      await service.save(episode.toObject())
          .then(() => {
            this.$emit('onSave');
            this.$toast.add({severity: 'success', summary: 'Back pain episode saved', life: 3000});
            this.close_modal();
          })
          .catch(e => this.handle_error(e));
    },
    close_modal() {
      this.clear();
      this.$emit('onClose');
    },
    handle_error(e) {
      this.$log.error(e);
      this.$toast.add({severity: 'error', summary: 'Failed', detail: e, life: 3000});
    }
  }
};
</script>

<style scoped>
.back-pain-date {
  margin: 0 0 0.5rem;
}
.back-pain-help {
  margin: 0 0 1rem;
  color: #666;
}
.back-pain-location-grid {
  display: grid;
  grid-template-columns: 5rem repeat(3, minmax(0, 1fr));
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}
.back-pain-column-label,
.back-pain-row-label {
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
}
.back-pain-location {
  min-height: 4rem;
  padding: 0.5rem;
  border: 1px solid #b8c2cc;
  border-radius: 0.4rem;
  color: #333;
  background: #fff;
  cursor: pointer;
}
.back-pain-location:hover {
  border-color: #2196f3;
  background: #f3f9fe;
}
.back-pain-location.selected {
  border: 2px solid #1976d2;
  color: #0d47a1;
  background: #e3f2fd;
  font-weight: 700;
}
.back-pain-field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  margin-top: 1.5rem;
}
.back-pain-field .p-inputtext {
  width: 100%;
}
@media (max-width: 640px) {
  .back-pain-location-grid {
    grid-template-columns: 4rem repeat(3, minmax(0, 1fr));
    gap: 0.3rem;
  }
  .back-pain-location {
    min-height: 4.5rem;
    padding: 0.25rem;
    font-size: 0.75rem;
  }
}
</style>
