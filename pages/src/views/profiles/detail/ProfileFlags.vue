<template>
  <LoadingState v-if="loading" message="Loading JVM flags..." />

  <ErrorState v-else-if="error" :message="error" />

  <div v-else>
    <PageHeader
        title="JVM Flags"
        description="JVM configuration flags captured during profiling"
        icon="bi-flag"
    />

    <div v-if="flagsData">
      <!-- Summary Metrics -->
      <StatsTable :metrics="summaryMetrics" class="mb-4" />

      <!-- Tabbed Content Section -->
      <ChartSectionWithTabs
          icon="flag"
          :tabs="analysisTabs"
          :full-width="true"
          id-prefix="flags-"
      >
        <!-- Dashboard Tab -->
        <template #dashboard>
          <!-- Filter Input -->
          <div class="mb-4">
            <div class="filter-container">
              <input
                  type="text"
                  class="form-control"
                  placeholder="Filter by flag name..."
                  v-model="searchTerm"
                  autocomplete="off"
              />
              <button
                  v-if="searchTerm"
                  class="clear-btn"
                  type="button"
                  @click="searchTerm = ''"
                  title="Clear filter"
              >
                <i class="bi bi-x"></i>
              </button>
            </div>
            <div class="filter-info" v-if="searchTerm">
              Showing {{ filteredFlagCount }} of {{ flagsData?.totalFlags }} flags
            </div>
          </div>

          <!-- Flags Table -->
          <div class="flags-table-container">
            <table class="table table-sm table-hover mb-0" v-if="filteredFlags.length > 0">
              <thead>
                <tr>
                  <SortableTableHeader
                      column="name"
                      label="Flag Name"
                      :sort-column="sortColumn"
                      :sort-direction="sortDirection"
                      @sort="toggleSort"
                  />
                  <SortableTableHeader
                      column="value"
                      label="Value"
                      :sort-column="sortColumn"
                      :sort-direction="sortDirection"
                      @sort="toggleSort"
                  />
                  <SortableTableHeader
                      column="type"
                      label="Type"
                      :sort-column="sortColumn"
                      :sort-direction="sortDirection"
                      @sort="toggleSort"
                  />
                  <SortableTableHeader
                      column="origin"
                      label="Origin"
                      :sort-column="sortColumn"
                      :sort-direction="sortDirection"
                      @sort="toggleSort"
                  />
                  <SortableTableHeader
                      column="changed"
                      label="Changed"
                      :sort-column="sortColumn"
                      :sort-direction="sortDirection"
                      @sort="toggleSort"
                  />
                </tr>
              </thead>
              <tbody>
                <template v-for="flag in filteredFlags" :key="flag.name">
                  <!-- Main Flag Row -->
                  <tr :class="{ 'expanded-row': isExpanded(flag.name) }">
                    <td class="flag-name-cell">
                      <div class="flag-name-wrapper">
                        <div class="flag-name-row">
                          <button
                              v-if="flag.hasChanged"
                              class="expand-btn"
                              type="button"
                              @click="toggleExpand(flag.name)"
                              :title="isExpanded(flag.name) ? 'Collapse' : 'Show change history'"
                          >
                            <i class="bi" :class="isExpanded(flag.name) ? 'bi-chevron-down' : 'bi-chevron-right'"></i>
                          </button>
                          <code class="flag-name">{{ flag.name }}</code>
                        </div>
                        <p v-if="flag.description"
                           class="flag-description"
                           :class="{ 'with-expand-offset': flag.hasChanged }">
                          {{ flag.description }}
                        </p>
                      </div>
                    </td>
                    <td class="flag-value">
                      <span :class="{ 'boolean-true': flag.type === 'Boolean' && flag.value === 'true', 'boolean-false': flag.type === 'Boolean' && flag.value === 'false' }">
                        {{ formatFlagValue(flag) }}
                      </span>
                    </td>
                    <td>
                      <Badge :value="flag.type" :variant="getTypeVariant(flag.type)" size="s" />
                    </td>
                    <td>
                      <Badge :value="flag.origin" :variant="getOriginVariant(flag.origin)" size="s" />
                    </td>
                    <td>
                      <Badge
                          v-if="flag.hasChanged"
                          value="Yes"
                          variant="orange"
                          size="s"
                      />
                      <Badge v-else value="No" variant="grey" size="s" />
                    </td>
                  </tr>

                  <!-- Change History Detail Row -->
                  <tr v-if="flag.hasChanged && isExpanded(flag.name)" class="history-row">
                    <td colspan="5">
                      <div class="change-history">
                        <div class="change-history-header">
                          <i class="bi bi-clock-history me-2"></i>
                          Change History
                        </div>
                        <div class="change-history-list">
                          <div
                              v-for="(change, idx) in flag.changeHistory"
                              :key="idx"
                              class="change-item"
                              :class="{ 'current-value': idx === 0 }"
                          >
                            <span class="change-timestamp">{{ formatTimestamp(change.timestamp) }}</span>
                            <span class="change-value">{{ change.value }}</span>
                            <Badge v-if="idx === 0" value="current" variant="green" size="xs" />
                          </div>
                        </div>
                      </div>
                    </td>
                  </tr>
                </template>
              </tbody>
            </table>

            <div v-else class="no-results">
              <i class="bi bi-funnel"></i>
              <p>No flags match "{{ searchTerm }}"</p>
            </div>
          </div>
        </template>

        <!-- About Tab -->
        <template #about>
          <div class="about-container">
            <!-- Header Section -->
            <div class="about-header">
              <div class="about-header-icon">
                <i class="bi bi-question-circle"></i>
              </div>
              <div>
                <h5 class="mb-1">Understanding JVM Flags</h5>
                <p class="text-muted mb-0">Configuration options that control JVM behavior</p>
              </div>
            </div>

            <!-- Intro -->
            <div class="about-intro">
              <p>JVM flags are command-line options that control various aspects of the Java Virtual Machine's behavior
                and performance. JFR (Java Flight Recorder) captures these flags during recording, providing insights
                into how the JVM is configured and how it operates during runtime.</p>
            </div>

            <!-- Flag Origins Section -->
            <h6 class="section-title">
              <i class="bi bi-signpost-split me-2"></i>
              Flag Origins
            </h6>

            <div class="feature-grid">
              <div class="feature-card">
                <div class="feature-icon" style="background: linear-gradient(135deg, #5e64ff 0%, #7c4dff 100%);">
                  <i class="bi bi-terminal"></i>
                </div>
                <div class="feature-content">
                  <h6>Command Line</h6>
                  <p>Flags explicitly set via <code>-XX:</code> arguments when starting the JVM. These represent intentional configuration choices by the administrator or developer.</p>
                </div>
              </div>

              <div class="feature-card">
                <div class="feature-icon" style="background: linear-gradient(135deg, #ffc107 0%, #ff9800 100%);">
                  <i class="bi bi-gear-wide-connected"></i>
                </div>
                <div class="feature-content">
                  <h6>Management</h6>
                  <p>Flags modified at runtime through JMX (Java Management Extensions) or diagnostic commands like <code>jcmd</code>. These may change during the recording.</p>
                </div>
              </div>

              <div class="feature-card">
                <div class="feature-icon" style="background: linear-gradient(135deg, #28a745 0%, #20c997 100%);">
                  <i class="bi bi-cpu"></i>
                </div>
                <div class="feature-content">
                  <h6>Ergonomic</h6>
                  <p>Flags automatically tuned by the JVM based on the system's hardware resources (CPU, memory) and workload characteristics. The JVM optimizes these for best performance.</p>
                </div>
              </div>

              <div class="feature-card">
                <div class="feature-icon" style="background: linear-gradient(135deg, #6c757d 0%, #495057 100%);">
                  <i class="bi bi-box"></i>
                </div>
                <div class="feature-content">
                  <h6>Default</h6>
                  <p>Flags using their built-in default values as defined in the JVM implementation. These weren't explicitly set or modified by any other mechanism.</p>
                </div>
              </div>
            </div>

            <!-- Flag Types Section -->
            <h6 class="section-title">
              <i class="bi bi-list-check me-2"></i>
              Flag Types
            </h6>

            <div class="type-list">
              <div class="type-item">
                <Badge value="Boolean" variant="blue" size="s" />
                <span>Toggle flags that can be <code>true</code> or <code>false</code></span>
              </div>
              <div class="type-item">
                <Badge value="Int" variant="purple" size="s" />
                <span>32-bit signed integer values</span>
              </div>
              <div class="type-item">
                <Badge value="Long" variant="purple" size="s" />
                <span>64-bit signed integer values (often used for memory sizes)</span>
              </div>
              <div class="type-item">
                <Badge value="UnsignedInt" variant="orange" size="s" />
                <span>32-bit unsigned integer values</span>
              </div>
              <div class="type-item">
                <Badge value="String" variant="teal" size="s" />
                <span>Text values for paths, names, or complex configurations</span>
              </div>
            </div>

            <!-- Why It Matters -->
            <h6 class="section-title">
              <i class="bi bi-lightning-charge me-2"></i>
              Why It Matters
            </h6>

            <div class="benefits-list">
              <div class="benefit-item">
                <i class="bi bi-check-circle-fill text-success"></i>
                <span>Understand how your JVM is configured for debugging and optimization</span>
              </div>
              <div class="benefit-item">
                <i class="bi bi-check-circle-fill text-success"></i>
                <span>Identify non-default settings that may affect performance or behavior</span>
              </div>
              <div class="benefit-item">
                <i class="bi bi-check-circle-fill text-success"></i>
                <span>Track runtime flag changes for troubleshooting intermittent issues</span>
              </div>
              <div class="benefit-item">
                <i class="bi bi-check-circle-fill text-success"></i>
                <span>Document production configurations for compliance and reproducibility</span>
              </div>
            </div>

            <!-- Note -->
            <div class="about-note">
              <div class="note-icon">
                <i class="bi bi-lightbulb-fill"></i>
              </div>
              <div class="note-content">
                <strong>Changed Flags</strong>
                <p class="mb-0">Flags marked as "Changed" had their values modified during the recording period,
                  typically via JMX or diagnostic commands. The dashboard shows the latest value along with
                  previous values for tracking configuration drift.</p>
              </div>
            </div>
          </div>
        </template>
      </ChartSectionWithTabs>
    </div>

    <div v-else class="no-data">
      <div class="alert alert-info d-flex align-items-center">
        <i class="bi bi-info-circle me-3 fs-4"></i>
        <div>
          <h6 class="mb-1">No JVM Flags Available</h6>
          <p class="mb-0 small">No JVM flag events were found in this profile. This may occur if the JFR recording was configured without flag events.</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import PageHeader from '@/components/layout/PageHeader.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import StatsTable from '@/components/StatsTable.vue';
