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
              :valueA="`${selectedPool.statistics.timeWithPendingThreadsInPercent.toFixed(1)}%`"
              labelA="Time with a pending thread"
              :variant="selectedPool.statistics.timeWithPendingThreadsInPercent > 10 ? 'warning' : 'success'"
            />
          </div>
        </section>

        <!-- Event Charts Section -->
        <section v-if="selectedPool.eventStatistics.length > 0" class="dashboard-section">
          <div class="dashboard-tabs mb-4">
            <ul class="nav nav-tabs" role="tablist">
              <li v-for="(event, index) in selectedPool.eventStatistics" :key="event.eventName" class="nav-item" role="presentation">
                <button class="nav-link" :class="{ active: index === 0 }" :id="`event-${event.eventName}-tab`" data-bs-toggle="tab" :data-bs-target="`#event-${event.eventName}-tab-pane`" type="button" role="tab" :aria-controls="`event-${event.eventName}-tab-pane`" :aria-selected="index === 0">
                  <i class="bi bi-graph-up me-2"></i>{{ getEventName(event.eventName) }}
                </button>
              </li>
            </ul>

            <div class="tab-content">
              <!-- Individual Event Chart Tabs -->
              <div v-for="(event, index) in selectedPool.eventStatistics" :key="event.eventName" class="tab-pane fade" :class="{ 'show active': index === 0 }" :id="`event-${event.eventName}-tab-pane`" role="tabpanel" :aria-labelledby="`event-${event.eventName}-tab`" tabindex="0">
                <div class="chart-container">
                  <TimeSeriesLineGraph
                    :primary-data="getEventTimeSeriesData(event.eventName)"
                    :primary-title="`${getEventName(event.eventName)} (ms)`"
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
                  <tr v-for="event in selectedPool.eventStatistics" :key="event.eventName" class="leaf-row">
                    <td>
                      <div class="d-flex align-items-center event-name-cell">
                        <span class="tree-leaf-icon me-2">
                          <i class="bi bi-circle-fill"></i>
                        </span>
                        <span class="event-name">{{ getEventName(event.eventName) }}</span>
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
import DashboardHeader from '@/components/DashboardHeader.vue';
import DashboardCard from '@/components/DashboardCard.vue';
import TimeSeriesLineGraph from '@/components/TimeSeriesLineGraph.vue';
import PoolData from "@/services/profile/custom/jdbc/model/PoolData.ts";
import PoolConfiguration from "@/services/profile/custom/jdbc/model/PoolConfiguration.ts";
import PoolStatistics from "@/services/profile/custom/jdbc/model/PoolStatistics.ts";
import PoolEventStatistics from "@/services/profile/custom/jdbc/model/PoolEventStatistics.ts";

// Props definition
defineProps({
  profile: {
    type: Object,
    required: true
  },
  secondaryProfile: {
    type: Object,
    default: null
  }
});

// Reactive state
const poolDataList = ref<PoolData[]>([]);
const selectedPool = ref<PoolData | null>(null);
const isLoading = ref(true);
const error = ref<string | null>(null);

// Event name mapping
const eventNames: Record<number, string> = {
  1: 'Connection Acquired',
  2: 'Connection Created',
  3: 'Connection Borrowed',
  4: 'Acquiring Timeout',
  5: 'Pool Statistics'
};

