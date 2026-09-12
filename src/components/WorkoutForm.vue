<template><WorkoutEditor v-bind="$attrs" /></template>
<script>
import WorkoutEditor from './WorkoutEditor.vue';
export default {name: 'WorkoutForm', components: {WorkoutEditor}, inheritAttrs: false};
</script>
