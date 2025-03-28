<template>
  <div class="guardian-analysis">
    <h4 class="mb-4">Guardian Analysis Results</h4>
    
    <!-- Filter Controls -->
    <div class="card mb-4 filter-card">
      <div class="card-body pb-0">
        <div class="row g-3 align-items-center">
          <div class="col-12 col-md-4">
            <div class="phoenix-search">
              <span class="input-group-text border-0 ps-3 pe-0 search-icon-container">
                <i class="bi bi-search text-primary"></i>
              </span>
              <input 
                type="text" 
                class="form-control border-0 py-2" 
                placeholder="Search checks..."
                v-model="filters.search"
                @input="onSearchInput"
              >
              <span v-if="filters.search" class="clear-icon-container" @click="clearSearch">
                <i class="bi bi-x-circle text-muted"></i>
              </span>
            </div>
          </div>
          
          <div class="col-12 col-md-4">
            <select class="form-select custom-select" v-model="filters.severity">
              <option value="all">All Severity Levels</option>
              <option value="1">Critical Issues</option>
              <option value="2">Warning Issues</option>
              <option value="3">Good Performance</option>
              <option value="4">Disabled Checks</option>
            </select>
          </div>
          
          <div class="col-12 col-md-4">
            <select class="form-select custom-select" v-model="filters.status">
              <option value="all">All Statuses</option>
              <option value="success">Success</option>
              <option value="warning">Warning</option>
              <option value="error">Error</option>
              <option value="info">Info</option>
              <option value="disabled">Disabled</option>
            </select>
          </div>
        </div>
        
        <div class="mt-3 mb-3 d-flex justify-content-between align-items-center">
          <div class="text-muted small">
            Showing {{ filteredAndSortedChecks.length }} of {{ guardianChecks.length }} checks
          </div>
          <button 
            class="btn btn-sm btn-outline-secondary" 
            @click="resetFilters"
            :disabled="!isFiltered"
          >
            <i class="bi bi-arrow-counterclockwise me-1"></i>
            Reset Filters
          </button>
        </div>
      </div>
    </div>
    
    <div class="row g-3">
      <template v-for="(check, index) in filteredAndSortedChecks" :key="check.id">
        <!-- Category headers -->
        <div v-if="index === 0 || getSeverityCategory(filteredAndSortedChecks[index]) !== getSeverityCategory(filteredAndSortedChecks[index-1])" 
             class="col-12 mb-1 mt-3">
          <h5 class="category-header" 
              :class="getSeverityCategoryClass(getSeverityCategory(check))">
            {{ getSeverityCategoryName(getSeverityCategory(check)) }}
          </h5>
        </div>
        <!-- Check cards -->
        <div class="col-12 col-md-6 col-lg-4">
          <div class="card h-100 guardian-card" 
               :class="`border-${getScoreColorClass(check.score)}`">
            <div class="card-body pb-0">
              <div class="d-flex justify-content-between align-items-start mb-3">
                <h5 class="card-title">{{ check.name }}</h5>
                <div :class="`status-icon bg-${getScoreColorClass(check.score)}`">
                  <i class="bi" :class="getIconForScore(check.score)"></i>
                </div>
              </div>
              
              <div class="score-container mb-3">
                <div class="d-flex justify-content-between mb-1">
                  <span>Issue Severity</span>
                  <span :class="`text-${getScoreColorClass(check.score)}`">{{ check.score }}/100</span>
                </div>
                <div class="progress" style="height: 6px;">
                  <div class="progress-bar" 
                       :class="`bg-${getScoreColorClass(check.score)}`"
                       role="progressbar" 
                       :style="{ width: `${check.score}%` }" 
                       :aria-valuenow="check.score" 
                       aria-valuemin="0" 
                       aria-valuemax="100">
                  </div>
                </div>
              </div>
              
              <p class="card-text small">{{ check.brief }}</p>
              
            </div>
            <div class="card-footer bg-transparent">
              <div class="d-flex">
                <button 
                  type="button" 
                  class="btn btn-sm btn-outline-primary me-2"
                  @click="showDetailsModal(check)">
                  <i class="bi bi-info-circle me-1"></i>Description
                </button>
                
                <button 
                  type="button" 
                  class="btn btn-sm btn-outline-info"
                  :disabled="!check.flamegraphData" 
                  @click="navigateToFlamegraph(check)">
                  <i class="bi bi-fire me-1"></i>Flamegraph
                </button>
              </div>
            </div>
          </div>
        </div>
      </template>
      
      <!-- No Results Message -->
      <div v-if="filteredAndSortedChecks.length === 0" class="col-12 text-center py-5">
        <div class="no-results">
          <i class="bi bi-search text-muted mb-3" style="font-size: 2rem;"></i>
          <h5>No matching guardian checks found</h5>
          <p class="text-muted">Try adjusting your search or filter criteria</p>
          <button class="btn btn-primary" @click="resetFilters">Reset All Filters</button>
        </div>
      </div>
    </div>

    <!-- Check Details Modal -->
    <div class="modal fade" id="checkDetailsModal" tabindex="-1" aria-labelledby="checkDetailsModalLabel" aria-hidden="true">
      <div class="modal-dialog">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title" id="checkDetailsModalLabel">
              <span v-if="selectedCheck">{{ selectedCheck.name }}</span>
            </h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
          </div>
          <div class="modal-body" v-if="selectedCheck">
            <div class="d-flex align-items-center mb-3">
              <div :class="`status-icon bg-${getScoreColorClass(selectedCheck.score)} me-3`">
                <i class="bi" :class="getIconForScore(selectedCheck.score)"></i>
              </div>
              <div class="score-display">
                <span class="text-muted">Issue Severity:</span>
                <span :class="`text-${getScoreColorClass(selectedCheck.score)} fw-bold ms-1`">
                  {{ selectedCheck.score }}/100
                </span>
                <small class="d-block text-muted mt-1">(Lower score is better)</small>
              </div>
            </div>
            
            <div class="mb-3">
              <h6>Summary</h6>
              <p>{{ selectedCheck.summary }}</p>
            </div>
            
            <div class="mb-3">
              <h6>Explanation</h6>
              <p>{{ selectedCheck.explanation }}</p>
            </div>
            
            <div class="mb-3">
              <h6>Solution</h6>
              <p>{{ selectedCheck.solution }}</p>
            </div>
            
            <div v-if="selectedCheck.details" class="mb-3">
              <h6>Technical Details</h6>
              <pre class="bg-light p-2 rounded small"><code>{{ selectedCheck.details }}</code></pre>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
            <button 
              v-if="selectedCheck && selectedCheck.flamegraphData" 
              type="button" 
              class="btn btn-primary"
              @click="navigateToFlamegraphFromModal">
              <i class="bi bi-fire me-1"></i>Show Flamegraph
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Modal } from 'bootstrap';

