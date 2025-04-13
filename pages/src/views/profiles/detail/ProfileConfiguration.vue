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

import {onMounted, ref} from 'vue';
import InformationService from '@/services/InformationService';
import FormattingService from "@/services/FormattingService";
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
        console.log(data)

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
</script>

<template>
  <div class="container-fluid p-0">
    <div class="mb-4">
      <h2 class="config-title">
        <i class="bi bi-gear-fill me-2"></i>
        Profile Configuration
      </h2>
      <p class="text-muted fs-6">View the content of configuration events</p>
    </div>

    <div class="card shadow-sm">
      <div class="card-header bg-white">
        <div class="nav-container">
          <ul class="nav nav-pills nav-fill" role="tablist">
            <li class="nav-item" v-for="(item, index) in items" :key="index">
              <button 
                class="nav-link" 
                :class="{ 'active': active === index }" 
                @click="active = index; selectSection()"
                :id="`tab-${index}`"
                type="button" 
                role="tab" 
                :aria-selected="active === index">
                {{ item.label }}
              </button>
            </li>
          </ul>
        </div>
      </div>
      
      <div class="card-body">
        <div class="tab-content">
          <div class="tab-pane fade show active">
            <div class="table-responsive">
              <table class="table table-hover">
                <tbody>
                  <tr v-for="(row, index) in section" :key="index">
                    <th scope="row" class="w-25">{{ row.key }}</th>
                    <td>{{ row.value }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.config-title {
  font-size: 1.75rem;
  font-weight: 600;
  color: #343a40;
  margin-bottom: 0.5rem;
  display: flex;
  align-items: center;
}

.card {
  border: none;
  border-radius: 0.5rem;
  overflow: hidden;
  transition: all 0.3s ease;
  margin-bottom: 2rem;
}

.shadow-sm {
  box-shadow: 0 0.125rem 0.375rem rgba(0, 0, 0, 0.1) !important;
}

.card-header {
  padding: 1.25rem;
  background-color: #fff;
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
}

.nav-container {
  overflow-x: auto;
  -ms-overflow-style: none;
  scrollbar-width: none;
  margin-right: -0.5rem;
  margin-left: -0.5rem;
  padding: 0 0.5rem;

  &::-webkit-scrollbar {
    display: none;
  }
}

.nav-pills {
  .nav-link {
    color: #6c757d;
    font-weight: 500;
    padding: 0.35rem 0.5rem;
    margin: 0 0.1rem;
    border-radius: 0.25rem;
    transition: all 0.3s ease;
    font-size: 0.75rem;
    white-space: nowrap;
    text-overflow: ellipsis;
    overflow: hidden;

    &:hover {
      color: #5e64ff;
      background-color: rgba(94, 100, 255, 0.08);
    }

    &.active {
      background-color: #5e64ff;
      color: white;
      font-weight: 500;
      box-shadow: 0 0.25rem 0.5rem rgba(94, 100, 255, 0.15);
    }
  }
}

.table {
  margin-bottom: 0;

  th {
    font-weight: 600;
    color: #495057;
    background-color: rgba(0, 0, 0, 0.02);
    padding: 1rem;
    font-size: 0.9rem;
  }

  td {
    color: #212529;
    vertical-align: middle;
    padding: 1rem;
    font-size: 0.95rem;
  }

  tr {
    transition: all 0.2s ease;

    &:hover {
      background-color: rgba(94, 100, 255, 0.04);
    }
  }
}

.card-body {
  padding: 0;
}

.table-responsive {
  border-radius: 0 0 0.5rem 0.5rem;
  overflow: hidden;
}

.table-hover tbody tr:hover {
  background-color: rgba(94, 100, 255, 0.05);
}

@media (max-width: 768px) {
  .config-title {
    font-size: 1.5rem;
  }
  
  .nav-pills .nav-link {
    padding: 0.25rem 0.4rem;
    font-size: 0.7rem;
  }
  
  .table th, .table td {
    padding: 0.75rem;
    font-size: 0.85rem;
  }
}
</style>