import ChartSectionWithTabs from '@/components/ChartSectionWithTabs.vue';
import SortableTableHeader from '@/components/table/SortableTableHeader.vue';
import Badge from '@/components/Badge.vue';
import FlagsClient from '@/services/api/FlagsClient';
import FlagsData from '@/services/api/model/FlagsData';
import JvmFlag from '@/services/api/model/JvmFlag';
import FormattingService from '@/services/FormattingService';

const route = useRoute();
const { workspaceId, projectId } = useNavigation();
const profileId = route.params.profileId as string;
const loading = ref(true);
const error = ref<string | null>(null);
const flagsData = ref<FlagsData | null>(null);
const searchTerm = ref('');
const sortColumn = ref('name');
const sortDirection = ref<'asc' | 'desc'>('asc');
const expandedFlags = ref<Set<string>>(new Set());

let client: FlagsClient;

// Toggle expanded state for a flag
const toggleExpand = (flagName: string) => {
  if (expandedFlags.value.has(flagName)) {
    expandedFlags.value.delete(flagName);
  } else {
    expandedFlags.value.add(flagName);
  }
};

const isExpanded = (flagName: string) => expandedFlags.value.has(flagName);

// Format timestamp for display
const formatTimestamp = (isoTimestamp: string): string => {
  const date = new Date(isoTimestamp);
  return FormattingService.formatDateTime(date);
};

