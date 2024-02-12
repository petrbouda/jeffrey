<script setup>
import { computed, watch, ref, onBeforeUnmount } from 'vue';
import AppTopbar from './AppTopbar.vue';
import AppConfig from './AppConfig.vue';
import { usePrimeVue } from 'primevue/config';
import { useLayout } from '@/layout/composables/layout';

const $primevue = usePrimeVue();
const { layoutConfig, layoutState, isSidebarActive } = useLayout();
const outsideClickListener = ref(null);
const topbarRef = ref(null);

watch(isSidebarActive, (newVal) => {
    if (newVal) {
        bindOutsideClickListener();
    } else {
        unbindOutsideClickListener();
    }
});

onBeforeUnmount(() => {
    unbindOutsideClickListener();
});

const containerClass = computed(() => {
    return [
        {
            'layout-light': layoutConfig.colorScheme.value === 'light',
            'layout-dark': layoutConfig.colorScheme.value === 'dark',
            'layout-light-menu': layoutConfig.menuTheme.value === 'light',
            'layout-dark-menu': layoutConfig.menuTheme.value === 'dark',
            'layout-light-topbar': layoutConfig.topbarTheme.value === 'light',
            'layout-dark-topbar': layoutConfig.topbarTheme.value === 'dark',
            'layout-transparent-topbar': layoutConfig.topbarTheme.value === 'transparent',
            'layout-overlay': layoutConfig.menuMode.value === 'overlay',
            'layout-static': layoutConfig.menuMode.value === 'static',
            'layout-slim': layoutConfig.menuMode.value === 'slim',
            'layout-slim-plus': layoutConfig.menuMode.value === 'slim-plus',
            'layout-horizontal': layoutConfig.menuMode.value === 'horizontal',
            'layout-reveal': layoutConfig.menuMode.value === 'reveal',
            'layout-drawer': layoutConfig.menuMode.value === 'drawer',
            'layout-static-inactive': layoutState.staticMenuDesktopInactive.value && layoutConfig.menuMode.value === 'static',
            'layout-overlay-active': layoutState.overlayMenuActive.value,
            'layout-mobile-active': layoutState.staticMenuMobileActive.value,
            'p-input-filled': $primevue.config.inputStyle === 'filled',
            'p-ripple-disabled': $primevue.config.ripple === false,
            'layout-sidebar-active': layoutState.sidebarActive.value,
            'layout-sidebar-anchored': layoutState.anchored.value
        }
    ];
});

const bindOutsideClickListener = () => {
    if (!outsideClickListener.value) {
        outsideClickListener.value = (event) => {
            if (isOutsideClicked(event)) {
                layoutState.overlayMenuActive.value = false;
                layoutState.overlaySubmenuActive.value = false;
                layoutState.staticMenuMobileActive.value = false;
                layoutState.menuHoverActive.value = false;
            }
        };
        document.addEventListener('click', outsideClickListener.value);
    }
};
const unbindOutsideClickListener = () => {
    if (outsideClickListener.value) {
        document.removeEventListener('click', outsideClickListener);
        outsideClickListener.value = null;
    }
};
const isOutsideClicked = (event) => {
    if (!topbarRef.value) return;

    const sidebarEl = topbarRef?.value.$el.querySelector('.layout-sidebar');
    const topbarEl = topbarRef?.value.$el.querySelector('.topbar-start > button');

    return !(sidebarEl.isSameNode(event.target) || sidebarEl.contains(event.target) || topbarEl.isSameNode(event.target) || topbarEl.contains(event.target));
};
</script>

<template>
    <div class="layout-container" :class="containerClass">
        <AppTopbar ref="topbarRef"></AppTopbar>
        <AppConfig></AppConfig>
        <div class="layout-content-wrapper">
            <div class="layout-content">
                <router-view></router-view>
            </div>
        </div>

        <div class="layout-mask"></div>
    </div>
</template>

<style lang="scss"></style>
