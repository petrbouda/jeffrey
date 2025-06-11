<template>
  <div>
    <DashboardHeader
      title="JDBC Connection Pools"
      icon="layers"
    />
    
    <!-- Loading state -->
    <div v-if="isLoading" class="p-4 text-center">
      <div class="spinner-border" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
    </div>

    <!-- Error state -->
    <div v-else-if="error" class="p-4 text-center">
      <div class="alert alert-danger" role="alert">
        Error loading pool data: {{ error }}
      </div>
    </div>

    <!-- Dashboard content -->
    <div v-else-if="poolDataList.length > 0" class="dashboard-container">
      <!-- Pool Selection Section -->
      <section class="dashboard-section">
        <div class="pool-selector-grid">
          <div 
            v-for="pool in poolDataList" 
            :key="pool.poolName"
            class="pool-selector-card"
            :class="{ 'selected': selectedPool?.poolName === pool.poolName }"
            @click="selectPool(pool)"
          >
            <div class="pool-card-header">
              <h4 class="pool-card-name">{{ pool.poolName }}</h4>
              <span class="pool-health-badge" :class="`badge-${getPoolHealthVariant(pool)}`">
                {{ getPoolHealthStatus(pool) }}
              </span>
            </div>
            <div class="pool-card-info">
              <div class="pool-card-stat">
                <span class="stat-label">Max:</span>
                <span class="stat-value">{{ pool.configuration.maxConnectionCount }}</span>
              </div>
              <div class="pool-card-stat">
                <span class="stat-label">Peak:</span>
                <span class="stat-value">{{ pool.statistics.peakConnectionCount }}</span>
              </div>
              <div class="pool-card-stat">
                <span class="stat-label">Pending:</span>
                <span class="stat-value">{{ pool.statistics.pendingPeriodsPercent.toFixed(1) }}%</span>
              </div>
              <div class="pool-card-stat">
                <span class="stat-label">Timeouts:</span>
                <span class="stat-value">{{ pool.statistics.timeoutsCount }}</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- Selected Pool Details -->
      <div v-if="selectedPool">
        <!-- Pool Header with More Space -->
        <div class="selected-pool-header-spacious">
          <div class="pool-header-content">
            <i class="bi bi-database-fill"></i>
            <span class="selected-pool-name">{{ selectedPool.poolName }}</span>
          </div>
        </div>

        <!-- Configuration Section -->
        <section class="dashboard-section">
          <div class="dashboard-grid">
            <DashboardCard
              title="Peak Connections"
              :value="selectedPool.statistics.peakConnectionCount"
              :valueA="selectedPool.configuration.minConnectionCount"
              :valueB="selectedPool.configuration.maxConnectionCount"
              labelA="Min"
              labelB="Max"
              variant="highlight"
            />
            <DashboardCard
              title="Max Active Connections"
              :value="selectedPool.statistics.peakActiveConnectionCount"
              :valueA="selectedPool.statistics.p99ActiveConnectionCount"
              labelA="P99"
              variant="info"
            />
            <DashboardCard
              title="Timeouts"
              :value="selectedPool.statistics.timeoutsCount"
              :valueA="`${(selectedPool.statistics.timeoutRate * 100).toFixed(3)}%`"
              labelA="Rate"
              :variant="selectedPool.statistics.timeoutsCount > 0 ? 'danger' : 'success'"
            />
            <DashboardCard
              title="Max Pending Threads"
              :value="selectedPool.statistics.maxPendingThreadCount"
              :valueA="`${selectedPool.statistics.pendingPeriodsPercent.toFixed(1)}%`"
              labelA="Time with a pending thread"
              :variant="selectedPool.statistics.pendingPeriodsPercent > 10 ? 'warning' : 'success'"
            />
          </div>
        </section>

        <!-- Event Charts Section -->
        <section v-if="selectedPool.eventStatistics.length > 0" class="dashboard-section">
          <div class="dashboard-tabs mb-4">
            <ul class="nav nav-tabs" role="tablist">
              <li v-for="(event, index) in selectedPool.eventStatistics" :key="event.eventType" class="nav-item" role="presentation">
                <button class="nav-link" :class="{ active: index === 0 }" :id="`event-${event.eventType}-tab`" data-bs-toggle="tab" :data-bs-target="`#event-${event.eventType}-tab-pane`" type="button" role="tab" :aria-controls="`event-${event.eventType}-tab-pane`" :aria-selected="index === 0">
                  <i class="bi bi-graph-up me-2"></i>{{ event.eventName }}
                </button>
              </li>
            </ul>

            <div class="tab-content">
              <!-- Individual Event Chart Tabs -->
              <div v-for="(event, index) in selectedPool.eventStatistics" :key="event.eventType" class="tab-pane fade" :class="{ 'show active': index === 0 }" :id="`event-${event.eventType}-tab-pane`" role="tabpanel" :aria-labelledby="`event-${event.eventType}-tab`" tabindex="0">
                <div class="chart-container">
                  <TimeSeriesLineGraph
                    :primary-data="getEventTimeSeriesData(event.eventType)"
                    :primary-title="`${event.eventName} (ms)`"
                    :visible-minutes="15"
                  />
                </div>
              </div>
            </div>
          </div>
        </section>

        <!-- Event Statistics Table -->
        <section v-if="selectedPool.eventStatistics.length > 0" class="dashboard-section">
          <h3 class="section-title">Event Statistics</h3>
          <div class="card mb-4">
            <div class="card-body p-0">
              <table class="table table-hover mb-0 event-tree-table">
                <thead>
                  <tr>
                    <th>Event Type</th>
                    <th class="text-center">Count</th>
                    <th class="text-center">Min (ms)</th>
                    <th class="text-center">P50 (ms)</th>
                    <th class="text-center">P99 (ms)</th>
                    <th class="text-center">Max (ms)</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="event in selectedPool.eventStatistics" :key="event.eventType" class="leaf-row">
                    <td>
                      <div class="d-flex align-items-center event-name-cell">
                        <span class="tree-leaf-icon me-2">
                          <i class="bi bi-circle-fill"></i>
                        </span>
                        <span class="event-name">{{ event.eventName }}</span>
                      </div>
                    </td>
                    <td class="text-center">{{ event.count.toLocaleString() }}</td>
                    <td class="text-center">{{ formatDuration(event.min) }}</td>
                    <td class="text-center">{{ formatDuration(event.p50) }}</td>
                    <td class="text-center">{{ formatDuration(event.p99) }}</td>
                    <td class="text-center">{{ formatDuration(event.max) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </section>
      </div>
    </div>

    <!-- No data state -->
    <div v-else class="p-4 text-center">
      <h3 class="text-muted">No Pool Data Available</h3>
      <p class="text-muted">No JDBC connection pool data found for this profile</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import DashboardHeader from '@/components/DashboardHeader.vue';
import DashboardCard from '@/components/DashboardCard.vue';
import TimeSeriesLineGraph from '@/components/TimeSeriesLineGraph.vue';
import PoolData from "@/services/profile/custom/jdbc/model/PoolData.ts";
import ProfileJdbcPoolClient from "@/services/profile/custom/jdbc/ProfileJdbcPoolClient.ts";

const route = useRoute();

// Reactive state
const poolDataList = ref<PoolData[]>([]);
const selectedPool = ref<PoolData | null>(null);
const isLoading = ref(true);
const error = ref<string | null>(null);
const timeseriesCache = ref<Map<string, number[][]>>(new Map());

// Client initialization
const client = new ProfileJdbcPoolClient(route.params.projectId as string, route.params.profileId as string);

// Methods
const loadPoolData = async () => {
  try {
    isLoading.value = true;
    error.value = null;
    
    // Load data from API
    poolDataList.value = await client.getPoolData();
    
    // Select the first pool by default
    if (poolDataList.value.length > 0) {
      selectedPool.value = poolDataList.value[0];
    }
    
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading pool data:', err);
  } finally {
    isLoading.value = false;
  }
};


const formatDuration = (nanoseconds: number): string => {
  const ms = nanoseconds / 1_000_000;
  if (ms < 1) {
    return `${(nanoseconds / 1_000).toFixed(2)}μs`;
  } else if (ms < 1000) {
    return `${ms.toFixed(2)}ms`;
  } else {
    return `${(ms / 1000).toFixed(2)}s`;
  }
};

const selectPool = (pool: PoolData) => {
  selectedPool.value = pool;
  // Clear cache when switching pools to reload timeseries data
  timeseriesCache.value.clear();
};

const getPoolHealthStatus = (pool: PoolData): string => {
  const stats = pool.statistics;
  
  if (stats.timeoutsCount > 0 || stats.timeoutRate > 0.01) {
    return 'Critical';
  } else if (stats.pendingPeriodsPercent > 10) {
    return 'Warning';
  } else {
    return 'Healthy';
  }
};

const getPoolHealthVariant = (pool: PoolData): string => {
  const status = getPoolHealthStatus(pool);
  switch (status) {
    case 'Critical':
      return 'danger';
    case 'Warning':
      return 'warning';
    case 'Healthy':
      return 'success';
    default:
      return 'info';
  }
};

const getEventTimeSeriesData = (eventName: string): number[][] => {
  if (!selectedPool.value) {
    return [];
  }
  
  const cacheKey = `${selectedPool.value.poolName}-${eventName}`;
  
  // Return cached data if available
  if (timeseriesCache.value.has(cacheKey)) {
    return timeseriesCache.value.get(cacheKey)!;
  }
  
  // Load data asynchronously and return empty array initially
  loadTimeseriesData(selectedPool.value.poolName, eventName, cacheKey);
  
  return [];
};

const loadTimeseriesData = async (poolName: string, eventType: string, cacheKey: string) => {
  try {
    const serie = await client.getTimeseries(poolName, eventType);
    timeseriesCache.value.set(cacheKey, serie.data);
    // Trigger reactivity update
    timeseriesCache.value = new Map(timeseriesCache.value);
  } catch (err) {
    console.error('Error loading timeseries data:', err);
    // Set empty data on error
    timeseriesCache.value.set(cacheKey, []);
    timeseriesCache.value = new Map(timeseriesCache.value);
  }
};



// Lifecycle
onMounted(() => {
  loadPoolData();
});
</script>

<style scoped>
.dashboard-container {
  padding: 1.5rem;
}

.dashboard-section {
  margin-bottom: 2rem;
}

.section-title {
  color: #2c3e50;
  font-size: 1.25rem;
  font-weight: 600;
  margin-bottom: 1rem;
  padding-bottom: 0.5rem;
  border-bottom: 2px solid #e9ecef;
}


.pool-selector-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.pool-selector-card {
  background: white;
  border: 2px solid #e9ecef;
  border-radius: 12px;
  padding: 1rem;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.pool-selector-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  border-color: #667eea;
}

.pool-selector-card.selected {
  border-color: #667eea;
  background: #f8f9ff;
  color: #2c3e50;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.15);
}

.pool-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.pool-card-name {
  font-size: 1.1rem;
  font-weight: 600;
  margin: 0;
}

.pool-health-badge {
  padding: 0.25rem 0.5rem;
  border-radius: 6px;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
}

.badge-success {
  background-color: #d4edda;
  color: #155724;
}

.badge-warning {
  background-color: #fff3cd;
  color: #856404;
}

.badge-danger {
  background-color: #f8d7da;
  color: #721c24;
}

.pool-selector-card.selected .badge-success {
  background-color: #d4edda;
  color: #155724;
}

.pool-selector-card.selected .badge-warning {
  background-color: #fff3cd;
  color: #856404;
}

.pool-selector-card.selected .badge-danger {
  background-color: #f8d7da;
  color: #721c24;
}

.pool-card-info {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
}

.pool-card-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
}

.stat-label {
  font-size: 0.75rem;
  font-weight: 500;
  opacity: 0.8;
  margin-bottom: 0.25rem;
}

.stat-value {
  font-size: 1rem;
  font-weight: 600;
}

.selected-pool-header-spacious {
  background: #f8f9ff;
  border: 1px solid #e9ecef;
  border-radius: 8px;
  margin: 1.5rem 0;
  padding: 1rem 0;
  display: flex;
  justify-content: center;
  align-items: center;
}

.pool-header-content {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  color: #2c3e50;
  font-size: 1.1rem;
  font-weight: 600;
}

.pool-header-content i {
  font-size: 1.25rem;
}

.selected-pool-name {
  font-weight: 600;
}

.section-title {
  color: #2c3e50;
  font-size: 1.25rem;
  font-weight: 600;
  margin-bottom: 1rem;
  padding-bottom: 0.5rem;
  border-bottom: 2px solid #e9ecef;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 1rem;
  margin-bottom: 1.5rem;
}

/* Table styles - Copied from ProfileEventTypes.vue */
.event-tree-table {
  width: 100%;
  table-layout: fixed;
}

.event-tree-table th:nth-child(1) {
  width: 50%;
}

.event-tree-table th:nth-child(2),
.event-tree-table th:nth-child(3),
.event-tree-table th:nth-child(4),
.event-tree-table th:nth-child(5),
.event-tree-table th:nth-child(6) {
  width: 10%;
}

.event-name-cell {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tree-leaf-icon {
  display: inline-block;
  width: 20px;
  text-align: center;
  font-size: 6px;
  vertical-align: middle;
  color: #adb5bd;
}

.event-name {
  font-weight: 500;
}

.leaf-row {
  background-color: #fff;
}

.card {
  border: none;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

/* Dashboard Tabs - Copied from ProfileHeapMemory.vue */
.dashboard-tabs {
  background-color: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
  flex-grow: 1;
}

.nav-tabs {
  border-bottom: 1px solid #e9ecef;
  padding: 0 1rem;
}

.nav-tabs .nav-link {
  margin-bottom: -1px;
  border-radius: 0;
  padding: 0.75rem 1rem;
  font-size: 0.9rem;
  color: #6c757d;
  border: none;
  border-bottom: 2px solid transparent;
}

.nav-tabs .nav-link.active {
  background-color: transparent;
  color: #0d6efd;
  border-bottom: 2px solid #0d6efd;
}

.nav-tabs .nav-link:hover:not(.active) {
  border-color: transparent;
  color: #212529;
}

.tab-content {
  padding: 1.5rem;
}

/* Chart Container */
.chart-container {
  height: 450px;
  width: 100%;
}

.alert {
  border-radius: 8px;
  border: none;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.spinner-border {
  width: 3rem;
  height: 3rem;
}

@media (max-width: 768px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
  
  .dashboard-container {
    padding: 1rem;
  }
}
</style>
