<template>
  <div v-if="!profile" class="text-center py-5">
    <div class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>
    <p class="mt-2">Loading flamegraph data...</p>
  </div>

  <div v-else class="flamegraphs-primary-container">
    <h4 class="mb-4">Primary Flamegraphs</h4>
    
    <div class="row g-3">
      <!-- Execution Samples Card -->
      <div class="col-12 col-md-6 col-lg-4">
        <div class="card mb-2 shadow-sm guardian-card border-primary h-100 bg-primary-subtle">
          <div class="card-header bg-primary-subtle">
            <div class="d-flex align-items-center mb-1">
              <div class="status-icon bg-primary me-2">
                <i class="bi bi-cpu"></i>
              </div>
              <h5 class="card-title mb-0">Execution Samples</h5>
            </div>
          </div>
          <div class="card-body">
            <div class="row g-3">
              <div class="col-md-6">
                <div class="small text-muted">Code</div>
                <div class="fw-bold">Java</div>
              </div>
              <div class="col-md-6">
                <div class="small text-muted">Sub-Type</div>
                <div class="fw-bold">CPU Time</div>
              </div>
              <div class="col-md-6">
                <div class="small text-muted">Source</div>
                <div class="fw-bold">JFR Recording</div>
              </div>
              <div class="col-md-6">
                <div class="small text-muted">Samples</div>
                <div class="fw-bold">12,450</div>
              </div>
              <div class="col-md-6">
                <div class="small text-muted">Total Time on CPU</div>
                <div class="fw-bold">345.21 s</div>
              </div>
              <div class="col-md-6">
                <div class="small text-muted">Sample Interval</div>
                <div class="fw-bold">10 ms</div>
              </div>
              <div class="col-md-12 mt-4">
                <div class="form-check">
                  <input class="form-check-input" type="checkbox" id="threadMode">
                  <label class="form-check-label" for="threadMode">
                    Use Thread-mode
                  </label>
                </div>
              </div>
            </div>
          </div>
          <div class="card-footer bg-transparent">
            <div class="d-flex justify-content-end">
              <router-link 
                :to="{ 
                  name: 'profile-flamegraph-view', 
                  params: { projectId, profileId }, 
                  query: { 
                    type: 'execution',
                    source: 'jfr',
                    mode: 'cpu'
                  }
                }" 
                class="btn btn-primary"
              >
                <i class="bi bi-fire me-1"></i> Show Flamegraph
              </router-link>
            </div>
          </div>
        </div>
      </div>
      
      <!-- Wall-Clock Samples Card -->
      <div class="col-12 col-md-6 col-lg-4">
        <div class="card mb-2 shadow-sm guardian-card border-info h-100 bg-info-subtle">
          <div class="card-header bg-info-subtle">
            <div class="d-flex align-items-center mb-1">
              <div class="status-icon bg-info me-2">
                <i class="bi bi-clock"></i>
              </div>
              <h5 class="card-title mb-0">Wall-Clock Samples</h5>
            </div>
          </div>
          <div class="card-body">
            <div class="row g-3">
              <div class="col-md-6">
                <div class="small text-muted">Code</div>
                <div class="fw-bold">Java</div>
              </div>
              <div class="col-md-6">
                <div class="small text-muted">Source</div>
                <div class="fw-bold">JFR Recording</div>
              </div>
              <div class="col-md-6">
                <div class="small text-muted">Samples</div>
                <div class="fw-bold">8,723</div>
              </div>
              <div class="col-md-6">
                <div class="small text-muted">Total Time</div>
                <div class="fw-bold">421.58 s</div>
              </div>
              <div class="col-md-6">
                <div class="small text-muted">Sample Interval</div>
                <div class="fw-bold">20 ms</div>
              </div>
              <div class="col-md-12 mt-4">
                <div class="d-flex flex-column">
                  <div class="form-check mb-2">
                    <input class="form-check-input" type="checkbox" id="threadMode2" checked>
                    <label class="form-check-label" for="threadMode2">
                      Use Thread-mode
                    </label>
                  </div>
                  <div class="form-check mb-2">
                    <input class="form-check-input" type="checkbox" id="excludeIdle" checked>
                    <label class="form-check-label" for="excludeIdle">
                      Exclude Idle Samples
                      <i class="bi bi-info-circle-fill text-muted ms-1" data-bs-toggle="tooltip" data-bs-placement="top" title="Excludes samples that are parked in thread-pools"></i>
                    </label>
                  </div>
                  <div class="form-check">
                    <input class="form-check-input" type="checkbox" id="excludeNonJava" checked>
                    <label class="form-check-label" for="excludeNonJava">
                      Exclude non-Java Samples
                      <i class="bi bi-info-circle-fill text-muted ms-1" data-bs-toggle="tooltip" data-bs-placement="top" title="Excludes samples belonging to JIT, Garbage Collector, and other non-Java threads"></i>
                    </label>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div class="card-footer bg-transparent">
            <div class="d-flex justify-content-end">
              <router-link 
                :to="{ 
                  name: 'profile-flamegraph-view', 
                  params: { projectId, profileId }, 
                  query: { 
                    type: 'wallclock',
                    source: 'jfr',
                    mode: 'time'
                  }
                }" 
                class="btn btn-primary"
              >
                <i class="bi bi-fire me-1"></i> Show Flamegraph
              </router-link>
            </div>
          </div>
        </div>
      </div>
      
      <!-- Memory Allocation Card -->
      <div class="col-12 col-md-6 col-lg-4">
        <div class="card mb-2 shadow-sm guardian-card border-success h-100 bg-success-subtle">
          <div class="card-header bg-success-subtle">
            <div class="d-flex align-items-center mb-1">
              <div class="status-icon bg-success me-2">
                <i class="bi bi-memory"></i>
              </div>
              <h5 class="card-title mb-0">Memory Allocation</h5>
            </div>
          </div>
          <div class="card-body">
            <div class="row g-3">
              <div class="col-md-6">
                <div class="small text-muted">Code</div>
                <div class="fw-bold">Java</div>
              </div>
              <div class="col-md-6">
                <div class="small text-muted">Source</div>
                <div class="fw-bold">JFR Recording</div>
              </div>
              <div class="col-md-6">
                <div class="small text-muted">Samples</div>
                <div class="fw-bold">7,216</div>
              </div>
              <div class="col-md-6">
                <div class="small text-muted">Total Allocation</div>
                <div class="fw-bold">2.45 GB</div>
              </div>
              <div class="col-md-12 mt-4">
                <div class="d-flex flex-column">
                  <div class="form-check mb-2">
                    <input class="form-check-input" type="checkbox" id="memThreadMode">
                    <label class="form-check-label" for="memThreadMode">
                      Use Thread-mode
                    </label>
                  </div>
                  <div class="form-check">
                    <input class="form-check-input" type="checkbox" id="useTotalAllocation" checked>
                    <label class="form-check-label" for="useTotalAllocation">
                      Use Total Allocation
                    </label>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div class="card-footer bg-transparent">
            <div class="d-flex justify-content-end">
              <router-link 
                :to="{ 
                  name: 'profile-flamegraph-view', 
                  params: { projectId, profileId }, 
                  query: { 
                    type: 'memory',
                    source: 'jfr',
                    mode: 'allocation'
                  }
                }" 
                class="btn btn-primary"
              >
                <i class="bi bi-fire me-1"></i> Show Flamegraph
              </router-link>
            </div>
          </div>
        </div>
      </div>
      
      <!-- Monitor Blocked Card -->
      <div class="col-12 col-md-6 col-lg-4">
        <div class="card mb-2 shadow-sm guardian-card border-danger h-100 bg-danger-subtle">
          <div class="card-header bg-danger-subtle">
            <div class="d-flex align-items-center mb-1">
              <div class="status-icon bg-danger me-2">
                <i class="bi bi-stopwatch"></i>
              </div>
              <h5 class="card-title mb-0">Monitor Blocked</h5>
            </div>
          </div>
          <div class="card-body">
            <div class="row g-3">
              <div class="col-md-6">
                <div class="small text-muted">Code</div>
                <div class="fw-bold">Java</div>
              </div>
              <div class="col-md-6">
                <div class="small text-muted">Source</div>
                <div class="fw-bold">JFR Recording</div>
              </div>
              <div class="col-md-6">
                <div class="small text-muted">Samples</div>
                <div class="fw-bold">3,851</div>
              </div>
              <div class="col-md-6">
                <div class="small text-muted">Blocked Time</div>
                <div class="fw-bold">12.73 s</div>
              </div>
              <div class="col-md-12 mt-4">
                <div class="d-flex flex-column">
                  <div class="form-check mb-2">
                    <input class="form-check-input" type="checkbox" id="blockThreadMode">
                    <label class="form-check-label" for="blockThreadMode">
                      Use Thread-mode
                    </label>
                  </div>
                  <div class="form-check">
                    <input class="form-check-input" type="checkbox" id="useBlockedTime" checked>
                    <label class="form-check-label" for="useBlockedTime">
                      Use Blocked Time
                    </label>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div class="card-footer bg-transparent">
            <div class="d-flex justify-content-end">
              <router-link 
                :to="{ 
                  name: 'profile-flamegraph-view', 
                  params: { projectId, profileId }, 
                  query: { 
                    type: 'monitor-blocked',
                    source: 'jfr',
                    mode: 'blocked'
                  }
                }" 
                class="btn btn-primary"
              >
                <i class="bi bi-fire me-1"></i> Show Flamegraph
              </router-link>
            </div>
          </div>
        </div>
      </div>
      
      <!-- Monitor Wait Card -->
      <div class="col-12 col-md-6 col-lg-4">
        <div class="card mb-2 shadow-sm guardian-card border-danger h-100 bg-danger-subtle">
          <div class="card-header bg-danger-subtle">
            <div class="d-flex align-items-center mb-1">
              <div class="status-icon bg-danger me-2">
                <i class="bi bi-stopwatch"></i>
              </div>
              <h5 class="card-title mb-0">Monitor Wait</h5>
            </div>
          </div>
          <div class="card-body">
            <div class="row g-3">
              <div class="col-md-6">
                <div class="small text-muted">Code</div>
                <div class="fw-bold">Java</div>
              </div>
              <div class="col-md-6">
                <div class="small text-muted">Source</div>
                <div class="fw-bold">JFR Recording</div>
              </div>
              <div class="col-md-6">
                <div class="small text-muted">Samples</div>
                <div class="fw-bold">2,742</div>
              </div>
              <div class="col-md-6">
                <div class="small text-muted">Blocked Time</div>
                <div class="fw-bold">8.45 s</div>
              </div>
              <div class="col-md-12 mt-4">
                <div class="d-flex flex-column">
                  <div class="form-check mb-2">
                    <input class="form-check-input" type="checkbox" id="waitThreadMode">
                    <label class="form-check-label" for="waitThreadMode">
                      Use Thread-mode
                    </label>
                  </div>
                  <div class="form-check">
                    <input class="form-check-input" type="checkbox" id="useWaitTime" checked>
                    <label class="form-check-label" for="useWaitTime">
                      Use Blocked Time
                    </label>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div class="card-footer bg-transparent">
            <div class="d-flex justify-content-end">
              <router-link 
                :to="{ 
                  name: 'profile-flamegraph-view', 
                  params: { projectId, profileId }, 
                  query: { 
                    type: 'monitor-wait',
                    source: 'jfr',
                    mode: 'wait'
                  }
                }" 
                class="btn btn-primary"
              >
                <i class="bi bi-fire me-1"></i> Show Flamegraph
              </router-link>
            </div>
          </div>
        </div>
      </div>
      
      <!-- Thread Park Card -->
      <div class="col-12 col-md-6 col-lg-4">
        <div class="card mb-2 shadow-sm guardian-card border-danger h-100 bg-danger-subtle">
          <div class="card-header bg-danger-subtle">
            <div class="d-flex align-items-center mb-1">
              <div class="status-icon bg-danger me-2">
                <i class="bi bi-stopwatch"></i>
              </div>
              <h5 class="card-title mb-0">Thread Park</h5>
            </div>
          </div>
          <div class="card-body">
            <div class="row g-3">
              <div class="col-md-6">
                <div class="small text-muted">Code</div>
                <div class="fw-bold">Java</div>
              </div>
              <div class="col-md-6">
                <div class="small text-muted">Source</div>
                <div class="fw-bold">JFR Recording</div>
              </div>
              <div class="col-md-6">
                <div class="small text-muted">Samples</div>
                <div class="fw-bold">1,845</div>
              </div>
              <div class="col-md-6">
                <div class="small text-muted">Blocked Time</div>
                <div class="fw-bold">5.32 s</div>
              </div>
              <div class="col-md-12 mt-4">
                <div class="d-flex flex-column">
                  <div class="form-check mb-2">
                    <input class="form-check-input" type="checkbox" id="parkThreadMode">
                    <label class="form-check-label" for="parkThreadMode">
                      Use Thread-mode
                    </label>
                  </div>
                  <div class="form-check">
                    <input class="form-check-input" type="checkbox" id="useParkTime" checked>
                    <label class="form-check-label" for="useParkTime">
                      Use Blocked Time
                    </label>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div class="card-footer bg-transparent">
            <div class="d-flex justify-content-end">
              <router-link 
                :to="{ 
                  name: 'profile-flamegraph-view', 
                  params: { projectId, profileId }, 
                  query: { 
                    type: 'thread-park',
                    source: 'jfr',
                    mode: 'park'
                  }
                }" 
                class="btn btn-primary"
              >
                <i class="bi bi-fire me-1"></i> Show Flamegraph
              </router-link>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { defineProps, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Profile } from '@/types';

