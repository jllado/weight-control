<template>
  <section class="domain-card" :aria-label="title">
    <h3>{{ title }}</h3>
    <p>{{ section.summary }}</p>
    <p class="domain-next-action"><strong>Next action:</strong> {{ section.nextAction }}</p>
  </section>
</template>

<script>
export default {
  name: 'ReflectionDomainCard',
  props: {
    title: {type: String, required: true},
    section: {type: Object, required: true}
  }
};
</script>

<style scoped>
.domain-card {
  padding: 0.85rem;
  border: 1px solid var(--line);
  border-radius: 0.65rem;
  background: var(--paper);
  overflow-wrap: anywhere;
}
.domain-card h3 {
  margin: 0 0 0.5rem;
  font-size: 1rem;
}
.domain-card p {
  margin: 0;
  line-height: 1.4;
}
.domain-card .domain-next-action {
  margin-top: 0.5rem;
}
</style>
