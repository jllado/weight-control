<template>
  <component :is="action ? 'ActionButton' : 'Button'" v-bind="{...$attrs, ...(action ? {action} : {})}" v-tooltip.bottom="ariaLabel" :icon="icon" :aria-label="ariaLabel" class="compact-action p-button-outlined" :class="{'p-button-danger': destructive}" @focus="tooltipEvent($event, 'mouseenter')" @blur="tooltipEvent($event, 'mouseleave')" />
</template>

<script>
import Tooltip from 'primevue/tooltip';

export default {
  inheritAttrs: false,
  directives: {tooltip: Tooltip},
  props: {icon: {type: String, required: true}, ariaLabel: {type: String, required: true}, destructive: Boolean, action: Function},
  methods: {
    // Use the tooltip's pointer events for keyboard focus too, retaining PrimeVue positioning and dismissal.
    tooltipEvent(event, type) { event.currentTarget.dispatchEvent(new MouseEvent(type)); }
  }
};
</script>
