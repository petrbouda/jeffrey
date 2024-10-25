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
import {onMounted, ref} from "vue";
import ProjectsService from "@/service/project/ProjectsService";

const activePage = ref('profiles');
const currentProjects = ref(null);

onMounted(() => {
  ProjectsService.list()
      .then((data) => {
        currentProjects.value = data;
      });
});

const moveTo = (targetSubPage) => {
  activePage.value = targetSubPage

  router.push({
    name: targetSubPage,
  });
};
</script>

<template>
  <div class="layout-content m-4">
    <div class="grid auto-ro grid-nogutter bg-white border-round mb-4">
      <div class="col-12 p-2 text-center flex align-items-center flex-wrap">
        <div class="col-2 font-bold flex justify-content-start">
          <img @mouseover="(e) => e.currentTarget.classList.add('cursor-pointer')"
               @mouseout="(e) => e.currentTarget.classList.remove('cursor-pointer')"
               @click="moveTo('index')"
              src="/jeffrey_small.png" style="width:70px; height: auto; border-radius: 5px" alt=""/>
        </div>

        <div class="col-8">
<!--          <Button label="Select a Primary Profile" type="button"-->
<!--                  :class="{ 'p-button-raised' : activePage === 'profiles', 'p-button-outlined' : activePage !== 'profiles'}"-->
<!--                  class="mr-3" @click="moveTo('index-profiles')"></Button>-->
<!--          <Button label="Generate from Recordings" type="button"-->
<!--                  :class="{ 'p-button-raised' : activePage === 'recordings', 'p-button-outlined' : activePage !== 'recordings'}"-->
<!--                  @click="moveTo('index-recordings')" class="mr-3"></Button>-->
        </div>
        <div class="col-2">
        </div>
      </div>
    </div>

    <router-view></router-view>
  </div>
</template>
