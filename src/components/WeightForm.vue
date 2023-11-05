<template>
  <Dialog id="weight-form" appendTo="body" header="Weight" v-model:visible="display_modal" :closeOnEscape="false" :closable="false" :modal="true" data-toggle="validator" ref="form">
    <br>
    <div class="p-flex-row p-pb-5">
        <span class="p-float-label">
            <Calendar v-model="vv.date.$model" dateFormat="dd/mm/yy" appendTo="body" v-model:locale="custom_locale" />
            <label for="weight">Date</label>
        </span>
      <span class="error">{{ vv.date?.$errors[0]?.$message }}</span>
    </div>
    <div class="p-flex-row p-pb-5">
        <span class="p-float-label">
            <InputNumber id="weight" v-model="vv.weight.$model" mode="decimal" :minFractionDigits="2" :maxFractionDigits="2" />
            <label for="weight">Weight</label>
            kg
        </span>
      <span class="error">{{ vv.weight?.$errors[0]?.$message }}</span>
    </div>
    <div class="p-flex-row p-pb-5">
        <span class="p-float-label">
            <InputNumber id="fat-percentage" v-model="vv.fat_percentage.$model" mode="decimal" :minFractionDigits="2" :maxFractionDigits="2" />
            <label for="fat-percentage">Fat</label>
            %
        </span>
      <span class="error">{{ vv.fat_percentage?.$errors[0]?.$message }}</span>
    </div>
    <div class="p-flex-row p-pb-5">
        <span class="p-float-label">
            <InputNumber id="muscle" v-model="vv.muscle.$model" mode="decimal" :minFractionDigits="2" :maxFractionDigits="2" />
            <label for="muscle">Muscle</label>
            kg
        </span>
      <span class="error">{{ vv.muscle?.$errors[0]?.$message }}</span>
    </div>
    <div class="p-flex-row p-pb-5"  >
    </div>
    <div class="p-flex-row p-pb-5" >
      <ProgressBar :value="this.uploadPhotoFrontProgress" v-if="this.uploadPhotoFrontProgress > 0" />
      <FileUpload choose-label="Choose Front Photo" mode="basic" accept="image/*" :auto="true" :customUpload="true" @uploader="upload_photo_front" :disabled="this.isUploadingPhoto()" v-if="!vv.photo_front.$model && !this.uploadPhotoFrontProgress" />
      <a :href="vv.photo_front.$model" target="_blank" v-if="vv.photo_front.$model" ><img :src="vv.photo_front.$model" style="width: 50px; height: 50px" /> Front Photo</a>
    </div>
    <div class="p-flex-row p-pb-5" >
      <ProgressBar :value="this.uploadPhotoRightProgress" v-if="this.uploadPhotoRightProgress > 0" />
      <FileUpload choose-label="Choose Right Photo" mode="basic" accept="image/*" :auto="true" :customUpload="true" @uploader="upload_photo_right" :disabled="this.isUploadingPhoto()" v-if="!vv.photo_right.$model && !this.uploadPhotoRightProgress" />
       <a :href="vv.photo_right.$model" target="_blank" v-if="vv.photo_right.$model" ><img :src="vv.photo_right.$model" style="width: 50px; height: 50px" /> Right Photo</a>
    </div>
    <div class="p-flex-row p-pb-5" >
      <ProgressBar :value="this.uploadPhotoLeftProgress" v-if="this.uploadPhotoLeftProgress > 0" />
      <FileUpload choose-label="Choose Left Photo" mode="basic" accept="image/*" :auto="true" :customUpload="true" @uploader="upload_photo_left" :disabled="this.isUploadingPhoto()" v-if="!vv.photo_left.$model && !this.uploadPhotoLeftProgress" />
       <a :href="vv.photo_left.$model" target="_blank" v-if="vv.photo_left.$model" ><img :src="vv.photo_left.$model" style="width: 50px; height: 50px" /> Left Photo</a>
    </div>
    <template #footer>
      <Button label="Save" icon="pi pi-check" @click="save" :disabled="this.isUploadingPhoto()" />
      <Button label="Cancel" icon="pi pi-times" @click="close_modal" class="p-button-secondary" />
    </template>
  </Dialog>
</template>

<script>
import service from '../services/WeightService';
import Weight from '../model/Weight';
import { reactive, toRef } from "vue";
import { useVuelidate } from "@vuelidate/core";
import { required } from "@vuelidate/validators";
import { userState } from '../state';
import weightService from "../services/WeightService";

