<template>
  <div>
    <div class="p-grid p-mt-1" >
      <div class="p-col-4 p-text-right">
        <RadioButton id="photo_type1" name="photo_type" value="left" v-model="photo_type" />
        <label for="photo_type1" class="p-ml-1">Left</label>
      </div>
      <div class="p-col-4 p-text-center">
        <RadioButton id="photo_type2" name="photo_type" value="front" v-model="photo_type" />
        <label for="photo_type3" class="p-ml-1">Front</label>
      </div>
      <div class="p-col-4 p-text-left">
        <RadioButton id="photo_type3" name="photo_type" value="right" v-model="photo_type"  />
        <label for="photo_type3" class="p-ml-1">Right</label>
      </div>
    </div>
    <div class="center">
      <carousel :items-to-show="1.5" :transition="500" >
        <slide v-for="photo in this.photos" :key="photo">
          <div style="width: 450px">
            <div class="center">
              {{ photo.weight.dateFormat }}
            </div>
            <div class="center">
              {{ photo.weight.weight }} kg ({{ photo.weight.fat_percentage }}%)
            </div>
            <div>
              <img :src="photo.photo_left" style="width: 100%"  v-if="photo_type == 'left'" />
              <img :src="photo.photo_front" style="width: 100%" v-if="photo_type == 'front'" />
              <img :src="photo.photo_right" style="width: 100%" v-if="photo_type == 'right'" />
            </div>
          </div>
        </slide>

        <template #addons>
          <navigation />
        </template>
      </carousel>
    </div>

  </div>
</template>

<script>
import 'vue3-carousel/dist/carousel.css'
import {Carousel, Navigation, Slide} from 'vue3-carousel'
import service from '../services/WeightService';
import {userState} from '../state';

export default {
  components: {
    Carousel,
    Slide,
    Navigation,
  },
  data() {
    return {
      photos: [],
      photo_type: 'front',
      state: userState()
    }
  },
  async created () {
    await this.load_photos();
    this.photos.forEach( photo => {
      console.log(photo);
    })
  },
  methods: {
    async get_photo_list() {
      return await service.get_all_photos_by(this.state.user.mail);
    },
    async load_photos() {
      this.state.loading = true;
      this.photos = await this.get_photo_list();
      this.state.loading = false;
    }
  }
}
</script>