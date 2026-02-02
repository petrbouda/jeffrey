<template>
  <PageHeader
    title="Instance History"
    description="View all instances that have connected to this project, including both online and offline instances."
    icon="bi-clock-history"
  >
    <!-- Summary Stats Row -->
    <div class="row g-3 mb-3" v-if="!loading && instances.length > 0">
      <div class="col-md-4">
        <div class="compact-stat-card">
          <div class="compact-stat-header">
            <i class="bi bi-grid text-primary"></i>
            <span class="compact-stat-title">Total Instances</span>
          </div>
          <div class="compact-stat-value">{{ instances.length }}</div>
        </div>
      </div>
      <div class="col-md-4">
        <div class="compact-stat-card">
          <div class="compact-stat-header">
            <i class="bi bi-broadcast text-success"></i>
            <span class="compact-stat-title">Online</span>
          </div>
          <div class="compact-stat-value">{{ onlineCount }}</div>
        </div>
      </div>
      <div class="col-md-4">
        <div class="compact-stat-card">
          <div class="compact-stat-header">
            <i class="bi bi-power" style="color: #9ca3af;"></i>
            <span class="compact-stat-title">Offline</span>
          </div>
          <div class="compact-stat-value">{{ offlineCount }}</div>
        </div>
      </div>
    </div>

    <!-- Search Box and Filter -->
    <div class="d-flex gap-3 mb-3 align-items-start">
      <SearchBox v-model="searchQuery" placeholder="Search instances..." />
      <div class="btn-group filter-btn-group" role="group">
        <button
          type="button"
          class="btn btn-sm"
          :class="statusFilter === '' ? 'btn-primary' : 'btn-outline-secondary'"
          @click="statusFilter = ''"
        >All</button>
        <button
          type="button"
          class="btn btn-sm"
          :class="statusFilter === 'ONLINE' ? 'btn-primary' : 'btn-outline-secondary'"
          @click="statusFilter = 'ONLINE'"
        >Online</button>
        <button
          type="button"
          class="btn btn-sm"
          :class="statusFilter === 'OFFLINE' ? 'btn-primary' : 'btn-outline-secondary'"
          @click="statusFilter = 'OFFLINE'"
        >Offline</button>
      </div>
    </div>

    <!-- Instances Header Bar -->
    <div class="col-12">
      <div class="d-flex align-items-center mb-3 gap-3">
        <div class="instances-header-bar flex-grow-1 d-flex align-items-center px-3">
          <span class="header-text">All Instances ({{ filteredInstances.length }})</span>
          <div class="ms-auto d-flex gap-2">
            <Badge :value="`${onlineCount} online`" variant="green" size="xs" />
            <Badge :value="`${offlineCount} offline`" variant="gray" size="xs" />
          </div>
        </div>
      </div>
    </div>

    <!-- Loading Indicator -->
    <LoadingState v-if="loading" message="Loading instance history..." />

    <!-- Instances List -->
    <div v-else class="col-12">
      <EmptyState
        v-if="filteredInstances.length === 0"
        icon="bi-clock-history"
        title="No Instances Found"
        description="No instances match your search criteria."
      />

      <div v-else>
        <router-link
          v-for="instance in filteredInstances"
          :key="instance.id"
          :to="generateInstanceUrl(instance.id)"
          class="instance-card d-block text-decoration-none mb-2"
          :class="instance.status === 'ONLINE' ? 'instance-online' : 'instance-offline'"
        >
          <div class="d-flex align-items-center">
            <div class="instance-icon-square me-3" :class="instance.status === 'ONLINE' ? 'icon-online' : 'icon-offline'">
              <i class="bi bi-box"></i>
            </div>
            <div class="flex-grow-1 min-width-0">
              <div class="d-flex align-items-center">
                <span class="fw-bold text-dark">{{ instance.hostname }}</span>
                <Badge
                  class="ms-2"
                  :value="instance.status"
                  :variant="instance.status === 'ONLINE' ? 'green' : 'gray'"
                  size="xs"
                />
              </div>
              <div class="instance-meta">
                <span><i class="bi bi-layers me-1"></i>{{ instance.sessionCount }} sessions</span>
                <span><i class="bi bi-play-circle me-1"></i>Started {{ FormattingService.formatRelativeTime(instance.startedAt) }}</span>
              </div>
            </div>
            <i class="bi bi-chevron-right text-muted"></i>
          </div>
        </router-link>
      </div>
    </div>
  </PageHeader>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import PageHeader from '@/components/layout/PageHeader.vue';
