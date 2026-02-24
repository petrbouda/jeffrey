<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<template>
  <LoadingState v-if="loading" message="Loading guardian analysis..." />

  <div v-else>
    <PageHeader
      title="Guardian Analysis"
      description="Automated analysis and recommendations for your profile based on traversing Flamegraphs"
      icon="bi-shield-check"
    >
      <!-- Summary Card with Chart and Prerequisites -->
      <div class="summary-card mb-4" v-if="analysisRulesFlat.length > 0 || prerequisites.length > 0">
        <div class="summary-card-body">
          <div class="row align-items-stretch">
            <!-- Donut Chart -->
            <div class="col-lg-4 col-md-5">
              <div class="chart-section">
                <h6 class="section-title"><i class="bi bi-pie-chart me-2"></i>Results Overview</h6>
                <apexchart
                  v-if="analysisRulesFlat.length > 0"
                  type="donut"
                  :options="chartOptions"
                  :series="chartSeries"
                  height="200"
                />
                <div v-else class="no-analysis-data">
                  <i class="bi bi-slash-circle"></i>
                  <span>No analysis data</span>
                </div>
              </div>
            </div>

            <!-- Analysis Results Legend -->
            <div class="col-lg-4 col-md-4">
              <div class="legend-section">
                <h6 class="section-title">Analysis Results</h6>
                <div class="severity-legend">
                  <div class="legend-item" v-if="severityCounts.ok > 0">
                    <i class="bi bi-check-circle-fill text-success"></i>
                    <span class="legend-label">Passed</span>
                    <span class="legend-value">{{ severityCounts.ok }}</span>
                  </div>
                  <div class="legend-item" v-if="severityCounts.warning > 0">
                    <i class="bi bi-exclamation-triangle-fill text-danger"></i>
                    <span class="legend-label">Warnings</span>
                    <span class="legend-value">{{ severityCounts.warning }}</span>
                  </div>
                  <div class="legend-item" v-if="severityCounts.info > 0">
                    <i class="bi bi-info-circle-fill text-primary"></i>
                    <span class="legend-label">Information</span>
                    <span class="legend-value">{{ severityCounts.info }}</span>
                  </div>
                  <div class="legend-item" v-if="severityCounts.na > 0">
                    <i class="bi bi-slash-circle-fill text-secondary"></i>
                    <span class="legend-label">Skipped</span>
                    <span class="legend-value">{{ severityCounts.na }}</span>
                  </div>
                  <div v-if="analysisRulesFlat.length === 0" class="no-results-msg">
                    No analysis rules executed
                  </div>
                </div>
              </div>
            </div>

            <!-- Data Quality / Prerequisites -->
            <div class="col-lg-4 col-md-3">
              <div class="prerequisites-section">
                <h6 class="section-title">Data Quality</h6>
                <div class="prerequisites-list">
                  <div
                    v-for="prereq in prerequisites"
                    :key="prereq.rule"
                    class="prereq-item"
                    :class="prereq.severity === 'OK' ? 'prereq-ok' : 'prereq-warning'"
                  >
                    <div class="prereq-header">
                      <i
                        class="bi"
                        :class="prereq.severity === 'OK' ? 'bi-check-circle-fill text-success' : 'bi-exclamation-triangle-fill text-warning'"
                      ></i>
                      <span class="prereq-name">{{ prereq.rule }}</span>
                    </div>
                    <div class="prereq-value" v-if="prereq.score">
                      {{ prereq.score }}
                    </div>
                  </div>
                  <div v-if="prerequisites.length === 0" class="no-prereq-msg">
                    No prerequisites data
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Filter Bar -->
      <div class="filter-bar mb-3" v-if="analysisRulesFlat.length > 0">
        <div class="filter-group">
          <select v-model="groupFilter" class="form-select form-select-sm">
            <option value="">All Groups</option>
            <option v-for="g in groups" :key="g" :value="g">{{ g }}</option>
          </select>
          <select v-model="categoryFilter" class="form-select form-select-sm">
            <option value="">All Categories</option>
            <option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</option>
          </select>
          <select v-model="severityFilter" class="form-select form-select-sm">
            <option value="">All Severities</option>
            <option value="WARNING">Warnings</option>
            <option value="INFO">Information</option>
            <option value="OK">Passed</option>
          </select>
        </div>
        <div class="search-group">
          <i class="bi bi-search"></i>
          <input
            v-model="searchQuery"
            type="text"
            class="form-control form-control-sm"
            placeholder="Search rules..."
          />
        </div>
      </div>

      <!-- Unified Rules Table -->
      <div class="rules-table-container" v-if="filteredAndSortedRules.length > 0">
        <div class="rules-table">
          <!-- Table Header -->
          <div class="table-header">
            <div class="col-status">Status</div>
            <div class="col-rule">Rule</div>
            <div class="col-group">Group</div>
            <div class="col-category">Category</div>
            <div class="col-score">Score</div>
            <div class="col-actions">Actions</div>
          </div>

          <!-- Table Rows -->
          <div
            v-for="rule in filteredAndSortedRules"
            :key="rule.id"
            class="table-row-wrapper"
          >
            <div
              class="table-row"
              :class="[`severity-${rule.severity?.toLowerCase() || 'default'}`]"
              @click="toggleRow(rule.id)"
            >
              <div class="col-status">
                <i
                  class="bi"
                  :class="[`bi-${getSeverityIcon(rule.severity)}`, getSeverityTextClass(rule.severity)]"
                ></i>
              </div>
              <div class="col-rule">{{ rule.rule }}</div>
              <div class="col-group">
                <span class="group-badge" v-if="rule.group">{{ rule.group }}</span>
                <span v-else class="no-score">-</span>
              </div>
              <div class="col-category">
                <span class="category-badge">{{ rule.category }}</span>
              </div>
              <div class="col-score">
                <div class="score-wrapper" v-if="rule.score">
                  <span class="score-text">{{ rule.score }}</span>
                  <div v-if="isPercentageScore(rule.score)" class="mini-progress">
                    <div
                      class="mini-progress-bar"
                      :style="{ width: rule.score, backgroundColor: getSeverityColor(rule.severity) }"
                    ></div>
                  </div>
                </div>
                <span v-else class="no-score">-</span>
              </div>
              <div class="col-actions">
                <button
                  v-if="rule.visualization"
                  class="flame-btn"
                  @click.stop="openFlamegraph(rule)"
                  title="View Flamegraph"
                >
                  <i class="bi bi-fire"></i>
                </button>
                <i
                  class="bi expand-icon"
                  :class="expandedRows.has(rule.id) ? 'bi-chevron-up' : 'bi-chevron-down'"
                ></i>
              </div>
            </div>

            <!-- Expandable Row Details -->
            <transition name="expand">
              <div v-if="expandedRows.has(rule.id)" class="row-details">
                <div class="details-grid">
                  <div v-if="rule.summary" class="detail-item">
                    <span class="detail-label">Summary</span>
                    <p class="detail-text" v-html="rule.summary"></p>
                  </div>
                  <div v-if="rule.explanation" class="detail-item">
                    <span class="detail-label">Explanation</span>
                    <p class="detail-text" v-html="rule.explanation"></p>
                  </div>
                  <div v-if="rule.solution" class="detail-item">
                    <span class="detail-label">Solution</span>
                    <p class="detail-text" v-html="rule.solution"></p>
                  </div>
                </div>
              </div>
            </transition>
          </div>
        </div>
      </div>

      <!-- No Results After Filter -->
      <div v-else-if="analysisRulesFlat.length > 0" class="empty-state">
        <i class="bi bi-funnel"></i>
        <h6>No Matching Rules</h6>
        <p>Try adjusting your filters or search query.</p>
      </div>

      <!-- Empty State -->
      <div v-if="guards.length === 0 && !loading" class="empty-state">
        <i class="bi bi-inbox"></i>
        <h6>No Analysis Results</h6>
        <p>No guardian rules were executed for this profile.</p>
      </div>

      <!-- Flamegraph Modal -->
      <div
        class="modal fade"
        id="flamegraphModal"
        tabindex="-1"
        aria-labelledby="flamegraphModalLabel"
        aria-hidden="true"
      >
        <div class="modal-dialog modal-lg" style="width: 95vw; max-width: 95%">
          <div class="modal-content">
            <div class="modal-header">
              <button type="button" class="btn-close" @click="closeFlamegraphModal" aria-label="Close" />
            </div>
            <div
              id="scrollable-wrapper"
              class="modal-body pr-2 pl-2"
              v-if="showFlamegraphDialog && activeVisualization"
            >
              <SearchBarComponent :graph-updater="graphUpdater" :with-timeseries="true" />
              <TimeSeriesChart
                :graph-updater="graphUpdater"
                :primary-axis-type="
                  TimeseriesEventAxeFormatter.resolveAxisFormatter(
                    activeVisualization.useWeight,
                    activeVisualization.eventType
                  )
                "
                :visible-minutes="60"
                :zoom-enabled="true"
                time-unit="seconds"
              />
              <FlamegraphComponent
                :with-timeseries="true"
                :use-weight="activeVisualization.useWeight"
                :use-guardian="activeVisualization"
                scrollableWrapperClass="scrollable-wrapper"
                :flamegraph-tooltip="flamegraphTooltip"
                :graph-updater="graphUpdater"
                @loaded="scrollToTop"
              />
            </div>
          </div>
        </div>
      </div>
    </PageHeader>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import GuardianClient from '@/services/api/GuardianClient';
