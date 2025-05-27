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
import DashboardHeader from '@/components/DashboardHeader.vue';

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

  InformationService.info(route.params.projectId as string, route.params.profileId as string)
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
</script>

<template>
  <div class="container-fluid p-0">
    <DashboardHeader 
      title="Profile Configuration"
      description="View the content of configuration events"
      icon="gear-fill"
    />

    <div class="config-container">
      <!-- Tab navigation -->
      <div class="mb-3">
        <div class="card">
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
        </div>
      </div>
      
      <!-- Configuration table -->
      <div class="card mb-4">
        <div class="card-body p-0">
          <table class="table table-hover mb-0 config-tree-table">
            <thead>
              <tr>
                <th>Configuration Key</th>
                <th>Value</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, index) in section" :key="index" class="config-row">
                <td class="config-name-cell">
                  <span class="config-name">{{ row.key }}</span>
                </td>
                <td class="config-value-cell">
                  <span class="config-value">{{ row.value }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.config-container {
  background-color: #fff;
  border-radius: 0.4rem;
}

/* Tab navigation styles - more compact */
.card-header {
  padding: 0.75rem 1rem;
  background-color: #fff;
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
}

.nav-container {
  overflow-x: auto;
  -ms-overflow-style: none;
  scrollbar-width: none;
  margin-right: -0.25rem;
  margin-left: -0.25rem;
  padding: 0 0.25rem;

  &::-webkit-scrollbar {
    display: none;
  }
}

.nav-pills {
  gap: 0.15rem;
  
  .nav-link {
    color: #6c757d;
    font-weight: 500;
    padding: 0.25rem 0.5rem;
    margin: 0;
    border-radius: 0.2rem;
    transition: all 0.2s ease;
    font-size: 0.7rem;
    white-space: nowrap;
    text-overflow: ellipsis;
    overflow: hidden;
    line-height: 1.2;

    &:hover {
      color: #5e64ff;
      background-color: rgba(94, 100, 255, 0.08);
    }

    &.active {
      background-color: #5e64ff;
      color: white;
      font-weight: 600;
      box-shadow: 0 0.125rem 0.25rem rgba(94, 100, 255, 0.2);
    }
  }
}

/* Table styles - matching ProfileEventTypes */
.config-tree-table {
  width: 100%;
  table-layout: fixed;
}

.config-tree-table th:nth-child(1) {
  width: 30%;
}

.config-tree-table th:nth-child(2) {
  width: 70%;
}

.config-tree-table th {
  padding: 0.75rem 0.75rem;
  font-size: 0.8rem;
}

.config-tree-table td {
  padding: 0.6rem 0.75rem;
  font-size: 0.8rem;
  vertical-align: middle;
}

.config-name-cell {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.config-name {
  font-weight: 500;
}

.config-value-cell {
  word-break: break-word;
}

.config-value {
  font-family: ui-monospace, SFMono-Regular, "SF Mono", Monaco, Menlo, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 0.85em;
  color: #495057;
  line-height: 1.3;
}

.config-row {
  transition: background-color 0.15s ease;
}

.config-row:hover {
  background-color: #f8f9fa;
}

/* Card styles matching ProfileEventTypes */
.card {
  border: none;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

/* Responsive adjustments */
@media (max-width: 768px) {
  .card-header {
    padding: 0.5rem 0.75rem;
  }
  
  .config-tree-table {
    table-layout: auto;
  }

  .config-name-cell {
    max-width: 200px;
  }
  
  .config-tree-table th:nth-child(1) {
    width: 30%;
  }
  
  .config-tree-table th:nth-child(2) {
    width: 70%;
  }
  
  .config-tree-table th {
    padding: 0.6rem 0.5rem;
    font-size: 0.75rem;
  }
  
  .config-tree-table td {
    padding: 0.5rem 0.5rem;
    font-size: 0.75rem;
  }
}

@media (max-width: 576px) {
  .config-name-cell,
  .config-value-cell {
    white-space: normal;
  }
  
  .nav-pills .nav-link {
    padding: 0.15rem 0.3rem;
    font-size: 0.6rem;
  }
  
  .config-tree-table th {
    padding: 0.5rem 0.4rem;
    font-size: 0.7rem;
  }
  
  .config-tree-table td {
    padding: 0.4rem 0.4rem;
    font-size: 0.7rem;
  }
}
</style>
