<template>
  <div v-if="!profile" class="text-center py-5">
    <div class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>
    <p class="mt-2">Loading event data...</p>
  </div>

  <div v-else class="events-container">
    <!-- Filter Controls -->
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
                placeholder="Search events..." 
                v-model="searchQuery"
                @input="filterEvents"
              >
            </div>
          </div>
          
          <div class="col-md-3">
            <select class="form-select" v-model="selectedEventType">
              <option value="">All Event Types</option>
              <option v-for="type in eventTypes" :key="type">{{ type }}</option>
            </select>
          </div>
          
          <div class="col-md-3">
            <select class="form-select" v-model="selectedThread">
              <option value="">All Threads</option>
              <option v-for="thread in threads" :key="thread">{{ thread }}</option>
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
    
    <!-- Events Timeline Chart -->
    <div class="card mb-4">
      <div class="card-header d-flex justify-content-between align-items-center">
        <h5 class="card-title mb-0">Events Timeline</h5>
        <div class="btn-group btn-group-sm">
          <button type="button" class="btn btn-outline-secondary">
            <i class="bi bi-zoom-in"></i>
          </button>
          <button type="button" class="btn btn-outline-secondary">
            <i class="bi bi-zoom-out"></i>
          </button>
          <button type="button" class="btn btn-outline-secondary">
            <i class="bi bi-arrow-repeat"></i>
          </button>
        </div>
      </div>
      <div class="card-body p-0">
        <div class="events-timeline">
          <div class="text-center py-4">
            <i class="bi bi-bar-chart-steps fs-1 text-muted mb-3"></i>
            <p>Events timeline visualization would be displayed here</p>
            <!-- Mock timeline chart -->
            <div class="timeline-chart mx-auto"></div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- Events Table -->
    <div class="card">
      <div class="card-header d-flex justify-content-between align-items-center">
        <h5 class="card-title mb-0">Event List</h5>
        <span class="badge bg-primary">{{ filteredEvents.length }} events</span>
      </div>
      <div class="card-body p-0">
        <div class="table-responsive">
          <table class="table table-hover mb-0">
            <thead>
              <tr>
                <th>Time</th>
                <th>Type</th>
                <th>Thread</th>
                <th>Duration</th>
                <th>Details</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(event, index) in paginatedEvents" :key="index" @click="selectEvent(event)" class="cursor-pointer">
                <td class="text-nowrap">{{ formatTimestamp(event.timestamp) }}</td>
                <td>
                  <span class="badge" :class="getEventTypeBadgeClass(event.type)">
                    {{ event.type }}
                  </span>
                </td>
                <td class="text-nowrap">{{ event.thread }}</td>
                <td class="text-nowrap">{{ event.duration }}ms</td>
                <td class="text-truncate" style="max-width: 300px;">{{ event.details }}</td>
              </tr>
              <tr v-if="filteredEvents.length === 0">
                <td colspan="5" class="text-center py-3">No events found matching the current filters.</td>
              </tr>
            </tbody>
          </table>
        </div>
        
        <!-- Pagination -->
        <div class="d-flex justify-content-between align-items-center p-3 border-top">
          <div>
            Showing {{ paginationStart + 1 }} to {{ paginationEnd }} of {{ filteredEvents.length }} events
          </div>
          <nav aria-label="Events pagination">
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
    
    <!-- Event Detail Modal -->
    <div class="modal fade" id="eventDetailModal" tabindex="-1" 
         :class="{ 'show': showEventDetailModal }" 
         :style="{ display: showEventDetailModal ? 'block' : 'none' }">
      <div class="modal-dialog modal-lg">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Event Details</h5>
            <button type="button" class="btn-close" @click="closeEventDetailModal"></button>
          </div>
          <div class="modal-body" v-if="selectedEvent">
            <div class="row mb-3">
              <div class="col-md-6">
                <div class="mb-2">
                  <div class="small text-muted">Event Type</div>
                  <div>
                    <span class="badge" :class="getEventTypeBadgeClass(selectedEvent.type)">
                      {{ selectedEvent.type }}
                    </span>
                  </div>
                </div>
              </div>
              <div class="col-md-6">
                <div class="mb-2">
                  <div class="small text-muted">Timestamp</div>
                  <div>{{ formatTimestamp(selectedEvent.timestamp, true) }}</div>
                </div>
              </div>
            </div>
            
            <div class="row mb-3">
              <div class="col-md-6">
                <div class="mb-2">
                  <div class="small text-muted">Thread</div>
                  <div>{{ selectedEvent.thread }}</div>
                </div>
              </div>
              <div class="col-md-6">
                <div class="mb-2">
                  <div class="small text-muted">Duration</div>
                  <div>{{ selectedEvent.duration }}ms</div>
                </div>
              </div>
            </div>
            
            <hr>
            
            <h6>Event Properties</h6>
            <div class="table-responsive">
              <table class="table table-sm">
                <thead>
                  <tr>
                    <th>Property</th>
                    <th>Value</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(value, key) in selectedEvent.properties" :key="key">
                    <td>{{ key }}</td>
                    <td>{{ value }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            
            <div v-if="selectedEvent.stackTrace">
              <h6>Stack Trace</h6>
              <pre class="bg-light p-3 rounded small">{{ selectedEvent.stackTrace }}</pre>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="closeEventDetailModal">Close</button>
          </div>
        </div>
      </div>
    </div>
    <div class="modal-backdrop fade show" v-if="showEventDetailModal"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, defineProps, watch } from 'vue';
