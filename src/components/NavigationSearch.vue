<template>
  <div class="navigation-search" @focusout="leave" @click="keepMobileMenuOpen">
    <div class="navigation-search-field">
      <i class="pi pi-search" aria-hidden="true" />
      <input ref="input" v-model="query" type="search" class="p-inputtext p-component" role="combobox"
             placeholder="Go to…" aria-label="Search navigation" aria-autocomplete="list" aria-controls="navigation-matches"
             :aria-expanded="searching" :aria-activedescendant="activeIndex >= 0 ? optionId(activeIndex) : undefined"
             @focus="$emit('focus-search')" @keydown.down="move($event, 1)" @keydown.up="move($event, -1)"
             @keydown.enter="openMatch" @keydown.esc="escape" />
      <button v-if="query" type="button" class="navigation-search-clear" aria-label="Clear search" @click="clear">
        <i class="pi pi-times" aria-hidden="true" />
      </button>
    </div>
    <div v-show="searching" class="navigation-search-popup">
      <div id="navigation-matches" role="listbox" aria-label="Matching destinations">
        <RouterLink v-for="(item, index) in matches" :key="item.to" :to="item.to" custom v-slot="{ href, navigate }">
          <a :id="optionId(index)" :href="href" role="option" tabindex="-1" :aria-selected="index === activeIndex"
             @mousedown.prevent @click="select($event, navigate)">
            <i :class="item.icon" aria-hidden="true" />
            <span>{{ item.label }}</span><small>{{ item.group }}</small>
          </a>
        </RouterLink>
      </div>
      <div v-if="searching && !matches.length" role="status" class="navigation-search-empty">No matching destinations.</div>
    </div>
  </div>
</template>

<script>
export default {
  props: {groups: {type: Array, required: true}},
  emits: ['filter', 'selected', 'focus-search'],
  data() { return {query: '', activeIndex: -1}; },
  computed: {
    term() { return this.query.trim().toLocaleLowerCase(); },
    searching() { return Boolean(this.term); },
    matches() {
      if (!this.searching) return [];
      return this.groups.flatMap(group => group.items
        .filter(item => item.label.toLocaleLowerCase().includes(this.term))
        .map(item => ({...item, group: group.label})));
    }
  },
  watch: {
    query() { this.activeIndex = -1; },
    searching(value) { this.$emit('filter', value); },
    '$route.fullPath'() { this.query = ''; }
  },
  methods: {
    optionId(index) { return 'navigation-match-' + index; },
    focus() { this.$refs.input.focus(); },
    clear() { this.query = ''; this.focus(); },
    leave(event) { if (!this.$el.contains(event.relatedTarget)) this.query = ''; },
    keepMobileMenuOpen(event) {
      // PrimeVue treats its start/end slots as outside the mobile menu list.
      if (this.$el.closest('.p-menubar-mobile-active')) event.stopPropagation();
    },
    move(event, direction) {
      if (!this.matches.length) return;
      event.preventDefault();
      const last = this.matches.length - 1;
      this.activeIndex = this.activeIndex < 0 ? (direction > 0 ? 0 : last)
        : Math.max(0, Math.min(last, this.activeIndex + direction));
      document.getElementById(this.optionId(this.activeIndex)).scrollIntoView({block: 'nearest'});
    },
    openMatch(event) {
      if (event.isComposing || !this.matches.length) return;
      event.preventDefault();
      document.getElementById(this.optionId(Math.max(0, this.activeIndex))).click();
    },
    escape(event) {
      if (!this.query) return;
      event.preventDefault();
      event.stopPropagation();
      this.clear();
    },
    select(event, navigate) {
      navigate(event);
      if (event.defaultPrevented) {
        this.query = '';
        this.$emit('selected');
      }
    }
  }
};
</script>

<style scoped>
.navigation-search { position: relative; width: clamp(8.5rem, 12vw, 12rem); }
.navigation-search-field { position: relative; }
.navigation-search-field > .pi { position: absolute; left: .65rem; top: 50%; transform: translateY(-50%); color: var(--text-color-secondary); pointer-events: none; }
.navigation-search-field input { width: 100%; padding: .45rem 2rem; }
.navigation-search-field input::-webkit-search-cancel-button { appearance: none; }
.navigation-search-clear { position: absolute; right: 0; inset-block: 0; width: 2rem; border: 0; background: transparent; color: var(--text-color-secondary); cursor: pointer; }
.navigation-search-popup { position: absolute; top: calc(100% + .25rem); left: 0; z-index: 1000; width: 17rem; max-width: calc(100vw - 2rem); max-height: min(50dvh, 24rem); overflow: auto; background: var(--surface-overlay); color: var(--text-color); border: 1px solid var(--surface-border); border-radius: var(--border-radius); box-shadow: 0 3px 8px #0002; }
.navigation-search-popup a { display: flex; align-items: center; gap: .6rem; min-height: 2.65rem; padding: .5rem .75rem; color: inherit; text-decoration: none; }
.navigation-search-popup a:hover, .navigation-search-popup a[aria-selected="true"] { background: var(--surface-hover); }
.navigation-search-popup a[aria-selected="true"] { box-shadow: inset 3px 0 var(--primary-color); }
.navigation-search-popup small { margin-left: auto; color: inherit; }
.navigation-search-empty { padding: .75rem; }
@media (max-width: 960px) {
  .navigation-search { display: none; }
  .p-menubar-mobile-active .navigation-search { display: block; position: absolute; top: 100%; inset-inline: 0; width: auto; z-index: 1000; background: var(--surface-overlay); border: 1px solid var(--surface-border); box-shadow: 0 2px 4px #0002; }
  .navigation-search-field { display: flex; align-items: center; height: var(--mobile-search-height); padding: .45rem; }
  .navigation-search-field input { font-size: 1rem; min-height: 2.35rem; }
  .navigation-search-field > .pi { left: 1.1rem; }
  .navigation-search-clear { right: .45rem; }
  .navigation-search-popup { position: static; border: 0; border-top: 1px solid var(--surface-border); width: 100%; max-width: none; box-shadow: none; }
}
</style>