// Methods
const loadPoolData = async () => {
  try {
    isLoading.value = true;
    error.value = null;
    
    // TODO: Replace with actual API call
    // const response = await fetch(`/api/profiles/${props.profile.id}/pool-data`);
    // poolData.value = await response.json();
    
    // Mock data for demonstration
    await new Promise(resolve => setTimeout(resolve, 500));
    
    // Generate realistic mock data for multiple pools
    poolDataList.value = [
      // Primary HikariCP Pool - Healthy
      new PoolData(
        "HikariCP-Primary",
        new PoolConfiguration(20, 5),
        new PoolStatistics(15, 12, 8, 3, 2.5, 0, 0.0),
        [
          new PoolEventStatistics("EventTypeName", "jeffrey.EventTypeName", 1, 1250, 150000, 850000, 2100000),
          new PoolEventStatistics("EventTypeName", "jeffrey.EventTypeName", 2, 45, 2500000, 8200000, 15000000),
          new PoolEventStatistics("EventTypeName", "jeffrey.EventTypeName", 3, 1180, 50000, 180000, 450000),
          new PoolEventStatistics("EventTypeName", "jeffrey.EventTypeName", 5, 120, 100000, 250000, 500000)
        ]
      ),
      
      // Analytics Pool - Warning state
      new PoolData(
        "HikariCP-Analytics",
        new PoolConfiguration(10, 2),
        new PoolStatistics(9, 8, 7, 8, 15.2, 0, 0.0),
        [
          new PoolEventStatistics("EventTypeName", "jeffrey.EventTypeName", 1, 1250, 150000, 850000, 2100000),
          new PoolEventStatistics("EventTypeName", "jeffrey.EventTypeName", 2, 45, 2500000, 8200000, 15000000),
          new PoolEventStatistics("EventTypeName", "jeffrey.EventTypeName", 3, 1180, 50000, 180000, 450000),
          new PoolEventStatistics("EventTypeName", "jeffrey.EventTypeName", 5, 120, 100000, 250000, 500000)
        ]
      ),
      
      // Reporting Pool - Critical state
      new PoolData(
        "HikariCP-Reporting",
        new PoolConfiguration(15, 3),
        new PoolStatistics(15, 15, 14, 12, 25.8, 15, 0.025),
        [
          new PoolEventStatistics("EventTypeName", "jeffrey.EventTypeName", 1, 1250, 150000, 850000, 2100000),
          new PoolEventStatistics("EventTypeName", "jeffrey.EventTypeName", 2, 45, 2500000, 8200000, 15000000),
          new PoolEventStatistics("EventTypeName", "jeffrey.EventTypeName", 3, 1180, 50000, 180000, 450000),
          new PoolEventStatistics("EventTypeName", "jeffrey.EventTypeName", 5, 120, 100000, 250000, 500000)
        ]
      ),
      
      // Cache Pool - Healthy but smaller
      new PoolData(
        "HikariCP-Cache",
        new PoolConfiguration(8, 2),
        new PoolStatistics(6, 4, 3, 1, 1.2, 0, 0.0),
        [
          new PoolEventStatistics("EventTypeName", "jeffrey.EventTypeName", 1, 1250, 150000, 850000, 2100000),
          new PoolEventStatistics("EventTypeName", "jeffrey.EventTypeName", 2, 45, 2500000, 8200000, 15000000),
          new PoolEventStatistics("EventTypeName", "jeffrey.EventTypeName", 3, 1180, 50000, 180000, 450000),
          new PoolEventStatistics("EventTypeName", "jeffrey.EventTypeName", 5, 120, 100000, 250000, 500000)
        ]
      )
    ];
    
    // Select the first pool by default
    selectedPool.value = poolDataList.value[0];
    
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
  } finally {
    isLoading.value = false;
  }
};

