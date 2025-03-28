<template>
  <div v-if="!profile" class="text-center py-5">
    <div class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>
    <p class="mt-2">Loading flamegraph data...</p>
  </div>

  <div v-else class="flamegraph-container">
    <!-- Guardian Check Context (if coming from Guardian) -->
    <div v-if="isFromGuardian && guardianCheck" class="card mb-4 border-0 shadow-sm">
      <div class="card-body">
        <div class="d-flex align-items-start">
          <div :class="`status-icon bg-${getStatusColorClass(guardianCheck.status)} me-3`">
            <i class="bi" :class="guardianCheck.status === 'success' ? 'bi-check-circle-fill' : 
              guardianCheck.status === 'warning' ? 'bi-exclamation-triangle-fill' :
              guardianCheck.status === 'error' ? 'bi-x-circle-fill' :
              guardianCheck.status === 'info' ? 'bi-info-circle-fill' : 'bi-dash-circle-fill'"></i>
          </div>
          <div class="flex-grow-1">
            <div class="d-flex justify-content-between align-items-start">
              <div>
                <h5 class="mb-2">{{ guardianCheck.name }}</h5>
                <p class="text-muted mb-2">{{ guardianCheck.summary }}</p>
              </div>
              <span class="badge" :class="`bg-${getStatusColorClass(guardianCheck.status)}`">
                {{ guardianCheck.score }}/100
              </span>
            </div>
            <div class="small">
              <a href="#" class="link-primary" data-bs-toggle="collapse" data-bs-target="#guardianDetails">
                <i class="bi bi-chevron-down me-1"></i>Show Details
              </a>
            </div>
            <div class="collapse mt-3" id="guardianDetails">
              <div class="card card-body bg-light">
                <h6 class="mb-2">Explanation</h6>
                <p class="text-muted small mb-3">{{ guardianCheck.explanation }}</p>
                <h6 class="mb-2">Solution</h6>
                <p class="text-muted small mb-0">{{ guardianCheck.solution }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  
    <!-- Flamegraph Controls -->
    <div class="card mb-4">
      <div class="card-body">
        <div class="row align-items-center">
          <div class="col-md-4 mb-3 mb-md-0">
            <div class="d-flex align-items-center">
              <label for="flamegraphType" class="form-label mb-0 me-2">View:</label>
              <select class="form-select" id="flamegraphType" v-model="selectedView">
                <option value="cpu">CPU Time</option>
                <option value="wall">Wall Clock Time</option>
                <option value="count">Sample Count</option>
                <option value="alloc">Allocations</option>
              </select>
            </div>
          </div>
          
          <div class="col-md-4 mb-3 mb-md-0">
            <div class="d-flex align-items-center">
              <label for="threadFilter" class="form-label mb-0 me-2">Thread:</label>
              <select class="form-select" id="threadFilter" v-model="selectedThread" :disabled="isFromGuardian">
                <option value="all">All Threads</option>
                <option value="main">main</option>
                <option value="worker-1">worker-1</option>
                <option value="worker-2">worker-2</option>
                <option value="worker-3">worker-3</option>
              </select>
            </div>
          </div>
          
          <div class="col-md-4">
            <div class="d-flex justify-content-md-end gap-2">
              <button class="btn btn-outline-secondary" @click="resetZoom">
                <i class="bi bi-arrows-angle-expand me-1"></i> Reset Zoom
              </button>
              <button class="btn btn-outline-primary" @click="exportFlamegraph">
                <i class="bi bi-download me-1"></i> Export
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- Flamegraph -->
    <div class="card mb-4">
      <div class="card-body p-0">
        <div class="flamegraph-controls bg-light p-2 border-bottom">
          <div class="d-flex justify-content-between align-items-center">
            <div class="form-check form-switch">
              <input class="form-check-input" type="checkbox" id="invertFlamegraph" v-model="invertFlamegraph">
              <label class="form-check-label" for="invertFlamegraph">Invert Flamegraph</label>
            </div>
            
            <div class="btn-group btn-group-sm" role="group">
              <button type="button" class="btn btn-outline-secondary" title="Zoom In">
                <i class="bi bi-zoom-in"></i>
              </button>
              <button type="button" class="btn btn-outline-secondary" title="Zoom Out">
                <i class="bi bi-zoom-out"></i>
              </button>
              <button type="button" class="btn btn-outline-secondary" title="Search">
                <i class="bi bi-search"></i>
              </button>
            </div>
          </div>
        </div>
        
        <div class="flamegraph-placeholder">
          <div class="text-center py-5">
            <i class="bi bi-fire fs-1 text-danger mb-3"></i>
            <h5>Flamegraph Visualization</h5>
            <p class="text-muted">In a real implementation, this would render an interactive flamegraph using a library like d3-flame-graph.</p>
            <div class="flamegraph-demo mx-auto"></div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- Method Details -->
    <div class="card">
      <div class="card-header">
        <h5 class="card-title mb-0">Method Details</h5>
      </div>
      <div class="card-body p-0">
        <div class="method-details p-3 border-bottom" v-if="selectedMethod">
          <h6>{{ selectedMethod.name }}</h6>
          <div class="text-muted small mb-2">{{ selectedMethod.className }}</div>
          
          <div class="row">
            <div class="col-md-3 col-6 mb-2">
              <div class="small text-muted">CPU Time</div>
              <div class="fw-bold">{{ selectedMethod.cpuTime }}ms</div>
            </div>
            <div class="col-md-3 col-6 mb-2">
              <div class="small text-muted">Wall Time</div>
              <div class="fw-bold">{{ selectedMethod.wallTime }}ms</div>
            </div>
            <div class="col-md-3 col-6 mb-2">
              <div class="small text-muted">Samples</div>
              <div class="fw-bold">{{ selectedMethod.samples }}</div>
            </div>
            <div class="col-md-3 col-6 mb-2">
              <div class="small text-muted">Self Time</div>
              <div class="fw-bold">{{ selectedMethod.selfTime }}ms</div>
            </div>
          </div>
        </div>
        
        <div class="p-3" v-else>
          <p class="text-muted mb-0">Click on a frame in the flamegraph to view method details</p>
        </div>
        
        <div class="table-responsive">
          <table class="table table-hover mb-0">
            <thead>
              <tr>
                <th>Method</th>
                <th>Self Time</th>
                <th>Total Time</th>
                <th>Samples</th>
                <th>Percentage</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(method, index) in topMethods" :key="index" @click="selectMethod(method)" class="cursor-pointer">
                <td>
                  <div class="text-truncate" style="max-width: 300px;">{{ method.name }}</div>
                  <div class="small text-muted text-truncate">{{ method.className }}</div>
                </td>
                <td>{{ method.selfTime }}ms</td>
                <td>{{ method.cpuTime }}ms</td>
                <td>{{ method.samples }}</td>
                <td>
                  <div class="d-flex align-items-center">
                    <div class="progress flex-grow-1 me-2" style="height: 6px;">
                      <div class="progress-bar" role="progressbar" :style="{ width: method.percentage + '%' }"></div>
                    </div>
                    <span>{{ method.percentage }}%</span>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, defineProps, onMounted, computed } from 'vue';
