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
import InformationClient from '@/services/api/InformationClient';
import FormattingService from "@/services/FormattingService";
import {useRoute} from "vue-router";
import { useNavigation } from '@/composables/useNavigation';
import PageHeader from '@/components/layout/PageHeader.vue';

const route = useRoute();
const { workspaceId, projectId } = useNavigation();

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

  InformationClient.info(route.params.profileId as string)
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
  <PageHeader
    title="Profile Configuration"
    description="View the content of configuration events"
    icon="bi-gear-fill"
  >

    <div class="config-container">
      <!-- Section navigation pills -->
      <div class="pills-wrapper mb-3">
        <div class="pills-scroll">
          <button
            v-for="(item, index) in items"
            :key="index"
            class="config-pill"
            :class="{ 'active': active === index }"
            @click="active = index; selectSection()"
            type="button">
            {{ item.label }}
          </button>
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
  </PageHeader>
</template>

<style scoped>
.config-container {
  background-color: #fff;
  border-radius: 0.4rem;
}

/* Pills navigation - auto-width, scrollable */
.pills-wrapper {
  background: #fafbfc;
  border-radius: 8px;
  padding: 0.5rem;
  border: 1px solid #e9ecef;
}

.pills-scroll {
  display: flex;
  gap: 0.5rem;
  overflow-x: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
  padding: 0.25rem;
}

.pills-scroll::-webkit-scrollbar {
  display: none;
}

.config-pill {
  flex-shrink: 0;
  padding: 0.5rem 1rem;
  font-size: 0.875rem;
  font-weight: 500;
  color: #6c757d;
  background: #fff;
  border: 1px solid #dee2e6;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.config-pill:hover {
  color: #5e64ff;
  border-color: #5e64ff;
  background: rgba(94, 100, 255, 0.04);
}

.config-pill.active {
  color: #fff;
  background: #5e64ff;
  border-color: #5e64ff;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(94, 100, 255, 0.25);
}

/* Table styles */
.config-tree-table {
  width: 100%;
  table-layout: fixed;
}

.config-tree-table th:nth-child(1) {
  width: 20%;
}

.config-tree-table th:nth-child(2) {
  width: 80%;
}

.config-tree-table th {
  padding: 0.75rem 1rem;
  font-size: 0.8125rem;
}

.config-tree-table td {
  padding: 0.65rem 1rem;
  font-size: 0.8125rem;
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
  color: #495057;
}

.config-row {
  transition: background-color 0.15s ease;
}

.config-row:hover {
  background-color: #f8f9fa;
}

/* Card styles */
.card {
  border: none;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

/* Responsive adjustments */
@media (max-width: 768px) {
  .pills-wrapper {
    padding: 0.375rem;
  }

  .config-pill {
    padding: 0.4rem 0.75rem;
    font-size: 0.8rem;
  }

  .config-tree-table {
    table-layout: auto;
  }

  .config-name-cell {
    max-width: 200px;
  }

  .config-tree-table th:nth-child(1) {
    width: 25%;
  }

  .config-tree-table th:nth-child(2) {
    width: 75%;
  }

  .config-tree-table th {
    padding: 0.6rem 0.75rem;
    font-size: 0.8rem;
  }

  .config-tree-table td {
    padding: 0.5rem 0.75rem;
    font-size: 0.8rem;
  }
}

@media (max-width: 576px) {
  .config-name-cell,
  .config-value-cell {
    white-space: normal;
  }

  .config-pill {
    padding: 0.35rem 0.6rem;
    font-size: 0.75rem;
  }

  .config-tree-table th {
    padding: 0.5rem 0.5rem;
    font-size: 0.75rem;
  }

  .config-tree-table td {
    padding: 0.45rem 0.5rem;
    font-size: 0.75rem;
  }
}
</style>
