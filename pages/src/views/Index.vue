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

const activePage = ref('profiles');

onMounted(() => {
  router.push({
    name: 'profiles'
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
  <div class="grid grid-nogutter bg-white border-round mb-4">
    <div class="col-8 pl-6 p-2 text-center flex align-items-center flex-wrap">
      <div class="col-12">
        <div class="col-12 font-bold flex justify-content-start">
          <img src="/quotation_marks.png" style="width: 15px; height: auto" class="rotate-180" alt=""/>
        </div>

        <div class="font-semibold font-italic p-3 text-2xl">
          Hi, I'm Jeffrey, and love diving deep into the intricate
          layers of Java apps with JDK Flight Recorder.
        </div>

        <div class="col-12 font-bold flex align-items-center justify-content-end">
          <img src="/quotation_marks.png" style="width: 15px; height: auto" alt=""/>
        </div>
      </div>

      <!--        <div class="col-12 font-semibold font-italic p-5 pt-5 text-1xl">-->
      <!--          Let's start learning new stuff together by selecting a profile, or generating a new one from recordings.-->
      <!--        </div>-->

      <div class="col-12">
        <Button label="Select a Primary Profile" type="button"
                :class="{ 'p-button-raised' : activePage === 'profiles', 'p-button-outlined' : activePage === 'recordings'}"
                class="mr-3" @click="moveTo('profiles')"></Button>
        <Button label="Generate from Recordings" type="button"
                :class="{ 'p-button-raised' : activePage === 'recordings', 'p-button-outlined' : activePage === 'profiles'}"
                @click="moveTo('recordings')"></Button>
      </div>
    </div>

    <div class="col-4 overflow-hidden">
      <img src="/jeffrey.jpg" alt="Image" class="ml-auto block h-full border-round-right"
           style="clip-path: polygon(8% 0, 100% 0%, 100% 100%, 0 100%); width: 275px; height: auto;"/>
    </div>
  </div>

  <div class="grid">
    <div class="col-12">
      <div class="card">
        <router-view></router-view>
      </div>
    </div>
  </div>
</template>
