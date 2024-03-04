<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue';
import { useLayout } from '@/layout/composables/layout';
import AppSidebar from '@/layout/AppSidebar.vue';
import { usePrimeVue } from 'primevue/config';
import PrimaryProfileService from '@/service/PrimaryProfileService';
import SecondaryProfileService from '../service/SecondaryProfileService';


const $primevue = usePrimeVue();

defineExpose({
    $primevue
});
const { isHorizontal, onMenuToggle, showConfigSidebar, showSidebar } = useLayout();

const outsideClickListener = ref(null);
const topbarMenuActive = ref(false);

onMounted(() => {
    bindOutsideClickListener();
});

onBeforeUnmount(() => {
    unbindOutsideClickListener();
});

const bindOutsideClickListener = () => {
    if (!outsideClickListener.value) {
        outsideClickListener.value = (event) => {
            if (isOutsideClicked(event)) {
                topbarMenuActive.value = false;
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
    if (!topbarMenuActive.value) return;

    const sidebarEl = document.querySelector('.layout-topbar-menu');
    const topbarEl = document.querySelector('.layout-topbar-menu-button');

    return !(sidebarEl.isSameNode(event.target) || sidebarEl.contains(event.target) || topbarEl.isSameNode(event.target) || topbarEl.contains(event.target));
};
const onMenuButtonClick = () => {
    onMenuToggle();
};
</script>

<template>
    <div class="layout-topbar">
        <div class="topbar-start">
            <Button ref="menubutton" type="button" class="topbar-menubutton p-link p-trigger transition-duration-300"
                    @click="onMenuButtonClick()">
                <i class="pi pi-bars"></i>
            </Button>

            <div class="flex flex-wrap gap-2">
                <Button :label="PrimaryProfileService.profile.value" outlined severity="primary" />
                <Button :label="SecondaryProfileService.profile.value" outlined severity="secondary" />
            </div>
        </div>
        <div class="layout-topbar-menu-section">
            <AppSidebar></AppSidebar>
        </div>
    </div>
</template>
