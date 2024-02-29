<script setup>
import {FilterMatchMode} from 'primevue/api';
import {onBeforeMount, onMounted, ref} from 'vue';
import {useToast} from 'primevue/usetoast';
import ProfileService from '../service/ProfileService';
import PrimaryProfileService from '@/service/PrimaryProfileService';
import Utils from '../service/Utils';
import ProfileCard from "@/components/ProfileCard.vue";
import MessageBus from "@/service/MessageBus";

const toast = useToast();
const profiles = ref(null);
const dt = ref(null);
const filters = ref({});

const profileService = new ProfileService();

onBeforeMount(() => {
  filters.value = {
    name: {value: null, matchMode: FilterMatchMode.CONTAINS}
  };
});
onMounted(() => {
  profileService.list().then((data) => (profiles.value = data));
});

const selectPrimaryProfile = (profile) => {
  PrimaryProfileService.update(profile);
  toast.add({
    severity: 'success',
    summary: 'Successful',
    detail: 'Primary Profile Selected: ' + profile.name,
    life: 3000
  });
  profileService.list().then((data) => (profiles.value = data));
};

const deleteProfile = (profile) => {
  profileService.delete(profile.id)
      .then(() => {
        profiles.value = profiles.value.filter((val) => val.id !== profile.id);
        toast.add({
          severity: 'success',
          summary: 'Successful',
          detail: 'Profile Deleted: ' + profile.name,
          life: 3000
        });
      });
};

const toggle = (event) => {
  MessageBus.emit(MessageBus.PROFILE_CARD_TOGGLE, event)
};
</script>

<template>
  <DataTable
      id="datatable"
      ref="dt"
      :value="profiles"
      dataKey="Name"
      paginator
      :rows="20"
      :filters="filters"
      v-model:filters="filters"
      filterDisplay="menu"
      responsiveLayout="scroll">

    <Column header="">
      <template #body="slotProps">
        <Button icon="pi pi-info" outlined severity="secondary" class="mr-2"
                @click="toggle"/>
        <Button icon="pi pi-play" class="p-button-primary"
                @click="selectPrimaryProfile(slotProps.data)"/>
      </template>
    </Column>
    <Column field="name" header="Name" :sortable="true" headerStyle="width:60%; min-width:10rem;">
      <template #body="slotProps">
        <span class="font-bold">{{ slotProps.data.name }}</span>
      </template>
      <template #filter="{ filterModel }">
        <InputText v-model="filterModel.value" type="text" class="p-column-filter" placeholder="Search by name"/>
      </template>
    </Column>
    <!--        <Column field="code" header="ID" headerStyle="width:25%; min-width:10rem;">-->
    <!--            <template #body="slotProps">-->
    <!--                <span class="p-column-title">ID</span>-->
    <!--                {{ slotProps.data.id }}-->
    <!--            </template>-->
    <!--        </Column>-->
    <Column field="createdAt" header="Created at" :sortable="true" headerStyle="width:25%; min-width:10rem;">
      <template #body="slotProps">
        <span class="p-column-title">Created</span>
        {{ Utils.formatDateTime(slotProps.data.createdAt) }}
      </template>
    </Column>
    <Column>
      <template #body="slotProps">
        <Button icon="pi pi-trash" class="p-button-filled p-button-warning mt-2"
                @click="deleteProfile(slotProps.data)"/>
      </template>
    </Column>
  </DataTable>

  <ProfileCard></ProfileCard>

  <Toast/>
</template>

<style scoped lang="scss"></style>
