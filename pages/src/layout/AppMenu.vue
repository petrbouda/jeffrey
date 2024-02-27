<script setup>
import {ref} from 'vue';
import AppMenuItem from './AppMenuItem.vue';

const model = ref([
  {
    label: 'Profiles',
    items: [
      {label: 'Recordings', icon: 'pi pi-fw pi-file', to: '/'},
      {label: 'Select Profile', icon: 'pi pi-fw pi-box', to: '/profiles'}
    ]
  },
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
      {label: 'Startup Heatmaps', icon: 'pi pi-fw pi-table', to: '/flamegraph/startup'},
    ]
  },
  {
    label: 'New Look',
    items: [
      {label: 'Dashboard', icon: 'pi pi-fw pi-clone', to: '/dashboard'},
      {label: 'Sections', icon: 'pi pi-fw pi-clone', to: '/sections'},
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
