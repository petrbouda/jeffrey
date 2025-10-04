<template>
  <div class="container-fluid p-0">
    <DashboardHeader 
      title="Saved Flamegraphs"
      description="View and manage your saved flamegraphs"
      icon="bookmark"
    />

    <!-- Loading state -->
    <div v-if="isLoading" class="text-center py-5">
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
    </div>

    <!-- Flamegraphs table -->
    <div v-else-if="savedFlamegraphs.length > 0" class="flamegraphs-table">
      <div class="card">
        <div class="card-body p-0">
          <div class="table-responsive">
            <table class="table table-hover align-middle mb-0">
              <thead class="table-light">
                <tr>
                  <th scope="col">Name</th>
                  <th scope="col">Event Type</th>
                  <th scope="col">Type</th>
                  <th scope="col">Created</th>
                  <th scope="col" class="text-center">Actions</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="graph in savedFlamegraphs" :key="graph.id" class="border-bottom">
                  <td>
                    <div class="d-flex align-items-center">
                      <span class="fw-medium">{{ graph.name }}</span>
                    </div>
                  </td>
                  <td>
                    <span class="badge bg-light text-dark">{{ graph.eventType }}</span>
                  </td>
                  <td>
                    <div class="d-flex align-items-center">
                      <span v-if="graph.isPrimary" class="badge bg-success me-1">Primary</span>
                      <span v-else class="badge bg-info me-1">Differential</span>
                      <span v-if="graph.withTimeseries" class="badge bg-secondary ms-1">With Timeseries</span>
                    </div>
                  </td>
                  <td>
                    <span>{{ graph.createdAt }}</span>
                  </td>
                  <td>
                    <div class="d-flex justify-content-center">
                      <button @click="viewGraph(graph)" class="btn btn-sm btn-outline-primary me-2">
                        <i class="bi bi-eye"></i>
                      </button>
                      <button @click="deleteGraph(graph.id)" class="btn btn-sm btn-outline-danger">
                        <i class="bi bi-trash"></i>
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>

    <!-- Empty state -->
    <div v-else class="empty-state">
      <div class="card">
        <div class="card-body text-center py-5">
          <i class="bi bi-bookmark-star fs-1 text-muted mb-3"></i>
          <h5>No saved flamegraphs yet</h5>
          <p class="text-muted">Saved flamegraphs will appear here after you save them from any flamegraph view.</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import FlamegraphRepositoryClient from '@/services/flamegraphs/client/FlamegraphRepositoryClient';
import SavedGraphMetadata from "@/services/flamegraphs/model/save/SavedGraphMetadata";
import {useRoute, useRouter} from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import DashboardHeader from '@/components/DashboardHeader.vue';

// Define props
const props = defineProps({
  profile: {
    type: Object,
    default: null
  },
  secondaryProfile: {
    type: Object,
    default: null
  }
});

const router = useRouter();
const route = useRoute();
const { workspaceId, projectId } = useNavigation();
const savedFlamegraphs = ref<SavedGraphMetadata[]>([]);
const isLoading = ref(true);

const profileId = route.params.profileId as string;

onMounted(async () => {
  await loadFlamegraphs();
});

const loadFlamegraphs = async () => {
  isLoading.value = true;
  try {
    const client = new FlamegraphRepositoryClient(workspaceId.value!, projectId.value!, profileId);
    savedFlamegraphs.value = await client.list();
  } catch (error) {
    console.error('Failed to load saved flamegraphs:', error);
  } finally {
    isLoading.value = false;
  }
};

const viewGraph = (graph: SavedGraphMetadata) => {
  // Navigate to the simple flamegraph view
  router.push({
    name: 'profile-flamegraph-simple',
    params: {
      graphId: graph.id
    }
  });
};

const deleteGraph = async (graphId: string) => {
  if (!props.profile) {
    console.error('Cannot delete graph: profile is null');
    return;
  }

  if (confirm('Are you sure you want to delete this flamegraph?')) {
    try {
      const client = new FlamegraphRepositoryClient(workspaceId.value!, props.profile.projectId, props.profile.id);
      await client.delete(graphId);
      await loadFlamegraphs();
    } catch (error) {
      console.error('Failed to delete flamegraph:', error);
    }
  }
};
</script>

<style scoped>
.flamegraphs-saved-title {
  font-size: 1.75rem;
  font-weight: 600;
  color: #343a40;
  margin-bottom: 0.5rem;
  display: flex;
  align-items: center;
}

.empty-state .card {
  border: none;
  border-radius: 0.5rem;
  box-shadow: 0 0.125rem 0.25rem rgba(0, 0, 0, 0.075);
}

.empty-state .bi {
  display: block;
  margin: 0 auto 1rem;
}

.flamegraphs-table .card {
  border: none;
  border-radius: 0.5rem;
  box-shadow: 0 0.125rem 0.25rem rgba(0, 0, 0, 0.075);
}

.badge {
  font-weight: 500;
  padding: 0.35em 0.65em;
}
</style>