const analysisTabs = [
  { id: 'dashboard', label: 'JVM Flags', icon: 'flag' },
  { id: 'about', label: 'How It Works', icon: 'info-circle' }
];


// Computed metrics for StatsTable
const summaryMetrics = computed(() => {
  if (!flagsData.value) return [];
  return [
    {
      icon: 'flag',
      title: 'Total Flags',
      value: FormattingService.formatNumber(flagsData.value.totalFlags),
      variant: 'highlight' as const
    },
    {
      icon: 'arrow-repeat',
      title: 'Changed Flags',
      value: flagsData.value.changedFlags.toString(),
      variant: flagsData.value.changedFlags > 0 ? 'warning' as const : 'info' as const
    }
  ];
});

// Flatten all flags into a single array
const allFlags = computed(() => {
  if (!flagsData.value?.flagsByOrigin) return [];

  const flags: JvmFlag[] = [];
  for (const flagList of Object.values(flagsData.value.flagsByOrigin)) {
    flags.push(...flagList);
  }
  return flags;
});

// Filter and sort flags
const filteredFlags = computed(() => {
  let result = [...allFlags.value];

  // Filter by name
  const term = searchTerm.value.trim().toLowerCase();
  if (term) {
    result = result.filter(flag => flag.name.toLowerCase().includes(term));
  }

  // Sort
  const direction = sortDirection.value === 'asc' ? 1 : -1;
  result.sort((a, b) => {
    switch (sortColumn.value) {
      case 'name':
        return direction * a.name.localeCompare(b.name);
      case 'value':
        return direction * a.value.localeCompare(b.value);
      case 'type':
        return direction * a.type.localeCompare(b.type);
      case 'origin':
        const originOrder = ['Command line', 'Management', 'Ergonomic', 'Default'];
        return direction * (originOrder.indexOf(a.origin) - originOrder.indexOf(b.origin));
      case 'changed':
        return direction * ((a.hasChanged ? 1 : 0) - (b.hasChanged ? 1 : 0));
      default:
        return 0;
    }
  });

  return result;
});

