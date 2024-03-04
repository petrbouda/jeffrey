<script setup>
import {ref} from 'vue';
import AppMenuItem from './AppMenuItem.vue';

const model = ref([
  {
    label: 'Profile',
    items: [
      {label: 'Information', icon: 'pi pi-fw pi-list', to: '/profile/information'},
      {label: 'Hints', icon: 'pi pi-fw pi-table', to: '/profile/hints'},
    ]
  },
  {
    label: 'Flamegraphs',
    items: [
      {label: 'Complete', icon: 'pi pi-fw pi-bars', to: '/flamegraph/complete'},
      {label: 'Sub-Second', icon: 'pi pi-fw pi-table', to: '/flamegraph/startup'},
    ]
  },
  {
    label: 'Diffgraphs',
    items: [
      {label: 'Complete', icon: 'pi pi-fw pi-bars', to: '/flamegraph/complete'},
      {label: 'Sub-Second', icon: 'pi pi-fw pi-table', to: '/flamegraph/startup'},
    ]
  }
]);
</script>

<template>
  <ul class="layout-menu">
    <template v-for="(item, i) in model" :key="item">
      <AppMenuItem :item="item" root :index="i"/>

      <li class="menu-separator"></li>
    </template>
  </ul>
</template>

<style lang="scss" scoped></style>
