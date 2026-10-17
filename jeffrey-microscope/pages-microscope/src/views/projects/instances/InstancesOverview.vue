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

      <!-- Search Box and Filter -->
      <div class="d-flex gap-3 mb-3 align-items-center">
        <div class="input-group search-container flex-grow-1">
          <span class="input-group-text"><i class="bi bi-search search-icon"></i></span>
          <input
            type="text"
            class="form-control search-input"
            placeholder="Search instances..."
            v-model="searchQuery"
          />
          <button
            v-if="searchQuery"
            class="btn btn-outline-secondary clear-btn"
            type="button"
            @click="searchQuery = ''"
          >
            <i class="bi bi-x-lg"></i>
          </button>
        </div>
        <div class="btn-group filter-btn-group" role="group">
          <button
            type="button"
            class="btn btn-sm"
            :class="statusFilter === '' ? 'btn-primary' : 'btn-outline-secondary'"
            @click="statusFilter = ''"
          >
            All
          </button>
          <button
            type="button"
            class="btn btn-sm"
            :class="statusFilter === 'PENDING' ? 'btn-primary' : 'btn-outline-secondary'"
            @click="statusFilter = 'PENDING'"
          >
            Pending
          </button>
          <button
            type="button"
            class="btn btn-sm"
            :class="statusFilter === 'ACTIVE' ? 'btn-primary' : 'btn-outline-secondary'"
            @click="statusFilter = 'ACTIVE'"
          >
            Active
          </button>
          <button
            type="button"
            class="btn btn-sm"
            :class="statusFilter === 'FINISHED' ? 'btn-primary' : 'btn-outline-secondary'"
            @click="statusFilter = 'FINISHED'"
          >
            Finished
          </button>
        </div>
      </div>

      <!-- Instances Header Bar -->
      <div class="col-12">
        <SectionHeaderBar :text="`All Instances (${filteredInstances.length})`" />
      </div>

      <!-- Loading Indicator -->
      <LoadingState v-if="loading" message="Loading instance history..." />

      <!-- Instances List -->
      <div v-else class="col-12">
        <EmptyState
          v-if="filteredInstances.length === 0"
          icon="bi-box"
          title="No Instances Found"
          description="No instances match your search criteria."
        />

        <div v-else>
          <router-link
            v-for="instance in filteredInstances"
            :key="instance.id"
            :to="generateInstanceUrl(instance.id)"
            class="instance-card d-block text-decoration-none mb-2"
            :class="instanceCardClass(instance.status)"
          >
            <!-- Top section: instance name + badge + sessions + chevron -->
            <div class="instance-identity" :class="instanceIdentityClass(instance.status)">
              <div class="d-flex align-items-center">
                <div class="instance-icon-square me-3" :class="instanceIconClass(instance.status)">
                  <i class="bi bi-box"></i>
                </div>
                <div class="flex-grow-1 min-width-0">
                  <div class="d-flex align-items-center">
                    <span class="fw-bold text-dark">{{ instance.instanceName }}</span>
                    <Badge
                      class="ms-2"
                      :value="instance.status"
                      :variant="instanceBadgeVariant(instance.status)"
                      size="xs"
                    />
                  </div>
                  <div class="instance-meta">
                    <span
                      ><i class="bi bi-layers me-1"></i>{{ instance.sessionCount }} sessions</span
                    >
                  </div>
                </div>
                <i class="bi bi-chevron-right text-muted"></i>
              </div>
            </div>

            <!-- Bottom timeline section -->
            <TimelineBar
              :createdAt="instance.createdAt"
              :finishedAt="instance.finishedAt"
              :duration="instance.duration"
            />
          </router-link>
        </div>
      </div>
    </MainCard>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import LoadingState from '@shared/components/LoadingState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import Badge from '@shared/components/Badge.vue';
import MainCard from '@shared/components/MainCard.vue';
import MainCardHeader from '@shared/components/MainCardHeader.vue';
import SectionHeaderBar from '@shared/components/SectionHeaderBar.vue';
import ProjectInstanceClient from '@hubs/services/api/ProjectInstanceClient';
import ProjectRepositoryClient from '@hubs/services/api/ProjectRepositoryClient';
import ProjectInstance from '@hubs/services/api/model/ProjectInstance';
import type RepositoryStatistics from '@hubs/services/api/model/RepositoryStatistics';
import TimelineBar from '@shared/components/TimelineBar.vue';
import { useNavigation } from '@/composables/useNavigation';
import InstanceStatsSummary from './InstanceStatsSummary.vue';
import { storageFigure, summarizeInstances } from './instanceStats';
import { PROJECT_NAV_ACTIONS_SELECTOR } from '../projectNav';
import '@shared/styles/shared-components.css';
import type { Variant } from '@shared/types/ui';

