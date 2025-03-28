<template>
  <div v-if="!profile" class="text-center py-5">
    <div class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>
    <p class="mt-2">Loading thread data...</p>
  </div>

  <div v-else class="threads-container">
    <!-- Summary Stats -->
    <div class="row g-4 mb-4">
      <div class="col-sm-6 col-xl-3">
        <div class="card h-100">
          <div class="card-body">
            <div class="d-flex align-items-center">
              <div class="flex-shrink-0 bg-primary bg-opacity-10 p-3 rounded">
                <i class="bi bi-diagram-3 fs-3 text-primary"></i>
              </div>
              <div class="ms-3">
                <h6 class="card-subtitle text-muted mb-1">Total Threads</h6>
                <h5 class="card-title mb-0">{{ threadStats.totalThreads }}</h5>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <div class="col-sm-6 col-xl-3">
        <div class="card h-100">
          <div class="card-body">
            <div class="d-flex align-items-center">
              <div class="flex-shrink-0 bg-success bg-opacity-10 p-3 rounded">
                <i class="bi bi-check-circle fs-3 text-success"></i>
              </div>
              <div class="ms-3">
                <h6 class="card-subtitle text-muted mb-1">Active Threads</h6>
                <h5 class="card-title mb-0">{{ threadStats.activeThreads }}</h5>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <div class="col-sm-6 col-xl-3">
        <div class="card h-100">
          <div class="card-body">
            <div class="d-flex align-items-center">
              <div class="flex-shrink-0 bg-danger bg-opacity-10 p-3 rounded">
                <i class="bi bi-hourglass-split fs-3 text-danger"></i>
              </div>
              <div class="ms-3">
                <h6 class="card-subtitle text-muted mb-1">Blocked Threads</h6>
                <h5 class="card-title mb-0">{{ threadStats.blockedThreads }}</h5>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <div class="col-sm-6 col-xl-3">
        <div class="card h-100">
          <div class="card-body">
            <div class="d-flex align-items-center">
              <div class="flex-shrink-0 bg-warning bg-opacity-10 p-3 rounded">
                <i class="bi bi-pause-circle fs-3 text-warning"></i>
              </div>
              <div class="ms-3">
                <h6 class="card-subtitle text-muted mb-1">Waiting Threads</h6>
                <h5 class="card-title mb-0">{{ threadStats.waitingThreads }}</h5>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- Thread Filter Controls -->
    <div class="card mb-4">
      <div class="card-body">
        <div class="row g-3">
          <div class="col-md-4">
            <div class="input-group">
              <span class="input-group-text">
                <i class="bi bi-search"></i>
              </span>
              <input 
                type="text" 
                class="form-control" 
                placeholder="Search threads..." 
                v-model="searchQuery"
                @input="filterThreads"
              >
            </div>
          </div>
          
          <div class="col-md-3">
            <select class="form-select" v-model="selectedState">
              <option value="">All States</option>
              <option value="RUNNABLE">Runnable</option>
              <option value="BLOCKED">Blocked</option>
              <option value="WAITING">Waiting</option>
              <option value="TIMED_WAITING">Timed Waiting</option>
              <option value="TERMINATED">Terminated</option>
            </select>
          </div>
          
          <div class="col-md-3">
            <select class="form-select" v-model="selectedGroupType">
              <option value="">All Thread Groups</option>
              <option value="system">System Threads</option>
              <option value="worker">Worker Threads</option>
              <option value="gc">GC Threads</option>
            </select>
          </div>
          
          <div class="col-md-2">
            <button class="btn btn-primary w-100" @click="applyFilters">
              Apply Filters
            </button>
          </div>
        </div>
      </div>
    </div>
    
    <!-- Thread Dump -->
    <div class="card mb-4">
      <div class="card-header d-flex justify-content-between align-items-center">
        <h5 class="card-title mb-0">Thread Dump</h5>
        <button class="btn btn-outline-primary btn-sm" @click="exportThreadDump">
          <i class="bi bi-download me-1"></i> Export
        </button>
      </div>
      <div class="card-body p-0">
        <div class="table-responsive">
          <table class="table table-hover mb-0">
            <thead>
              <tr>
                <th>Name</th>
                <th>State</th>
                <th>CPU Time</th>
                <th>Blocked Time</th>
                <th>Wait Time</th>
                <th>Details</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(thread, index) in paginatedThreads" :key="index" @click="selectThread(thread)" class="cursor-pointer">
                <td class="text-nowrap">
                  <span v-if="thread.daemon" class="badge bg-secondary me-1" title="Daemon Thread">D</span>
                  {{ thread.name }}
                </td>
                <td>
                  <span class="badge" :class="getThreadStateBadgeClass(thread.state)">
                    {{ thread.state }}
                  </span>
                </td>
                <td class="text-nowrap">{{ thread.cpuTime }}ms</td>
                <td class="text-nowrap">{{ thread.blockedTime }}ms</td>
                <td class="text-nowrap">{{ thread.waitTime }}ms</td>
                <td>
                  <button class="btn btn-outline-secondary btn-sm">
                    <i class="bi bi-eye"></i>
                  </button>
                </td>
              </tr>
              <tr v-if="filteredThreads.length === 0">
                <td colspan="6" class="text-center py-3">No threads found matching the current filters.</td>
              </tr>
            </tbody>
          </table>
        </div>
        
        <!-- Pagination -->
        <div class="d-flex justify-content-between align-items-center p-3 border-top">
          <div>
            Showing {{ paginationStart + 1 }} to {{ paginationEnd }} of {{ filteredThreads.length }} threads
          </div>
          <nav aria-label="Threads pagination">
            <ul class="pagination pagination-sm mb-0">
              <li class="page-item" :class="{ disabled: currentPage === 1 }">
                <a class="page-link" href="#" @click.prevent="goToPage(currentPage - 1)">Previous</a>
              </li>
              <li v-for="page in totalPages" :key="page" 
                  class="page-item" :class="{ active: page === currentPage }">
                <a class="page-link" href="#" @click.prevent="goToPage(page)">{{ page }}</a>
              </li>
              <li class="page-item" :class="{ disabled: currentPage === totalPages }">
                <a class="page-link" href="#" @click.prevent="goToPage(currentPage + 1)">Next</a>
              </li>
            </ul>
          </nav>
        </div>
      </div>
    </div>
    
    <!-- Thread Detail Modal -->
    <div class="modal fade" id="threadDetailModal" tabindex="-1" 
         :class="{ 'show': showThreadDetailModal }" 
         :style="{ display: showThreadDetailModal ? 'block' : 'none' }">
      <div class="modal-dialog modal-lg">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Thread Details</h5>
            <button type="button" class="btn-close" @click="closeThreadDetailModal"></button>
          </div>
          <div class="modal-body" v-if="selectedThread">
            <div class="row mb-3">
              <div class="col-md-6">
                <div class="mb-2">
                  <div class="small text-muted">Thread Name</div>
                  <div class="fw-bold">{{ selectedThread.name }}</div>
                </div>
              </div>
              <div class="col-md-6">
                <div class="mb-2">
                  <div class="small text-muted">Thread State</div>
                  <div>
                    <span class="badge" :class="getThreadStateBadgeClass(selectedThread.state)">
                      {{ selectedThread.state }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
            
            <div class="row mb-3">
              <div class="col-md-4">
                <div class="mb-2">
                  <div class="small text-muted">Thread ID</div>
                  <div>{{ selectedThread.id }}</div>
                </div>
              </div>
              <div class="col-md-4">
                <div class="mb-2">
                  <div class="small text-muted">Priority</div>
                  <div>{{ selectedThread.priority }}</div>
                </div>
              </div>
              <div class="col-md-4">
                <div class="mb-2">
                  <div class="small text-muted">Daemon</div>
                  <div>{{ selectedThread.daemon ? 'Yes' : 'No' }}</div>
                </div>
              </div>
            </div>
            
            <div class="row mb-3">
              <div class="col-md-4">
                <div class="mb-2">
                  <div class="small text-muted">CPU Time</div>
                  <div>{{ selectedThread.cpuTime }}ms</div>
                </div>
              </div>
              <div class="col-md-4">
                <div class="mb-2">
                  <div class="small text-muted">Blocked Time</div>
                  <div>{{ selectedThread.blockedTime }}ms</div>
                </div>
              </div>
              <div class="col-md-4">
                <div class="mb-2">
                  <div class="small text-muted">Wait Time</div>
                  <div>{{ selectedThread.waitTime }}ms</div>
                </div>
              </div>
            </div>
            
            <hr>
            
            <h6>Stack Trace</h6>
            <pre class="bg-light p-3 rounded small">{{ selectedThread.stackTrace }}</pre>
            
            <h6 class="mt-3">Locks</h6>
            <div v-if="selectedThread.locks && selectedThread.locks.length > 0">
              <div class="table-responsive">
                <table class="table table-sm">
                  <thead>
                    <tr>
                      <th>Lock Type</th>
                      <th>Object</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(lock, index) in selectedThread.locks" :key="index">
                      <td>{{ lock.type }}</td>
                      <td>{{ lock.object }}</td>
                      <td>{{ lock.status }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
            <div v-else class="text-muted">
              No locks held or waited on by this thread.
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="closeThreadDetailModal">Close</button>
          </div>
        </div>
      </div>
    </div>
    <div class="modal-backdrop fade show" v-if="showThreadDetailModal"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, defineProps, watch } from 'vue';
import { Profile } from '@/types';
import ToastService from '@/services/ToastService';

// Define props
const props = defineProps<{
  profile?: Profile | null;
}>();

// State
const searchQuery = ref('');
const selectedState = ref('');
const selectedGroupType = ref('');
const showThreadDetailModal = ref(false);
const selectedThread = ref<any>(null);

// Pagination
const currentPage = ref(1);
const itemsPerPage = 10;

// Thread statistics
const threadStats = ref({
  totalThreads: 24,
  activeThreads: 18,
  blockedThreads: 2,
  waitingThreads: 4
});

// Mock data for demonstration
const threads = ref([
  {
    id: '1',
    name: 'main',
    state: 'RUNNABLE',
    cpuTime: 3450,
    blockedTime: 0,
    waitTime: 250,
    priority: 5,
    daemon: false,
    groupType: 'system',
    stackTrace: 'java.lang.Thread.dumpThreads(Native Method)\njava.lang.Thread.getAllStackTraces(Thread.java:1653)\ncom.example.ThreadMonitor.dumpAllThreads(ThreadMonitor.java:58)\ncom.example.Main.main(Main.java:45)',
    locks: []
  },
  {
    id: '2',
    name: 'GC Thread',
    state: 'RUNNABLE',
    cpuTime: 1250,
    blockedTime: 0,
    waitTime: 0,
    priority: 10,
    daemon: true,
    groupType: 'gc',
    stackTrace: '[GC Thread stack trace not available]',
    locks: []
  },
  {
    id: '3',
    name: 'worker-1',
    state: 'RUNNABLE',
    cpuTime: 2780,
    blockedTime: 0,
    waitTime: 150,
    priority: 5,
    daemon: false,
    groupType: 'worker',
    stackTrace: 'com.example.Worker.processTask(Worker.java:87)\ncom.example.Worker.run(Worker.java:42)\njava.lang.Thread.run(Thread.java:833)',
    locks: [
      { type: 'Monitor', object: 'com.example.TaskQueue@3a4e8df2', status: 'Holding' }
    ]
  },
  {
    id: '4',
    name: 'worker-2',
    state: 'BLOCKED',
    cpuTime: 1850,
    blockedTime: 380,
    waitTime: 0,
    priority: 5,
    daemon: false,
    groupType: 'worker',
    stackTrace: 'com.example.Worker.processTask(Worker.java:87)\ncom.example.Worker.run(Worker.java:42)\njava.lang.Thread.run(Thread.java:833)',
    locks: [
      { type: 'Monitor', object: 'com.example.TaskQueue@3a4e8df2', status: 'Waiting to lock' }
    ]
  },
  {
    id: '5',
    name: 'worker-3',
    state: 'WAITING',
    cpuTime: 1620,
    blockedTime: 120,
    waitTime: 450,
    priority: 5,
    daemon: false,
    groupType: 'worker',
    stackTrace: 'java.lang.Object.wait(Native Method)\ncom.example.Queue.take(Queue.java:164)\ncom.example.Worker.run(Worker.java:39)\njava.lang.Thread.run(Thread.java:833)',
    locks: [
      { type: 'Monitor', object: 'com.example.Queue@7c8e3f1a', status: 'Waiting on condition' }
    ]
  },
  {
    id: '6',
    name: 'Signal Dispatcher',
    state: 'RUNNABLE',
    cpuTime: 120,
    blockedTime: 0,
    waitTime: 0,
    priority: 9,
    daemon: true,
    groupType: 'system',
    stackTrace: '[Signal Dispatcher stack trace not available]',
    locks: []
  },
  {
    id: '7',
    name: 'Finalizer',
    state: 'WAITING',
    cpuTime: 240,
    blockedTime: 0,
    waitTime: 1200,
    priority: 8,
    daemon: true,
    groupType: 'system',
    stackTrace: 'java.lang.Object.wait(Native Method)\njava.lang.ref.ReferenceQueue.remove(ReferenceQueue.java:155)\njava.lang.ref.ReferenceQueue.remove(ReferenceQueue.java:176)\njava.lang.ref.Finalizer$FinalizerThread.run(Finalizer.java:170)',
    locks: [
      { type: 'Monitor', object: 'java.lang.ref.ReferenceQueue$Lock@4e718207', status: 'Waiting on condition' }
    ]
  },
  {
    id: '8',
    name: 'G1 Young Collection',
    state: 'TIMED_WAITING',
    cpuTime: 780,
    blockedTime: 0,
    waitTime: 850,
    priority: 10,
    daemon: true,
    groupType: 'gc',
    stackTrace: '[G1 Young Collection stack trace not available]',
    locks: []
  },
  {
    id: '9',
    name: 'worker-4',
    state: 'RUNNABLE',
    cpuTime: 2100,
    blockedTime: 80,
    waitTime: 320,
    priority: 5,
    daemon: false,
    groupType: 'worker',
    stackTrace: 'java.net.SocketInputStream.socketRead0(Native Method)\njava.net.SocketInputStream.socketRead(SocketInputStream.java:115)\njava.net.SocketInputStream.read(SocketInputStream.java:168)\ncom.example.NetworkWorker.processConnection(NetworkWorker.java:56)\ncom.example.Worker.run(Worker.java:42)\njava.lang.Thread.run(Thread.java:833)',
    locks: []
  },
  {
    id: '10',
    name: 'worker-5',
    state: 'BLOCKED',
    cpuTime: 1850,
    blockedTime: 420,
    waitTime: 0,
    priority: 5,
    daemon: false,
    groupType: 'worker',
    stackTrace: 'com.example.DatabaseService.executeQuery(DatabaseService.java:112)\ncom.example.Worker.processTask(Worker.java:78)\ncom.example.Worker.run(Worker.java:42)\njava.lang.Thread.run(Thread.java:833)',
    locks: [
      { type: 'Monitor', object: 'com.example.DatabaseConnection@5a2c4df7', status: 'Waiting to lock' }
    ]
  }
]);

const filteredThreads = ref([...threads.value]);

// Computed properties
const totalPages = computed(() => {
  return Math.ceil(filteredThreads.value.length / itemsPerPage);
});

const paginationStart = computed(() => {
  return (currentPage.value - 1) * itemsPerPage;
});

const paginationEnd = computed(() => {
  const end = paginationStart.value + itemsPerPage;
  return Math.min(end, filteredThreads.value.length);
});

const paginatedThreads = computed(() => {
  return filteredThreads.value.slice(paginationStart.value, paginationEnd.value);
});

// Watch for changes to filters
watch([searchQuery, selectedState, selectedGroupType], () => {
  // Auto-apply filters if implementation prefers immediate filtering
  // applyFilters();
});

// Methods
const filterThreads = () => {
  // This is just for immediate search response
  // Full filtering is done in applyFilters
  if (!searchQuery.value.trim()) {
    filteredThreads.value = [...threads.value];
    return;
  }
  
  const query = searchQuery.value.toLowerCase();
  filteredThreads.value = threads.value.filter(thread => 
    thread.name.toLowerCase().includes(query) ||
    thread.stackTrace.toLowerCase().includes(query)
  );
  currentPage.value = 1;
};

const applyFilters = () => {
  let filtered = [...threads.value];
  
  // Apply search query
  if (searchQuery.value.trim()) {
    const query = searchQuery.value.toLowerCase();
    filtered = filtered.filter(thread => 
      thread.name.toLowerCase().includes(query) ||
      thread.stackTrace.toLowerCase().includes(query)
    );
  }
  
  // Apply thread state filter
  if (selectedState.value) {
    filtered = filtered.filter(thread => thread.state === selectedState.value);
  }
  
  // Apply thread group filter
  if (selectedGroupType.value) {
    filtered = filtered.filter(thread => thread.groupType === selectedGroupType.value);
  }
  
  filteredThreads.value = filtered;
  currentPage.value = 1;
};

const selectThread = (thread: any) => {
  selectedThread.value = thread;
  showThreadDetailModal.value = true;
};

const closeThreadDetailModal = () => {
  showThreadDetailModal.value = false;
};

const goToPage = (page: number) => {
  if (page >= 1 && page <= totalPages.value) {
    currentPage.value = page;
  }
};

const exportThreadDump = () => {
  // In a real app, this would trigger a download of a thread dump file
  ToastService.show('profileToast', 'Thread dump export started');
};

const getThreadStateBadgeClass = (state: string) => {
  switch (state) {
    case 'RUNNABLE':
      return 'bg-success';
    case 'BLOCKED':
      return 'bg-danger';
    case 'WAITING':
    case 'TIMED_WAITING':
      return 'bg-warning text-dark';
    case 'TERMINATED':
      return 'bg-secondary';
    default:
      return 'bg-secondary';
  }
};
</script>

<style scoped>
.threads-container .card {
  border: none;
  box-shadow: 0 0.125rem 0.25rem rgba(0, 0, 0, 0.075);
}

.cursor-pointer {
  cursor: pointer;
}

.modal-backdrop {
  background-color: rgba(0, 0, 0, 0.5);
}

/* Make the table rows have a hover effect for better interaction */
.table-hover tbody tr:hover {
  background-color: rgba(63, 81, 181, 0.05);
}

/* Style pagination to match the design */
.pagination .page-item.active .page-link {
  background-color: #3f51b5;
  border-color: #3f51b5;
}

.pagination .page-link {
  color: #3f51b5;
}

/* Style the pre tag for stack traces */
pre {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 0.8125rem;
  white-space: pre-wrap;
  word-break: break-all;
  margin-bottom: 0;
  max-height: 250px;
  overflow-y: auto;
}
</style>