import GuardResponse from '@/services/api/model/GuardResponse';
import GuardAnalysisResult from '@/services/api/model/GuardAnalysisResult';
import GuardVisualization from '@/services/api/model/GuardVisualization';
import GuardianFlamegraphClient from '@/services/api/GuardianFlamegraphClient';
import FlamegraphComponent from '@/components/FlamegraphComponent.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import SearchBarComponent from '@/components/SearchBarComponent.vue';
import FlamegraphTooltip from '@/services/flamegraphs/tooltips/FlamegraphTooltip';
import FlamegraphTooltipFactory from '@/services/flamegraphs/tooltips/FlamegraphTooltipFactory';
import GraphUpdater from '@/services/flamegraphs/updater/GraphUpdater';
import FullGraphUpdater from '@/services/flamegraphs/updater/FullGraphUpdater';
import TimeseriesEventAxeFormatter from '@/services/timeseries/TimeseriesEventAxeFormatter';
import PageHeader from '@/components/layout/PageHeader.vue';
import LoadingState from '@/components/LoadingState.vue';
import * as bootstrap from 'bootstrap';

// Constants
const PREREQUISITES_CATEGORY = 'Prerequisites';

// Types
interface FlatRule extends GuardAnalysisResult {
  id: string;
  category: string;
}