const { hubId, workspaceId, projectId, generateInstanceUrl } = useNavigation();

const loading = ref(true);
const searchQuery = ref('');
const statusFilter = ref('');
const instances = ref<ProjectInstance[]>([]);
const repositoryStatistics = ref<RepositoryStatistics | null>(null);
const statisticsError = ref(false);

const storage = computed(() => storageFigure(repositoryStatistics.value, statisticsError.value));

const stats = computed(() => summarizeInstances(instances.value));

function instanceCardClass(status: string): string {
  if (status === 'PENDING') {
    return 'instance-pending';
  }
  if (status === 'ACTIVE') {
    return 'instance-active';
  }
  return 'instance-finished';
}

function instanceIdentityClass(status: string): string {
  if (status === 'PENDING') {
    return 'identity-pending';
  }
  if (status === 'ACTIVE') {
    return 'identity-active';
  }
  return 'identity-finished';
}

function instanceIconClass(status: string): string {
  if (status === 'PENDING') {
    return 'icon-pending';
  }
  if (status === 'ACTIVE') {
    return 'icon-active';
  }
  return 'icon-finished';
}

function instanceBadgeVariant(status: string): Variant {
  if (status === 'PENDING') {
    return 'blue';
  }
  if (status === 'ACTIVE') {
    return 'warning';
  }
  return 'green';
}

const filteredInstances = computed(() => {
  let result = instances.value;

  if (statusFilter.value) {
    result = result.filter(i => i.status === statusFilter.value);
  }

  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase();
    result = result.filter(instance => instance.instanceName.toLowerCase().includes(query));
  }

  return result;
});

onMounted(async () => {
  const client = new ProjectInstanceClient(hubId.value, workspaceId.value!, projectId.value!);
  instances.value = await client.list();
  loading.value = false;

  const repositoryClient = new ProjectRepositoryClient(
    hubId.value,
    workspaceId.value!,
    projectId.value!
  );
  // The summary's storage figure is the only reader of this call. Left to reject, a failed call
  // would drop it without a word, and a project that looks storage-free is easy to mistake for one that is.
  try {
    repositoryStatistics.value = await repositoryClient.getRepositoryStatistics();
  } catch (error) {
    console.error('Failed to load repository statistics', error);
    statisticsError.value = true;
  }
});
</script>

<style scoped>
/* Filter button group */
.filter-btn-group {
  flex-shrink: 0;
  align-self: center;
}

.filter-btn-group .btn {
  font-size: 0.8rem;
  padding: 0.4rem 0.85rem;
  font-weight: 500;
}

/* Instance cards */
.instance-card {
  background-color: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  transition: all var(--transition-fast);
  color: inherit;
  box-shadow: var(--shadow-base);
  overflow: hidden;
}

.instance-card:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-md);
}

.instance-pending {
  border-left: 3px solid var(--color-blue-500);
}

.instance-active {
  border-left: 3px solid var(--color-amber);
}

.instance-finished {
  border-left: 3px solid var(--color-success);
}

.instance-identity {
  padding: 12px 16px;
}

.identity-pending {
  background-color: rgba(59, 130, 246, 0.06);
}

.identity-active {
  background-color: rgba(245, 158, 11, 0.06);
}

.identity-finished {
  background-color: rgba(16, 185, 129, 0.04);
}

.instance-icon-square {
  width: 36px;
  height: 36px;
  min-width: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1rem;
}

.icon-pending {
  background-color: rgba(59, 130, 246, 0.12);
  color: var(--color-accent-blue);
}

.icon-active {
  background-color: rgba(245, 158, 11, 0.12);
  color: var(--color-amber-highlight);
}

.icon-finished {
  background-color: rgba(16, 185, 129, 0.12);
  color: var(--color-success-hover);
}

.instance-meta {
  display: flex;
  gap: 12px;
  font-size: 0.75rem;
  color: var(--color-text-muted);
  margin-top: 2px;
}

.min-width-0 {
  min-width: 0;
}
</style>