const route = useRoute();
const router = useRouter();

// Modal instance
let detailsModal = null;

// Selected check for the modal
const selectedCheck = ref(null);

// Filter state
const filters = ref({
  search: '',
  severity: 'all',
  status: 'all'
});

// Debounce timer for search
let searchDebounceTimer = null;

// Initialize modal when component is mounted
onMounted(() => {
  detailsModal = new Modal(document.getElementById('checkDetailsModal'));
  
  // Check if there are URL params to set initial filters
  const queryParams = route.query;
  if (queryParams.severity) {
    filters.value.severity = queryParams.severity;
  }
  if (queryParams.status) {
    filters.value.status = queryParams.status;
  }
  if (queryParams.search) {
    filters.value.search = queryParams.search;
  }
});

// Watch for filter changes to update URL
watch(filters, (newFilters) => {
  // Update the URL with the current filter settings
  const query = {...route.query};
  
  if (newFilters.search) {
    query.search = newFilters.search;
  } else {
    delete query.search;
  }
  
  if (newFilters.severity !== 'all') {
    query.severity = newFilters.severity;
  } else {
    delete query.severity;
  }
  
  if (newFilters.status !== 'all') {
    query.status = newFilters.status;
  } else {
    delete query.status;
  }
  
  // Only update if the query has changed to avoid unnecessary navigation
  if (JSON.stringify(query) !== JSON.stringify(route.query)) {
    router.replace({ query });
  }
}, { deep: true });

