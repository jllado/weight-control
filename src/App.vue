<template>
  <div class="p-mb-5" >
    <Menubar :model="items" />
  </div>
  <Toast />
  <router-view />
</template>

<script>
export default {
  name: "app",
  data() {
    return {
      items: [
        {
          label:'Home',
          icon:'pi pi-fw pi-home',
          to: '/'
        },
        {
          label:'History',
          icon:'pi pi-fw pi-chart-bar',
          to: '/history'
        },
        {
          label:'Backup',
          icon:'pi pi-fw pi-upload',
          to: '/backup'
        }
      ]
    }
  },
};
</script>

<style>
@media ( max-width: 650px ) { /* Default 35em */
  .p-filter-column, .non-responsive-column {
    display: none;
  }
}
.error {
  color: red;
  font-size: smaller;
  font-style: italic;
  font-weight: 500;
  margin-top: 4px;
}
.table-header {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
@media (max-width: 575px) {
  .mobile-none {
    display: none;
  }
  .p-datatable th, td {
    padding-left: 2px !important;
  }
}
@media (min-width: 575px) {
  span.percentage:before {
    content: '(';
  }
  span.percentage:after {
    content: ')';
  }
}
</style>