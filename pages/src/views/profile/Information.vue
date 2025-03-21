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
import InformationService from '@/service/InformationService';
import FormattingService from "@/service/FormattingService";
import BreadcrumbComponent from "@/components/BreadcrumbComponent.vue";
import {useRoute} from "vue-router";

const route = useRoute()

let info = null;
let active = ref(0);

let items = ref([]);

let section = ref(null)
let formatMap = null

onMounted(() => {
  formatMap = {
    'JVM Information - JVM Start Time': function (value) {
      return value + " (" + new Date(parseInt(value, 10)) + ")"
    },
    'GC Heap Configuration - Minimum Heap Size': function (value) {
      return FormattingService.formatBytes(value)
    },
    'GC Heap Configuration - Maximum Heap Size': function (value) {
      return FormattingService.formatBytes(value)
    },
    'GC Heap Configuration - Initial Heap Size': function (value) {
      return FormattingService.formatBytes(value)
    },
    'TLAB Configuration - Minimum TLAB Size': function (value) {
      return FormattingService.formatBytes(value)
    },
    'Young Generation Configuration - Minimum Young Generation Size': function (value) {
      return FormattingService.formatBytes(value)
    },
    'Young Generation Configuration - Maximum Young Generation Size': function (value) {
      return FormattingService.formatBytes(value)
    },
    'Container Configuration - Container Host Total Memory': function (value) {
      return FormattingService.formatBytes(value)
    },
    'Container Configuration - Memory and Swap Limit': function (value) {
      return FormattingService.formatBytes(value)
    },
    'Container Configuration - Memory Limit': function (value) {
      return FormattingService.formatBytes(value)
    },
    'Container Configuration - Memory Soft Limit': function (value) {
      return FormattingService.formatBytes(value)
    },
  }

  InformationService.info(route.params.projectId, route.params.profileId)
      .then((data) => {
        info = data;

        Object.keys(info).forEach(function (key, index) {
          let formatted = key
              .replace("Configuration", "")
              .replace("Information", "")
          items.value.push({label: formatted})
        });

        section.value = formatSections(Object.values(info).at(0))
      });
});

/*
{
  "Container Type":"cgroupv2",
  "CPU Slice Period":"-1",
  "CPU Quota":"-1",
  ...
}
=>
[
 {
  "field": "Container Type",
  "value": "cgroupv2",
 },
 ...
]
*/
const formatSections = (original) => {
  const formatted = []
  for (const [key, value] of Object.entries(original)) {
    let formatKey = Object.keys(info).at(active.value) + " - " + key
    let formatter = formatMap[formatKey];

    let formattedValue = value
    if (formatter != null) {
      formattedValue = formatter(value)
    }

    formatted.push({
      key: key,
      value: formattedValue
    })
  }
  return formatted
}

const selectSection = () => {
  section.value = formatSections(Object.values(info).at(active.value))
}


const breadcrumbs = [
  {label: 'Information', route: 'information'}
]
</script>

<template>
  <breadcrumb-component :path="breadcrumbs"></breadcrumb-component>

  <div class="card card-w-title">
    <TabMenu v-model:activeIndex="active" :model="items" @click="selectSection"/>

    <DataTable :value="section" stripedRows>
      <Column class="w-2 font-semibold" field="key"></Column>
      <Column field="value"></Column>
    </DataTable>
  </div>

</template>

<style scoped lang="scss"></style>