// Helper function to get severity category for sorting and filtering
const getSeverityCategory = (check) => {
  if (check.status === 'disabled') return 4; // Disabled checks at the end
  if (check.score <= 30) return 3; // Good (low severity)
  if (check.score <= 60) return 2; // Warning (medium severity)
  return 1; // Critical (high severity)
};

// Helper function to get category name
const getSeverityCategoryName = (category) => {
  switch (category) {
    case 1: return 'Critical Issues';
    case 2: return 'Warning Issues';
    case 3: return 'Good Performance';
    case 4: return 'Disabled Checks';
    default: return '';
  }
};

// Helper function to get category class
const getSeverityCategoryClass = (category) => {
  switch (category) {
    case 1: return 'text-danger';
    case 2: return 'text-warning';
    case 3: return 'text-success';
    case 4: return 'text-secondary';
    default: return '';
  }
};

// Filter functions
const onSearchInput = () => {
  // Debounce the search to avoid too many re-renders
  if (searchDebounceTimer) {
    clearTimeout(searchDebounceTimer);
  }
  
  searchDebounceTimer = setTimeout(() => {
    // The actual filtering is done by the computed property
  }, 300);
};

const clearSearch = () => {
  filters.value.search = '';
};

const resetFilters = () => {
  filters.value = {
    search: '',
    severity: 'all',
    status: 'all'
  };
};

// Computed property to check if any filters are active
const isFiltered = computed(() => {
  return filters.value.search !== '' || 
         filters.value.severity !== 'all' ||
         filters.value.status !== 'all';
});

// First filter the checks based on the filter criteria
const filteredGuardianChecks = computed(() => {
  return guardianChecks.value.filter(check => {
    // Search filter - check name, brief, summary, explanation
    const searchLower = filters.value.search.toLowerCase();
    const matchesSearch = !filters.value.search || 
      check.name.toLowerCase().includes(searchLower) ||
      check.brief.toLowerCase().includes(searchLower) ||
      (check.summary && check.summary.toLowerCase().includes(searchLower)) ||
      (check.explanation && check.explanation.toLowerCase().includes(searchLower));
      
    // Severity filter
    const severityCategory = getSeverityCategory(check);
    const matchesSeverity = filters.value.severity === 'all' || 
      severityCategory === parseInt(filters.value.severity);
      
    // Status filter
    const matchesStatus = filters.value.status === 'all' || 
      check.status === filters.value.status;
      
    return matchesSearch && matchesSeverity && matchesStatus;
  });
});

// Then sort the filtered checks
const filteredAndSortedChecks = computed(() => {
  return [...filteredGuardianChecks.value].sort((a, b) => {
    const categoryA = getSeverityCategory(a);
    const categoryB = getSeverityCategory(b);
    
    // First, sort by category (critical first, then warning, then good, then disabled)
    if (categoryA !== categoryB) {
      return categoryA - categoryB;
    }
    
    // Within the same category, sort by score (higher score first)
    if (a.score !== b.score) {
      return b.score - a.score;
    }
    
    // If both category and score are equal, sort by name
    return a.name.localeCompare(b.name);
  });
});