const route = useRoute();
const { workspaceId, projectId } = useNavigation();

// Data
const guards = ref<GuardResponse[]>([]);
const loading = ref(true);

// Filter State
const groupFilter = ref('');
const categoryFilter = ref('');
const severityFilter = ref('');
const searchQuery = ref('');

// UI State
const expandedRows = ref<Set<string>>(new Set());

// Flamegraph State
const showFlamegraphDialog = ref(false);
const activeVisualization = ref<GuardVisualization | null>(null);
let flamegraphTooltip: FlamegraphTooltip;
let graphUpdater: GraphUpdater;
let modalInstance: bootstrap.Modal | null = null;

// Severity priority for sorting (warnings first)
const severityOrder: Record<string, number> = {
  WARNING: 0,
  INFO: 1,
  OK: 2,
  NA: 3,
  IGNORE: 4
};

// Computed: Extract prerequisites (data quality checks)
const prerequisites = computed(() => {
  const prereqCategory = guards.value.find(g => g.category === PREREQUISITES_CATEGORY);
  return prereqCategory?.results || [];
});

// Computed: All rules EXCLUDING prerequisites
const allRulesFlat = computed<FlatRule[]>(() =>
  guards.value
    .filter(g => g.category !== PREREQUISITES_CATEGORY)
    .flatMap((g, gIdx) =>
      g.results.map((r, rIdx) => ({
        ...r,
        category: g.category,
        id: `${gIdx}-${rIdx}-${r.rule}`
      }))
    )
);

