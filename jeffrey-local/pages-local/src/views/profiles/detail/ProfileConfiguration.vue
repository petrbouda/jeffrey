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

import PageHeader from '@/components/layout/PageHeader.vue';

interface SectionRow {
  key: string;
  value: unknown;
}

interface TabItem {
  label: string;
}

type ValueFormatter = (value: unknown) => string;

const route = useRoute();

let info: Record<string, Record<string, unknown>> | null = null;
const active = ref(0);

const items = ref<TabItem[]>([]);

const section = ref<SectionRow[] | null>(null);
let formatMap: Record<string, ValueFormatter> = {};

onMounted(() => {
  formatMap = {
    'JVM Information - JVM Start Time': (value: unknown) => {
      return value + " (" + new Date(parseInt(String(value), 10)) + ")"
    },
    'GC Heap Configuration - Minimum Heap Size': (value: unknown) => FormattingService.formatBytes(value as number),
    'GC Heap Configuration - Maximum Heap Size': (value: unknown) => FormattingService.formatBytes(value as number),
    'GC Heap Configuration - Initial Heap Size': (value: unknown) => FormattingService.formatBytes(value as number),
    'TLAB Configuration - Minimum TLAB Size': (value: unknown) => FormattingService.formatBytes(value as number),
    'Young Generation Configuration - Minimum Young Generation Size': (value: unknown) => FormattingService.formatBytes(value as number),
    'Young Generation Configuration - Maximum Young Generation Size': (value: unknown) => FormattingService.formatBytes(value as number),
    'Container Configuration - Container Host Total Memory': (value: unknown) => FormattingService.formatBytes(value as number),
    'Container Configuration - Memory and Swap Limit': (value: unknown) => FormattingService.formatBytes(value as number),
    'Container Configuration - Memory Limit': (value: unknown) => FormattingService.formatBytes(value as number),
    'Container Configuration - Memory Soft Limit': (value: unknown) => FormattingService.formatBytes(value as number),
  };

  new InformationClient(route.params.profileId as string).info()
      .then((data: Record<string, Record<string, unknown>>) => {
        info = data;

        Object.keys(info).forEach((key) => {
          const formatted = key
              .replace("Configuration", "")
              .replace("Information", "")
          items.value.push({label: formatted})
        });

        const sections = Object.values(info);
        if (sections.length > 0) {
          section.value = formatSections(sections[0]);
        }
      });
});

const formatSections = (original: Record<string, unknown>): SectionRow[] => {
  const formatted: SectionRow[] = []
  for (const [key, value] of Object.entries(original)) {
    const infoKeys = info ? Object.keys(info) : [];
    const formatKey = (infoKeys[active.value] || '') + " - " + key
    const formatter = formatMap[formatKey];

    const formattedValue = formatter != null ? formatter(value) : value

    formatted.push({
      key: key,
      value: formattedValue
    })
  }
  return formatted
}

const selectSection = () => {
  if (!info) return;
  const sections = Object.values(info);
  if (active.value < sections.length) {
    section.value = formatSections(sections[active.value]);
  }
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
