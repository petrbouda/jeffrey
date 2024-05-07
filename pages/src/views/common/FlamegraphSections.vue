<script setup>

import router from "@/router";
import {ref} from "vue";

const radioValue = ref("Monitor Lock");
const enabledThreadMode = ref(false);

const moveTo = (targetSubPage) => {
  router.push({name: ""});
};
</script>

<template>

  <div class="card">
    <div class="grid">
      <div class="lg:col-4 md:col-6" onmouseover="this.classList.add('bg-blue-50')"
           onmouseout="this.classList.remove('bg-blue-50')">
        <div class="shadow-1 surface-card text-center h-full relative">
          <div class="p-4 bg-blue-50 text-blue-600 inline-flex justify-content-center mb-4 w-full">
            <span class="material-symbols-outlined text-5xl">sprint</span>
          </div>
          <div class="text-900 font-bold text-2xl mb-4 p-1">Execution Sample</div>
          <div class="text-700 mb-4 line-height-3 pl-3 pr-3">
            Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.
          </div>

          <div class="grid mx-5">
            <div class="col-12">
              <span class="font-bold">Async-Profiler - ctimer</span>
            </div>

            <div class="col-12 flex align-items-center">
              <Checkbox v-model="enabledThreadMode" :binary="true"/>
              <label for="ingredient1" class="ml-2">Use Thread-mode</label>
            </div>
          </div>

          <div>
            <button class="p-button p-button-text m-2" type="button">
              <span class="material-symbols-outlined text-2xl">help</span>
            </button>

            <button class="p-button p-component p-button-text m-2" type="button"
                    @click="router.push({ name: 'flamegraph-difference' })">
              <span class="p-button-label" data-pc-section="label">Enter Flamegraph</span>
            </button>
          </div>
        </div>
      </div>

      <div class="lg:col-4 md:col-6" onmouseover="this.classList.add('bg-green-50')"
           onmouseout="this.classList.remove('bg-green-50')">
        <div class="shadow-1 surface-card text-center h-full">
          <div class="p-4 bg-green-50 text-green-600 inline-flex justify-content-center mb-4 w-full">
            <span class="material-symbols-outlined text-5xl">memory</span>
          </div>
          <div class="text-900 font-bold text-2xl mb-4 p-1">Object Allocation</div>
          <div class="text-700 mb-4 line-height-3 pl-3 pr-3">
            Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.
          </div>

          <div class="grid mx-5">
            <div class="col-12">
              <span class="font-bold">Async-Profiler - New TLAB Allocation</span>
            </div>

            <div class="col-12 flex align-items-center">
              <Checkbox v-model="enabledThreadMode" :binary="true"/>
              <label for="ingredient1" class="ml-2">Use Thread-mode</label>
            </div>

            <div class="col-12 flex align-items-center">
              <Checkbox v-model="enabledThreadMode" :binary="true"/>
              <label for="ingredient1" class="ml-2">Use Total Allocation</label>
            </div>
          </div>

          <div>
            <button class="p-button p-button-text m-2" type="button">
              <span class="material-symbols-outlined text-2xl">help</span>
            </button>

            <button class="p-button p-component p-button-text m-2" type="button"
                    @click="router.push({ name: 'flamegraph-difference' })">
              <span class="p-button-label" data-pc-section="label">Show  Flamegraph</span>
            </button>
          </div>
        </div>
      </div>

      <div class="lg:col-4 md:col-6" onmouseover="this.classList.add('bg-red-50')"
           onmouseout="this.classList.remove('bg-red-50')">
        <div class="shadow-1 surface-card text-center h-full">
          <div class="p-4 bg-red-50 text-red-600 inline-flex justify-content-center mb-4 w-full">
            <span class="material-symbols-outlined text-5xl">lock</span>
          </div>
          <div class="text-900 font-bold text-2xl mb-4 p-1">Locking / Parking</div>

          <div class="flex justify-content-center flex-wrap mx-5 h-max">
            <div class="field-radiobutton px-2">
              <RadioButton id="option1" name="option" value="Monitor Lock" v-model="radioValue"/>
              <label for="option1">Monitor Lock</label>
            </div>
            <div class="field-radiobutton px-2">
              <RadioButton id="option2" name="option" value="Monitor Wait" v-model="radioValue"/>
              <label for="option2">Monitor Wait</label>
            </div>
            <div class="field-radiobutton px-2">
              <RadioButton id="option3" name="option" value="Thread Park" v-model="radioValue"/>
              <label for="option3">Thread Park</label>
            </div>
          </div>

          <div>
            <button class="p-button p-button-text m-2" type="button">
              <span class="material-symbols-outlined text-2xl">help</span>
            </button>

            <button class="p-button p-component p-button-text m-2" type="button"
                    @click="router.push({ name: 'flamegraph-difference' })">
              <span class="p-button-label" data-pc-section="label">Show  Flamegraph</span>
            </button>
          </div>
        </div>
      </div>

      <div class="lg:col-4 md:col-6" onmouseover="this.classList.add('bg-yellow-50')"
           onmouseout="this.classList.remove('bg-yellow-50')">
        <div class="shadow-1 surface-card text-center h-full">
          <div class="p-4 bg-yellow-50 text-yellow-600 inline-flex justify-content-center mb-4 w-full">
            <span class="material-symbols-outlined text-5xl">delete_forever</span>
          </div>
          <div class="text-900 font-bold text-2xl mb-4 p-1">Old Allocation Sample</div>

          <div class="flex justify-content-center flex-wrap mx-5 h-max">
            <div class="field-radiobutton px-2">
              <RadioButton id="option1" name="option" value="Monitor Lock" v-model="radioValue"/>
              <label for="option1">Monitor Lock</label>
            </div>
            <div class="field-radiobutton px-2">
              <RadioButton id="option2" name="option" value="Monitor Wait" v-model="radioValue"/>
              <label for="option2">Monitor Wait</label>
            </div>
            <div class="field-radiobutton px-2">
              <RadioButton id="option3" name="option" value="Thread Park" v-model="radioValue"/>
              <label for="option3">Thread Park</label>
            </div>
          </div>

          <div>
            <button class="p-button p-button-text m-2" type="button">
              <span class="material-symbols-outlined text-2xl">help</span>
            </button>

            <button class="p-button p-component p-button-text m-2" type="button"
                    @click="router.push({ name: 'flamegraph-difference' })">
              <span class="p-button-label" data-pc-section="label">Show  Flamegraph</span>
            </button>
          </div>
        </div>
      </div>


