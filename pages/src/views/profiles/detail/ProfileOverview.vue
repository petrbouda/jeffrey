<template>
  <div v-if="!profile" class="text-center py-5">
    <div class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>
    <p class="mt-2">Loading profile data...</p>
  </div>
  
  <div v-else class="profile-overview">
    <!-- Summary Cards -->
    <div class="row g-4 mb-4">
      <div class="col-sm-6 col-xl-3">
        <div class="card">
          <div class="card-body p-0">
            <div class="d-flex align-items-center">
              <div class="flex-shrink-0 bg-primary bg-opacity-10 p-3 rounded">
                <i class="bi bi-clock fs-3 text-primary"></i>
              </div>
              <div class="ms-3">
                <h6 class="card-subtitle text-muted mb-1">Recording Duration</h6>
                <h5 class="card-title mb-0">{{ recordingStats.duration }}</h5>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <div class="col-sm-6 col-xl-3">
        <div class="card">
          <div class="card-body p-0">
            <div class="d-flex align-items-center">
              <div class="flex-shrink-0 bg-success bg-opacity-10 p-3 rounded">
                <i class="bi bi-cpu fs-3 text-success"></i>
              </div>
              <div class="ms-3">
                <h6 class="card-subtitle text-muted mb-1">CPU Utilization</h6>
                <h5 class="card-title mb-0">{{ recordingStats.cpuUtilization }}%</h5>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <div class="col-sm-6 col-xl-3">
        <div class="card">
          <div class="card-body p-0">
            <div class="d-flex align-items-center">
              <div class="flex-shrink-0 bg-info bg-opacity-10 p-3 rounded">
                <i class="bi bi-diagram-3 fs-3 text-info"></i>
              </div>
              <div class="ms-3">
                <h6 class="card-subtitle text-muted mb-1">Active Threads</h6>
                <h5 class="card-title mb-0">{{ recordingStats.threadCount }}</h5>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <div class="col-sm-6 col-xl-3">
        <div class="card">
          <div class="card-body p-0">
            <div class="d-flex align-items-center">
              <div class="flex-shrink-0 bg-warning bg-opacity-10 p-3 rounded">
                <i class="bi bi-list-check fs-3 text-warning"></i>
              </div>
              <div class="ms-3">
                <h6 class="card-subtitle text-muted mb-1">Total Events</h6>
                <h5 class="card-title mb-0">{{ recordingStats.eventCount.toLocaleString() }}</h5>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- Detail Sections -->
    <div class="row g-4">
      <!-- System Info -->
      <div class="col-md-6">
        <div class="card h-100">
          <div class="card-header">
            <h5 class="card-title mb-0">System Information</h5>
          </div>
          <div class="card-body">
            <table class="table">
              <tbody>
                <tr>
                  <th scope="row" style="width: 40%">JVM Version</th>
                  <td>{{ systemInfo.jvmVersion }}</td>
                </tr>
                <tr>
                  <th scope="row">Java Version</th>
                  <td>{{ systemInfo.javaVersion }}</td>
                </tr>
                <tr>
                  <th scope="row">Operating System</th>
                  <td>{{ systemInfo.os }}</td>
                </tr>
                <tr>
                  <th scope="row">CPU</th>
                  <td>{{ systemInfo.cpu }}</td>
                </tr>
                <tr>
                  <th scope="row">Memory</th>
                  <td>{{ systemInfo.memory }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
      
      <!-- Memory Usage -->
      <div class="col-md-6">
        <div class="card h-100">
          <div class="card-header d-flex justify-content-between align-items-center">
            <h5 class="card-title mb-0">Memory Usage</h5>
            <div class="btn-group btn-group-sm" role="group">
              <button type="button" class="btn btn-outline-primary active">Heap</button>
              <button type="button" class="btn btn-outline-primary">Non-Heap</button>
            </div>
          </div>
          <div class="card-body">
            <div class="chart-container" style="position: relative; height:200px;">
              <div id="memoryChart">
                <!-- Chart will be rendered here by ApexCharts -->
                <div class="text-center py-5 text-muted">
                  <i class="bi bi-graph-up fs-2"></i>
                  <p>Memory usage chart will be displayed here</p>
                </div>
              </div>
            </div>
            
            <div class="d-flex justify-content-between mt-3">
              <div class="text-center">
                <div class="fw-bold">{{ memoryStats.heapUsed }}</div>
                <div class="small text-muted">Used</div>
              </div>
              <div class="text-center">
                <div class="fw-bold">{{ memoryStats.heapCommitted }}</div>
                <div class="small text-muted">Committed</div>
              </div>
              <div class="text-center">
                <div class="fw-bold">{{ memoryStats.heapMax }}</div>
                <div class="small text-muted">Max</div>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <!-- Hot Methods -->
      <div class="col-md-6">
        <div class="card h-100">
          <div class="card-header">
            <h5 class="card-title mb-0">Hot Methods</h5>
          </div>
          <div class="card-body p-0">
            <div class="list-group list-group-flush">
              <div v-for="(method, index) in hotMethods" :key="index" class="list-group-item">
                <div class="d-flex justify-content-between align-items-center">
                  <div>
                    <div class="fw-bold text-truncate" style="max-width: 300px;">{{ method.name }}</div>
                    <div class="small text-muted">{{ method.className }}</div>
                  </div>
                  <div class="text-end">
                    <div class="fw-bold">{{ method.cpuTime }}ms</div>
                    <div class="progress mt-1" style="width: 100px; height: 6px;">
                      <div class="progress-bar" role="progressbar" :style="{ width: method.percentage + '%' }"></div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <!-- GC Activity -->
      <div class="col-md-6">
        <div class="card h-100">
          <div class="card-header">
            <h5 class="card-title mb-0">Garbage Collection</h5>
          </div>
          <div class="card-body p-0">
            <div class="table-responsive">
              <table class="table mb-0">
                <thead>
                  <tr>
                    <th>Collector</th>
                    <th>Count</th>
                    <th>Time</th>
                    <th>Avg</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(gc, index) in gcActivity" :key="index">
                    <td>{{ gc.name }}</td>
                    <td>{{ gc.count }}</td>
                    <td>{{ gc.totalTime }}ms</td>
                    <td>{{ gc.avgTime }}ms</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, defineProps, onMounted } from 'vue';