// Alias for template readability
const analysisRulesFlat = allRulesFlat;

// Computed: Unique categories for filter dropdown (excluding Prerequisites)
const categories = computed(() =>
  [...new Set(guards.value
    .filter(g => g.category !== PREREQUISITES_CATEGORY)
    .map(g => g.category)
  )].sort()
);

// Computed: Unique groups for filter dropdown
const groups = computed(() =>
  [...new Set(allRulesFlat.value
    .map(r => r.group)
    .filter((g): g is string => g != null)
  )].sort()
);

// Computed: Filtered and sorted rules
const filteredAndSortedRules = computed(() => {
  let rules = [...allRulesFlat.value];

  // Apply group filter
  if (groupFilter.value) {
    rules = rules.filter(r => r.group === groupFilter.value);
  }

  // Apply category filter
  if (categoryFilter.value) {
    rules = rules.filter(r => r.category === categoryFilter.value);
  }

  // Apply severity filter
  if (severityFilter.value) {
    rules = rules.filter(r => r.severity === severityFilter.value);
  }

  // Apply search filter
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase();
    rules = rules.filter(r =>
      r.rule.toLowerCase().includes(query) ||
      r.category.toLowerCase().includes(query) ||
      r.group?.toLowerCase().includes(query) ||
      r.summary?.toLowerCase().includes(query)
    );
  }

  // Sort by severity (warnings first)
  return rules.sort((a, b) =>
    (severityOrder[a.severity] ?? 99) - (severityOrder[b.severity] ?? 99)
  );
});

// Computed: Severity counts (excluding prerequisites)
const severityCounts = computed(() => ({
  ok: allRulesFlat.value.filter(r => r.severity === 'OK').length,
  warning: allRulesFlat.value.filter(r => r.severity === 'WARNING').length,
  info: allRulesFlat.value.filter(r => r.severity === 'INFO').length,
  na: allRulesFlat.value.filter(r => r.severity === 'NA' || r.severity === 'IGNORE').length
}));

// Computed: Chart options
const chartOptions = computed(() => ({
  chart: {
    type: 'donut' as const,
    fontFamily: 'inherit',
    animations: {
      enabled: false
    }
  },
  labels: ['Passed', 'Warnings', 'Info', 'N/A'],
  colors: ['#28a745', '#dc3545', '#0d6efd', '#6c757d'],
  plotOptions: {
    pie: {
      donut: {
        size: '65%',
        labels: {
          show: true,
          name: {
            show: true,
            fontSize: '12px',
            fontWeight: 600
          },
          value: {
            show: true,
            fontSize: '18px',
            fontWeight: 700
          },
          total: {
            show: true,
            label: 'Passed',
            fontSize: '11px',
            fontWeight: 500,
            color: '#6c757d',
            formatter: () => `${severityCounts.value.ok}/${allRulesFlat.value.length}`
          }
        }
      }
    }
  },
  dataLabels: {
    enabled: false
  },
  legend: {
    show: false
  },
  stroke: {
    width: 2,
    colors: ['#fff']
  },
  tooltip: {
    enabled: true,
    y: {
      formatter: (val: number) => `${val} rules`
    }
  }
}));

// Computed: Chart series
const chartSeries = computed(() => [
  severityCounts.value.ok,
  severityCounts.value.warning,
  severityCounts.value.info,
  severityCounts.value.na
]);

