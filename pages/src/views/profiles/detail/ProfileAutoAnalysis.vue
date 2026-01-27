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
  <LoadingState v-if="loading" message="Loading auto analysis..." />

  <div v-else>
    <PageHeader
      title="Auto Analysis"
      description="Calculated Auto-analysis from the events"
      icon="bi-robot"
    >
      <!-- Summary Card with Chart -->
      <div class="summary-card mb-4" v-if="rules.length > 0">
        <div class="summary-card-body">
          <div class="row align-items-stretch">
            <!-- Donut Chart -->
            <div class="col-lg-5 col-md-6">
              <div class="chart-section">
                <h6 class="section-title"><i class="bi bi-pie-chart me-2"></i>Results Overview</h6>
                <apexchart
                  v-if="rules.length > 0"
                  type="donut"
                  :options="chartOptions"
                  :series="chartSeries"
                  height="200"
                />
              </div>
            </div>

            <!-- Analysis Results Legend -->
            <div class="col-lg-7 col-md-6">
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
                  <div v-if="rules.length === 0" class="no-results-msg">
                    No analysis rules executed
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Filter Bar -->
      <div class="filter-bar mb-3" v-if="rules.length > 0">
        <div class="filter-group">
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
            <div class="col-score" title="Severity score (0-100). Higher values indicate more significant findings.">Severity</div>
            <div class="col-actions">Details</div>
          </div>

          <!-- Table Rows -->
          <div
            v-for="(rule, index) in filteredAndSortedRules"
            :key="`${index}-${rule.rule}`"
            class="table-row-wrapper"
          >
            <div
              class="table-row"
              :class="[`severity-${rule.severity?.toLowerCase() || 'default'}`]"
              @click="toggleRow(index)"
            >
              <div class="col-status">
                <i
                  class="bi"
                  :class="[`bi-${getSeverityIcon(rule.severity)}`, getSeverityTextClass(rule.severity)]"
                ></i>
              </div>
              <div class="col-rule">{{ rule.rule }}</div>
              <div class="col-score">
                <div class="severity-bar-wrapper" v-if="parseScore(rule.score) != null">
                  <div class="severity-bar-track">
                    <div
                      class="severity-bar-fill"
                      :style="{ width: parseScore(rule.score) + '%', backgroundColor: getSeverityColor(rule.severity) }"
                    ></div>
                  </div>
                  <span class="severity-bar-value">{{ parseScore(rule.score) }}</span>
                </div>
                <span v-else class="no-score">-</span>
              </div>
              <div class="col-actions">
                <i
                  class="bi expand-icon"
                  :class="expandedRows.has(index) ? 'bi-chevron-up' : 'bi-chevron-down'"
                ></i>
              </div>
            </div>

            <!-- Expandable Row Details -->
            <transition name="expand">
              <div v-if="expandedRows.has(index)" class="row-details">
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
      <div v-else-if="rules.length > 0" class="empty-state">
        <i class="bi bi-funnel"></i>
        <h6>No Matching Rules</h6>
        <p>Try adjusting your filters or search query.</p>
      </div>

      <!-- Empty State -->
      <div v-if="rules.length === 0 && !loading" class="empty-state">
        <i class="bi bi-inbox"></i>
        <h6>No Analysis Results</h6>
        <p>No auto analysis rules were executed for this profile.</p>
      </div>
    </PageHeader>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import AutoAnalysisClient from '@/services/api/AutoAnalysisClient';
import AnalysisResult from '@/services/api/model/AnalysisResult';
import PageHeader from '@/components/layout/PageHeader.vue';
import LoadingState from '@/components/LoadingState.vue';

const route = useRoute();
const { workspaceId, projectId } = useNavigation();

// Data
const rules = ref<AnalysisResult[]>([]);
const loading = ref(true);

// Filter State
const severityFilter = ref('');
const searchQuery = ref('');

// UI State
const expandedRows = ref<Set<number>>(new Set());

// Severity priority for sorting (warnings first)
const severityOrder: Record<string, number> = {
  WARNING: 0,
  INFO: 1,
  OK: 2,
  NA: 3,
  IGNORE: 4
};

// Computed: Filtered and sorted rules
const filteredAndSortedRules = computed(() => {
  let result = [...rules.value];

  // Apply severity filter
  if (severityFilter.value) {
    result = result.filter(r => r.severity === severityFilter.value);
  }

  // Apply search filter
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase();
    result = result.filter(r =>
      r.rule.toLowerCase().includes(query) ||
      r.summary?.toLowerCase().includes(query)
    );
  }

  // Sort by severity (warnings first)
  return result.sort((a, b) =>
    (severityOrder[a.severity] ?? 99) - (severityOrder[b.severity] ?? 99)
  );
});

// Computed: Severity counts
const severityCounts = computed(() => ({
  ok: rules.value.filter(r => r.severity === 'OK').length,
  warning: rules.value.filter(r => r.severity === 'WARNING').length,
  info: rules.value.filter(r => r.severity === 'INFO').length,
  na: rules.value.filter(r => r.severity === 'NA' || r.severity === 'IGNORE').length
}));

// Computed: Chart options
const chartOptions = computed(() => ({
  chart: {
    type: 'donut' as const,
    fontFamily: 'inherit'
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
            formatter: () => `${severityCounts.value.ok}/${rules.value.length}`
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
  AutoAnalysisClient.rules(route.params.profileId as string)
    .then((data: AnalysisResult[]) => {
      rules.value = data;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
});

// Helper Functions
function toggleRow(index: number) {
  if (expandedRows.value.has(index)) {
    expandedRows.value.delete(index);
  } else {
    expandedRows.value.add(index);
  }
  // Trigger reactivity
  expandedRows.value = new Set(expandedRows.value);
}

function parseScore(score: string | null): number | null {
  if (score == null) return null;
  const parsed = parseFloat(score);
  return isNaN(parsed) ? null : Math.round(parsed * 10) / 10;
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
  grid-template-columns: 60px 1fr 160px 80px;
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
  grid-template-columns: 60px 1fr 160px 80px;
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
  align-items: center;
  justify-content: center;
}

.severity-bar-wrapper {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
}

.severity-bar-track {
  flex: 1;
  height: 8px;
  background: #e5e7eb;
  border-radius: 4px;
  overflow: hidden;
}

.severity-bar-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.3s ease;
}

.severity-bar-value {
  font-size: 0.7rem;
  font-weight: 600;
  color: #374151;
  min-width: 28px;
  text-align: right;
  flex-shrink: 0;
}

.no-score {
  color: #9ca3af;
  font-size: 0.75rem;
}

.col-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
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
  .legend-section {
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

  .col-score {
    order: 4;
    width: auto;
    justify-content: flex-start;
  }
}
</style>
