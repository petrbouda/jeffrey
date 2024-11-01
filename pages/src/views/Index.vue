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

import {onMounted, ref} from "vue";
import ProjectCard from "@/components/ProjectCard.vue";
import ProjectsService from "@/service/project/ProjectsService";
import {useToast} from "primevue/usetoast";

const visibleCreateProjectModal = ref(false);
const newProjectName = ref(null);
const currentProjects = ref(null);

const toast = useToast();

onMounted(() => {
  updateProjects()
});

const createProject = () => {
  ProjectsService.create(newProjectName.value)
      .then(() => {
        updateProjects()
        newProjectName.value = null;
        visibleCreateProjectModal.value = false;
      })
      .catch((error) => {
        console.log(error)
        toast.add({
          severity: 'error',
          summary: 'Cannot create a project',
          detail: error.response.data,
          life: 3000
        });
      });
};

function updateProjects() {
  ProjectsService.list()
      .then((data) => {
        currentProjects.value = data;
      });
}

const openNewProjectModal = () => {
  newProjectName.value = null
  visibleCreateProjectModal.value = true;
};
</script>

<template>
  <div class="card">
    <div class="grid">
      <ProjectCard v-for="project in currentProjects" :project="project"></ProjectCard>

      <div class="col-12 md:col-6 xl:col-3 p-3">
        <div class="surface-card shadow-1 p-3 h-full flex justify-content-center align-items-center"
             style="border-radius: 6px;"
             @click="openNewProjectModal"
             @mouseover="(e) => e.currentTarget.classList.add('shadow-3', 'cursor-pointer')"
             @mouseout="(e) => e.currentTarget.classList.remove('shadow-3', 'cursor-pointer')">
          <div class="grid justify-content-center">
            <div class="col-12 flex justify-content-center material-symbols-outlined p-3 text-6xl text-400 border-400">
              add
            </div>
            <div class="col-12 flex justify-content-center text-xl font-medium text-400">Add a New Project</div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <div class="grid">
    <div class="col-12">
      <div class="card">
        <router-view></router-view>
      </div>
    </div>
  </div>

  <Dialog v-model:visible="visibleCreateProjectModal" modal header="Create a New Project" :style="{ width: '500px' }">
    <span class="text-surface-500 dark:text-surface-400 block mb-3">Project's Name</span>
    <div>
      <input type="text" v-model="newProjectName" class="p-inputtext p-component p-inputtext-fluid w-full" autofocus
             @keyup.enter="createProject">
    </div>
    <div class="grid mt-4">
      <Button class="col-4" type="button" label="Cancel" severity="secondary"
              @click="visibleCreateProjectModal = false"></Button>
      <Button class="col-4 ml-3" type="button" label="Save" @click="createProject"></Button>
    </div>
  </Dialog>

  <Toast/>
</template>
