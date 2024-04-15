<script setup>
import AppMenu from './AppMenu.vue';
import {useLayout} from '@/layout/composables/layout';
import {useRouter} from 'vue-router';

const router = useRouter();

const navigateToDashboard = () => {
  router.push('/index/profiles');
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
    <div class="sidebar-header cursor-pointer" @click="navigateToDashboard" >
      <div class="hero__avatar">
        <img class="lazy" src="/jeffrey_small.png" width="48" height="48"/>
      </div>
      <div @click="navigateToDashboard" class="app-logo cursor-pointer"
           style="font-family: 'Permanent Marker', cursive; font-weight: 400; font-style: normal;font-size: 32px; padding-left:5px;">
        Jeffrey
      </div>
      <button class="layout-sidebar-anchor p-link" type="button" @click="anchor()"></button>
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
