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
import router from "@/router";

const props = defineProps([
  'project'
]);

const moveToProject = (projectId) => {
  router.push({
    name: "projects-profiles",
    params: {projectId: projectId},
  });
};
</script>

<template>
  <div class="col-12 md:col-6 xl:col-3 p-3">
    <div class="surface-card shadow-1 p-3 h-full" style="border-radius: 6px;"
         @click="() => moveToProject(props.project.id)"
         @mouseover="(e) => e.currentTarget.classList.add('shadow-3', 'cursor-pointer')"
         @mouseout="(e) => e.currentTarget.classList.remove('shadow-3', 'cursor-pointer')">
      <div class="flex justify-content-between align-items-start">
        <div>
          <div class="text-xl font-medium text-900 mb-2">{{ props.project.name }}</div>
          <p class="mt-0 mb-3 text-600">PNG image - 971 KB</p></div>
      </div>
      <ul class="list-none m-0 p-0">
        <li class="px-0 py-2 flex justify-content-between align-items-center border-bottom-1 surface-border">
          <span class="text-600 font-medium text-sm">Created</span><span class="text-900 font-medium text-sm">Yesterday 11:30</span>
        </li>
        <li class="px-0 py-2 flex justify-content-between align-items-center border-bottom-1 surface-border">
          <span class="text-600 font-medium text-sm">Dimensions</span><span
            class="text-900 font-medium text-sm">2732x1690</span></li>
        <li class="px-0 py-2 flex justify-content-between align-items-center"><span
            class="text-600 font-medium text-sm">Resolution</span><span
            class="text-900 font-medium text-sm">144x144</span>
        </li>
      </ul>
    </div>
  </div>
</template>