import { Profile } from '@/types';

// Define props
const props = defineProps<{
  profile?: Profile | null;
}>();

// State
const searchQuery = ref('');
const selectedEventType = ref('');
const selectedThread = ref('');
const showEventDetailModal = ref(false);
const selectedEvent = ref<any>(null);

// Pagination
const currentPage = ref(1);
const itemsPerPage = 10;

// Mock data for demonstration
const events = ref([
  {
    timestamp: 1650000000000,
    type: 'JVM Initialization',
    thread: 'main',
    duration: 240,
    details: 'JVM initialized with default parameters',
    properties: {
      'java.vm.name': 'OpenJDK 64-Bit Server VM',
      'java.vm.version': '17.0.8+9',
      'java.home': '/usr/lib/jvm/java-17-openjdk-amd64'
    }
  },
  {
    timestamp: 1650000001500,
    type: 'Thread Start',
    thread: 'worker-1',
    duration: 15,
    details: 'Worker thread started',
    properties: {
      'thread.id': '12',
      'thread.priority': '5'
    }
  },
  {
    timestamp: 1650000002000,
    type: 'Method Sample',
    thread: 'worker-1',
    duration: 150,
    details: 'com.example.Service.processRequest()',
    properties: {
      'method.name': 'processRequest',
      'class.name': 'com.example.Service'
    },
    stackTrace: 'com.example.Service.processRequest(Service.java:120)\ncom.example.RequestHandler.handle(RequestHandler.java:45)\ncom.example.Main.main(Main.java:30)'
  },
  {
    timestamp: 1650000004500,
    type: 'GC Begin',
    thread: 'GC Thread',
    duration: 50,
    details: 'G1 Young Generation garbage collection started',
    properties: {
      'gc.name': 'G1 Young Generation',
      'gc.cause': 'Allocation Failure'
    }
  },
  {
    timestamp: 1650000004550,
    type: 'GC End',
    thread: 'GC Thread',
    duration: 0,
    details: 'G1 Young Generation garbage collection completed',
    properties: {
      'gc.name': 'G1 Young Generation',
      'gc.cause': 'Allocation Failure',
      'gc.heap.before': '1.2 GB',
      'gc.heap.after': '0.8 GB'
    }
  },
  {
    timestamp: 1650000006000,
    type: 'Method Sample',
    thread: 'main',
    duration: 80,
    details: 'com.example.DatabaseService.executeQuery()',
    properties: {
      'method.name': 'executeQuery',
      'class.name': 'com.example.DatabaseService'
    },
    stackTrace: 'com.example.DatabaseService.executeQuery(DatabaseService.java:87)\ncom.example.Service.getData(Service.java:65)\ncom.example.Main.main(Main.java:35)'
  },
  {
    timestamp: 1650000007500,
    type: 'Exception',
    thread: 'worker-2',
    duration: 20,
    details: 'java.lang.NullPointerException at com.example.Processor.process()',
    properties: {
      'exception.type': 'java.lang.NullPointerException',
      'exception.message': 'Cannot invoke method on null object'
    },
    stackTrace: 'java.lang.NullPointerException: Cannot invoke method on null object\n\tat com.example.Processor.process(Processor.java:42)\n\tat com.example.Worker.run(Worker.java:28)\n\tat java.base/java.lang.Thread.run(Thread.java:833)'
  },
  {
    timestamp: 1650000009000,
    type: 'Socket Read',
    thread: 'worker-3',
    duration: 120,
    details: 'Read 2048 bytes from socket connection',
    properties: {
      'socket.host': '192.168.1.10',
      'socket.port': '8080',
      'bytes.read': '2048'
    }
  },
  {
    timestamp: 1650000010500,
    type: 'File Write',
    thread: 'worker-1',
    duration: 35,
    details: 'Write 4096 bytes to file log.txt',
    properties: {
      'file.path': '/var/log/app/log.txt',
      'bytes.written': '4096'
    }
  },
  {
    timestamp: 1650000012000,
    type: 'Method Sample',
    thread: 'main',
    duration: 200,
    details: 'com.example.ReportGenerator.generateReport()',
    properties: {
      'method.name': 'generateReport',
      'class.name': 'com.example.ReportGenerator'
    },
    stackTrace: 'com.example.ReportGenerator.generateReport(ReportGenerator.java:55)\ncom.example.Service.createReport(Service.java:92)\ncom.example.Main.main(Main.java:40)'
  },
  {
    timestamp: 1650000015000,
    type: 'Thread End',
    thread: 'worker-1',
    duration: 10,
    details: 'Worker thread terminated',
    properties: {
      'thread.id': '12',
      'thread.state': 'TERMINATED'
    }
  },
  {
    timestamp: 1650000020000,
    type: 'JVM Shutdown',
    thread: 'main',
    duration: 180,
    details: 'JVM shutdown initiated',
    properties: {
      'exit.code': '0',
      'uptime.ms': '20000'
    }
  }
]);

