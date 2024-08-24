<!--
  - Jeffrey
  - Copyright (C) 2024 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<script setup>
import {ref} from 'vue';
import AppMenuItem from './AppMenuItem.vue';

// https://fonts.google.com/icons?selected=Material+Symbols+Outlined
const model = ref([
  {
    label: 'Profile',
    items: [
      {label: 'Information', icon: 'info', to: '/profile/information'},
      {label: 'Guardian', icon: 'guardian', to: '/profile/guardian'},
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
