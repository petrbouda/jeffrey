<script setup>
import {ref} from 'vue';
import AppMenuItem from './AppMenuItem.vue';

// https://fonts.google.com/icons?selected=Material+Symbols+Outlined
const model = ref([
  {
    label: 'Profile',
    items: [
      {label: 'Information', icon: 'info', to: '/profile/information'},
      {label: 'Hints', icon: 'checklist_rtl', to: '/profile/hints'},
      {label: 'Event Viewer', icon: 'pageview', to: '/profile/eventViewer'},
    ]
  },
  {
    label: 'Common Graphs',
    items: [
      {label: 'Flamegraphs', icon: 'local_fire_department', to: '/common/flamegraphs'},
      {label: 'Sub-Second Graphs', icon: 'transition_dissolve', to: '/common/subsecond'},
      {label: 'Saved Graphs', icon: 'save', to: '/common/savedgraphs'},
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