// Lifecycle
onMounted(() => {
  GuardianClient.list(route.params.profileId as string)
    .then((data: GuardResponse[]) => {
      guards.value = data;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });

  // Initialize flamegraph modal
  nextTick(() => {
    const modalEl = document.getElementById('flamegraphModal');
    if (modalEl) {
      modalEl.addEventListener('hidden.bs.modal', () => {
        showFlamegraphDialog.value = false;
      });
    }
  });
});

onUnmounted(() => {
  if (modalInstance) {
    modalInstance.dispose();
    modalInstance = null;
  }
});

// Watch for flamegraph modal visibility
watch(showFlamegraphDialog, isVisible => {
  if (isVisible) {
    if (!modalInstance) {
      const modalEl = document.getElementById('flamegraphModal');
      if (modalEl) {
        modalInstance = new bootstrap.Modal(modalEl);
      }
    }
    if (modalInstance) {
      modalInstance.show();
    }
  } else {
    if (modalInstance) {
      modalInstance.hide();
    }
  }
});

// Helper Functions
function toggleRow(ruleId: string) {
  if (expandedRows.value.has(ruleId)) {
    expandedRows.value.delete(ruleId);
  } else {
    expandedRows.value.add(ruleId);
  }
  // Trigger reactivity
  expandedRows.value = new Set(expandedRows.value);
}

function isPercentageScore(score: string | null): boolean {
  return score != null && typeof score === 'string' && score.includes('%');
}

function getSeverityIcon(severity: string): string {
  switch (severity) {
    case 'OK': return 'check-circle-fill';
    case 'WARNING': return 'exclamation-triangle-fill';
    case 'INFO': return 'info-circle-fill';
    case 'NA': return 'slash-circle-fill';
    case 'IGNORE': return 'eye-slash-fill';
    default: return 'question-circle-fill';
  }
}

function getSeverityTextClass(severity: string): string {
  switch (severity) {
    case 'OK': return 'text-success';
    case 'WARNING': return 'text-danger';
    case 'INFO': return 'text-primary';
    case 'NA':
    case 'IGNORE': return 'text-secondary';
    default: return 'text-muted';
  }
}

function getSeverityColor(severity: string): string {
  switch (severity) {
    case 'OK': return '#198754';
    case 'WARNING': return '#dc3545';
    case 'INFO': return '#0d6efd';
    case 'NA':
    case 'IGNORE': return '#6c757d';
    default: return '#6c757d';
  }
}

// Flamegraph Functions
function openFlamegraph(rule: FlatRule) {
  if (rule.visualization) {
    activeVisualization.value = rule.visualization;

    const flamegraphClient = new GuardianFlamegraphClient(
      rule.visualization.primaryProfileId,
      rule.visualization.eventType,
      rule.visualization.useWeight,
      rule.visualization.markers
    );

    graphUpdater = new FullGraphUpdater(flamegraphClient, false);
    graphUpdater.setTimeseriesSearchEnabled(false);
    flamegraphTooltip = FlamegraphTooltipFactory.create(
      rule.visualization.eventType,
      rule.visualization.useWeight,
      false
    );

    // Delayed initialization to ensure modal is rendered
    setTimeout(() => {
      graphUpdater.initialize();
    }, 200);

    showFlamegraphDialog.value = true;
  }
}

function closeFlamegraphModal() {
  if (modalInstance) {
    modalInstance.hide();
  }
  showFlamegraphDialog.value = false;
}

function scrollToTop() {
  const wrapper = document.querySelector('.scrollable-wrapper');
  if (wrapper) {
    wrapper.scrollTop = 0;
  }
}
</script>

<style scoped>
/* Summary Card */
.summary-card {
  background: #fff;
  border-radius: 0.75rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  overflow: hidden;
}

.summary-card-body {
  padding: 1.25rem;
}