import LoadingState from '@/components/LoadingState.vue';
import EmptyState from '@/components/EmptyState.vue';
import Badge from '@/components/Badge.vue';
import SearchBox from '@/components/SearchBox.vue';
import ProjectInstanceClient from '@/services/api/ProjectInstanceClient';
import ProjectInstance from '@/services/api/model/ProjectInstance';
import FormattingService from '@/services/FormattingService';
import { useNavigation } from '@/composables/useNavigation';
import '@/styles/shared-components.css';

const { workspaceId, projectId, generateInstanceUrl } = useNavigation();

const loading = ref(true);
const searchQuery = ref('');
const statusFilter = ref('');
const instances = ref<ProjectInstance[]>([]);

const onlineCount = computed(() => instances.value.filter(i => i.status === 'ONLINE').length);
const offlineCount = computed(() => instances.value.filter(i => i.status === 'OFFLINE').length);

const filteredInstances = computed(() => {
  let result = instances.value;

  if (statusFilter.value) {
    result = result.filter(i => i.status === statusFilter.value);
  }

  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase();
    result = result.filter(instance =>
      instance.hostname.toLowerCase().includes(query)
    );
  }

  return result;
});

onMounted(async () => {
  const client = new ProjectInstanceClient(workspaceId.value!, projectId.value!);
  instances.value = await client.list();
  loading.value = false;
});
</script>

<style scoped>
.instances-header-bar {
  background-color: #f8fafc;
  border-radius: 6px;
  padding: 0.5rem 1rem;
  border: 1px solid #e2e8f0;
}

.header-text {
  font-weight: 600;
  color: #475569;
  font-size: 0.85rem;
}

/* Compact stat cards */
.compact-stat-card {
  background: linear-gradient(135deg, #f8f9fa, #ffffff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 8px;
  padding: 12px 16px;
  height: 100%;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04), 0 1px 2px rgba(0, 0, 0, 0.02);
}

.compact-stat-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 3px 8px rgba(0, 0, 0, 0.06), 0 2px 4px rgba(94, 100, 255, 0.1);
  border-color: rgba(94, 100, 255, 0.15);
}

.compact-stat-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.compact-stat-header i {
  font-size: 1rem;
}

.compact-stat-title {
  font-size: 0.75rem;
  font-weight: 600;
  color: #6b7280;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.compact-stat-value {
  font-size: 1.25rem;
  font-weight: 700;
  color: #374151;
}

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
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px 16px;
  transition: all 0.2s ease;
  color: inherit;
}

.instance-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.instance-online {
  border-left: 3px solid #22c55e;
  background-color: rgba(34, 197, 94, 0.03);
}

.instance-online:hover {
  background-color: rgba(34, 197, 94, 0.06);
}

.instance-offline {
  border-left: 3px solid #9ca3af;
  background-color: rgba(156, 163, 175, 0.03);
  opacity: 0.7;
}

.instance-offline:hover {
  opacity: 0.85;
  background-color: rgba(156, 163, 175, 0.06);
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

.icon-online {
  background-color: rgba(34, 197, 94, 0.12);
  color: #16a34a;
}

.icon-offline {
  background-color: rgba(156, 163, 175, 0.12);
  color: #6b7280;
}

.instance-meta {
  display: flex;
  gap: 12px;
  font-size: 0.75rem;
  color: #6b7280;
  margin-top: 2px;
}

.min-width-0 {
  min-width: 0;
}
</style>