const filteredFlagCount = computed(() => filteredFlags.value.length);

const toggleSort = (column: string) => {
  if (sortColumn.value === column) {
    sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc';
  } else {
    sortColumn.value = column;
    sortDirection.value = 'asc';
  }
};

const getTypeVariant = (type: string): string => {
  const typeMap: Record<string, string> = {
    'Boolean': 'blue',
    'Int': 'purple',
    'Long': 'purple',
    'UnsignedInt': 'orange',
    'String': 'teal'
  };
  return typeMap[type] || 'grey';
};

const getOriginVariant = (origin: string): string => {
  const originMap: Record<string, string> = {
    'Command line': 'indigo',
    'Management': 'yellow',
    'Ergonomic': 'green',
    'Default': 'grey'
  };
  return originMap[origin] || 'grey';
};

const formatFlagValue = (flag: JvmFlag): string => {
  if (flag.type === 'Boolean') {
    return flag.value;
  }
  // For numeric values that look like memory sizes, format them
  if ((flag.type === 'Long' || flag.type === 'UnsignedInt') && /^\d+$/.test(flag.value)) {
    const numValue = parseInt(flag.value, 10);
    // If it looks like a memory size (> 1024), format as bytes
    if (numValue > 1024 && flag.name.toLowerCase().includes('size')) {
      return FormattingService.formatBytes(numValue);
    }
  }
  return flag.value;
};