import { useRoute } from 'vue-router';
import { Profile, GuardianCheck } from '@/types';

const route = useRoute();

// Define props
const props = defineProps<{
  profile?: Profile | null;
}>();

// State
const selectedView = ref('cpu');
const selectedThread = ref('all');
const invertFlamegraph = ref(false);
const guardianCheck = ref<GuardianCheck | null>(null);

// Check if we're coming from a Guardian analysis
const isFromGuardian = computed(() => {
  return route.query.source === 'guardian' && route.query.checkId;
});

// Load Guardian check data if available
onMounted(() => {
  // Check if we have a Guardian check in session storage
  const storedCheck = sessionStorage.getItem('guardianCheck');
  if (storedCheck && isFromGuardian.value) {
    try {
      guardianCheck.value = JSON.parse(storedCheck);
      
      // If we have methods from the Guardian check, use them
      if (guardianCheck.value?.flamegraphData?.methods) {
        topMethods.value = guardianCheck.value.flamegraphData.methods;
        selectedMethod.value = topMethods.value[0];
      }
    } catch (e) {
      console.error('Error parsing Guardian check data:', e);
    }
  }
});

// Mock data for demonstration when not coming from Guardian
const topMethods = ref([
  { 
    name: 'processRequest', 
    className: 'com.example.RequestHandler', 
    cpuTime: 1250, 
    wallTime: 1300,
    selfTime: 350,
    samples: 526,
    percentage: 85 
  },
  { 
    name: 'parseJSON', 
    className: 'com.example.JSONParser', 
    cpuTime: 980, 
    wallTime: 1050,
    selfTime: 420,
    samples: 418,
    percentage: 70 
  },
  { 
    name: 'executeQuery', 
    className: 'com.example.DatabaseService', 
    cpuTime: 750, 
    wallTime: 820,
    selfTime: 380,
    samples: 312,
    percentage: 55 
  },
  { 
    name: 'calculateStatistics', 
    className: 'com.example.StatisticsEngine', 
    cpuTime: 650, 
    wallTime: 720,
    selfTime: 290,
    samples: 270,
    percentage: 45 
  },
  { 
    name: 'renderTemplate', 
    className: 'com.example.TemplateEngine', 
    cpuTime: 480, 
    wallTime: 520,
    selfTime: 210,
    samples: 198,
    percentage: 30 
  }
]);

