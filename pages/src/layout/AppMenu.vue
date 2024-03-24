<script setup>
import {ref} from 'vue';
import AppMenuItem from './AppMenuItem.vue';

const model = ref([
  {
    label: 'Profile',
    items: [
      {label: 'Information', icon: 'pi pi-fw pi-list', to: '/profile/information'},
      {label: 'Hints', icon: 'pi pi-fw pi-table', to: '/profile/hints'},
      {label: 'Event Viewer', icon: 'pi pi-fw pi-table', to: '/profile/eventViewer'},
    ]
  },
  {
    label: 'Common Graphs',
    items: [
      {label: 'Flamegraphs', icon: 'pi pi-fw pi-bars', to: '/common/flamegraphs'},
      {label: 'Sub-Second Graphs', icon: 'pi pi-fw pi-table', to: '/common/subsecond'},
      {label: 'Saved Graphs', icon: 'pi pi-fw pi-save', to: '/common/savedgraphs'},
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