// Mock guardian checks data
const guardianChecks = ref([
  {
    id: 1,
    name: "Memory Leak Detection",
    status: "warning",
    score: 65,
    brief: "Potential memory leaks detected in application code.",
    summary: "Guardian has identified potential memory leaks in your application.",
    explanation: "Analysis found objects being created but not properly garbage collected. This can lead to increased memory usage over time and eventually out of memory errors. The most common culprits are event listeners not being removed and static collections growing unbounded.",
    solution: "Review code handling event listeners to ensure they're being detached when components are destroyed. Examine static collections and caches to ensure they have a size limit or expiration policy.",
    details: "Class com.example.CustomCache is accumulating objects without bounds.\nLines 247-258 in CustomCache.java create new entries without checking capacity.",
    flamegraphData: {
      id: "fg-1",
      checkId: 1,
      profileId: route.params.profileId,
      methods: [
        {
          id: "m-1",
          name: "addToCache",
          className: "com.example.CustomCache",
          cpuTime: 350,
          wallTime: 400,
          selfTime: 120,
          samples: 145,
          percentage: 42
        },
        {
          id: "m-2",
          name: "createCacheEntry",
          className: "com.example.CustomCache",
          cpuTime: 280,
          wallTime: 310,
          selfTime: 150,
          samples: 118,
          percentage: 35
        },
        {
          id: "m-3",
          name: "storeValue",
          className: "com.example.CustomCache",
          cpuTime: 230,
          wallTime: 250,
          selfTime: 100,
          samples: 95,
          percentage: 28
        }
      ]
    }
  },
  {
    id: 2,
    name: "Thread Pool Configuration",
    status: "success",
    score: 12,
    brief: "Thread pools are properly configured.",
    summary: "Thread pools are properly sized for your application workload.",
    explanation: "Analysis found that thread pool configurations match the recommended settings for your CPU count and workload patterns. Thread pool utilization is balanced, and no thread starvation issues were detected.",
    solution: "Continue monitoring thread pool performance as your application scales. Consider implementing adaptive sizing if workload patterns change significantly.",
    details: null,
    flamegraphData: null
  },
  {
    id: 3,
    name: "Database Connection Usage",
    status: "error",
    score: 35,
    brief: "Database connections are not being properly closed or reused.",
    summary: "Guardian detected database connections not being properly closed or returned to the connection pool.",
    explanation: "Your application is creating new database connections for each request without properly closing them or returning them to the connection pool. This will eventually lead to connection pool exhaustion, database server overload, and application failures.",
    solution: "Ensure all database connections are closed in finally blocks or use try-with-resources. Verify connection pool settings match your workload requirements. Consider implementing connection timeout monitoring.",
    details: "Found 17 instances where Connection.close() is not called in finally block.\nConnection pool max size (20) is frequently reached during normal operation.",
    flamegraphData: {
      id: "fg-3",
      checkId: 3,
      profileId: route.params.profileId,
      methods: [
        {
          id: "m-4",
          name: "executeQuery",
          className: "com.example.DatabaseService",
          cpuTime: 750,
          wallTime: 1200,
          selfTime: 380,
          samples: 312,
          percentage: 55
        },
        {
          id: "m-5",
          name: "getConnection",
          className: "com.example.DatabaseService",
          cpuTime: 520,
          wallTime: 580,
          selfTime: 290,
          samples: 210,
          percentage: 38
        },
        {
          id: "m-6",
          name: "handleRequest",
          className: "com.example.ApiController",
          cpuTime: 1250,
          wallTime: 1800,
          selfTime: 450,
          samples: 520,
          percentage: 72
        }
      ]
    }
  },
  {
    id: 4,
    name: "CPU Hotspots",
    status: "info",
    score: 25,
    brief: "Several CPU hotspots identified that could be optimized.",
    summary: "Guardian identified several methods consuming significant CPU time.",
    explanation: "Analysis found that 60% of CPU time is spent in just 3 methods. While this is not necessarily a problem, it indicates areas where optimization efforts could have the most impact. The hotspots are primarily in data processing and JSON serialization routines.",
    solution: "Review the identified methods for algorithmic improvements. Consider caching results where appropriate, using more efficient data structures, or adopting specialized libraries for performance-critical operations.",
    details: "com.example.DataProcessor.transform() - 28% CPU time\ncom.example.JsonSerializer.toJson() - 22% CPU time\ncom.example.QueryBuilder.buildQuery() - 10% CPU time",
    flamegraphData: {
      id: "fg-4",
      checkId: 4,
      profileId: route.params.profileId,
      methods: [
        {
          id: "m-7",
          name: "transform",
          className: "com.example.DataProcessor",
          cpuTime: 980,
          wallTime: 1050,
          selfTime: 420,
          samples: 418,
          percentage: 70
        },
        {
          id: "m-8",
          name: "toJson",
          className: "com.example.JsonSerializer",
          cpuTime: 780,
          wallTime: 820,
          selfTime: 380,
          samples: 332,
          percentage: 58
        },
        {
          id: "m-9",
          name: "buildQuery",
          className: "com.example.QueryBuilder",
          cpuTime: 350,
          wallTime: 380,
          selfTime: 150,
          samples: 142,
          percentage: 25
        }
      ]
    }
  },
  {
    id: 5,
    name: "Garbage Collection Pressure",
    status: "warning",
    score: 72,
    brief: "Higher than expected garbage collection activity.",
    summary: "Your application is generating excessive garbage collection pressure.",
    explanation: "Analysis detected high allocation rates that are triggering frequent garbage collection cycles. This can lead to application pauses, reduced throughput, and inconsistent response times. The primary source appears to be short-lived objects in request processing paths.",
    solution: "Optimize high-allocation code paths to reduce object creation. Consider object pooling for frequently used objects. Review collection usage patterns to minimize redundant allocations and conversions.",
    details: "Average GC pause: 127ms\nGC frequency: Every 3.5 seconds\nPrimary allocation sources: com.example.RequestHandler lines 125-187",
    flamegraphData: {
      id: "fg-5",
      checkId: 5,
      profileId: route.params.profileId,
      methods: [
        {
          id: "m-10",
          name: "processRequest",
          className: "com.example.RequestHandler",
          cpuTime: 1250,
          wallTime: 1300,
          selfTime: 350,
          samples: 526,
          percentage: 85
        },
        {
          id: "m-11",
          name: "createDTO",
          className: "com.example.RequestHandler",
          cpuTime: 520,
          wallTime: 550,
          selfTime: 220,
          samples: 215,
          percentage: 32
        },
        {
          id: "m-12",
          name: "parseParameters",
          className: "com.example.RequestHandler",
          cpuTime: 420,
          wallTime: 450,
          selfTime: 180,
          samples: 178,
          percentage: 28
        }
      ]
    }
  },
  {
    id: 6,
    name: "Lock Contention Analysis",
    status: "disabled",
    score: 0,
    brief: "Lock contention analysis is disabled for this profile.",
    summary: "Lock contention analysis requires additional configuration to be enabled.",
    explanation: "This check examines synchronized blocks and locks to identify potential contention points that could be limiting scalability. However, the necessary JFR events were not enabled when creating this profile.",
    solution: "Enable the 'Java Locks' event category when creating the profile. For maximum insights, set the threshold to track all locks, not just contended ones.",
    details: null,
    flamegraphData: null
  },
  {
    id: 7,
    name: "Network I/O Efficiency",
    status: "success",
    score: 8,
    brief: "Network I/O patterns are efficient and well-optimized.",
    summary: "Network connections are being properly managed and used efficiently.",
    explanation: "Analysis found that your application makes good use of connection pooling, properly batches network operations, and handles I/O in non-blocking fashion where appropriate. No significant issues were detected with network resource usage.",
    solution: "Continue monitoring network performance metrics as your application scales. Consider implementing circuit breakers for external service calls if not already present.",
    details: null,
    flamegraphData: null
  },
  {
    id: 8,
    name: "Exception Rate",
    status: "error",
    score: 45,
    brief: "High exception rate detected in application code.",
    summary: "Your application is generating an unusually high number of exceptions.",
    explanation: "Analysis found that your application throws and catches a large number of exceptions during normal operation. Using exceptions for control flow is inefficient and can lead to performance problems, as the JVM must capture stack traces and handle exception propagation.",
    solution: "Refactor code to use return values or Optional instead of exceptions for expected conditions. Reserve exceptions for truly exceptional circumstances. Add appropriate validation to prevent exceptions from being thrown.",
    details: "12,450 exceptions/minute\nTop exception types:\njava.lang.IllegalArgumentException: 45%\njava.lang.NullPointerException: 30%\ncom.example.ValidationException: 25%",
    flamegraphData: {
      id: "fg-8",
      checkId: 8,
      profileId: route.params.profileId,
      methods: [
        {
          id: "m-13",
          name: "validateInput",
          className: "com.example.ValidationService",
          cpuTime: 850,
          wallTime: 900,
          selfTime: 380,
          samples: 360,
          percentage: 62
        },
        {
          id: "m-14",
          name: "processException",
          className: "com.example.ExceptionHandler",
          cpuTime: 720,
          wallTime: 780,
          selfTime: 320,
          samples: 298,
          percentage: 52
        },
        {
          id: "m-15",
          name: "logError",
          className: "com.example.Logger",
          cpuTime: 450,
          wallTime: 480,
          selfTime: 190,
          samples: 185,
          percentage: 32
        }
      ]
    }
  },
  {
    id: 9,
    name: "JIT Compilation",
    status: "info",
    score: 25,
    brief: "Most hot methods are being properly JIT compiled.",
    summary: "JIT compilation appears to be working effectively for most methods.",
    explanation: "Analysis shows that the JVM is successfully identifying and compiling frequently executed methods. However, there are a few methods that are being deoptimized due to unexpected polymorphism or complex control flow.",
    solution: "Review the methods that are being deoptimized and consider simplifying their structure or reducing their complexity. In some cases, breaking complex methods into smaller, more focused methods can help the JIT compiler optimize them more effectively.",
    details: "JIT compiled methods: 437\nDeoptimized methods: 17\nRecompilation events: 24",
    flamegraphData: {
      id: "fg-9",
      checkId: 9,
      profileId: route.params.profileId,
      methods: [
        {
          id: "m-16",
          name: "compileMethod",
          className: "org.hotspot.jit.Compiler",
          cpuTime: 580,
          wallTime: 620,
          selfTime: 250,
          samples: 240,
          percentage: 42
        },
        {
          id: "m-17",
          name: "optimizeMethod",
          className: "org.hotspot.jit.Optimizer",
          cpuTime: 480,
          wallTime: 510,
          selfTime: 210,
          samples: 195,
          percentage: 35
        },
        {
          id: "m-18",
          name: "inlineMethod",
          className: "org.hotspot.jit.Inliner",
          cpuTime: 320,
          wallTime: 340,
          selfTime: 140,
          samples: 130,
          percentage: 22
        }
      ]
    }
  }
]);

