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

  repositoryService.create(inputRepositoryPath.value, inputCreateDirectoryCheckbox.value)
      .then(() => {
        repositoryService.get()
            .then((data) => {
              currentRepository.value = data
              toast.add({severity: 'success', summary: 'Repository Link', detail: 'Repository link has been updated', life: 5000});
            });

        inputRepositoryPath.value = ''
        inputCreateDirectoryCheckbox.value = true
      });
}

function unlinkRepository() {
  repositoryService.delete()
      .then(() => {
        currentRepository.value = null
        toast.add({severity: 'success', summary: 'Repository Link', detail: 'Repository has been unlinked', life: 5000});
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
          <Panel class="mb-3" header="Current Repository" toggleable v-if="currentRepository && currentRepository.active">
            <div class="mb-2">
              <Tag severity="success" value="Directory Exists" v-if="currentRepository.directoryExists"></Tag>
              <Tag severity="danger" value="Directory Does Not Exist" v-if="!currentRepository.directoryExists"></Tag>
              <span class="ml-2 font-italic font-bold">{{ currentRepository.repositoryPath }}</span>
            </div>

            <div class="mt-3">
              <Button label="Create a new Recording" class="w-auto" @click="generateRecording"/>
            </div>
          </Panel>

        <Panel header="Repository Linking / Unlinking" toggleable :collapsed="currentRepository && currentRepository.active">
          <div class="mb-3">
            <label for="repository_path" class="block font-normal text-900 mb-3">Path to a Repository: <span class="font-italic">(e.g. /home/my-user/recordings)</span></label>
            <InputText id="repository_path" v-model="inputRepositoryPath" :placeholder="currentRepository && currentRepository.repositoryPath" />
          </div>
          <div class="mb-3 flex align-items-center">
            <Checkbox v-model="inputCreateDirectoryCheckbox" :binary="true"/>
            <span class="ml-2 font-normal text-base text-color-primary">Create a directory (if not exists)</span>
          </div>
          <div>
            <Button label="Update a link to the Repository" class="w-auto" @click="updateRepositoryLink"/>
            <Button label="Unlink the Repository" severity="danger" class="ml-3 w-auto"
                    @click="unlinkRepository" v-if="currentRepository && currentRepository.active" />
          </div>
        </Panel>
      </div>
    </div>
  </div>

  <Toast/>
</template>