// Define props
const props = defineProps<{
  profile?: Profile | null;
}>();

const route = useRoute();
const router = useRouter();
const projectId = route.params.projectId as string;
const profileId = route.params.profileId as string;

// Initialize tooltips on component mount
onMounted(() => {
  const tooltipTriggerList = document.querySelectorAll('[data-bs-toggle="tooltip"]');
  if (typeof bootstrap !== 'undefined') {
    tooltipTriggerList.forEach(tooltipTriggerEl => {
      new bootstrap.Tooltip(tooltipTriggerEl);
    });
  }
});
</script>

<style scoped>
.flamegraphs-primary-container .card {
  border: none;
  overflow: hidden;
}

.guardian-card {
  position: relative;
  transition: transform 0.2s, box-shadow 0.2s;
  border-width: 1px;
  border-left-width: 4px;
  overflow: hidden;
}

.guardian-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 0.5rem 1rem rgba(0, 0, 0, 0.1);
  z-index: 10;
}

.card-header {
  border-bottom: 1px solid rgba(0, 0, 0, 0.1);
  padding: 1rem 1.25rem;
}

.status-icon {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 14px;
}

.card-footer {
  border-top: 1px solid rgba(0, 0, 0, 0.05);
  padding: 0.75rem 1rem;
}

.form-check-label {
  font-size: 0.875rem;
}

.border-primary {
  border-left-color: #5e64ff !important;
}

.border-info {
  border-left-color: #17a2b8 !important;
}

.border-success {
  border-left-color: #28a745 !important;
}

.bg-primary-subtle {
  background-color: rgba(94, 100, 255, 0.1) !important;
}

.bg-info-subtle {
  background-color: rgba(23, 162, 184, 0.1) !important;
}

.bg-success-subtle {
  background-color: rgba(40, 167, 69, 0.1) !important;
}

.border-danger {
  border-left-color: #dc3545 !important;
}

.bg-danger-subtle {
  background-color: rgba(220, 53, 69, 0.1) !important;
}

.btn-primary {
  background-color: #5e64ff;
  border-color: #5e64ff;
}

.btn-primary:hover {
  background-color: #4349e8;
  border-color: #4349e8;
}
</style>
