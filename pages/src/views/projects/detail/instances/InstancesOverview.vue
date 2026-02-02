<template>
  <PageHeader
    title="Instances Overview"
    description="View all instances connected to this project. Filter by status to see active or finished instances."
    icon="bi-box"
  >
    <!-- Inline Stats Strip -->
    <div class="inline-stats-strip mb-3" v-if="!loading && instances.length > 0">
      <div class="stat-item">
        <i class="bi bi-grid"></i>
        <span class="stat-label">Total</span>
        <span class="stat-value">{{ instances.length }}</span>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-item">
        <i class="bi bi-broadcast" style="color: #f59e0b;"></i>
        <span class="stat-label">Active</span>
        <span class="stat-value">{{ activeCount }}</span>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-item">
        <i class="bi bi-check-circle" style="color: #10b981;"></i>
        <span class="stat-label">Finished</span>
        <span class="stat-value">{{ finishedCount }}</span>
      </div>
    </div>

    <!-- Search Box and Filter -->
    <div class="d-flex gap-3 mb-3 align-items-center">
      <SearchBox v-model="searchQuery" placeholder="Search instances..." class="flex-grow-1" />
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
      <div class="d-flex align-items-center mb-3 gap-3">
        <div class="instances-header-bar flex-grow-1 d-flex align-items-center px-3">
          <span class="header-text">All Instances ({{ filteredInstances.length }})</span>
        </div>
      </div>
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
          :class="instance.status === 'ACTIVE' ? 'instance-active' : 'instance-finished'"
        >
          <div class="d-flex align-items-center">
            <div class="instance-icon-square me-3" :class="instance.status === 'ACTIVE' ? 'icon-active' : 'icon-finished'">
              <i class="bi bi-box"></i>
            </div>
            <div class="flex-grow-1 min-width-0">
              <div class="d-flex align-items-center">
                <span class="fw-bold text-dark">{{ instance.hostname }}</span>
                <Badge
                  class="ms-2"
                  :value="instance.status"
                  :variant="instance.status === 'ACTIVE' ? 'warning' : 'green'"
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

const activeCount = computed(() => instances.value.filter(i => i.status === 'ACTIVE').length);
const finishedCount = computed(() => instances.value.filter(i => i.status === 'FINISHED').length);

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
/* Blue gradient header bar matching RepositoryView */
.instances-header-bar {
  background: linear-gradient(135deg, #5e64ff 0%, #4a50e2 100%);
  border: 1px solid #4a50e2;
  border-radius: 6px;
  box-shadow: 0 2px 6px rgba(94, 100, 255, 0.25);
  height: 31px;
}

.header-text {
  font-size: 0.75rem;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.95);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  font-family: 'SF Pro Display', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
}

/* Inline stats strip */
.inline-stats-strip {
  display: flex;
  align-items: center;
  gap: 16px;
  background: linear-gradient(135deg, #f8f9fa, #ffffff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 8px;
  padding: 8px 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.stat-item i {
  font-size: 0.85rem;
  color: #5e64ff;
}

.stat-label {
  font-size: 0.75rem;
  font-weight: 600;
  color: #6b7280;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.stat-value {
  font-size: 0.85rem;
  font-weight: 700;
  color: #374151;
}

.stat-divider {
  width: 1px;
  height: 18px;
  background-color: #e2e8f0;
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
</style>
