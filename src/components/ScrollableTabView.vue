<script>
import TabView from 'primevue/tabview';

const SCROLL_EDGE_TOLERANCE = 1;

export default {
  name: 'ScrollableTabView',
  extends: TabView,
  methods: {
    updateButtonState() {
      const content = this.$refs.content;
      this.isPrevButtonDisabled = content.scrollLeft <= SCROLL_EDGE_TOLERANCE;
      this.isNextButtonDisabled = content.scrollWidth - content.clientWidth - content.scrollLeft <= SCROLL_EDGE_TOLERANCE;
    }
  }
};
</script>