// Helper functions
const getStatusColorClass = (status) => {
  switch (status) {
    case 'success': return 'success';
    case 'warning': return 'warning';
    case 'error': return 'danger';
    case 'info': return 'info';
    case 'disabled': return 'secondary';
    default: return 'primary';
  }
};

// Helper function to get color based on score (lower is better)
const getScoreColorClass = (score) => {
  if (score <= 30) return 'success';
  if (score <= 60) return 'warning';
  if (score <= 85) return 'danger';
  return 'danger';
};

// Helper function to get the appropriate icon based on score
const getIconForScore = (score) => {
  if (score <= 30) return 'bi-check-circle-fill';
  if (score <= 60) return 'bi-exclamation-triangle-fill';
  if (score <= 85) return 'bi-x-circle-fill';
  return 'bi-x-circle-fill';
};

const getStatusIcon = (status) => {
  switch (status) {
    case 'success': return 'bi-check-circle-fill';
    case 'warning': return 'bi-exclamation-triangle-fill';
    case 'error': return 'bi-x-circle-fill';
    case 'info': return 'bi-info-circle-fill';
    case 'disabled': return 'bi-dash-circle-fill';
    default: return 'bi-question-circle-fill';
  }
};

// Method to show details modal
const showDetailsModal = (check) => {
  selectedCheck.value = check;
  detailsModal.show();
};

