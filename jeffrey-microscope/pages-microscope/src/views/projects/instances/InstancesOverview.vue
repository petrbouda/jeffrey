<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<template>
  <div>
    <MainCard>
      <template #header>
        <MainCardHeader icon="bi bi-box" title="Instances Overview" />
      </template>

      <!-- Figures live in the project tab row; they leave with this page -->
      <Teleport defer :to="PROJECT_NAV_ACTIONS_SELECTOR">
        <InstanceStatsSummary
          v-if="!loading && instances.length > 0"
          :stats="stats"
          :storage="storage"
        />
      </Teleport>

      <LoadingState v-if="loading" message="Loading instances..." />

      <ErrorState
        v-else-if="loadError"
        message="Instances could not be loaded"
        @retry="loadInstances"
      />

      <EmptyState
        v-else-if="instances.length === 0"
        icon="bi-box"
        title="No Instances Yet"
        description="No instance of this project has started a recording session."
      />

      <DataTable v-else>
        <template #toolbar>
          <TableToolbar v-model="searchQuery" search-placeholder="Filter instances...">
            <span class="toolbar-info">Newest first</span>
            <template #filters>
              <Badge
                key-label="Instances"
                :value="filteredInstances.length"
                variant="primary"
                size="s"
                borderless
              />
            </template>
          </TableToolbar>
        </template>
        <thead>
          <tr>
            <th>Instance</th>
            <th>Started</th>
            <th class="chevron-col"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="filteredInstances.length === 0" class="empty-row">
            <td colspan="3">No instances match this filter.</td>
          </tr>
          <tr
            v-for="instance in filteredInstances"
            :key="instance.id"
            class="instance-row"
            @click="navigateToInstance(instance.id)"
          >
            <td>
              <router-link :to="generateInstanceUrl(instance.id)" class="instance-name" @click.stop>
                {{ instance.id }}
              </router-link>
            </td>
            <td
              v-if="instance.createdAt !== null"
              :title="FormattingService.formatTimestampUTC(instance.createdAt)"
            >
              {{ FormattingService.formatTimestampUTCShort(instance.createdAt) }}
              <span class="started-relative">
                · {{ FormattingService.formatRelativeTime(instance.createdAt) }}
              </span>
            </td>
            <td v-else class="no-time">Not listed by the share</td>
            <td class="chevron-col"><i class="bi bi-chevron-right"></i></td>
          </tr>
        </tbody>
      </DataTable>
    </MainCard>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import Badge from '@shared/components/Badge.vue';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import DataTable from '@shared/components/table/DataTable.vue';
import TableToolbar from '@shared/components/table/TableToolbar.vue';
import FormattingService from '@shared/services/FormattingService';
import ProjectInstanceClient from '@remote/services/api/ProjectInstanceClient';
import ProjectRepositoryClient from '@remote/services/api/ProjectRepositoryClient';
import ProjectInstance from '@remote/services/api/model/ProjectInstance';
import type RepositoryStatistics from '@remote/services/api/model/RepositoryStatistics';
import { useNavigation } from '@/composables/useNavigation';
import InstanceStatsSummary from './InstanceStatsSummary.vue';
import { storageFigure, summarizeInstances } from './instanceStats';
import { PROJECT_NAV_ACTIONS_SELECTOR } from '../projectNav';
import '@shared/styles/shared-components.css';

const { workspaceId, projectId, generateInstanceUrl, navigateToInstance } = useNavigation();

const loading = ref(true);
const loadError = ref(false);
const searchQuery = ref('');
const instances = ref<ProjectInstance[]>([]);
const repositoryStatistics = ref<RepositoryStatistics | null>(null);
const statisticsError = ref(false);

const storage = computed(() => storageFigure(repositoryStatistics.value, statisticsError.value));

const stats = computed(() => summarizeInstances(instances.value));

// Newest started first; on a share that lists no times, by name.
function compareByStart(a: ProjectInstance, b: ProjectInstance): number {
  if (a.createdAt !== b.createdAt) {
    return (b.createdAt ?? Number.NEGATIVE_INFINITY) - (a.createdAt ?? Number.NEGATIVE_INFINITY);
  }
  return a.id.localeCompare(b.id);
}

const filteredInstances = computed(() => {
  let result = instances.value;

  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase();
    result = result.filter(instance => instance.id.toLowerCase().includes(query));
  }

  return [...result].sort(compareByStart);
});

async function loadInstances() {
  loading.value = true;
  loadError.value = false;
  try {
    const client = new ProjectInstanceClient(workspaceId.value!, projectId.value!);
    instances.value = await client.list();
  } catch (error) {
    console.error('Failed to load instances', error);
    loadError.value = true;
  } finally {
    loading.value = false;
  }
}

async function loadRepositoryStatistics() {
  const repositoryClient = new ProjectRepositoryClient(workspaceId.value!, projectId.value!);
  // The summary's storage figure is the only reader of this call. Left to reject, a failed call
  // would drop it without a word, and a project that looks storage-free is easy to mistake for one that is.
  try {
    repositoryStatistics.value = await repositoryClient.getRepositoryStatistics();
  } catch (error) {
    console.error('Failed to load repository statistics', error);
    statisticsError.value = true;
  }
}

onMounted(async () => {
  await loadInstances();
  await loadRepositoryStatistics();
});
</script>

<style scoped>
.toolbar-info {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--color-text);
}

.instance-row {
  cursor: pointer;
}

.instance-name {
  font-family: var(--font-family-monospace);
  font-weight: var(--font-weight-medium);
  color: var(--color-dark);
  text-decoration: none;
}

.instance-name:hover {
  color: var(--color-primary);
  text-decoration: underline;
}

.started-relative,
.no-time {
  color: var(--color-text-muted);
}

.chevron-col {
  width: 40px;
  text-align: center;
  color: var(--color-text-light);
}

.empty-row > td {
  padding: 1rem;
  text-align: center;
  font-size: 0.85rem;
  color: var(--color-text-muted);
}
</style>
