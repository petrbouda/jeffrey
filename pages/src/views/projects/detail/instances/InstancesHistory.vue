<template>
  <PageHeader
    title="Instance History"
    description="View all instances that have connected to this project, including both online and offline instances."
    icon="bi-clock-history"
  >
    <!-- Search Box and Filter -->
    <div class="d-flex gap-3 mb-3">
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
      <select v-model="statusFilter" class="form-select" style="width: auto;">
        <option value="">All Statuses</option>
        <option value="ONLINE">Online</option>
        <option value="OFFLINE">Offline</option>
      </select>
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
        <div v-for="instance in filteredInstances" :key="instance.id"
             class="child-row p-3 mb-2 rounded"
             :class="{ 'offline-row': instance.status === 'OFFLINE' }">
          <div class="d-flex justify-content-between align-items-center">
            <!-- Left: Status dot + Instance info -->
            <div class="d-flex align-items-center">
              <span class="status-dot me-3" :class="instance.status === 'ONLINE' ? 'online' : 'offline'"></span>
              <div>
                <div class="fw-bold">
                  <i class="bi bi-box me-2 text-secondary"></i>
                  {{ instance.hostname }}
                  <Badge
                    class="ms-2"
                    :value="instance.status"
                    :variant="instance.status === 'ONLINE' ? 'green' : 'gray'"
                    size="xs"
                  />
                </div>
                <!-- Metadata row -->
                <div class="d-flex text-muted small mt-1">
                  <div class="me-3">
                    <i class="bi bi-layers me-1"></i>
                    {{ instance.sessionCount }} sessions
                  </div>
                  <div>
                    <i class="bi bi-play-circle me-1"></i>
                    Started: {{ FormattingService.formatRelativeTime(instance.startedAt) }}
                  </div>
                </div>
              </div>
            </div>

            <!-- Right: Action buttons -->
            <div class="d-flex">
              <button class="btn btn-sm btn-outline-primary" @click="navigateToInstance(instance.id)">
                <i class="bi bi-eye me-1"></i> View Sessions
              </button>
            </div>
          </div>
        </div>
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
import ProjectInstanceClient from '@/services/api/ProjectInstanceClient';
import ProjectInstance from '@/services/api/model/ProjectInstance';
import FormattingService from '@/services/FormattingService';
import { useNavigation } from '@/composables/useNavigation';
import '@/styles/shared-components.css';

const { workspaceId, projectId, navigateToInstance } = useNavigation();

const loading = ref(true);
const searchQuery = ref('');
const statusFilter = ref('');
const instances = ref<ProjectInstance[]>([]);

const onlineCount = computed(() => instances.value.filter(i => i.status === 'ONLINE').length);
const offlineCount = computed(() => instances.value.filter(i => i.status === 'OFFLINE').length);

const filteredInstances = computed(() => {
  let result = instances.value;

  // Filter by status
  if (statusFilter.value) {
    result = result.filter(i => i.status === statusFilter.value);
  }

  // Filter by search query
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

.status-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-dot.online {
  background-color: #22c55e;
  box-shadow: 0 0 8px rgba(34, 197, 94, 0.4);
}

.status-dot.offline {
  background-color: #9ca3af;
}

.offline-row {
  opacity: 0.7;
}

.form-select {
  border-radius: 6px;
  border: 1px solid #e2e8f0;
  font-size: 0.875rem;
  padding: 0.5rem 2rem 0.5rem 0.75rem;
}
</style>
