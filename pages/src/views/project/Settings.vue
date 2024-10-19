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

const route = useRoute()

const toast = useToast();

const currentProject = ref(null);

onMounted(() => {
  ProjectService.settings(route.params.projectId)
      .then((data) => {
        currentProject.value = data
      });
});
</script>

<template>
  <div class="surface-card p-3 flex-auto xl:ml-5" v-if="currentProject">
    <div class="flex gap-5 flex-column-reverse md:flex-row">
      <div class="flex-auto p-fluid">
        <h3>Project's Settings</h3>

        <div class="mb-4"><label for="email" class="block font-normal text-900 mb-2">Name</label>
          <input id="email" type="text" :placeholder="currentProject.name" class="p-inputtext p-component p-element">
        </div>
        <div class="mb-4"><label for="bio" class="block font-normal text-900 mb-2">Description</label>
          <textarea id="bio" type="text" pinputtextarea="" rows="5"
                    :placeholder="currentProject.description"
                    class="p-inputtextarea p-inputtext p-component p-element p-inputtextarea-resizable"
                    style="height: 119px; overflow: hidden;"></textarea>
        </div>
        <div class="mb-4"><label for="website" class="block font-normal text-900 mb-2">URL</label>
          <div class="p-inputgroup"><span class="p-inputgroup-addon">https://</span>
            <input id="website" type="text" pinputtext="" class="p-inputtext p-component p-element">
          </div>
        </div>
        <div class="mb-4"><label for="company" class="block font-normal text-900 mb-2">Company</label><input
            id="company" type="text" pinputtext="" class="p-inputtext p-component p-element"></div>
        <div class="mb-4"><label for="visibility" class="block font-normal text-900 mb-2">Profile Visibility</label>
          <div class="flex align-items-center">
            <p-checkbox inputid="visibility" class="p-element ng-untouched ng-pristine ng-valid">
              <div class="p-checkbox p-component">
                <div class="p-hidden-accessible">
                  <input type="checkbox" value="undefined" id="visibility" aria-checked="false"></div>
                <div class="p-checkbox-box"><!----></div>
              </div><!----></p-checkbox>
            <span
                class="ml-2 font-normal text-base text-color-primary">Make profile private and hide all activity</span>
          </div>
        </div>
        <div>
          <button pbutton="" pripple="" label="Update Profile"
                  class="p-element p-ripple w-auto mt-3 p-button p-component">
            <span class="p-button-label">Update Profile</span>
            <span class="p-ink"></span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
