<script setup>
import {ref} from 'vue';
import AppMenuItem from './AppMenuItem.vue';

// https://fonts.google.com/icons?selected=Material+Symbols+Outlined
const model = ref([
  {
    label: 'Profile',
    items: [
      {label: 'Information', icon: 'info', to: '/profile/information'},
      {label: 'Auto Analysis', icon: 'checklist_rtl', to: '/profile/autoAnalysis'},
      {label: 'Event Viewer', icon: 'pageview', to: '/profile/eventViewer'},
      {label: 'Saved Graphs', icon: 'save', to: '/common/savedgraphs'},
    ]
  },
  {
    label: 'Flamegraphs',
    items: [
      {label: 'Primary', icon: 'local_fire_department', to: '/common/flamegraph-sections'},
      {label: 'Differential', icon: 'difference', to: '/common/diff-flamegraph-sections'},
    ]
  },
  {
    label: 'Sub-Second Graphs',
    items: [
      {label: 'Primary', icon: 'transition_dissolve', to: '/common/subsecond-sections'},
      {label: 'Differential', icon: 'difference', to: '/common/diff-subsecond-sections'},
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
