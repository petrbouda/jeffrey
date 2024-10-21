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

const dialogCleaner = ref(false);
const dialogCleanerTimeUnit = ref(['Minute', 'Hour', 'Day'])
const dialogCleanerSelectedTimeUnit = ref('Hour')

const dialogGenerator = ref(false);

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
</script>

<template>
  <div class="surface-card p-3 flex-auto xl:ml-5" v-if="currentProject">
    <h3>Scheduler</h3>
    <div class="text-500 mb-5">Creates periodical jobs to manage data belonging to the given project. e.g.
      <span class="font-italic">removing unnecessary old files from the repository</span>
    </div>

    <div class="grid">
      <div class="col-12 lg:col-6 p-3">
        <div class="p-3 border-round shadow-1 flex align-items-center surface-card"
             @mouseover="(e) => e.currentTarget.classList.add('shadow-2')"
             @mouseout="(e) => e.currentTarget.classList.remove('shadow-2')">
          <div class="bg-teal-100 inline-flex align-items-center justify-content-center mr-3"
               style="width: 48px; height: 48px; border-radius: 10px;">
            <span class="material-symbols-outlined text-teal-600 text-3xl">delete</span>
          </div>
          <div>
            <span class="text-900 text-xl font-medium mb-2">Repository Cleaner</span>
            <p class="mt-1 mb-0 text-600 font-medium text-sm">Task for removing old source files from the repository</p>
          </div>
          <div class="ml-auto">
            <Button text @click="dialogCleaner = true">
              <span class="material-symbols-outlined text-xl font-bold text-gray-600">add</span>
            </Button>
          </div>
        </div>
      </div>

      <div class="col-12 lg:col-6 p-3">
        <div class="p-3 border-round shadow-1 flex align-items-center surface-card"
             @mouseover="(e) => e.currentTarget.classList.add('shadow-2')"
             @mouseout="(e) => e.currentTarget.classList.remove('shadow-2')">
          <div class="bg-blue-100 inline-flex align-items-center justify-content-center mr-3"
               style="width: 48px; height: 48px; border-radius: 10px;">
            <span class="material-symbols-outlined text-blue-600 text-3xl">description</span>
          </div>
          <div>
            <span class="text-900 text-xl font-medium mb-2">Recording Generator</span>
            <p class="mt-1 mb-0 text-600 font-medium text-sm">Generates a new Recording from the repository data</p>
          </div>
          <div class="ml-auto">
            <Button text  @click="dialogGenerator = true">
              <span class="material-symbols-outlined text-xl font-bold text-gray-600">add</span>
            </Button>
          </div>
        </div>
      </div>
    </div>

    <h3 class="mt-8">Active Jobs</h3>
    <DataTable :value="activeTasks" tableStyle="min-width: 50rem">
      <Column field="job" header="Job"></Column>
      <Column field="parameters" header="Parameters"></Column>
      <Column field="quantity" header=""></Column>
    </DataTable>
  </div>

  <!-- ------------------------------------------ -->
  <!-- Dialog Window for Repository Cleaner Job   -->
  <!-- ------------------------------------------ -->
  <Dialog v-model:visible="dialogCleaner" modal header="Create a Repository Cleaner Job"
          :style="{ width: '40rem' }">
    <p>
      <span class="text-surface-500 dark:text-surface-400 block mb-4">
        Fill in a duration for how long to keep files in the repository.
        The files with the last modification date (on a filesystem)
        older than the given duration will be removed. Choose a reasonable
        time-length for the source files in the repository.
      </span>
    </p>
    <p>
      <span class="text-surface-500 dark:text-surface-400 block mb-4">

      </span>
    </p>
    <div class="flex align-items-center gap-4 mb-4">
      <label for="username" class="font-semibold w-2">Duration</label>
      <InputText id="username" class="flex-auto" autocomplete="off"/>
    </div>

    <div class="flex align-items-center gap-4 mb-4">
      <label for="username" class="font-semibold w-2">Time Unit</label>
      <SelectButton v-model="dialogCleanerSelectedTimeUnit" :options="dialogCleanerTimeUnit" aria-labelledby="basic"/>
    </div>
    <!--    <div class="flex align-items-center gap-4 mb-4">-->
    <!--      <label for="email" class="font-semibold w-2">Email</label>-->
    <!--      <InputText id="email" class="flex-auto" autocomplete="off"/>-->
    <!--    </div>-->
    <div class="flex justify-end gap-2">
      <Button type="button" label="Cancel" severity="secondary" @click="dialogCleaner = false"></Button>
      <Button type="button" label="Save" @click="visible = false"></Button>
    </div>
  </Dialog>

  <!-- -------------------------------------------- -->
  <!-- Dialog Window for Repository Generator Job   -->
  <!-- -------------------------------------------- -->
  <Dialog v-model:visible="dialogGenerator" modal header="Create a Repository Generator Job"
          :style="{ width: '40rem' }">
    <p>
      <span class="text-surface-500 dark:text-surface-400 block mb-4">
        Fill in a duration for how long to keep files in the repository.
        The files with the last modification date (on a filesystem)
        older than the given duration will be removed. Choose a reasonable
        time-length for the source files in the repository.
      </span>
    </p>
    <p>
      <span class="text-surface-500 dark:text-surface-400 block mb-4">

      </span>
    </p>
    <div class="flex align-items-center gap-4 mb-4">
      <FloatLabel variant="on">
        <DatePicker v-model="value3" inputId="on_label" showIcon iconDisplay="input" />
        <label for="on_label">On Label</label>
      </FloatLabel>

      <label for="username" class="font-semibold w-2">Generate At</label>
      <InputText id="username" class="flex-auto" autocomplete="off"/>
    </div>

    <div class="flex align-items-center gap-4 mb-4">
      <label for="username" class="font-semibold w-2">Time Unit</label>
      <SelectButton v-model="dialogCleanerSelectedTimeUnit" :options="dialogCleanerTimeUnit" aria-labelledby="basic"/>
    </div>
    <!--    <div class="flex align-items-center gap-4 mb-4">-->
    <!--      <label for="email" class="font-semibold w-2">Email</label>-->
    <!--      <InputText id="email" class="flex-auto" autocomplete="off"/>-->
    <!--    </div>-->
    <div class="flex justify-end gap-2">
      <Button type="button" label="Cancel" severity="secondary" @click="dialogCleaner = false"></Button>
      <Button type="button" label="Save" @click="visible = false"></Button>
    </div>
  </Dialog>

  <Toast/>
</template>
