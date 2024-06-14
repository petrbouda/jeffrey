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
import PrimaryProfileService from '@/service/PrimaryProfileService';
import Utils from '../service/Utils';
import ProfileCard from "@/components/ProfileCard.vue";
import {useRouter} from "vue-router";
import SecondaryProfileService from "@/service/SecondaryProfileService";
import ProfileService from "@/service/ProfileService";
import FormattingService from "@/service/FormattingService";
import GlobalVars from "@/service/GlobalVars";

const toast = useToast();
const profiles = ref(null);
const dt = ref(null);
const filters = ref({
  name: {value: null, matchMode: FilterMatchMode.CONTAINS}
});

const router = useRouter();

const clearCallback = ref(null)
const uploadUrl = GlobalVars.url + "/recordings/uploadAndInit"

onMounted(() => {
  ProfileService.list().then((data) => (profiles.value = data));
});

function onTemplatedUpload() {
  clearCallback.value()

  ProfileService.list()
      .then((data) => (profiles.value = data));
}

function onUpload(upload, clear) {
  upload();
  clearCallback.value = clear;
}

function onUploadError(response) {
  toast.add({severity: 'error', summary: 'Upload Failed', detail: response.xhr.responseText, life: 3000});
  clearCallback.value()
}

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

// const toggle = (event) => {
//   MessageBus.emit(MessageBus.PROFILE_CARD_TOGGLE, event)
// };
</script>

<template>
  <div class="grid">
    <div class="col-12">
      <FileUpload name="files[]" :url="uploadUrl" @upload="onTemplatedUpload()" @error="onUploadError"
                  :multiple="true">
        <template #header="{ chooseCallback, uploadCallback, clearCallback, files }">
          <div class="flex flex-wrap justify-content-between align-items-center flex-1 gap-2">
            <div class="flex gap-2">
              <Button @click="chooseCallback()" icon="pi pi-images" rounded outlined></Button>
              <Button
                  @click="onUpload(function() { uploadCallback() }, function() { clearCallback() })"
                  icon="pi pi-cloud-upload" rounded outlined
                  severity="success" :disabled="!files || files.length === 0"></Button>
              <Button @click="clearCallback()" icon="pi pi-times" rounded outlined severity="danger"
                      :disabled="!files || files.length === 0"></Button>
            </div>
          </div>
        </template>

        <template #content="{ files, uploadedFiles, removeUploadedFileCallback, removeFileCallback }">
          <div v-if="files.length > 0">
            <div style="width: 100%;">
              <div v-for="(file, index) of files" :key="file.name + file.type + file.size"
                   class="card flex flex-wrap border-1 surface-border align-items-center gap-3">
                <Button icon="pi pi-times" @click="removeFileCallback(index)" outlined rounded severity="danger"/>

                <div style="width: 92%">
                  <div class="font-semibold">{{ file.name }}</div>
                  <div>{{ FormattingService.formatBytes(file.size) }}</div>
                </div>

              </div>
            </div>
          </div>
        </template>

        <template #empty>
          <div class="flex align-items-center justify-content-center flex-column">
            <i class="pi pi-cloud-upload border-2 border-circle p-3 text-6xl text-400 border-400"/>
          </div>
        </template>
      </FileUpload>
    </div>

    <div class="col-12">
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
            <!--            <Button icon="pi pi-info" outlined severity="secondary" class="mr-2"-->
            <!--                    @click="toggle"/>-->
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
    </div>
  </div>
</template>
