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
import {onBeforeMount, onBeforeUnmount, ref} from 'vue';
import {useRoute, useRouter} from "vue-router";
import ProjectSettingsService from "@/service/project/ProjectSettingsService";
import MessageBus from "@/service/MessageBus";

const route = useRoute()
const router = useRouter();

const activePage = ref(route.name);

const currentProject = ref(null);

const settingsService = new ProjectSettingsService(route.params.projectId)

onBeforeMount(() => {
  settingsService.get()
      .then((data) => {
        currentProject.value = data
      });

  MessageBus.on(MessageBus.UPDATE_PROJECT_SETTINGS, (content) => {
    currentProject.value = content
  });
});

onBeforeUnmount(() => {
  MessageBus.off(MessageBus.UPDATE_PROJECT_SETTINGS);
});

const moveTo = (targetSubPage) => {
  activePage.value = targetSubPage

  router.push({
    name: targetSubPage,
  });
};
</script>

<template>
  <div class="surface-section px-4 py-4 lg:py-5 lg:px-6 h-full border-round" v-if="currentProject">
    <div class="flex flex-column md:flex-row w-full justify-content-between md:align-items-center">
      <div><h2 class="mt-0 mb-6">{{ currentProject.name }}</h2>
        <p class="mt-0 mb-0 text-500" v-if="currentProject.description != null">{{ currentProject.description }}</p>
      </div>
    </div>
    <div class="p-fluid flex flex-column lg:flex-row">
      <ul class="list-none m-0 p-0 flex flex-row lg:flex-column justify-content-between lg:justify-content-start mb-5 lg:mb-0">
        <li class="mt-1">
          <a @click="moveTo('projects-profiles')" :class="{ 'surface-200' : activePage === 'projects-profiles'}"
             class="p-ripple p-element lg:w-15rem flex align-items-center cursor-pointer p-3 border-round hover:surface-200 transition-duration-150 transition-colors">
            <span class="material-symbols-outlined md:mr-2 text-600 text-2xl">monitoring</span>
            <span class="font-medium hidden md:block text-700">Profiles</span><span class="p-ink"></span>
          </a>
        </li>
        <li class="mt-1">
          <a @click="moveTo('projects-recordings')" :class="{ 'surface-200' : activePage === 'projects-recordings'}"
             class="p-ripple p-element lg:w-15rem flex align-items-center cursor-pointer p-3 border-round hover:surface-200 transition-duration-150 transition-colors">
            <span class="material-symbols-outlined md:mr-2 text-600 text-2xl">description</span>
            <span class="font-medium hidden md:block text-700">Recordings</span><span class="p-ink"></span>
          </a>
        </li>
        <li class="mt-1">
          <a @click="moveTo('projects-repository')" :class="{ 'surface-200' : activePage === 'projects-repository'}"
             class="p-ripple p-element lg:w-15rem flex align-items-center cursor-pointer p-3 border-round hover:surface-200 transition-duration-150 transition-colors">
            <span class="material-symbols-outlined md:mr-2 text-600 text-2xl">home_storage</span>
            <span class="font-medium hidden md:block text-700">Repository</span><span class="p-ink"></span>
          </a>
        </li>
        <li class="mt-1">
          <a @click="moveTo('projects-scheduler')" :class="{ 'surface-200' : activePage === 'projects-scheduler'}"
             class="p-ripple p-element lg:w-15rem flex align-items-center cursor-pointer p-3 border-round hover:surface-200 transition-duration-150 transition-colors">
            <span class="material-symbols-outlined md:mr-2 text-600 text-2xl">timer</span>
            <span class="font-medium hidden md:block text-700">Scheduler</span><span class="p-ink"></span>
          </a>
        </li>
        <li class="mt-1">
          <a @click="moveTo('projects-settings')" :class="{ 'surface-200' : activePage === 'projects-settings'}"
             class="p-ripple p-element lg:w-15rem flex align-items-center cursor-pointer p-3 border-round hover:surface-200 transition-duration-150 transition-colors">
            <span class="material-symbols-outlined md:mr-2 text-600 text-2xl">settings</span>
            <span class="font-medium hidden md:block text-700">Settings</span><span class="p-ink"></span>
          </a>
        </li>
      </ul>

      <router-view></router-view>
    </div>
  </div>
</template>