// Selected method
const selectedMethod = ref(topMethods.value[0]);

// Get status color class for Guardian check
const getStatusColorClass = (status: string) => {
  switch (status) {
    case 'success': return 'success';
    case 'warning': return 'warning';
    case 'error': return 'danger';
    case 'info': return 'info';
    case 'disabled': return 'secondary';
    default: return 'primary';
  }
};

// Methods
const resetZoom = () => {
  console.log('Reset zoom');
  // In a real implementation, this would reset the flamegraph zoom
};

const exportFlamegraph = () => {
  console.log('Export flamegraph');
  // In a real implementation, this would export the flamegraph as an SVG or PNG
};

const selectMethod = (method: any) => {
  selectedMethod.value = method;
  // In a real implementation, this would highlight the corresponding frame in the flamegraph
};
</script>

<style scoped>
.flamegraph-container .card {
  border: none;
  box-shadow: 0 0.125rem 0.25rem rgba(0, 0, 0, 0.075);
}

.cursor-pointer {
  cursor: pointer;
}

.flamegraph-placeholder {
  height: 300px;
  overflow: auto;
}

.flamegraph-demo {
  width: 80%;
  height: 150px;
  background-image: linear-gradient(to right, 
    #ff9d9d, #ff9d9d 20%, #ffbf9d 20%, #ffbf9d 25%,
    #ffd19d 25%, #ffd19d 30%, #c9e7a5 30%, #c9e7a5 40%,
    #9dddff 40%, #9dddff 50%, #9db5ff 50%, #9db5ff 60%,
    #cc99ff 60%, #cc99ff 70%, #ff9dce 70%, #ff9dce 85%,
    #9dffee 85%, #9dffee 100%
  );
  border-radius: 4px;
  position: relative;
}

.flamegraph-demo::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-image: linear-gradient(to bottom, 
    rgba(255,255,255,0) 0%,
    rgba(255,255,255,0) 50%,
    rgba(255,255,255,0.1) 51%,
    rgba(255,255,255,0.1) 100%
  );
  background-size: 100% 20px;
}

.progress {
  background-color: #e9ecef;
}

.progress-bar {
  background-color: #3f51b5;
}

.status-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 16px;
  flex-shrink: 0;
}
</style>