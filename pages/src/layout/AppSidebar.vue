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
import AppMenu from './AppMenu.vue';
import {useLayout} from '@/layout/composables/layout';
import {useRouter} from 'vue-router';

const router = useRouter();

const navigateToDashboard = () => {
  router.push('/');
};

const {layoutState} = useLayout();

let timeout = null;

const onMouseEnter = () => {
  if (!layoutState.anchored.value) {
    if (timeout) {
      clearTimeout(timeout);
      timeout = null;
    }
    layoutState.sidebarActive.value = true;
  }
};

const onMouseLeave = () => {
  if (!layoutState.anchored.value) {
    if (!timeout) {
      timeout = setTimeout(() => (layoutState.sidebarActive.value = false), 300);
    }
  }
};

const anchor = () => {
  layoutState.anchored.value = !layoutState.anchored.value;
};
</script>

<template>
  <div class="layout-sidebar" @mouseenter="onMouseEnter()" @mouseleave="onMouseLeave()">
    <div class="sidebar-header cursor-pointer" @click="navigateToDashboard">
<!--      <img @mouseover="(e) => e.currentTarget.classList.add('cursor-pointer')"-->
<!--           @mouseout="(e) => e.currentTarget.classList.remove('cursor-pointer')"-->
<!--           @click="moveTo('index')"-->
<!--           src="/jeffrey_small.png" style="width:70px; height: auto; border-radius: 5px" alt=""/>-->
      <div @click="navigateToDashboard" class="app-logo cursor-pointer w-full text-center"
           style="font-family: 'Permanent Marker', cursive; font-weight: 400; font-style: normal;font-size: 32px; padding-left:5px;">
        Jeffrey
      </div>
<!--      <button class="layout-sidebar-anchor p-link" type="button" @click="anchor()"></button>-->
    </div>

    <div ref="menuContainer" class="layout-menu-container">
      <AppMenu></AppMenu>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.hero__avatar {
  width: 48px;
  height: 48px;
  transform: translate(0);
  border-radius: 50%;
  overflow: hidden;
  user-select: none;
  background: white;
}
</style>
