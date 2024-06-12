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
    <div class="col-6 lg:col-7 pl-6 p-2 text-center flex align-items-center flex-wrap">
      <div class="col-12">
        <div class="col-12 font-bold flex justify-content-start">
          <img src="/quotation_marks.png" style="width: 15px; height: auto" class="rotate-180" alt=""/>
        </div>

        <div class="font-semibold font-italic p-3 text-2xl">
          Hi, I'm Jeffrey, and love unraveling the mysteries of code performance and diving deep into the intricate
          layers of Java apps with Java Flight Recorder.
        </div>

        <div class="col-12 font-bold flex align-items-center justify-content-end">
          <img src="/quotation_marks.png" style="width: 15px; height: auto" alt=""/>
        </div>
      </div>

      <!--        <div class="col-12 font-semibold font-italic p-5 pt-5 text-1xl">-->
      <!--          Let's start learning new stuff together by selecting a profile, or generating a new one from recordings.-->
      <!--        </div>-->

<!--      <div class="col-12">-->
<!--        <Button label="Select a Primary Profile" type="button"-->
<!--                :class="{ 'p-button-raised' : activePage === 'profiles', 'p-button-outlined' : activePage === 'recordings'}"-->
<!--                class="mr-3" @click="moveTo('profiles')"></Button>-->
<!--        <Button label="Generate from Recordings" type="button"-->
<!--                :class="{ 'p-button-raised' : activePage === 'recordings', 'p-button-outlined' : activePage === 'profiles'}"-->
<!--                @click="moveTo('recordings')"></Button>-->
<!--      </div>-->
    </div>

    <div class="col-6 lg:col-5 overflow-hidden">
      <img src="/jeffrey.jpg" alt="Image" class="md:ml-auto block md:h-full border-round-right"
           style="clip-path: polygon(8% 0, 100% 0%, 100% 100%, 0 100%); width: 550px; height: auto;"/>
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
