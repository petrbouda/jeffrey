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
import {onBeforeUnmount, onMounted, ref} from 'vue';
import {useLayout} from '@/layout/composables/layout';
import AppSidebar from '@/layout/AppSidebar.vue';
import {usePrimeVue} from 'primevue/config';
import SecondaryProfileService from '../service/SecondaryProfileService';
import ProfileDialog from "@/components/ProfileDialog.vue";
import MessageBus from "@/service/MessageBus";
import ProfileType from "@/service/flamegraphs/ProfileType";

const $primevue = usePrimeVue();

defineExpose({
  $primevue
});
const {onMenuToggle} = useLayout();

const outsideClickListener = ref(null);
const topbarMenuActive = ref(false);

onMounted(() => {
  bindOutsideClickListener();
});

onBeforeUnmount(() => {
  unbindOutsideClickListener();
});

const profileSelectorDialog = (isPrimary) => {
  if (isPrimary) {
    MessageBus.emit(MessageBus.PROFILE_DIALOG_TOGGLE, ProfileType.PRIMARY)
  } else {
    MessageBus.emit(MessageBus.PROFILE_DIALOG_TOGGLE, ProfileType.SECONDARY)
  }
}

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
        <div v-if="SecondaryProfileService.profile.value != null">
          <Button :label="SecondaryProfileService.profile.value" severity="secondary"
                  @click="profileSelectorDialog(false)"/>
        </div>
        <div v-else>
          <Button label="Select Secondary Profile" outlined severity="secondary" @click="profileSelectorDialog(false)"/>
        </div>
      </div>
    </div>
    <div class="layout-topbar-menu-section">
      <AppSidebar></AppSidebar>
    </div>

    <ProfileDialog></ProfileDialog>
  </div>
</template>
