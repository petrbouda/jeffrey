<template>
  <PageHeader
    title="Active Instances"
    description="View currently connected application instances. Each instance represents a running container or pod connected to this project."
    icon="bi-box"
  >
    <!-- Summary Stats Row -->
    <div class="row g-3 mb-3" v-if="!loading && filteredInstances.length > 0">
      <div class="col-md-4">
        <div class="compact-stat-card">
          <div class="compact-stat-header">
            <i class="bi bi-broadcast text-success"></i>
            <span class="compact-stat-title">Total Active</span>
          </div>
          <div class="compact-stat-value">{{ filteredInstances.length }}</div>
        </div>
      </div>
      <div class="col-md-4">
        <div class="compact-stat-card">
          <div class="compact-stat-header">
            <i class="bi bi-layers text-primary"></i>
            <span class="compact-stat-title">Total Sessions</span>
          </div>
          <div class="compact-stat-value">{{ totalSessions }}</div>
        </div>
      </div>
      <div class="col-md-4">
        <div class="compact-stat-card">
          <div class="compact-stat-header">
            <i class="bi bi-clock text-teal"></i>
            <span class="compact-stat-title">Latest Start</span>
          </div>
          <div class="compact-stat-value compact-stat-value-sm">{{ latestStart }}</div>
        </div>
      </div>
    </div>

    <!-- Search Box -->
    <SearchBox v-model="searchQuery" placeholder="Search instances..." />

    <!-- Instances Header Bar -->
    <div class="col-12 mt-3">
      <div class="d-flex align-items-center mb-3 gap-3">
        <div class="instances-header-bar flex-grow-1 d-flex align-items-center px-3">
          <span class="header-text">Active Instances ({{ filteredInstances.length }})</span>
        </div>
      </div>
    </div>

    <!-- Loading Indicator -->
    <LoadingState v-if="loading" message="Loading instances..." />

    <!-- Instances List -->
    <div v-else class="col-12">
      <EmptyState
        v-if="filteredInstances.length === 0"
        icon="bi-box"
        title="No Active Instances"
        description="No active instances found. Instances will appear here when your application connects to this project."
      />

      <div v-else>
        <router-link
          v-for="instance in filteredInstances"
          :key="instance.id"
          :to="generateInstanceUrl(instance.id)"
          class="instance-card d-block text-decoration-none mb-2"
        >
          <div class="d-flex align-items-center">
            <div class="instance-icon-square me-3">
              <i class="bi bi-box"></i>
            </div>
            <div class="flex-grow-1 min-width-0">
              <div class="d-flex align-items-center">
                <span class="fw-bold text-dark">{{ instance.hostname }}</span>
                <Badge
                  class="ms-2"
                  value="ONLINE"
                  variant="green"
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
const instances = ref<ProjectInstance[]>([]);

const filteredInstances = computed(() => {
  const onlineInstances = instances.value.filter(i => i.status === 'ONLINE');
  if (!searchQuery.value) {
    return onlineInstances;
  }
  const query = searchQuery.value.toLowerCase();
  return onlineInstances.filter(instance =>
    instance.hostname.toLowerCase().includes(query)
  );
});

const totalSessions = computed(() => {
  return filteredInstances.value.reduce((sum, i) => sum + i.sessionCount, 0);
});

const latestStart = computed(() => {
  if (filteredInstances.value.length === 0) return 'N/A';
  const latest = filteredInstances.value.reduce((max, i) => i.startedAt > max.startedAt ? i : max);
  return FormattingService.formatRelativeTime(latest.startedAt);
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

.compact-stat-value-sm {
  font-size: 0.9rem;
}

.text-teal {
  color: #14b8a6 !important;
}

/* Instance cards */
.instance-card {
  background-color: white;
  border: 1px solid #e2e8f0;
  border-left: 3px solid #22c55e;
  border-radius: 8px;
  padding: 12px 16px;
  background-color: rgba(34, 197, 94, 0.03);
  transition: all 0.2s ease;
  color: inherit;
}

.instance-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  background-color: rgba(34, 197, 94, 0.06);
}

.instance-icon-square {
  width: 36px;
  height: 36px;
  min-width: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgba(34, 197, 94, 0.12);
  color: #16a34a;
  font-size: 1rem;
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