export default {
  name: "WeightForm",
  emits: ["onSave", "onClose"],
  props: {
    show: Boolean,
    weight: Object
  },
  data() {
    const locale = {
      firstDayOfWeek: 1,
      dayNames: ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"],
      dayNamesShort: ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"],
      dayNamesMin: ["Su","Mo","Tu","We","Th","Fr","Sa"],
      monthNames: [ "January","February","March","April","May","June","July","August","September","October","November","December" ],
      monthNamesShort: [ "Jan", "Feb", "Mar", "Apr", "May", "Jun","Jul", "Aug", "Sep", "Oct", "Nov", "Dec" ],
      today: 'Today',
      clear: 'Clear',
      dateFormat: 'mm/dd/yy',
      weekHeader: 'Wk'
    };
    const fform = reactive({
      date: new Date(),
      weight: null,
      fat_percentage: null,
      muscle: null,
      photo_front: null,
      photo_right: null,
      photo_left: null
    });
    const rules = {
      date: {},
      weight: { required },
      fat_percentage: { required },
      muscle: { required },
      photo_front: {},
      photo_right: {},
      photo_left: {}
    };
    const vv = useVuelidate(rules, {
      date: toRef(fform, "date"),
      weight: toRef(fform, "weight"),
      fat_percentage: toRef(fform, "fat_percentage"),
      muscle: toRef(fform, "muscle"),
      photo_front: toRef(fform, "photo_front"),
      photo_right: toRef(fform, "photo_right"),
      photo_left: toRef(fform, "photo_left")
    });
    return {
      vv,
      fform,
      custom_locale: locale,
      state: userState(),
      display_modal: this.show,
      uploadPhotoTask: undefined,
      uploadPhotoFrontProgress: undefined,
      uploadPhotoRightProgress: undefined,
      uploadPhotoLeftProgress: undefined,
    }
  },
  updated() {
    this.display_modal = this.show;
    if (this.weight) {
      this.vv.date.$model = this.weight.date;
      this.vv.weight.$model = this.weight.weight;
      this.vv.fat_percentage.$model = this.weight.fat_percentage;
      this.vv.muscle.$model = this.weight.muscle;
      this.vv.photo_front.$model = this.weight.photo_front;
      this.vv.photo_right.$model = this.weight.photo_right;
      this.vv.photo_left.$model = this.weight.photo_left;
    }
  },
  methods: {
    clear() {
      this.vv.date.$model = new Date();
      this.vv.weight.$model = null;
      this.vv.fat_percentage.$model = null;
      this.vv.muscle.$model = null;
      this.vv.photo_front.$model = null;
      this.vv.photo_right.$model = null;
      this.vv.photo_left.$model = null;
      this.vv.$reset();
    },
    async save() {
      this.vv.$touch();
      if (this.vv.$invalid) {
        return;
      }
      let weight_id = this.weight ? this.weight.id : null;
      let user = this.state.user.mail;
      let previous_weight = await get_previous_weight(this.weight, user);
      await service.save(build_weight(this.vv, weight_id, user, previous_weight))
          .then(() => {
            this.$emit('onSave');
            this.$toast.add({severity:'success', summary: 'Weight saved', life: 3000});
            this.close_modal();
          })
          .catch(e => {
            this.handle_error(e)
          });
      this.clear();

      async function get_previous_weight(weight, user) {
        if (weight) {
          return service.get_previous(weight.date, user);
        }
        return service.get_last(user);
      }

      function build_weight(vv, id, user, previous_weight) {
        let weight = new Weight()
        weight.id = id;
        weight.user = user;
        weight.date = vv.date.$model;
        weight.weight = vv.weight.$model;
        weight.fat_percentage = vv.fat_percentage.$model;
        weight.fat = (vv.fat_percentage.$model * vv.weight.$model) / 100;
        weight.muscle = vv.muscle.$model;
        weight.muscle_percentage = (vv.muscle.$model * 100) / vv.weight.$model;
        weight.photo_front = vv.photo_front.$model;
        weight.photo_right = vv.photo_right.$model;
        weight.photo_left = vv.photo_left.$model;
        weight.load_lost(previous_weight)
        return weight.toObject();
      }
    },
    close_modal() {
      this.clear();
      this.$emit('onClose');
    },
    set_photo_front(downloadURL) {
      this.vv.photo_front.$model = downloadURL;
    },
    set_photo_right(downloadURL) {
      this.vv.photo_right.$model = downloadURL;
    },
   set_photo_left(downloadURL) {
      this.vv.photo_left.$model = downloadURL;
    },
    set_photo_front_progress(progress) {
      this.uploadPhotoFrontProgress = progress;
    },
    set_photo_right_progress(progress) {
      this.uploadPhotoRightProgress = progress;
    },
   set_photo_left_progress(progress) {
      this.uploadPhotoLeftProgress = progress;
    },
    upload_photo_front(event) {
      this.upload_photo(event.files[0], this.set_photo_front, this.set_photo_front_progress);
    },
    upload_photo_right(event) {
      this.upload_photo(event.files[0], this.set_photo_right, this.set_photo_right_progress);
    },
    upload_photo_left(event) {
      this.upload_photo(event.files[0], this.set_photo_left, this.set_photo_left_progress);
    },
    isUploadingPhoto() {
      return this.uploadPhotoFrontProgress || this.uploadPhotoRightProgress || this.uploadPhotoLeftProgress;
    },
    upload_photo(file, set_photo, set_photo_progress) {
      this.uploadPhotoTask = weightService.upload_image(file);
      set_photo_progress(0);
      this.uploadPhotoTask.on('state_changed',
          sp => {
            set_photo_progress(Math.floor(sp.bytesTransferred / sp.totalBytes * 100));
          },
          null,
          () => {
            this.uploadPhotoTask.snapshot.ref.getDownloadURL().then(downloadURL => {
              set_photo(downloadURL);
            });
            set_photo_progress(undefined);
            this.uploadPhotoTask = undefined;
            this.$toast.add({severity:'success', summary: 'Photo uploaded', life: 2000});
          }
      );
    },
    handle_error(e) {
      this.$log.error(e);
      this.$toast.add({severity:'error', summary: 'Failed', detail: e, life: 3000});
    }
  }
}
</script>
