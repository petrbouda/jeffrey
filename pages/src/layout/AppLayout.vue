<script setup>
import {ref} from 'vue';
import {usePrimeVue} from 'primevue/config';
import {useLayout} from '@/layout/composables/layout';

const $primevue = usePrimeVue();
const {layoutConfig, layoutState, isSidebarActive} = useLayout();
const outsideClickListener = ref(null);
</script>

<template>
  <div class="layout-container">
    <div class="layout-content-wrapper">
      <div class="layout-content">
        <router-view></router-view>
      </div>
    </div>

    <div class="layout-mask"></div>
  </div>
</template>

<style lang="scss"></style>