const filteredEvents = ref([...events.value]);

// Computed properties
const eventTypes = computed(() => {
  const types = new Set<string>();
  events.value.forEach(event => types.add(event.type));
  return Array.from(types).sort();
});

const threads = computed(() => {
  const threadsSet = new Set<string>();
  events.value.forEach(event => threadsSet.add(event.thread));
  return Array.from(threadsSet).sort();
});

const totalPages = computed(() => {
  return Math.ceil(filteredEvents.value.length / itemsPerPage);
});

const paginationStart = computed(() => {
  return (currentPage.value - 1) * itemsPerPage;
});

const paginationEnd = computed(() => {
  const end = paginationStart.value + itemsPerPage;
  return Math.min(end, filteredEvents.value.length);
});

const paginatedEvents = computed(() => {
  return filteredEvents.value.slice(paginationStart.value, paginationEnd.value);
});

// Watch for changes to filters
watch([searchQuery, selectedEventType, selectedThread], () => {
  // Auto-apply filters if implementation prefers immediate filtering
  // applyFilters();
});

// Methods
const filterEvents = () => {
  // This is just for immediate search response
  // Full filtering is done in applyFilters
  if (!searchQuery.value.trim()) {
    filteredEvents.value = [...events.value];
    return;
  }
  
  const query = searchQuery.value.toLowerCase();
  filteredEvents.value = events.value.filter(event => 
    event.details.toLowerCase().includes(query) ||
    event.type.toLowerCase().includes(query) ||
    event.thread.toLowerCase().includes(query)
  );
  currentPage.value = 1;
};

const applyFilters = () => {
  let filtered = [...events.value];
  
  // Apply search query
  if (searchQuery.value.trim()) {
    const query = searchQuery.value.toLowerCase();
    filtered = filtered.filter(event => 
      event.details.toLowerCase().includes(query) ||
      event.type.toLowerCase().includes(query) ||
      event.thread.toLowerCase().includes(query)
    );
  }
  
  // Apply event type filter
  if (selectedEventType.value) {
    filtered = filtered.filter(event => event.type === selectedEventType.value);
  }
  
  // Apply thread filter
  if (selectedThread.value) {
    filtered = filtered.filter(event => event.thread === selectedThread.value);
  }
  
  filteredEvents.value = filtered;
  currentPage.value = 1;
};

const selectEvent = (event: any) => {
  selectedEvent.value = event;
  showEventDetailModal.value = true;
};

const closeEventDetailModal = () => {
  showEventDetailModal.value = false;
};

const goToPage = (page: number) => {
  if (page >= 1 && page <= totalPages.value) {
    currentPage.value = page;
  }
};

const formatTimestamp = (timestamp: number, detailed = false) => {
  const date = new Date(timestamp);
  if (detailed) {
    return date.toLocaleString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      millisecond: 'numeric'
    });
  } else {
    return date.toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      millisecond: 'numeric'
    });
  }
};

const getEventTypeBadgeClass = (type: string) => {
  switch (type) {
    case 'JVM Initialization':
    case 'JVM Shutdown':
      return 'bg-dark';
    case 'Thread Start':
    case 'Thread End':
      return 'bg-secondary';
    case 'Method Sample':
      return 'bg-primary';
    case 'GC Begin':
    case 'GC End':
      return 'bg-success';
    case 'Exception':
      return 'bg-danger';
    case 'Socket Read':
    case 'File Write':
      return 'bg-info';
    default:
      return 'bg-secondary';
  }
};
</script>

<style scoped>
.events-container .card {
  border: none;
  box-shadow: 0 0.125rem 0.25rem rgba(0, 0, 0, 0.075);
}

.cursor-pointer {
  cursor: pointer;
}

.timeline-chart {
  width: 90%;
  height: 100px;
  background-color: #f8f9fa;
  border-radius: 4px;
  border: 1px solid #dee2e6;
  position: relative;
  overflow: hidden;
}

.timeline-chart::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  height: 100%;
  width: 100%;
  background-image:
    linear-gradient(to right, transparent 0%, transparent 95%, rgba(63, 81, 181, 0.5) 95%, rgba(63, 81, 181, 0.5) 100%),
    linear-gradient(to bottom, transparent 0%, transparent 95%, rgba(0, 0, 0, 0.1) 95%, rgba(0, 0, 0, 0.1) 100%);
  background-size: 100px 100%, 10% 20px;
}

.timeline-chart::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
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