<!--            <div class="lg:col-4 md:col-6" onmouseover="this.classList.add('bg-pink-50')"-->
<!--                 onmouseout="this.classList.remove('bg-pink-50')">-->
      <!--        <div class="shadow-1 surface-card text-center">-->
      <!--          <div class="p-4 bg-pink-50 text-pink-600 inline-flex justify-content-center align-items-center mb-4 w-full">-->
      <!--            <span class="material-symbols-outlined text-5xl">apps</span>-->
      <!--          </div>-->
      <!--          <div class="text-900 font-medium text-2xl mb-4 p-1">Sub-Second Analysis</div>-->
      <!--          <div class="text-700 mb-4 line-height-3 pl-3 pr-3">-->
      <!--            Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.-->
      <!--          </div>-->

      <!--          <div class="flex justify-content-between flex-wrap">-->
      <!--            <button class="p-button p-button-text m-2" type="button">-->
      <!--              <span class="material-symbols-outlined text-2xl">help</span>-->
      <!--            </button>-->

      <!--            <button class="p-button p-component p-button-text m-2" type="button"-->
      <!--                    @click="router.push({ name: 'flamegraph-startup' })">-->
      <!--              <span class="p-button-label" data-pc-section="label">Enter Section</span>-->
      <!--            </button>-->
      <!--          </div>-->
      <!--        </div>-->
      <!--      </div>-->

      <!--      <div class="lg:col-4 md:col-6" onmouseover="this.classList.add('bg-orange-50')"-->
      <!--           onmouseout="this.classList.remove('bg-orange-50')">-->
      <!--        <div class="shadow-1 surface-card text-center">-->
      <!--          <div-->
      <!--              class="p-4 bg-orange-50 text-orange-600 inline-flex justify-content-center align-items-center mb-4 w-full">-->
      <!--            <span class="material-symbols-outlined text-5xl">memory</span>-->
      <!--          </div>-->
      <!--          <div class="text-900 font-medium text-2xl mb-4 p-1">Native Memory</div>-->
      <!--          <div class="text-700 mb-4 line-height-3 pl-3 pr-3">-->
      <!--            Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.-->
      <!--          </div>-->

      <!--          <div class="flex justify-content-between flex-wrap">-->
      <!--            <button class="p-button p-button-text m-2" type="button">-->
      <!--              <span class="material-symbols-outlined text-2xl">help</span>-->
      <!--            </button>-->

      <!--            <button class="p-button p-component p-button-text m-2" type="button"-->
      <!--                    @click="router.push({ name: 'native-memory' })">-->
      <!--              <span class="p-button-label" data-pc-section="label">Enter Section</span>-->
      <!--            </button>-->
      <!--          </div>-->
      <!--        </div>-->
      <!--      </div>-->

      <!--      <div class="lg:col-4 md:col-6" onmouseover="this.classList.add('bg-yellow-50')"-->
      <!--           onmouseout="this.classList.remove('bg-yellow-50')">-->
      <!--        <div class="shadow-1 surface-card text-center">-->
      <!--          <div-->
      <!--              class="p-4 bg-yellow-50 text-yellow-600 inline-flex justify-content-center align-items-center mb-4 w-full">-->
      <!--            <span class="material-symbols-outlined text-5xl">delete</span>-->
      <!--          </div>-->
      <!--          <div class="text-900 font-medium text-2xl mb-4 p-1">Garbage Collection</div>-->
      <!--          <div class="text-700 mb-4 line-height-3 pl-3 pr-3">-->
      <!--            Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.-->
      <!--          </div>-->

      <!--          <div class="flex justify-content-between flex-wrap">-->
      <!--            <button class="p-button p-button-text m-2" type="button">-->
      <!--              <span class="material-symbols-outlined text-2xl">help</span>-->
      <!--            </button>-->

      <!--            <button class="p-button p-component p-button-text m-2" type="button"-->
      <!--                    @click="router.push({ name: 'garbage-collection' })">-->
      <!--              <span class="p-button-label" data-pc-section="label">Enter Section</span>-->
      <!--            </button>-->
      <!--          </div>-->
      <!--        </div>-->
      <!--      </div>-->
    </div>
  </div>
</template>
