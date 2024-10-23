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
import {FilterMatchMode} from 'primevue/api';
import {onMounted, ref} from 'vue';
import {useToast} from 'primevue/usetoast';
import ProfileCard from "@/components/ProfileCard.vue";
import {useRoute, useRouter} from "vue-router";
import SecondaryProfileService from "@/service/SecondaryProfileService";
import ProjectProfileService from "@/service/project/ProjectProfileService";
import Utils from "../../service/Utils";

const toast = useToast();
const profiles = ref(null);
const dt = ref(null);
const filters = ref({
  name: {value: null, matchMode: FilterMatchMode.CONTAINS}
});

let profileService

const route = useRoute()
const router = useRouter();

onMounted(() => {
  profileService = new ProjectProfileService(route.params.projectId)
  profileService.list().then((data) => (profiles.value = data));
});

const selectPrimaryProfile = (profile) => {
  SecondaryProfileService.remove();
  router.push({
    name: 'profile-information',
    params: {profileId: profile.id}
  });
};

const deleteProfile = (profile) => {
  profileService.delete(profile.id)
      .then(() => {
        profileService.list()
            .then((data) => {
              profiles.value = data

              toast.add({
                severity: 'success',
                summary: 'Successful',
                detail: 'Profile Deleted: ' + profile.name,
                life: 3000
              });
            });
      });
};
</script>

<template>
  <div class="surface-card p-3 flex-auto xl:ml-5">
    <div class="grid">
      <div class="col-12">
        <h3>Profiles</h3>
        <DataTable
            id="datatable"
            ref="dt"
            :value="profiles"
            dataKey="Name"
            paginator
            :rows="20"
            v-model:filters="filters"
            filterDisplay="menu">

          <Column header="" headerStyle="width:5%">
            <template #body="slotProps">
              <Button class="p-button-primary justify-content-center w-2"
                      @click="selectPrimaryProfile(slotProps.data)">
                <div class="material-symbols-outlined text-xl">play_arrow</div>
              </Button>
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
              <div class="flex justify-content-end">
                <Button icon="pi pi-trash" severity="danger" rounded text @click="deleteProfile(slotProps.data)"/>
              </div>
            </template>
          </Column>
        </DataTable>

        <ProfileCard></ProfileCard>

        <Toast/>
      </div>
    </div>
  </div>
</template>
