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
import {onMounted, ref} from 'vue';
import {useToast} from 'primevue/usetoast';
import {useRoute} from 'vue-router'
import ProjectService from "@/service/project/ProjectService";
import ProjectRepositoryService from "@/service/project/ProjectRepositoryService";
import Utils from "@/service/Utils";

const route = useRoute()

const toast = useToast();

const currentProject = ref(null);
const currentRepository = ref(null);

const repositoryService = new ProjectRepositoryService(route.params.projectId)

const inputCreateDirectoryCheckbox = ref(true);
const inputRepositoryPath = ref('')
const inputRepositoryType = ref('ASYNC_PROFILER')

onMounted(() => {
  repositoryService.get()
      .then((data) => {
        currentRepository.value = data
      });

  ProjectService.settings(route.params.projectId)
      .then((data) => {
        currentProject.value = data
      });
});

function updateRepositoryLink() {
  if (!Utils.isNotBlank(inputRepositoryPath.value)) {
    toast.add({severity: 'error', summary: 'Repository Link', detail: 'Repository path is required', life: 5000});
    return
  }

  repositoryService.create(inputRepositoryPath.value, inputRepositoryType.value, inputCreateDirectoryCheckbox.value)
      .then(() => {
        repositoryService.get()
            .then((data) => {
              currentRepository.value = data
              toast.add({
                severity: 'success',
                summary: 'Repository Link',
                detail: 'Repository link has been updated',
                life: 5000
              });
            });

        inputRepositoryPath.value = ''
        inputCreateDirectoryCheckbox.value = true
      })
      .catch((error) => {
        toast.add({
          severity: 'error',
          summary: 'Cannot link a Repository',
          detail: error.response.data,
          life: 5000
        });
      })
}

function unlinkRepository() {
  repositoryService.delete()
      .then(() => {
        currentRepository.value = null
        toast.add({
          severity: 'success',
          summary: 'Repository Link',
          detail: 'Repository has been unlinked',
          life: 5000
        });
      });
}

function generateRecording() {
  repositoryService.generateRecording()
      .then(() => {
        toast.add({severity: 'success', summary: 'Recording', detail: 'New Recording generated', life: 5000});
      });
}

</script>

<template>
  <div class="surface-card p-3 flex-auto xl:ml-5" v-if="currentProject">
    <div class="flex gap-5 flex-column-reverse md:flex-row">
      <div class="flex-auto p-fluid">
        <div class="surface-section" v-if="currentRepository && currentRepository.active">
          <h3>Current Repository</h3>
          <div class="text-500 mb-5">Linked Repository is a directory with the latest recordings from the application.
          Generate a concrete recording form the repository and make a new Profile from it.</div>
          <ul class="list-none p-0 m-0">
            <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
              <div class="text-500 w-6 md:w-2 font-medium">Repository Path</div>
              <div class="text-900 w-full md:w-10 md:flex-order-0 flex-order-1">
                <span>{{ currentRepository.repositoryPath }}</span>
                <Tag severity="success" class="ml-2" value="Directory Exists" v-if="currentRepository.directoryExists"></Tag>
                <Tag severity="danger" class="ml-2" value="Directory Does Not Exist" v-if="!currentRepository.directoryExists"></Tag>
              </div>
<!--              <div class="w-6 md:w-2 flex justify-content-end">-->
<!--                <button pbutton="" pripple="" label="Edit" icon="pi pi-pencil"-->
<!--                        class="p-element p-ripple p-button-text p-button p-component"><span-->
<!--                    class="p-button-icon p-button-icon-left pi pi-pencil" aria-hidden="true"></span><span-->
<!--                    class="p-button-label">Edit</span><span class="p-ink"></span></button>-->
<!--              </div>-->
            </li>
            <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
              <div class="text-500 w-6 md:w-2 font-medium">Repository Type</div>
              <div class="text-900 w-full md:w-10 md:flex-order-0 flex-order-1">{{ currentRepository.repositoryType }}</div>
<!--              <div class="w-6 md:w-2 flex justify-content-end">-->
<!--                <button pbutton="" pripple="" label="Edit" icon="pi pi-pencil"-->
<!--                        class="p-element p-ripple p-button-text p-button p-component"><span-->
<!--                    class="p-button-icon p-button-icon-left pi pi-pencil" aria-hidden="true"></span><span-->
<!--                    class="p-button-label">Edit</span><span class="p-ink"></span></button>-->
<!--              </div>-->
            </li>
            <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
              <Button label="Create a new Recording" class="w-auto" @click="generateRecording"/>
              <Button label="Unlink the Repository" severity="danger" class="ml-3 w-auto"
                      @click="unlinkRepository" v-if="currentRepository && currentRepository.active"/>
            </li>
          </ul>
        </div>

        <div class="surface-section" v-if="!currentRepository || !currentRepository.active">
          <h3 class="font-medium text-3xl text-900 mb-3">Link a Repository</h3>
          <div class="text-500 mb-5">Link a directory with the latest recordings on the host, e.g. <span class="font-italic">/home/my-account/recordings</span></div>
          <ul class="list-none p-0 m-0">
            <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
              <div class="text-500 w-6 md:w-2 font-medium">Repository Path</div>
              <div class="text-900 w-full md:w-10 md:flex-order-0 flex-order-1">
                <InputText id="repository_path" v-model="inputRepositoryPath"
                           :placeholder="currentRepository && currentRepository.repositoryPath"/>
              </div>
            </li>
            <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
              <div class="text-500 w-6 md:w-2 font-medium">Repository Type</div>
              <div class="text-900 h-full w-full md:w-8 md:flex-order-0 flex-order-1">
                <div class="flex flex-wrap gap-4">
                  <div class="flex align-items-center">
                    <RadioButton v-model="inputRepositoryType" value="ASYNC_PROFILER"/>
                    <label for="ingredient1" class="ml-2">Async-Profiler</label>
                  </div>
                  <div class="flex align-items-center">
                    <RadioButton v-model="inputRepositoryType" value="JDK" disabled/>
                    <label for="ingredient2" class="ml-2">JDK</label>
                  </div>
                </div>
              </div>
            </li>
            <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
              <div class="text-500 w-6 md:w-2 font-medium">Create a directory <br/> (if does not exist)</div>
              <div class="text-900 h-full w-full md:w-8 md:flex-order-0 flex-order-1">
                <Checkbox v-model="inputCreateDirectoryCheckbox" :binary="true"/>
              </div>
<!--              <div class="w-6 md:w-2 flex justify-content-end">-->
<!--                <button pbutton="" pripple="" label="Edit" icon="pi pi-pencil"-->
<!--                        class="p-element p-ripple p-button-text p-button p-component"><span-->
<!--                    class="p-button-icon p-button-icon-left pi pi-pencil" aria-hidden="true"></span><span-->
<!--                    class="p-button-label">Edit</span><span class="p-ink"></span></button>-->
<!--              </div>-->
            </li>
            <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
              <Button label="Update a link to the Repository" class="w-auto" @click="updateRepositoryLink"/>
            </li>
          </ul>
        </div>
      </div>
    </div>
  </div>

  <Toast/>
</template>
