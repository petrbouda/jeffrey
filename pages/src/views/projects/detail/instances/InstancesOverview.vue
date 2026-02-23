<template>
  <PageHeader
    title="Instances Overview"
    description="View all instances connected to this project. Filter by status to see active or finished instances."
    icon="bi-box"
  >
    <!-- Stat Cards -->
    <div class="mb-4" v-if="!loading && instances.length > 0">
      <div class="row g-3">
        <!-- Instances -->
        <div class="col-md-4 col-xl">
          <div class="compact-stat-card">
            <div class="compact-stat-header">
              <i class="bi bi-box text-primary"></i>
              <span class="compact-stat-title">Instances</span>
            </div>
            <div class="compact-stat-metrics">
              <div class="metric-item" v-if="pendingCount > 0">
                <span class="metric-label">Pending Instances</span>
                <span class="metric-value">{{ pendingCount }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-label">Active Instances</span>
                <span class="metric-value">{{ activeCount }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-label">Finished Instances</span>
                <span class="metric-value">{{ finishedCount }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-label">Total Instances</span>
                <span class="metric-value">{{ instances.length }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-label">Total Sessions</span>
                <span class="metric-value">{{ totalSessions }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-label">Uptime Range</span>
                <span class="metric-value">{{ uptimeRange }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Storage -->
        <div class="col-md-4 col-xl" v-if="repositoryStatistics">
          <div class="compact-stat-card">
            <div class="compact-stat-header">
              <i class="bi bi-hdd text-success"></i>
              <span class="compact-stat-title">Storage</span>
            </div>
            <div class="compact-stat-metrics">
              <div class="metric-item">
                <span class="metric-label">Total Size</span>
                <span class="metric-value">{{ FormattingService.formatBytes(repositoryStatistics.totalSize) }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-label">Total Files</span>
                <span class="metric-value">{{ repositoryStatistics.totalFiles }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-label">Biggest Session</span>
                <span class="metric-value">{{ FormattingService.formatBytes(repositoryStatistics.biggestSessionSize) }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- File Types -->
        <div class="col-md-4 col-xl" v-if="repositoryStatistics">
          <div class="compact-stat-card">
            <div class="compact-stat-header">
              <i class="bi bi-files text-info"></i>
              <span class="compact-stat-title">File Types</span>
            </div>
            <div class="compact-stat-metrics">
              <div class="metric-item">
                <span class="metric-label">JFR Files</span>
                <span class="metric-value" style="color: #5e64ff">{{ repositoryStatistics.jfrFiles ?? 0 }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-label">Heap Dumps</span>
                <span class="metric-value" style="color: #6f42c1">{{ repositoryStatistics.heapDumpFiles ?? 0 }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-label">JVM Logs</span>
                <span class="metric-value" style="color: #14b8a6">{{ repositoryStatistics.logFiles ?? 0 }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-label">JVM Error Logs</span>
                <span class="metric-value" style="color: #c62828">{{ repositoryStatistics.errorLogFiles ?? 0 }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-label">Other Files</span>
                <span class="metric-value" style="color: #6c757d">{{ repositoryStatistics.otherFiles ?? 0 }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

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
        >All</button>
        <button
          type="button"
          class="btn btn-sm"
          :class="statusFilter === 'PENDING' ? 'btn-primary' : 'btn-outline-secondary'"
          @click="statusFilter = 'PENDING'"
        >Pending</button>
        <button
          type="button"
          class="btn btn-sm"
          :class="statusFilter === 'ACTIVE' ? 'btn-primary' : 'btn-outline-secondary'"
          @click="statusFilter = 'ACTIVE'"
        >Active</button>
        <button
          type="button"
          class="btn btn-sm"
          :class="statusFilter === 'FINISHED' ? 'btn-primary' : 'btn-outline-secondary'"
          @click="statusFilter = 'FINISHED'"
        >Finished</button>
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
          <div class="d-flex align-items-center">
            <div class="instance-icon-square me-3" :class="instanceIconClass(instance.status)">
              <i class="bi bi-box"></i>
            </div>
            <div class="flex-grow-1 min-width-0">
              <div class="d-flex align-items-center">
                <span class="fw-bold text-dark">{{ instance.hostname }}</span>
                <Badge
                  class="ms-2"
                  :value="instance.status"
                  :variant="instanceBadgeVariant(instance.status)"
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
import SectionHeaderBar from '@/components/SectionHeaderBar.vue';
import ProjectInstanceClient from '@/services/api/ProjectInstanceClient';
import ProjectRepositoryClient from '@/services/api/ProjectRepositoryClient';
import ProjectInstance from '@/services/api/model/ProjectInstance';
import type RepositoryStatistics from '@/services/api/model/RepositoryStatistics';
import FormattingService from '@/services/FormattingService';
import { useNavigation } from '@/composables/useNavigation';
import '@/styles/shared-components.css';

const { workspaceId, projectId, generateInstanceUrl } = useNavigation();

const loading = ref(true);
const searchQuery = ref('');
const statusFilter = ref('');
const instances = ref<ProjectInstance[]>([]);
const repositoryStatistics = ref<RepositoryStatistics | null>(null);

const pendingCount = computed(() => instances.value.filter(i => i.status === 'PENDING').length);
const activeCount = computed(() => instances.value.filter(i => i.status === 'ACTIVE').length);
const finishedCount = computed(() => instances.value.filter(i => i.status === 'FINISHED').length);

const totalSessions = computed(() => instances.value.reduce((sum, i) => sum + i.sessionCount, 0));

const uptimeRange = computed(() => {
  if (instances.value.length === 0) return '\u2014';
  const oldest = Math.min(...instances.value.map(i => i.startedAt));
  return FormattingService.formatRelativeTime(oldest);
});

function instanceCardClass(status: string): string {
  if (status === 'PENDING') return 'instance-pending';
  if (status === 'ACTIVE') return 'instance-active';
  return 'instance-finished';
}

function instanceIconClass(status: string): string {
  if (status === 'PENDING') return 'icon-pending';
  if (status === 'ACTIVE') return 'icon-active';
  return 'icon-finished';
}

function instanceBadgeVariant(status: string): string {
  if (status === 'PENDING') return 'blue';
  if (status === 'ACTIVE') return 'warning';
  return 'green';
}

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

  const repositoryClient = new ProjectRepositoryClient(workspaceId.value!, projectId.value!);
  repositoryStatistics.value = await repositoryClient.getRepositoryStatistics();
});
</script>

<style scoped>
/* Compact Stat Cards (matching RepositoryStatistics.vue) */
.compact-stat-card {
  background: linear-gradient(135deg, #f8f9fa, #ffffff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 8px;
  padding: 12px 16px;
  height: 100%;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04),
  0 1px 2px rgba(0, 0, 0, 0.02);
}

.compact-stat-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 3px 8px rgba(0, 0, 0, 0.06),
  0 2px 4px rgba(94, 100, 255, 0.1);
  border-color: rgba(94, 100, 255, 0.15);
}

.compact-stat-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.compact-stat-header i {
  font-size: 1rem;
}

.compact-stat-title {
  font-size: 0.8rem;
  font-weight: 600;
  color: #374151;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.compact-stat-metrics {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.metric-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 2px 0;
}

.metric-label {
  font-size: 0.75rem;
  color: #6b7280;
  font-weight: 500;
}

.metric-value {
  font-size: 0.8rem;
  font-weight: 600;
  color: #374151;
  text-align: right;
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

.instance-pending {
  border-left: 3px solid #3b82f6;
  background-color: rgba(59, 130, 246, 0.03);
}

.instance-pending:hover {
  background-color: rgba(59, 130, 246, 0.06);
}

.instance-active {
  border-left: 3px solid #f59e0b;
  background-color: rgba(245, 158, 11, 0.03);
}

.instance-active:hover {
  background-color: rgba(245, 158, 11, 0.06);
}

.instance-finished {
  border-left: 3px solid #10b981;
  background-color: rgba(16, 185, 129, 0.03);
}

.instance-finished:hover {
  background-color: rgba(16, 185, 129, 0.06);
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
  color: #2563eb;
}

.icon-active {
  background-color: rgba(245, 158, 11, 0.12);
  color: #d97706;
}

.icon-finished {
  background-color: rgba(16, 185, 129, 0.12);
  color: #059669;
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

/* Responsive adjustments for smaller screens */
@media (max-width: 768px) {
  .compact-stat-card {
    padding: 10px 12px;
  }

  .compact-stat-header {
    gap: 6px;
    margin-bottom: 6px;
  }

  .compact-stat-title {
    font-size: 0.75rem;
  }

  .metric-label {
    font-size: 0.7rem;
  }

  .metric-value {
    font-size: 0.75rem;
  }
}

@media (max-width: 576px) {
  .compact-stat-card {
    padding: 8px 10px;
  }

  .compact-stat-metrics {
    gap: 3px;
  }

  .metric-item {
    padding: 1px 0;
  }
}
</style>
