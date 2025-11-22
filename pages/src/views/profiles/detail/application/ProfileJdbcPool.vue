<template>
  <div>
    <!-- Feature Disabled State -->
    <CustomDisabledFeatureAlert 
      v-if="isJdbcPoolDisabled"
      title="JDBC Pool Dashboard"
      eventType="JDBC connection pool"
    />

    <div v-else>
      <PageHeader
        title="JDBC Connection Pools"
        icon="bi-layers"
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
              :valueA="selectedPool.configuration.maxConnectionCount"
              :valueB="selectedPool.configuration.minConnectionCount"
              labelA="Configured MAX"
              labelB="Configured MIN"
              variant="highlight"
            />
            <DashboardCard
              title="Max Active Connections"
              :value="selectedPool.statistics.peakActiveConnectionCount"
              :valueA="selectedPool.statistics.avgActiveConnectionCount"
              labelA="Average"
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
        <ChartSectionWithTabs 
          v-if="selectedPool.eventStatistics.length > 0" 
          title="Pool Timeseries" 
          icon="graph-up" 
          :full-width="true"
          :tabs="eventTabs"
          @tab-change="onTabChange"
        >
          <template v-for="event in selectedPool.eventStatistics" :key="event.eventType" #[`event-${event.eventType}`]>
            <div v-if="isTimeseriesLoading(event.eventType)" class="chart-loading-overlay">
              <div class="spinner-border text-primary" role="status">
                <span class="visually-hidden">Loading chart data...</span>
              </div>
              <p class="mt-2 text-muted">Loading timeseries data...</p>
            </div>
            <ApexTimeSeriesChart
              v-else
              :primary-data="getEventTimeSeriesData(event.eventType)"
              :primary-title="`${event.eventName}`"
              :visible-minutes="15"
              :primary-axis-type="'durationInMillis'"
            />
          </template>
        </ChartSectionWithTabs>

        <!-- Event Statistics Table -->
        <ChartSection v-if="selectedPool.eventStatistics.length > 0" title="Event Statistics" icon="table" :full-width="true">
          <div class="table-responsive">
            <table class="table table-hover mb-0 event-tree-table">
              <thead>
                <tr>
                  <th>Event Type</th>
                  <th class="text-center">Count</th>
                  <th class="text-center">MAX</th>
                  <th class="text-center">AVG</th>
                  <th class="text-center">MIN</th>
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
                  <td class="text-center">{{ FormattingService.formatDuration2Units(event.max) }}</td>
                  <td class="text-center">{{ FormattingService.formatDuration2Units(event.avg) }}</td>
                  <td class="text-center">{{ FormattingService.formatDuration2Units(event.min) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </ChartSection>
      </div>
    </div>

      <!-- No data state -->
      <div v-else class="p-4 text-center">
        <h3 class="text-muted">No Pool Data Available</h3>
        <p class="text-muted">No JDBC connection pool data found for this profile</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, withDefaults, defineProps } from 'vue';
import { useRoute } from 'vue-router';
import PageHeader from '@/components/layout/PageHeader.vue';
import DashboardCard from '@/components/DashboardCard.vue';
import ChartSection from '@/components/ChartSection.vue';
import ChartSectionWithTabs from '@/components/ChartSectionWithTabs.vue';
import ApexTimeSeriesChart from '@/components/ApexTimeSeriesChart.vue';
import PoolData from "@/services/profile/custom/jdbc/model/PoolData.ts";
import ProfileJdbcPoolClient from "@/services/profile/custom/jdbc/ProfileJdbcPoolClient.ts";
import FormattingService from "@/services/FormattingService.ts";
import CustomDisabledFeatureAlert from '@/components/alerts/CustomDisabledFeatureAlert.vue';
import FeatureType from '@/services/profile/features/FeatureType';

// Define props
interface Props {
  disabledFeatures?: FeatureType[];
}

const props = withDefaults(defineProps<Props>(), {
  disabledFeatures: () => []
});

const route = useRoute();

// Reactive state
const poolDataList = ref<PoolData[]>([]);
const selectedPool = ref<PoolData | null>(null);
const isLoading = ref(true);
const error = ref<string | null>(null);
const currentTimeseriesLoading = ref(false);
const currentTimeseriesData = ref<number[][]>([]);
const activeEventType = ref<string | null>(null);

// Check if JDBC pool dashboard is disabled
const isJdbcPoolDisabled = computed(() => {
  return props.disabledFeatures.includes(FeatureType.JDBC_POOL_DASHBOARD);
});

// Client initialization
const client = new ProfileJdbcPoolClient(
  route.params.workspaceId as string,
  route.params.projectId as string,
  route.params.profileId as string
);

// Computed property for tabs
const eventTabs = computed(() => {
  if (!selectedPool.value) return [];
  return selectedPool.value.eventStatistics.map(event => ({
    id: `event-${event.eventType}`,
    label: event.eventName,
    icon: 'graph-up'
  }));
});

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

const selectPool = (pool: PoolData) => {
  selectedPool.value = pool;
  // Clear data and reset active tab when switching pools
  currentTimeseriesData.value = [];
  currentTimeseriesLoading.value = false;
  activeEventType.value = null;
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

const onTabClick = (eventType: string) => {
  activeEventType.value = eventType;
  // Always load fresh timeseries data when tab is clicked
  if (selectedPool.value && !currentTimeseriesLoading.value) {
    currentTimeseriesData.value = [];
    loadTimeseriesData(selectedPool.value.poolName, eventType);
  }
};

const onTabChange = (tabIndex: number, tab: any) => {
  const event = selectedPool.value?.eventStatistics[tabIndex];
  if (event) {
    onTabClick(event.eventType);
  }
};

const getEventTimeSeriesData = (eventName: string): number[][] => {
  if (!selectedPool.value) {
    return [];
  }
  
  // Only return data if this is the currently active event type
  if (activeEventType.value === eventName) {
    return currentTimeseriesData.value;
  }
  
  // Auto-load first tab on initial load
  if (activeEventType.value === null && selectedPool.value.eventStatistics[0]?.eventType === eventName) {
    if (!currentTimeseriesLoading.value) {
      activeEventType.value = eventName;
      loadTimeseriesData(selectedPool.value.poolName, eventName);
    }
  }
  
  return [];
};

const isTimeseriesLoading = (eventType: string): boolean => {
  // Only show loading for the currently active eve
  // nt type
  return activeEventType.value === eventType && currentTimeseriesLoading.value;
};

const loadTimeseriesData = async (poolName: string, eventType: string) => {
  try {
    currentTimeseriesLoading.value = true;
    const serie = await client.getTimeseries(poolName, eventType);
    currentTimeseriesData.value = serie.data;
  } catch (err) {
    console.error('Error loading timeseries data:', err);
    currentTimeseriesData.value = [];
  } finally {
    currentTimeseriesLoading.value = false;
  }
};

// Lifecycle
onMounted(() => {
  // Only load data if the feature is not disabled
  if (!isJdbcPoolDisabled.value) {
    loadPoolData();
  }
});
</script>

<style scoped>
.dashboard-section {
  margin-bottom: 2rem;
}

.pool-selector-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 1rem;
  margin-bottom: 1.5rem;
  max-width: 100%;
}

@media (min-width: 1600px) {
  .pool-selector-grid {
    grid-template-columns: repeat(4, 1fr);
  }
}

@media (min-width: 1200px) and (max-width: 1599px) {
  .pool-selector-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (min-width: 768px) and (max-width: 1199px) {
  .pool-selector-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 767px) {
  .pool-selector-grid {
    grid-template-columns: 1fr;
  }
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
  width: 35%;
}

.event-tree-table th:nth-child(2),
.event-tree-table th:nth-child(3),
.event-tree-table th:nth-child(4),
.event-tree-table th:nth-child(5) {
  width: 16.25%;
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


/* Chart Container */

.chart-loading-overlay {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  background-color: rgba(255, 255, 255, 0.9);
  min-height: 200px;
  border-radius: 8px;
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
}
</style>
