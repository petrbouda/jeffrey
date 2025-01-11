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

<script setup lang="ts">
import SecondaryProfileService from "@/service/SecondaryProfileService";
import {useRouter} from "vue-router";
import ProfileCard from "@/components/ProfileCard.vue";
import {onBeforeMount, onBeforeUnmount, onMounted, ref} from "vue";
import {FilterMatchMode} from "primevue/api";
import Utils from "../service/Utils";
import ProjectsClient from "@/service/project/ProjectsClient";
import ProjectInfo from "@/service/project/model/ProjectInfo";
import ProfileInfo from "@/service/project/model/ProfileInfo";
import MessageBus from "@/service/MessageBus";

const props = defineProps<{
  primaryProjectId: string,
  activated: boolean
}>()

const router = useRouter();

const projects = ref<ProjectInfo[]>([]);
const currentProject = ref<ProjectInfo>();

const dialogVisible = ref<boolean>(false);

const filters = ref<any>({
  name: {value: null, matchMode: FilterMatchMode.CONTAINS}
});

onBeforeMount(() => {
  MessageBus.on(MessageBus.PROFILE_DIALOG_TOGGLE, () => {
    loadProjects()
  })
})

onMounted(() => {
  if (props.activated) {
    loadProjects()
  }
})

function loadProjects() : Promise<void> {
  return new ProjectsClient()
      .listWithProfiles()
      .then((data) => {
        projects.value = data

        // Find the current project (prefer the profiles from the same project as Primary Profile is)
        let foundCurrProject = data.find((project) => project.id === props.primaryProjectId)
        if (foundCurrProject) {
          currentProject.value = foundCurrProject
        }

        // Make the dialog visible
        dialogVisible.value = true
      })
}


onBeforeUnmount(() => {
  MessageBus.off(MessageBus.PROFILE_DIALOG_TOGGLE);
});

const isCurrentProject = (project: ProjectInfo) => {
  return project.id === props.primaryProjectId
}

const selectProfile = (profile: ProfileInfo) => {
  SecondaryProfileService.update(profile);
  router.go()
}
</script>

<template>
  <Dialog v-model:visible="dialogVisible" modal header=" " :style="{ width: '80%' }">
    <Dropdown v-model="currentProject" :options="projects" optionLabel="name" class="w-full mb-4 mt-1">
      <template #value="slotProps">
        <span class="font-bold">{{ slotProps.value.name }}</span>
        <span v-if="isCurrentProject(slotProps.value)"> (primary)</span>
      </template>
      <template #option="slotProps">
        <span class="font-bold">{{ slotProps.option.name }}</span>
        <span v-if="isCurrentProject(slotProps.option)"> (primary)</span>
      </template>
    </Dropdown>

    <DataTable
        id="datatable"
        ref="dt"
        :value="currentProject?.profiles"
        dataKey="Name"
        paginator
        :rows="20"
        v-model:filters="filters"
        filterDisplay="menu">

      <Column header="" headerStyle="width:5%">
        <template #body="slotProps">
          <Button class="p-button-primary justify-content-center w-2"
                  @click="selectProfile(slotProps.data)">
            <div class="material-symbols-outlined text-xl">play_arrow</div>
          </Button>
        </template>
      </Column>
      <Column field="name" header="Name" :sortable="true" :showFilterMatchModes="false">
        <template #body="slotProps">
          <span class="font-bold">{{ slotProps.data.name }}</span>
        </template>
        <template #filter="{ filterModel }">
          <InputText v-model="filterModel.value" type="text" class="p-column-filter" placeholder="Search by name"/>
        </template>
      </Column>
      <Column field="createdAt" header="Created at" :sortable="true">
        <template #body="slotProps">
          {{ Utils.formatDateTime(slotProps.data.createdAt) }}
        </template>
      </Column>
    </DataTable>

    <ProfileCard></ProfileCard>
  </Dialog>
</template>