.section-title {
  font-size: 0.8rem;
  font-weight: 600;
  color: #4b5563;
  margin-bottom: 1rem;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

/* Chart Section */
.chart-section {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.no-analysis-data {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: #9ca3af;
  gap: 0.5rem;
}

.no-analysis-data i {
  font-size: 2rem;
  opacity: 0.5;
}

/* Legend Section */
.legend-section {
  height: 100%;
  border-left: 1px solid #f0f0f0;
  padding-left: 1.25rem;
}

.severity-legend {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.legend-item i {
  font-size: 0.9rem;
  flex-shrink: 0;
}

.legend-label {
  font-size: 0.85rem;
  font-weight: 500;
  color: #374151;
  flex: 1;
}

.legend-value {
  font-size: 0.85rem;
  font-weight: 600;
  color: #1f2937;
  background: #f3f4f6;
  padding: 0.15rem 0.5rem;
  border-radius: 0.25rem;
  min-width: 24px;
  text-align: center;
}

.no-results-msg {
  font-size: 0.8rem;
  color: #9ca3af;
  font-style: italic;
}

/* Prerequisites Section */
.prerequisites-section {
  height: 100%;
  border-left: 1px solid #f0f0f0;
  padding-left: 1.25rem;
}

.prerequisites-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.prereq-item {
  padding: 0.6rem 0.75rem;
  border-radius: 0.5rem;
  background: #f9fafb;
  border-left: 3px solid;
}

.prereq-item.prereq-ok {
  border-left-color: #28a745;
  background: linear-gradient(135deg, #f0fdf4, #f9fafb);
}

.prereq-item.prereq-warning {
  border-left-color: #f59e0b;
  background: linear-gradient(135deg, #fffbeb, #f9fafb);
}

.prereq-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.prereq-header i {
  font-size: 0.85rem;
}

.prereq-name {
  font-size: 0.8rem;
  font-weight: 600;
  color: #374151;
}

.prereq-value {
  font-size: 0.75rem;
  color: #6b7280;
  margin-top: 0.25rem;
  padding-left: 1.35rem;
}

.no-prereq-msg {
  font-size: 0.8rem;
  color: #9ca3af;
  font-style: italic;
}

/* Filter Bar */
.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  background: #fff;
  padding: 0.75rem 1rem;
  border-radius: 0.5rem;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.filter-group {
  display: flex;
  gap: 0.5rem;
}

.filter-group .form-select {
  width: auto;
  min-width: 140px;
  font-size: 0.8rem;
}

.search-group {
  position: relative;
  flex: 1;
  max-width: 280px;
}

.search-group i {
  position: absolute;
  left: 0.75rem;
  top: 50%;
  transform: translateY(-50%);
  color: #9ca3af;
  font-size: 0.8rem;
}

.search-group .form-control {
  padding-left: 2rem;
  font-size: 0.8rem;
}

/* Rules Table */
.rules-table-container {
  background: #fff;
  border-radius: 0.75rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  overflow: hidden;
}

.rules-table {
  width: 100%;
}

/* Table Header */
.table-header {
  display: grid;
  grid-template-columns: 60px 1fr 140px 150px 120px 80px;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  background: linear-gradient(135deg, #f8fafc, #f1f5f9);
  border-bottom: 1px solid #e5e7eb;
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: #6b7280;
}

/* Header columns should inherit header font styles */
.table-header > div {
  font-size: inherit;
  font-weight: inherit;
  color: inherit;
}

/* Table Row */
.table-row {
  display: grid;
  grid-template-columns: 60px 1fr 140px 150px 120px 80px;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  align-items: center;
  cursor: pointer;
  border-bottom: 1px solid #f3f4f6;
  transition: background-color 0.15s ease;
}

.table-row:hover {
  background-color: #f9fafb;
}

.table-row.severity-warning {
  border-left: 3px solid #dc3545;
}

.table-row.severity-info {
  border-left: 3px solid #0d6efd;
}

.table-row.severity-ok {
  border-left: 3px solid #28a745;
}

.table-row.severity-na,
.table-row.severity-ignore {
  border-left: 3px solid #6c757d;
}

/* Column Styles */
.col-status {
  display: flex;
  justify-content: center;
}

.col-status i {
  font-size: 1rem;
}

.col-group {
  display: flex;
  justify-content: flex-start;
  overflow: hidden;
}

.group-badge {
  display: inline-block;
  font-size: 0.7rem;
  font-weight: 500;
  color: #1e40af;
  background: #dbeafe;
  padding: 0.2rem 0.5rem;
  border-radius: 0.25rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

.col-category {
  display: flex;
  justify-content: flex-start;
  overflow: hidden;
}

.category-badge {
  display: inline-block;
  font-size: 0.7rem;
  font-weight: 500;
  color: #4b5563;
  background: #e5e7eb;
  padding: 0.2rem 0.5rem;
  border-radius: 0.25rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

.col-rule {
  font-size: 0.85rem;
  font-weight: 500;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.col-score {
  display: flex;
  justify-content: center;
}

.score-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
  width: 100%;
}

.score-text {
  font-size: 0.75rem;
  font-weight: 600;
  color: #374151;
}

.no-score {
  color: #9ca3af;
  font-size: 0.75rem;
}

.mini-progress {
  width: 100%;
  max-width: 80px;
  height: 4px;
  background: #e5e7eb;
  border-radius: 2px;
  overflow: hidden;
}

.mini-progress-bar {
  height: 100%;
  border-radius: 2px;
  transition: width 0.3s ease;
}

.col-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
}

.flame-btn {
  width: 1.6rem;
  height: 1.6rem;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  font-size: 0.8rem;
  transition: all 0.15s ease-in-out;
  background-color: #fff3e0;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  padding: 0;
  color: #fd7e14;
}

.flame-btn:hover {
  background-color: #fd7e14;
  color: white;
  box-shadow: 0 2px 8px rgba(253, 126, 20, 0.25);
}

.expand-icon {
  font-size: 0.8rem;
  color: #9ca3af;
  transition: transform 0.2s ease;
}

/* Row Details */
.row-details {
  background: #f9fafb;
  padding: 1rem 1.5rem;
  border-bottom: 1px solid #e5e7eb;
}

.details-grid {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.detail-label {
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: #6b7280;
}

.detail-text {
  font-size: 0.85rem;
  color: #374151;
  line-height: 1.5;
  margin: 0;
}

/* Empty State */
.empty-state {
  text-align: center;
  padding: 3rem 1rem;
  color: #6b7280;
}

.empty-state i {
  font-size: 3rem;
  margin-bottom: 1rem;
  opacity: 0.5;
}

.empty-state h6 {
  margin-bottom: 0.5rem;
  color: #374151;
}

.empty-state p {
  font-size: 0.9rem;
}

/* Transitions */
.expand-enter-active,
.expand-leave-active {
  transition: all 0.2s ease;
  overflow: hidden;
}

.expand-enter-from,
.expand-leave-to {
  opacity: 0;
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
}

.expand-enter-to,
.expand-leave-from {
  opacity: 1;
  max-height: 500px;
}

/* Responsive */
@media (max-width: 992px) {
  .table-header,
  .table-row {
    grid-template-columns: 50px 1fr 110px 120px 100px 70px;
  }

  .legend-section,
  .prerequisites-section {
    border-left: none;
    border-top: 1px solid #f0f0f0;
    padding-left: 0;
    padding-top: 1rem;
    margin-top: 1rem;
  }
}

@media (max-width: 768px) {
  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-group {
    flex-wrap: wrap;
  }

  .search-group {
    max-width: none;
  }

  .table-header {
    display: none;
  }

  .table-row {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
    padding: 0.75rem;
  }

  .col-status {
    order: 1;
    width: auto;
  }

  .col-rule {
    order: 2;
    flex: 1;
    white-space: normal;
  }

  .col-actions {
    order: 3;
    width: auto;
  }

  .col-group {
    order: 4;
    width: auto;
  }

  .col-category {
    order: 5;
    width: auto;
  }

  .col-score {
    order: 6;
    width: auto;
    justify-content: flex-start;
  }
}
</style>