const scrollToTop = () => {
  const workspaceContent = document.querySelector('.workspace-content');
  if (workspaceContent) {
    workspaceContent.scrollTop = 0;
  }
};

const loadData = async () => {
  try {
    loading.value = true;
    error.value = null;

    client = new FlagsClient(profileId);
    flagsData.value = await client.getAllFlags();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load JVM flags';
    console.error('Error loading JVM flags:', err);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  scrollToTop();
  loadData();
});
</script>

<style scoped>
.no-data {
  padding: 2rem;
}

/* Filter Container */
.filter-container {
  position: relative;
  max-width: 400px;
}

.filter-container .form-control {
  height: 38px;
  padding-right: 2.5rem;
}

.filter-container .form-control:focus {
  border-color: #5e64ff;
  box-shadow: 0 0 0 0.2rem rgba(94, 100, 255, 0.15);
}

.filter-container .clear-btn {
  position: absolute;
  right: 0.5rem;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  padding: 0.25rem;
  color: #6c757d;
  cursor: pointer;
  line-height: 1;
}

.filter-container .clear-btn:hover {
  color: #343a40;
}

.filter-info {
  margin-top: 0.5rem;
  font-size: 0.8rem;
  color: #6c757d;
}

/* Flags Table */
.flags-table-container {
  background: white;
}

.table thead th {
  background-color: #fafbfc;
  font-weight: 600;
  color: #495057;
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #e9ecef;
  white-space: nowrap;
}

.table td {
  font-size: 0.85rem;
  padding: 0.625rem 1rem;
  vertical-align: middle;
  border-bottom: 1px solid #f0f0f0;
}

.table tbody tr:hover {
  background-color: rgba(94, 100, 255, 0.04);
}

.table tbody tr:last-child td {
  border-bottom: none;
}

.flag-name-cell {
  min-width: 280px;
}

.flag-name-wrapper {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.flag-name {
  background: none;
  padding: 0;
  font-size: 0.85rem;
  color: #343a40;
  font-weight: 500;
}

.flag-value {
  font-size: 0.9em;
}

.flag-value .boolean-true {
  color: #28a745;
  font-weight: 500;
}

.flag-value .boolean-false {
  color: #6c757d;
}

.cursor-help {
  cursor: help;
}

/* No Results */
.no-results {
  text-align: center;
  padding: 3rem 1rem;
  color: #6c757d;
}

.no-results i {
  font-size: 2.5rem;
  margin-bottom: 1rem;
  opacity: 0.5;
}

.no-results p {
  margin: 0;
  font-size: 1rem;
}

/* About Tab Styles */
.about-container {
  max-width: 1100px;
  margin: 0 auto;
  padding: 1.5rem;
}

.about-header {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1.5rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid #e9ecef;
}

.about-header-icon {
  width: 48px;
  height: 48px;
  background: linear-gradient(135deg, #5e64ff 0%, #7c4dff 100%);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 1.5rem;
  flex-shrink: 0;
}

.about-header h5 {
  font-weight: 600;
  color: #343a40;
}

.about-intro {
  background: #f8f9fa;
  border-radius: 8px;
  padding: 1rem 1.25rem;
  margin-bottom: 1.5rem;
  font-size: 0.9rem;
  line-height: 1.6;
  color: #495057;
}

.section-title {
  font-size: 0.95rem;
  font-weight: 600;
  color: #343a40;
  margin-bottom: 1rem;
  margin-top: 1.5rem;
  display: flex;
  align-items: center;
}

.section-title i {
  color: #6c757d;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
  margin-bottom: 1.5rem;
}

@media (max-width: 768px) {
  .feature-grid {
    grid-template-columns: 1fr;
  }
}

.feature-card {
  display: flex;
  gap: 0.875rem;
  padding: 1rem;
  background: white;
  border: 1px solid #e9ecef;
  border-radius: 8px;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.feature-card:hover {
  border-color: #dee2e6;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.feature-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 1.1rem;
  flex-shrink: 0;
}

.feature-content h6 {
  font-size: 0.875rem;
  font-weight: 600;
  color: #343a40;
  margin-bottom: 0.25rem;
}

.feature-content p {
  font-size: 0.8rem;
  color: #6c757d;
  margin-bottom: 0;
  line-height: 1.5;
}

.feature-content code {
  background-color: #f1f3f4;
  padding: 0.1rem 0.35rem;
  border-radius: 3px;
  font-size: 0.85em;
  color: #d63384;
}

/* Type List in About */
.type-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-bottom: 1.5rem;
}

.type-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.5rem 0.75rem;
  background: #fafbfc;
  border-radius: 6px;
  font-size: 0.85rem;
}

.type-item code {
  background-color: #f1f3f4;
  padding: 0.1rem 0.35rem;
  border-radius: 3px;
  font-size: 0.9em;
  color: #d63384;
}

.benefits-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-bottom: 1.5rem;
}

