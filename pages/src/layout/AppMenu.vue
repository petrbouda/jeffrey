<script setup>
import { ref } from 'vue';

import AppMenuItem from './AppMenuItem.vue';

const model = ref([
    {
        label: 'Profiles',
        items: [
            { label: 'Recordings', icon: 'pi pi-fw pi-home', to: '/' },
            { label: 'Select Profile', icon: 'pi pi-fw pi-home', to: '/profiles' }
        ]
    },
    {
        label: 'Flamegraphs',
        items: [
            { label: 'General', icon: 'pi pi-fw pi-home', to: '/flamegraph/general' },
            { label: 'Selectable', icon: 'pi pi-fw pi-home', to: '/flamegraph/selectable' },
            { label: 'Differential', icon: 'pi pi-fw pi-home', to: '/flamegraph/differential' }
        ]
    }
]);
</script>

<template>
    <ul class="layout-menu">
        <template v-for="(item, i) in model" :key="item">
            <app-menu-item v-if="!item.separator" :item="item" :index="i"></app-menu-item>
            <li v-if="item.separator" class="menu-separator"></li>
        </template>
    </ul>
</template>

<style lang="scss" scoped></style>
