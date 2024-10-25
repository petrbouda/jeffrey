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
import {useRoute, useRouter} from 'vue-router'
import ProjectSettingsService from "@/service/project/ProjectSettingsService";
import ProjectService from "@/service/project/ProjectService";
import MessageBus from "@/service/MessageBus";

const route = useRoute()
const router = useRouter();

const toast = useToast();

const settingsService = new ProjectSettingsService(route.params.projectId)
const projectService = new ProjectService(route.params.projectId)

const currentProject = ref(null);
const inputProjectName = ref();

onMounted(() => {
  settingsService.get()
      .then((data) => {
        currentProject.value = data
      });
});

function updateProject() {
  settingsService.update(inputProjectName.value)
      .then(() => {
        toast.add({severity: 'success', summary: 'Project', detail: 'Project has been updated', life: 3000});
        inputProjectName.value = null;
        // Update the current project
        settingsService.get()
            .then((data) => {
              currentProject.value = data
              MessageBus.emit(MessageBus.UPDATE_PROJECT_SETTINGS, data)
            });
      })
}

function deleteProject() {
  projectService.delete()
      .then(() => {
        toast.add({severity: 'success', summary: 'Project', detail: 'Project has been deleted', life: 3000});
        router.push({
          name: 'index'
        });
      })
}
</script>

<template>
  <div class="surface-card p-3 flex-auto xl:ml-5" v-if="currentProject">
    <div class="flex gap-5 flex-column-reverse md:flex-row">
      <div class="flex-auto p-fluid">
        <h3>Settings</h3>

        <div class="mb-4"><label for="name" class="block font-normal text-900 mb-2">Name</label>
          <input id="name" type="text" v-model="inputProjectName" :placeholder="currentProject.name"
                 class="p-inputtext p-component p-element">
        </div>
        <!--        <div class="mb-4"><label for="bio" class="block font-normal text-900 mb-2">Description</label>-->
        <!--          <textarea id="bio" type="text" pinputtextarea="" rows="5"-->
        <!--                    :placeholder="currentProject.description"-->
        <!--                    class="p-inputtextarea p-inputtext p-component p-element p-inputtextarea-resizable"-->
        <!--                    style="height: 119px; overflow: hidden;"></textarea>-->
        <!--        </div>-->
        <!--        <div class="mb-4"><label for="website" class="block font-normal text-900 mb-2">URL</label>-->
        <!--          <div class="p-inputgroup"><span class="p-inputgroup-addon">https://</span>-->
        <!--            <input id="website" type="text" pinputtext="" class="p-inputtext p-component p-element">-->
        <!--          </div>-->
        <!--        </div>-->
        <!--        <div class="mb-4"><label for="company" class="block font-normal text-900 mb-2">Company</label><input-->
        <!--            id="company" type="text" pinputtext="" class="p-inputtext p-component p-element"></div>-->
        <!--        <div class="mb-4"><label for="visibility" class="block font-normal text-900 mb-2">Profile Visibility</label>-->
        <!--          <div class="flex align-items-center">-->
        <!--            <p-checkbox inputid="visibility" class="p-element ng-untouched ng-pristine ng-valid">-->
        <!--              <div class="p-checkbox p-component">-->
        <!--                <div class="p-hidden-accessible">-->
        <!--                  <input type="checkbox" value="undefined" id="visibility" aria-checked="false"></div>-->
        <!--                <div class="p-checkbox-box">&lt;!&ndash;&ndash;&gt;</div>-->
        <!--              </div>&lt;!&ndash;&ndash;&gt;</p-checkbox>-->
        <!--            <span-->
        <!--                class="ml-2 font-normal text-base text-color-primary">Make profile private and hide all activity</span>-->
        <!--          </div>-->
        <!--        </div>-->
        <div>
          <Button type="button" label="Update Project" class="w-3" @click="updateProject"/>
          <Button type="button" label="Delete Project" severity="danger" class="w-3 ml-3" @click="deleteProject"/>
        </div>
      </div>
    </div>
  </div>

  <Toast/>
</template>
