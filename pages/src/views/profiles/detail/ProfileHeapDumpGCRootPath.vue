<template>
  <LoadingState v-if="loading" message="Loading heap dump status..." />

  <div v-else-if="!heapExists" class="no-heap-dump">
    <div class="alert alert-info d-flex align-items-center">
      <i class="bi bi-info-circle me-3 fs-4"></i>
      <div>
        <h6 class="mb-1">No Heap Dump Available</h6>
        <p class="mb-0 small">No heap dump file (.hprof) was found for this profile. To analyze heap memory, generate a heap dump and add it to the recording folder.</p>
      </div>
    </div>
  </div>

  <HeapDumpNotInitialized
      v-else-if="!cacheReady"
      icon="signpost-2"
      message="The heap dump needs to be initialized before you can find paths to GC roots. This process builds indexes and prepares the data for analysis."
  />

  <ErrorState v-else-if="error" :message="error" />

  <div v-else>
    <PageHeader
        title="Path to GC Root"
        description="Find the reference chain from a specific object to its GC root"
        icon="bi-signpost-2"
    />

    <!-- Search Form -->
    <div class="search-bar mb-4">
      <form @submit.prevent="findPaths" class="d-flex align-items-center gap-3">
        <div class="input-group search-input-group">
          <span class="input-group-text">
            <i class="bi bi-search search-icon"></i>
          </span>
          <input
              id="objectIdInput"
              v-model="objectIdInput"
              type="text"
              inputmode="numeric"
              class="form-control"
              placeholder="Object ID"
          />
        </div>
        <label class="form-check mb-0 text-nowrap">
          <input class="form-check-input" type="checkbox" v-model="excludeWeakRefs">
          <span class="form-check-label small text-muted">Exclude weak refs</span>
        </label>
        <button type="submit" class="btn btn-primary btn-sm text-nowrap" :disabled="!parsedObjectId() || searching">
          <span v-if="searching" class="spinner-border spinner-border-sm me-1" role="status"></span>
          <i v-else class="bi bi-signpost-2 me-1"></i>
          Find Paths
        </button>
      </form>
    </div>

    <!-- Initial empty state -->
    <div v-if="!searched && !searching" class="text-center text-muted py-5">
      <i class="bi bi-signpost-2 fs-1 d-block mb-3" style="opacity: 0.3;"></i>
      <p class="mb-1">Enter an object ID to find its path to a GC root.</p>
      <p class="small">You can find object IDs in the Biggest Objects, Dominator Tree, or OQL Query pages.</p>
    </div>

    <!-- Searching spinner -->
    <div v-else-if="searching" class="text-center py-4">
      <div class="spinner-border spinner-border-sm me-2" role="status"></div>
      <span class="text-muted">Finding paths to GC roots...</span>
    </div>

    <!-- Search error -->
    <div v-else-if="searchError" class="alert alert-danger">
      <i class="bi bi-exclamation-triangle me-2"></i>
      {{ searchError }}
    </div>

    <!-- No results -->
    <div v-else-if="paths.length === 0" class="text-center text-muted py-4">
      <i class="bi bi-x-circle fs-3 d-block mb-2"></i>
      <p>No GC root path found for object <code>{{ searchedObjectId }}</code>.</p>
    </div>

    <!-- Results -->
    <div v-else>
      <div class="d-flex align-items-center gap-2 mb-3">
        <span class="badge bg-primary">{{ paths.length }} path{{ paths.length !== 1 ? 's' : '' }}</span>
        <span class="text-muted small">found for object <code>{{ searchedObjectId }}</code></span>
      </div>
      <GCRootPathVisualization :paths="paths" @select-object-id="onSelectObjectId" />
    </div>

    <InstanceDetailPanel
        :is-open="!!selectedObjectId"
        :object-id="selectedObjectId"
        :client="heapClient"
        @close="selectedObjectId = null"
        @navigate="onNavigateInstance"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, shallowRef } from 'vue';
import { useRoute } from 'vue-router';
import PageHeader from '@/components/layout/PageHeader.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import HeapDumpNotInitialized from '@/components/HeapDumpNotInitialized.vue';
import GCRootPathVisualization from '@/components/heap/GCRootPathVisualization.vue';
import InstanceDetailPanel from '@/components/heap/InstanceDetailPanel.vue';
import HeapDumpClient from '@/services/api/HeapDumpClient';
import { GCRootPath } from '@/services/api/model/GCRootPath';

const route = useRoute();
const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref<string | null>(null);
const heapExists = ref(false);
const cacheReady = ref(false);

const objectIdInput = ref('');
const excludeWeakRefs = ref(true);
const searching = ref(false);
const searchError = ref<string | null>(null);
const searched = ref(false);
const searchedObjectId = ref<number | null>(null);
const paths = ref<GCRootPath[]>([]);
const selectedObjectId = ref<number | null>(null);
const heapClient = shallowRef<HeapDumpClient | null>(null);

let client: HeapDumpClient;

const parsedObjectId = (): number | null => {
  const parsed = Number(objectIdInput.value);
  return !isNaN(parsed) && parsed > 0 ? parsed : null;
};

const findPaths = async () => {
  const objectId = parsedObjectId();
  if (!objectId) return;

  searching.value = true;
  searchError.value = null;
  paths.value = [];
  searched.value = true;
  searchedObjectId.value = objectId;

  try {
    paths.value = await client.getPathToGCRoot(objectId, excludeWeakRefs.value, 3);
  } catch (err) {
    searchError.value = err instanceof Error ? err.message : 'Failed to find GC root paths';
  } finally {
    searching.value = false;
  }
};

const onSelectObjectId = (objectId: number) => {
  selectedObjectId.value = objectId;
};

const onNavigateInstance = (objectId: number) => {
  selectedObjectId.value = objectId;
};

const loadData = async () => {
  try {
    loading.value = true;
    error.value = null;

    client = new HeapDumpClient(profileId);
    heapClient.value = client;

    heapExists.value = await client.exists();
    if (!heapExists.value) {
      loading.value = false;
      return;
    }

    cacheReady.value = await client.isCacheReady();
    if (!cacheReady.value) {
      loading.value = false;
      return;
    }

    // Check for query param deep-link
    const queryObjectId = route.query.objectId;
    if (queryObjectId) {
      objectIdInput.value = String(queryObjectId);
      if (parsedObjectId()) {
        await findPaths();
      }
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load heap dump status';
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.no-heap-dump {
  padding: 2rem;
}

.search-bar {
  background: #fafbfc;
  border: 1px solid #e9ecef;
  border-radius: 8px;
  padding: 0.6rem 1rem;
}

.search-input-group {
  max-width: 360px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  border-radius: 6px;
  overflow: hidden;
}

.search-input-group .input-group-text {
  background: #fff;
  border-right: none;
  padding: 0 0.6rem;
}

.search-input-group .search-icon {
  font-size: 0.8rem;
  color: #6c757d;
}

.search-input-group .form-control {
  border-left: none;
  font-size: 0.85rem;
  height: 34px;
}

.search-input-group .form-control:focus {
  box-shadow: none;
  border-color: #ced4da;
}
</style>