const getEventName = (eventCode: number): string => {
  return eventNames[eventCode] || `Event ${eventCode}`;
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

const getEventTimeSeriesData = (eventName: number) => {
  // Generate mock time series data for the event
  const now = Date.now();
  const data = [];
  
  // Generate data for the last 1 hour with 1-second intervals (3600 data points)
  const intervalSeconds = 1;
  const totalPoints = 3600; // 1 hour = 3600 seconds
  
  for (let i = totalPoints - 1; i >= 0; i--) {
    const timestamp = now - (i * intervalSeconds * 1000);
    
    // Get base configuration for this event type
    const eventConfig = getEventConfig(eventName);
    let value = eventConfig.baseValue;
    
    // Add time-based patterns
    const timeOfDay = new Date(timestamp).getHours();
    const minuteInHour = new Date(timestamp).getMinutes();
    const secondInMinute = new Date(timestamp).getSeconds();
    
    // Business hours pattern (higher activity 9-17)
    if (timeOfDay >= 9 && timeOfDay <= 17) {
      value *= eventConfig.businessHoursMultiplier;
    } else {
      value *= eventConfig.offHoursMultiplier;
    }
    
    // Add periodic patterns (every 5 minutes for connection pool maintenance)
    if (minuteInHour % 5 === 0 && secondInMinute < 30) {
      value *= eventConfig.maintenanceMultiplier;
    }
    
    // Add high-frequency variations for more realistic second-by-second data
    const highFreqNoise = 1 + (Math.random() - 0.5) * 0.1; // Small random variations
    value *= highFreqNoise;
    
    // Add medium frequency patterns (every 30 seconds)
    const mediumFreqPattern = 1 + Math.sin((secondInMinute / 30) * Math.PI) * 0.2;
    value *= mediumFreqPattern;
    
    // Add random variation
    const randomFactor = 1 + (Math.random() - 0.5) * eventConfig.variability;
    value *= randomFactor;
    
    // Add occasional spikes based on event type (less frequent for second-level data)
    if (Math.random() < eventConfig.spikeChance / 60) { // Reduce spike frequency for second-level data
      value *= eventConfig.spikeMultiplier;
    }
    
    // Add gradual trends over the hour
    const trendFactor = 1 + Math.sin((i / totalPoints) * Math.PI * 2) * eventConfig.trendAmplitude;
    value *= trendFactor;
    
    // Ensure minimum value and round appropriately
    value = Math.max(eventConfig.minValue, value);
    
    data.push([
      Math.floor(timestamp / 1000), // Convert to seconds (Unix timestamp)
      Math.round(value * 1000) / 1000 // Round to 3 decimal places
    ]);
  }
  
  return data;
};

const getEventConfig = (eventName: number) => {
  // Return detailed configuration for each event type
  switch (eventName) {
    case 1: // Connection Acquired
      return {
        baseValue: 0.85,
        businessHoursMultiplier: 1.3,
        offHoursMultiplier: 0.7,
        maintenanceMultiplier: 1.1,
        variability: 0.4,
        spikeChance: 0.08,
        spikeMultiplier: 2.5,
        trendAmplitude: 0.15,
        minValue: 0.1
      };
    case 2: // Connection Created
      return {
        baseValue: 8.2,
        businessHoursMultiplier: 1.5,
        offHoursMultiplier: 0.6,
        maintenanceMultiplier: 0.9,
        variability: 0.5,
        spikeChance: 0.12,
        spikeMultiplier: 3.0,
        trendAmplitude: 0.2,
        minValue: 2.0
      };
    case 3: // Connection Borrowed
      return {
        baseValue: 0.18,
        businessHoursMultiplier: 1.4,
        offHoursMultiplier: 0.8,
        maintenanceMultiplier: 1.05,
        variability: 0.3,
        spikeChance: 0.06,
        spikeMultiplier: 2.0,
        trendAmplitude: 0.1,
        minValue: 0.05
      };
    case 4: // Acquiring Timeout
      return {
        baseValue: 12.0,
        businessHoursMultiplier: 1.8,
        offHoursMultiplier: 0.5,
        maintenanceMultiplier: 0.8,
        variability: 0.6,
        spikeChance: 0.15,
        spikeMultiplier: 4.0,
        trendAmplitude: 0.3,
        minValue: 5.0
      };
    case 5: // Pool Statistics
      return {
        baseValue: 0.25,
        businessHoursMultiplier: 1.2,
        offHoursMultiplier: 0.9,
        maintenanceMultiplier: 1.15,
        variability: 0.2,
        spikeChance: 0.05,
        spikeMultiplier: 1.8,
        trendAmplitude: 0.08,
        minValue: 0.1
      };
    default:
      return {
        baseValue: 1.0,
        businessHoursMultiplier: 1.2,
        offHoursMultiplier: 0.8,
        maintenanceMultiplier: 1.0,
        variability: 0.3,
        spikeChance: 0.1,
        spikeMultiplier: 2.0,
        trendAmplitude: 0.1,
        minValue: 0.1
      };
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