// Method to navigate to flamegraph from modal
const navigateToFlamegraphFromModal = () => {
  if (selectedCheck.value && selectedCheck.value.flamegraphData) {
    // Close the modal
    detailsModal.hide();
    
    // Navigate to flamegraph
    navigateToFlamegraph(selectedCheck.value);
  }
};

// Method to navigate to full flamegraph view with check context
const navigateToFlamegraph = (check) => {
  if (check.flamegraphData) {
    // Store the check data in sessionStorage to pass it to the flamegraph view
    sessionStorage.setItem('guardianCheck', JSON.stringify(check));
    router.push({
      name: 'profile-flamegraph',
      params: { 
        projectId: route.params.projectId,
        profileId: route.params.profileId
      },
      query: { 
        source: 'guardian',
        checkId: check.id 
      }
    });
  }
};
</script>

<style scoped>
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

/* Filter card styles */
.filter-card {
  border-radius: 0.5rem;
  box-shadow: 0 0.125rem 0.25rem rgba(0, 0, 0, 0.075);
  background-color: #f8f9fa;
  border: 1px solid #dee2e6;
}

/* Phoenix search styles */
.phoenix-search {
  display: flex;
  align-items: center;
  border: 1px solid #e0e5eb;
  border-radius: 0.375rem;
  overflow: hidden;
  background-color: white;
  position: relative;
}