.benefit-item {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  font-size: 0.85rem;
  color: #495057;
  padding: 0.5rem 0;
}

.benefit-item i {
  flex-shrink: 0;
  margin-top: 0.1rem;
}

.about-note {
  display: flex;
  gap: 1rem;
  background: linear-gradient(135deg, #fff8e1 0%, #fffde7 100%);
  border: 1px solid #ffe082;
  border-radius: 8px;
  padding: 1rem;
}

.note-icon {
  color: #f9a825;
  font-size: 1.25rem;
  flex-shrink: 0;
}

.note-content {
  font-size: 0.85rem;
  line-height: 1.6;
  color: #5d4037;
}

.note-content strong {
  color: #4e342e;
}

/* Flag Description */
.flag-description {
  font-size: 0.75rem;
  color: #868e96;
  margin: 0;
  line-height: 1.4;
  max-width: 380px;
}

/* Expand Button */
.flag-name-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.expand-btn {
  background: none;
  border: none;
  padding: 0.125rem;
  color: #6c757d;
  cursor: pointer;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 4px;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.expand-btn:hover {
  background-color: rgba(94, 100, 255, 0.1);
  color: #5e64ff;
}

/* Offset description to align with flag name when expand button is present */
.flag-description.with-expand-offset {
  margin-left: calc(20px + 0.5rem);
}

/* Expanded Row Styling */
.expanded-row {
  background-color: rgba(94, 100, 255, 0.04);
}

.expanded-row td {
  border-bottom: none;
}

/* History Row */
.history-row {
  background-color: #f8f9fa;
}

.history-row td {
  padding: 0 !important;
  border-bottom: 1px solid #e9ecef;
}

.history-row:hover {
  background-color: #f8f9fa !important;
}

/* Change History Container */
.change-history {
  margin: 0.75rem 1rem 0.75rem calc(1rem + 20px + 0.5rem);
  padding: 0.75rem 1rem;
  background: white;
  border: 1px solid #e9ecef;
  border-radius: 6px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.change-history-header {
  font-size: 0.8rem;
  font-weight: 600;
  color: #495057;
  margin-bottom: 0.5rem;
  display: flex;
  align-items: center;
}

.change-history-header i {
  color: #6c757d;
}

.change-history-list {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.change-item {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.375rem 0.5rem;
  border-radius: 4px;
  font-size: 0.8rem;
}

.change-item.current-value {
  background-color: rgba(40, 167, 69, 0.08);
}

.change-timestamp {
  color: #6c757d;
  font-family: monospace;
  font-size: 0.75rem;
  min-width: 160px;
}

.change-value {
  font-weight: 500;
  color: #343a40;
}
</style>
