<script setup>
import {FilterMatchMode} from 'primevue/api';
import {onMounted, ref} from 'vue';
import {useToast} from 'primevue/usetoast';
import PrimaryProfileService from '@/service/PrimaryProfileService';
import Utils from '../service/Utils';
import ProfileCard from "@/components/ProfileCard.vue";
import MessageBus from "@/service/MessageBus";
import {useRouter} from "vue-router";
import SecondaryProfileService from "@/service/SecondaryProfileService";
import ProfileService from "@/service/ProfileService";

const toast = useToast();
const profiles = ref(null);
const dt = ref(null);
const filters = ref({
  name: {value: null, matchMode: FilterMatchMode.CONTAINS}
});
const matchModes = ref([
  {name: "contains"}
])

const router = useRouter();

onMounted(() => {
  ProfileService.list().then((data) => (profiles.value = data));
});

const selectPrimaryProfile = (profile) => {
  PrimaryProfileService.update(profile);
  SecondaryProfileService.remove();
  router.push({
    name: 'profile-information',
  });
};

const deleteProfile = (profile) => {
  ProfileService.delete(profile.id)
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
      v-model:filters="filters"
      filterDisplay="menu">

    <Column header="" headerStyle="width:12%">
      <template #body="slotProps">
        <Button icon="pi pi-info" outlined severity="secondary" class="mr-2"
                @click="toggle"/>
        <Button icon="pi pi-play" class="p-button-primary"
                @click="selectPrimaryProfile(slotProps.data)"/>
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