.search-icon-container {
  width: 40px;
  display: flex;
  justify-content: center;
  background-color: transparent;
}

.clear-icon-container {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  cursor: pointer;
  z-index: 5;
}

.clear-icon-container i {
  font-size: 14px;
}

.clear-icon-container:hover i {
  color: #6c757d !important;
}

.custom-select {
  height: 40px;
  border-color: #e0e5eb;
  font-size: 0.9rem;
}

.custom-select:focus {
  border-color: #5e64ff;
  box-shadow: 0 0 0 0.25rem rgba(94, 100, 255, 0.25);
}

/* No results styles */
.no-results {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  color: #6c757d;
}

.no-results i {
  font-size: 3rem;
  margin-bottom: 1rem;
}

.no-results h5 {
  margin-bottom: 0.5rem;
}

.no-results p {
  margin-bottom: 1.5rem;
}

/* Category headers */
.category-header {
  font-size: 1.1rem;
  font-weight: 600;
  margin-top: 1rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}

/* Modal styles */
.modal-body h6 {
  font-weight: 600;
  margin-bottom: 0.5rem;
  color: #495057;
}

.modal-body p {
  color: #6c757d;
  margin-bottom: 0.75rem;
}

.modal-body pre {
  margin-top: 0.5rem;
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

.progress {
  background-color: #f0f0f0;
  border-radius: 4px;
  overflow: hidden;
}

.card-footer {
  border-top: 1px solid rgba(0, 0, 0, 0.05);
  padding: 0.75rem 1rem;
}

.border-success {
  border-left-color: #28a745 !important;
}

.border-warning {
  border-left-color: #ffc107 !important;
}

.border-danger {
  border-left-color: #dc3545 !important;
}

.border-info {
  border-left-color: #17a2b8 !important;
}

.border-secondary {
  border-left-color: #6c757d !important;
}

/* Form control focus styles */
.form-select:focus {
  border-color: #5e64ff;
  box-shadow: 0 0 0 0.25rem rgba(94, 100, 255, 0.25);
}

.phoenix-search .form-control:focus {
  border-color: transparent;
  box-shadow: none;
}

/* Filter transition effects */
.row {
  transition: opacity 0.3s ease;
}

@media (max-width: 767.98px) {
  .filter-card .row > div {
    margin-bottom: 0.5rem;
  }
}
</style>
