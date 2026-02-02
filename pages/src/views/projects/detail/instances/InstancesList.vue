<template>
  <PageHeader
    title="Active Instances"
    description="View currently connected application instances. Each instance represents a running container or pod connected to this project."
    icon="bi-box"
  >
    <!-- Search Box -->
    <div class="mb-3">
      <div class="input-group search-container">
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
    </div>

    <!-- Instances Header Bar -->
    <div class="col-12">
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
        <div v-for="instance in filteredInstances" :key="instance.id"
             class="child-row p-3 mb-2 rounded">
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
                <i class="bi bi-eye me-1"></i> View
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
const instances = ref<ProjectInstance[]>([]);

// Filter to only show ONLINE instances for "Active" view
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
</style>
