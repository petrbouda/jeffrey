<script setup>
import {onBeforeUnmount, onMounted, ref} from 'vue';
import {useLayout} from '@/layout/composables/layout';
import AppSidebar from '@/layout/AppSidebar.vue';
import {usePrimeVue} from 'primevue/config';
import PrimaryProfileService from '@/service/PrimaryProfileService';
import SecondaryProfileService from '../service/SecondaryProfileService';
import Utils from "@/service/Utils";
import ProfileService from "@/service/ProfileService";
import {FilterMatchMode} from "primevue/api";
import ProfileCard from "@/components/ProfileCard.vue";
import MessageBus from "@/service/MessageBus";
import {useRouter} from "vue-router";

const router = useRouter();

const $primevue = usePrimeVue();
const profileSelector = ref(false)
const profileSelectorActivatedFor = ref("")

defineExpose({
  $primevue
});
const {isHorizontal, onMenuToggle, showConfigSidebar, showSidebar} = useLayout();

const outsideClickListener = ref(null);
const topbarMenuActive = ref(false);

const profiles = ref(null);
const filters = ref({
  name: {value: null, matchMode: FilterMatchMode.CONTAINS}
});

onMounted(() => {
  bindOutsideClickListener();
});

onBeforeUnmount(() => {
  unbindOutsideClickListener();
});

const profileSelectorDialog = (isPrimary) => {
  profileSelector.value = true

  if (isPrimary) {
    profileSelectorActivatedFor.value = 'primary'
  } else {
    profileSelectorActivatedFor.value = 'secondary'
  }

  ProfileService.list().then((data) => (profiles.value = data));
}

const selectProfile = (profile) => {
  if (profileSelectorActivatedFor.value === 'primary') {
    PrimaryProfileService.update(profile)
  } else {
    SecondaryProfileService.update(profile);
  }

  // profileSelectorActivatedFor.value = ""
  // profileSelector.value = false
  // profiles.value = null

  router.go()
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

const toggle = (event) => {
  MessageBus.emit(MessageBus.PROFILE_CARD_TOGGLE, event)
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
        <Button :label="PrimaryProfileService.profile.value" severity="primary" @click="profileSelectorDialog(true)"/>

        <div v-if="SecondaryProfileService.profile.value != null">
          <Button :label="SecondaryProfileService.profile.value" severity="secondary" @click="profileSelectorDialog(false)"/>
        </div>
        <div v-else>
          <Button label="Select Secondary Profile" outlined severity="secondary" @click="profileSelectorDialog(false)"/>
        </div>
      </div>
    </div>
    <div class="layout-topbar-menu-section">
      <AppSidebar></AppSidebar>
    </div>

    <Dialog v-model:visible="profileSelector" modal header="Header" :style="{ width: '80%' }">
      <DataTable
          id="datatable"
          ref="dt"
          :value="profiles"
          dataKey="Name"
          paginator
          :rows="20"
          v-model:filters="filters"
          filterDisplay="menu">

        <Column header="" headerStyle="width:12%">
          <template #body="slotProps">
            <Button icon="pi pi-info" outlined severity="secondary" class="mr-2" @click="toggle"/>
            <Button icon="pi pi-play" class="p-button-primary" @click="selectProfile(slotProps.data)"/>
          </template>
        </Column>
        <Column field="name" header="Name" :sortable="true" headerStyle="width:63%; min-width:10rem;"
                :showFilterMatchModes="false">
          <template #body="slotProps">
            <span class="font-bold">{{ slotProps.data.name }}</span>
          </template>
          <template #filter="{ filterModel }">
            <InputText v-model="filterModel.value" type="text" class="p-column-filter" placeholder="Search by name"/>
          </template>
        </Column>
        <Column field="createdAt" header="Created at" :sortable="true" headerStyle="width:25%; min-width:10rem;">
          <template #body="slotProps">
            {{ Utils.formatDateTime(slotProps.data.createdAt) }}
          </template>
        </Column>
      </DataTable>

      <ProfileCard></ProfileCard>
    </Dialog>

  </div>
</template>
