<template><span class="count-badge" aria-hidden="true">{{ value }}</span></template>

<script>
export default {
  name: 'CountBadge',
  props: {value: {type: Number, required: true}}
};
</script>

<style scoped>
.count-badge {
  position: absolute;
  top: -0.2rem;
  right: -0.2rem;
  min-width: 1.25rem;
  height: 1.25rem;
  padding: 0 0.3rem;
  border: 2px solid #fff;
  border-radius: 0.75rem;
  color: #fff;
  background: #dc3545;
  font-size: 0.7rem;
  font-weight: 700;
  line-height: 1rem;
  text-align: center;
  pointer-events: none;
}
</style>
