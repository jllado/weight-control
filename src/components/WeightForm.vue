<template>
  <Dialog id="weight-form" appendTo="body" header="Weight" v-model:visible="display_modal" :closeOnEscape="false" :closable="false" :modal="true" data-toggle="validator" ref="form">
    <br>
    <div v-if="!fixed_date" class="p-flex-row p-pb-5">
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
            <InputNumber id="muscle" v-model="vv.muscle.$model" mode="decimal" :minFractionDigits="2" :maxFractionDigits="2" @input="vv.muscle.$model = $event.value" />
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
      <div v-if="vv.photo_front.$model" >
        <a :href="vv.photo_front.$model" target="_blank" ><img :src="vv.photo_front.$model" style="width: 50px; height: 50px" /> Front Photo</a>
        <Button icon="pi pi-trash" class="p-button-rounded p-button-warning" @click="remove_photo_front" />
      </div>
    </div>
    <div class="p-flex-row p-pb-5" >
      <ProgressBar :value="this.uploadPhotoRightProgress" v-if="this.uploadPhotoRightProgress > 0" />
      <FileUpload choose-label="Choose Right Photo" mode="basic" accept="image/*" :auto="true" :customUpload="true" @uploader="upload_photo_right" :disabled="this.isUploadingPhoto()" v-if="!vv.photo_right.$model && !this.uploadPhotoRightProgress" />
      <div v-if="vv.photo_right.$model" >
         <a :href="vv.photo_right.$model" target="_blank"><img :src="vv.photo_right.$model" style="width: 50px; height: 50px" /> Right Photo</a>
         <Button icon="pi pi-trash" class="p-button-rounded p-button-warning" @click="remove_photo_right" />
      </div>
    </div>
    <div class="p-flex-row p-pb-5" >
      <ProgressBar :value="this.uploadPhotoLeftProgress" v-if="this.uploadPhotoLeftProgress > 0" />
      <FileUpload choose-label="Choose Left Photo" mode="basic" accept="image/*" :auto="true" :customUpload="true" @uploader="upload_photo_left" :disabled="this.isUploadingPhoto()" v-if="!vv.photo_left.$model && !this.uploadPhotoLeftProgress" />
       <div v-if="vv.photo_left.$model" >
         <a :href="vv.photo_left.$model" target="_blank" ><img :src="vv.photo_left.$model" style="width: 50px; height: 50px" /> Left Photo</a>
         <Button icon="pi pi-trash" class="p-button-rounded p-button-warning" @click="remove_photo_left" />
       </div>
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
    weight: Object,
    initial_date: Date,
    fixed_date: Boolean
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
      date: this.initial_date || new Date(),
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
      pendingFrontPhoto: null,
      pendingRightPhoto: null,
      pendingLeftPhoto: null
    }
  },
  watch: {
    show(value) {
      this.display_modal = value;
      if (value) {
        this.load_form();
      }
    },
    weight() {
      if (this.display_modal) {
        this.load_form();
      }
    },
    initial_date() {
      if (this.display_modal && !this.weight) {
        this.load_form();
      }
    }
  },
  methods: {
    load_form() {
      if (this.weight) {
        this.vv.date.$model = this.weight.date;
        this.vv.weight.$model = this.weight.weight;
        this.vv.fat_percentage.$model = this.weight.fat_percentage;
        this.vv.muscle.$model = this.weight.muscle;
        this.vv.photo_front.$model = this.weight.photo_front;
        this.vv.photo_right.$model = this.weight.photo_right;
        this.vv.photo_left.$model = this.weight.photo_left;
        return;
      }
      this.clear();
    },
    clear() {
      this.vv.date.$model = this.initial_date || new Date();
      this.vv.weight.$model = null;
      this.vv.fat_percentage.$model = null;
      this.vv.muscle.$model = null;
      this.vv.photo_front.$model = null;
      this.vv.photo_right.$model = null;
      this.vv.photo_left.$model = null;
      this.pendingFrontPhoto = null;
      this.pendingRightPhoto = null;
      this.pendingLeftPhoto = null;
      this.vv.$reset();
    },
    async save() {
      this.vv.$touch();
      if (this.vv.$invalid) {
        return;
      }
      let weight_id = this.weight ? this.weight.id : null;
      let user = this.state.user.mail;
      await service.save(build_weight(this.vv, weight_id, user))
          .then(async savedWeight => {
            if (this.pendingFrontPhoto) {
              savedWeight = await weightService.upload_image(savedWeight.id, 'front', this.pendingFrontPhoto);
            }
            if (this.pendingRightPhoto) {
              savedWeight = await weightService.upload_image(savedWeight.id, 'right', this.pendingRightPhoto);
            }
            if (this.pendingLeftPhoto) {
              await weightService.upload_image(savedWeight.id, 'left', this.pendingLeftPhoto);
            }
            this.$emit('onSave');
            this.$toast.add({severity:'success', summary: 'Weight saved', life: 3000});
            this.close_modal();
          })
          .catch(e => {
            this.handle_error(e)
          });
      this.clear();

      function build_weight(vv, id, user) {
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
        return weight.toObject();
      }
    },
    close_modal() {
      this.clear();
      this.$emit('onClose');
    },
    upload_photo_front(event) {
      this.pendingFrontPhoto = event.files[0];
      this.vv.photo_front.$model = URL.createObjectURL(event.files[0]);
    },
    upload_photo_right(event) {
      this.pendingRightPhoto = event.files[0];
      this.vv.photo_right.$model = URL.createObjectURL(event.files[0]);
    },
    upload_photo_left(event) {
      this.pendingLeftPhoto = event.files[0];
      this.vv.photo_left.$model = URL.createObjectURL(event.files[0]);
    },
    remove_photo_front() {
      this.pendingFrontPhoto = null;
      this.vv.photo_front.$model = null;
    },
    remove_photo_right() {
      this.pendingRightPhoto = null;
      this.vv.photo_right.$model = null;
    },
    remove_photo_left() {
      this.pendingLeftPhoto = null;
      this.vv.photo_left.$model = null;
    },
    isUploadingPhoto() {
      return false;
    },
    handle_error(e) {
      this.$log.error(e);
      this.$toast.add({severity:'error', summary: 'Failed', detail: e, life: 3000});
    }
  }
}
</script>
