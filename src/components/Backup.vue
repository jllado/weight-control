<template>
  {{ JSON.stringify(json, null, '\t') }}
</template>

<script>

import service from '../services/WeightService';

export default {
  name: 'Backup',
  data () {
    return {
      json: {}
    }
  },
  async created () {
    this.json = await this.doExport();
  },
  methods: {
    async doExport() {
      let newVar = await service.get_all();
      return newVar;
    }
  }
}
</script>
