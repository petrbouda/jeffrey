<template>
  <div>
    <MainCard>
      <template #header>
        <MainCardHeader
          icon="bi bi-box"
          title="Instances Overview"
        />
      </template>

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
              <div class="metric-item" v-if="expiredCount > 0">
                <span class="metric-label">Expired Instances</span>
                <span class="metric-value">{{ expiredCount }}</span>
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
                <span class="metric-value">{{
                  FormattingService.formatBytes(repositoryStatistics.totalSize)
                }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-label">Total Files</span>
                <span class="metric-value">{{ repositoryStatistics.totalFiles }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-label">Biggest Session</span>
                <span class="metric-value">{{
                  FormattingService.formatBytes(repositoryStatistics.biggestSessionSize)
                }}</span>
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
            <div class="compact-stat-metrics compact-stat-metrics-grid">
              <div class="metric-item metric-header-row">
                <span class="metric-label"></span>
                <span class="metric-col-header">Count</span>
                <span class="metric-col-header">Size</span>
              </div>
              <div class="metric-item">
                <span class="metric-label">JFR Files</span>
                <span class="metric-value" style="color: #5e64ff">{{
                  repositoryStatistics.jfrFiles ?? 0
                }}</span>
                <span class="metric-value metric-size" style="color: #5e64ff">{{
                  FormattingService.formatBytes(repositoryStatistics.jfrSize ?? 0)
                }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-label">Heap Dumps</span>
                <span class="metric-value" style="color: #6f42c1">{{
                  repositoryStatistics.heapDumpFiles ?? 0
                }}</span>
                <span class="metric-value metric-size" style="color: #6f42c1">{{
                  FormattingService.formatBytes(repositoryStatistics.heapDumpSize ?? 0)
                }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-label">JVM Logs</span>
                <span class="metric-value" style="color: #14b8a6">{{
                  repositoryStatistics.logFiles ?? 0
                }}</span>
                <span class="metric-value metric-size" style="color: #14b8a6">{{
                  FormattingService.formatBytes(repositoryStatistics.logSize ?? 0)
                }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-label">Application Logs</span>
                <span class="metric-value" style="color: #8b5e3c">{{
                  repositoryStatistics.appLogFiles ?? 0
                }}</span>
                <span class="metric-value metric-size" style="color: #8b5e3c">{{
                  FormattingService.formatBytes(repositoryStatistics.appLogSize ?? 0)
                }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-label">JVM Error Logs</span>
                <span class="metric-value" style="color: #c62828">{{
                  repositoryStatistics.errorLogFiles ?? 0
                }}</span>
                <span class="metric-value metric-size" style="color: #c62828">{{
                  FormattingService.formatBytes(repositoryStatistics.errorLogSize ?? 0)
                }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-label">Other Files</span>
                <span class="metric-value" style="color: #6c757d">{{
                  repositoryStatistics.otherFiles ?? 0
                }}</span>
                <span class="metric-value metric-size" style="color: #6c757d">{{
                  FormattingService.formatBytes(repositoryStatistics.otherSize ?? 0)
                }}</span>
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
        <button
          type="button"
          class="btn btn-sm"
          :class="statusFilter === 'EXPIRED' ? 'btn-primary' : 'btn-outline-secondary'"
          @click="statusFilter = 'EXPIRED'"
        >
          Expired
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
                  <Badge
                    v-if="instance.expiringAt"
                    class="ms-1"
                    value="Expiring"
                    variant="warning"
                    size="xs"
                  />
                </div>
                <div class="instance-meta">
                  <span><i class="bi bi-layers me-1"></i>{{ instance.sessionCount }} sessions</span>
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
import LoadingState from '@/components/LoadingState.vue';
import EmptyState from '@/components/EmptyState.vue';
import Badge from '@/components/Badge.vue';
import MainCard from '@/components/MainCard.vue';
import MainCardHeader from '@/components/MainCardHeader.vue';
import SectionHeaderBar from '@/components/SectionHeaderBar.vue';
import ProjectInstanceClient from '@/services/api/ProjectInstanceClient';
import ProjectRepositoryClient from '@/services/api/ProjectRepositoryClient';
import ProjectInstance from '@/services/api/model/ProjectInstance';
import type RepositoryStatistics from '@/services/api/model/RepositoryStatistics';
import FormattingService from '@/services/FormattingService';
import TimelineBar from '@/components/TimelineBar.vue';
import { useNavigation } from '@/composables/useNavigation';
import '@/styles/shared-components.css';
import type { Variant } from '@/types/ui';

const { serverId, workspaceId, projectId, generateInstanceUrl } = useNavigation();

const loading = ref(true);
const searchQuery = ref('');
const statusFilter = ref('');
const instances = ref<ProjectInstance[]>([]);
const repositoryStatistics = ref<RepositoryStatistics | null>(null);

const pendingCount = computed(() => instances.value.filter(i => i.status === 'PENDING').length);
const activeCount = computed(() => instances.value.filter(i => i.status === 'ACTIVE').length);
const finishedCount = computed(() => instances.value.filter(i => i.status === 'FINISHED').length);
const expiredCount = computed(() => instances.value.filter(i => i.status === 'EXPIRED').length);

const totalSessions = computed(() => instances.value.reduce((sum, i) => sum + i.sessionCount, 0));

const uptimeRange = computed(() => {
  if (instances.value.length === 0) return '\u2014';
  const oldest = Math.min(...instances.value.map(i => i.createdAt));
  return FormattingService.formatRelativeTime(oldest);
});

function instanceCardClass(status: string): string {
  if (status === 'PENDING') return 'instance-pending';
  if (status === 'ACTIVE') return 'instance-active';
  if (status === 'EXPIRED') return 'instance-expired';
  return 'instance-finished';
}

function instanceIdentityClass(status: string): string {
  if (status === 'PENDING') return 'identity-pending';
  if (status === 'ACTIVE') return 'identity-active';
  if (status === 'EXPIRED') return 'identity-expired';
  return 'identity-finished';
}

function instanceIconClass(status: string): string {
  if (status === 'PENDING') return 'icon-pending';
  if (status === 'ACTIVE') return 'icon-active';
  if (status === 'EXPIRED') return 'icon-expired';
  return 'icon-finished';
}

function instanceBadgeVariant(status: string): Variant {
  if (status === 'PENDING') return 'blue';
  if (status === 'ACTIVE') return 'warning';
  if (status === 'EXPIRED') return 'grey';
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
  const client = new ProjectInstanceClient(serverId.value, workspaceId.value!, projectId.value!);
  instances.value = await client.list();
  loading.value = false;

  const repositoryClient = new ProjectRepositoryClient(serverId.value, workspaceId.value!, projectId.value!);
  repositoryStatistics.value = await repositoryClient.getRepositoryStatistics();
});
</script>

<style scoped>
/* Compact Stat Cards (matching RepositoryStatistics.vue) */
.compact-stat-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 12px 16px;
  height: 100%;
  transition: all var(--transition-base);
  box-shadow: var(--shadow-base);
}

.compact-stat-card:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-md);
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
  color: var(--color-text);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.compact-stat-metrics {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.compact-stat-metrics-grid {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: 4px 12px;
}

.metric-item {
  display: grid;
  grid-template-columns: 1fr auto auto;
  align-items: center;
  gap: 12px;
  padding: 2px 0;
}

.compact-stat-metrics-grid .metric-item {
  display: contents;
}

.metric-label {
  font-size: 0.75rem;
  color: var(--color-text-muted);
  font-weight: 500;
}

.compact-stat-metrics-grid .metric-label,
.compact-stat-metrics-grid .metric-value {
  padding: 2px 0;
}

.metric-value {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--color-text);
  text-align: right;
  min-width: 36px;
}

.metric-size {
  min-width: 64px;
}

.metric-col-header {
  font-size: 0.65rem;
  font-weight: 600;
  color: var(--color-text-light);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  text-align: right;
  min-width: 36px;
}

.metric-col-header:last-child {
  min-width: 64px;
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

.instance-expired {
  border-left: 3px solid var(--color-text-light);
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

.identity-expired {
  background-color: rgba(156, 163, 175, 0.04);
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

.icon-expired {
  background-color: rgba(156, 163, 175, 0.12);
  color: var(--color-text-muted);
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
}
</style>