import { Profile } from '@/types';
import Utils from '@/services/Utils';

// Define props
const props = defineProps<{
  profile?: Profile | null;
}>();

// Mock data for demonstration
const recordingStats = ref({
  duration: 301.5, // seconds
  cpuUtilization: 65,
  threadCount: 24,
  eventCount: 425789
});

const systemInfo = ref({
  jvmVersion: 'OpenJDK 64-Bit Server VM (17.0.8+9)',
  javaVersion: 'Java(TM) SE Runtime Environment (17.0.8+9)',
  os: 'Linux 5.15.0-100-generic (x86_64)',
  cpu: 'Intel(R) Core(TM) i7-10700K @ 3.80GHz (8 cores, 16 threads)',
  memory: '32 GB'
});

const memoryStats = ref({
  heapUsed: '1.2 GB',
  heapCommitted: '2.0 GB',
  heapMax: '4.0 GB'
});

const hotMethods = ref([
  { name: 'processRequest', className: 'com.example.RequestHandler', cpuTime: 1250, percentage: 85 },
  { name: 'parseJSON', className: 'com.example.JSONParser', cpuTime: 980, percentage: 70 },
  { name: 'executeQuery', className: 'com.example.DatabaseService', cpuTime: 750, percentage: 55 },
  { name: 'calculateStatistics', className: 'com.example.StatisticsEngine', cpuTime: 650, percentage: 45 },
  { name: 'renderTemplate', className: 'com.example.TemplateEngine', cpuTime: 480, percentage: 30 }
]);

const gcActivity = ref([
  { name: 'G1 Young Generation', count: 42, totalTime: 850, avgTime: 20.2 },
  { name: 'G1 Old Generation', count: 3, totalTime: 320, avgTime: 106.7 }
]);

onMounted(() => {
  if (typeof window !== 'undefined' && window.ApexCharts) {
    // Initialize chart if ApexCharts is available
    // This is just a placeholder - in a real app, you would implement the chart
    console.log('ApexCharts is available, would initialize charts here');
  }
});
</script>

<style scoped>
.profile-overview .card {
  border: none;
  box-shadow: 0 0.125rem 0.25rem rgba(0, 0, 0, 0.075);
}

.chart-container {
  width: 100%;
}

.progress {
  background-color: #e9ecef;
}

.progress-bar {
  background-color: #3f51b5;
}

.list-group-item {
  border-left: none;
  border-right: none;
}

.list-group-item:first-child {
  border-top: none;
}
</style>
