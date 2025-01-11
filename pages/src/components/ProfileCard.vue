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

<script setup lang="ts">
import {onBeforeUnmount, onMounted, ref} from 'vue';
import {useToast} from 'primevue/usetoast';
import MessageBus from "@/service/MessageBus";

const props = defineProps(['op']);

const toast = useToast();
const op = ref(null)

onMounted(() => {
  MessageBus.on(MessageBus.PROFILE_CARD_TOGGLE, function (event) {
    op.value.toggle(event);
  });
});

onBeforeUnmount(() => {
  MessageBus.off(MessageBus.PROFILE_CARD_TOGGLE);
});
</script>

<template>
  <OverlayPanel ref="op" class="p-2">
    <div class="font-medium text-3xl text-900 mb-3">Applicant Profile</div>
    <div class="text-500 mb-5">Egestas sed tempus urna et pharetra pharetra massa massa ultricies.</div>
    <ul class="list-none p-0 m-0 border-top-1 surface-border">
      <li class="flex align-items-center py-3 px-2 flex-wrap surface-50">
        <div class="text-500 w-full md:w-2 font-medium">Name</div>
        <div class="text-900 w-full md:w-10">Elliot Alderson</div>
      </li>
      <li class="flex align-items-center py-3 px-2 flex-wrap">
        <div class="text-500 w-full md:w-2 font-medium">Bio</div>
        <div class="text-900 w-full md:w-10 line-height-3"> Faucibus pulvinar elementum integer enim neque volutpat ac
          tincidunt vitae. Commodo odio aenean sed adipiscing diam donec adipiscing tristique. Eget felis eget nunc
          lobortis mattis aliquam faucibus purus in.
        </div>
      </li>
      <li class="flex align-items-center py-3 px-2 flex-wrap surface-50">
        <div class="text-500 w-full md:w-2 font-medium">Salary Expectation</div>
        <div class="text-900 w-full md:w-10">$150,000</div>
      </li>
      <li class="flex align-items-center py-3 px-2 flex-wrap">
        <div class="text-500 w-full md:w-2 font-medium">Skills</div>
        <div class="text-900 w-full md:w-10"><span class="p-tag p-component p-tag-rounded mr-2" data-pc-name="tag"
                                                   data-pc-section="root"><!----><span class="p-tag-value"
                                                                                       data-pc-section="value">Algorithms</span></span><span
            class="p-tag p-component p-tag-success p-tag-rounded mr-2" data-pc-name="tag" data-pc-section="root"><!----><span
            class="p-tag-value" data-pc-section="value">Javascript</span></span><span
            class="p-tag p-component p-tag-danger p-tag-rounded mr-2" data-pc-name="tag"
            data-pc-section="root"><!----><span
            class="p-tag-value" data-pc-section="value">Python</span></span><span
            class="p-tag p-component p-tag-warning p-tag-rounded" data-pc-name="tag"
            data-pc-section="root"><!----><span class="p-tag-value" data-pc-section="value">SQL</span></span></div>
      </li>
      <li class="flex align-items-center py-3 px-2 flex-wrap surface-50">
        <div class="text-500 w-full md:w-2 font-medium">Repositories</div>
        <div class="text-900 w-full md:w-10">
          <div class="grid mt-0 mr-0">
            <div class="col-12 md:col-6">
              <div class="p-3 border-1 surface-border border-round surface-card">
                <div class="text-900 mb-2"><i class="pi pi-github mr-2"></i><span
                    class="font-medium">PrimeFaces</span></div>
                <div class="text-700">Ultimate UI Component Suite for JavaServer Faces</div>
              </div>
            </div>
            <div class="col-12 md:col-6">
              <div class="p-3 border-1 surface-border border-round surface-card">
                <div class="text-900 mb-2"><i class="pi pi-github mr-2"></i><span class="font-medium">PrimeNG</span>
                </div>
                <div class="text-700">The Most Complete Angular UI Component Library</div>
              </div>
            </div>
            <div class="col-12 md:col-6">
              <div class="p-3 border-1 surface-border border-round surface-card">
                <div class="text-900 mb-2"><i class="pi pi-github mr-2"></i><span
                    class="font-medium">PrimeReact</span></div>
                <div class="text-700">Advanced UI Components for ReactJS</div>
              </div>
            </div>
            <div class="col-12 md:col-6">
              <div class="p-3 border-1 surface-border border-round surface-card">
                <div class="text-900 mb-2"><i class="pi pi-github mr-2"></i><span class="font-medium">PrimeVue</span>
                </div>
                <div class="text-700">The Most Powerful Vue UI Component Library</div>
              </div>
            </div>
          </div>
        </div>
      </li>
    </ul>
  </OverlayPanel>
</template>
