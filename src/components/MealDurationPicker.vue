<template>
  <div class="duration-picker p-inputwrapper" :class="{'p-inputwrapper-filled': modelValue !== null, 'p-inputwrapper-focus': visible}">
    <button :id="inputId" ref="trigger" type="button" class="p-inputtext duration-trigger" :class="{'p-filled': modelValue !== null}" :aria-labelledby="labelledby" aria-haspopup="dialog" :aria-expanded="visible" @click="visible = true">{{ modelValue === null ? '\u00a0' : modelValue }}<i class="pi pi-clock" aria-hidden="true" /></button>
    <Dialog v-model:visible="visible" header="Meal duration" modal appendTo="body" :style="{width: '18rem', maxWidth: 'calc(100vw - 2rem)'}" @show="open" @hide="$refs.trigger.focus()">
      <p class="duration-hint">Scroll to choose minutes.</p>
      <div class="duration-wheel-frame">
        <div ref="wheel" class="duration-wheel" role="listbox" aria-label="Duration in minutes" tabindex="0" autofocus :aria-activedescendant="`${inputId}-option-${selectedIndex}`" @scroll="onScroll" @keydown="onKeydown">
          <div v-for="(option, index) in options" :id="`${inputId}-option-${index}`" :key="option.value" class="duration-option" :class="{'duration-selected': index === selectedIndex}" role="option" :aria-selected="index === selectedIndex" @click="select(index)">{{ option.label }}</div>
        </div>
        <div class="duration-wheel-selection" aria-hidden="true" />
      </div>
      <template #footer>
        <Button label="Clear" class="p-button-text" @click="apply(null)" />
        <Button label="Done" icon="pi pi-check" @click="apply(options[selectedIndex].value)" />
      </template>
    </Dialog>
  </div>
</template>

<script>
export default {
  props: {
    modelValue: {type: Number, default: null},
    inputId: {type: String, required: true},
    labelledby: {type: String, required: true}
  },
  emits: ['update:modelValue'],
  data() { return {visible: false, selectedIndex: 0}; },
  computed: {
    options() {
      const values = Array.from({length: 24}, (_, index) => (index + 1) * 5);
      if (this.modelValue !== null && !values.includes(this.modelValue)) values.push(this.modelValue);
      return [{label: 'Not set', value: null}, ...values.sort((left, right) => left - right).map(value => ({label: String(value), value}))];
    }
  },
  methods: {
    open() {
      this.select(this.options.findIndex(option => option.value === this.modelValue));
      this.$refs.wheel.focus();
    },
    select(index) {
      this.selectedIndex = index;
      this.$refs.wheel.scrollTop = index * 44;
    },
    onScroll() { this.selectedIndex = Math.round(this.$refs.wheel.scrollTop / 44); },
    onKeydown(event) {
      const indexes = {ArrowDown: this.selectedIndex + 1, ArrowUp: this.selectedIndex - 1, Home: 0, End: this.options.length - 1};
      if (event.key in indexes) {
        event.preventDefault();
        this.select(Math.max(0, Math.min(this.options.length - 1, indexes[event.key])));
      } else if (event.key === 'Enter') {
        event.preventDefault();
        this.apply(this.options[this.selectedIndex].value);
      }
    },
    apply(value) { this.$emit('update:modelValue', value); this.visible = false; }
  }
};
</script>

<style scoped>
.duration-trigger { display: flex; justify-content: space-between; align-items: center; width: 100%; cursor: pointer; text-align: left; }
.duration-trigger i { color: #6c757d; }
.duration-hint { margin: 0 0 1rem; text-align: center; }
/* PrimeVue has no wheel control; native scrolling and snap keep this local picker touch-friendly. */
.duration-wheel-frame { position: relative; }
.duration-wheel { height: 132px; padding: 44px 0; box-sizing: border-box; overflow-y: auto; overscroll-behavior: contain; scroll-snap-type: y mandatory; scrollbar-width: none; border-radius: 3px; }
.duration-wheel::-webkit-scrollbar { display: none; }
.duration-wheel:focus-visible { outline: 2px solid #007ad9; outline-offset: 2px; }
.duration-option { height: 44px; display: flex; align-items: center; justify-content: center; scroll-snap-align: center; cursor: pointer; color: #6c757d; font-size: 1.1rem; }
.duration-selected { color: #007ad9; font-weight: 600; font-size: 1.35rem; }
.duration-wheel-selection { position: absolute; top: 44px; height: 44px; left: 0; right: 0; border-top: 1px solid #c8c8c8; border-bottom: 1px solid #c8c8c8; pointer-events: none; }
</style>
