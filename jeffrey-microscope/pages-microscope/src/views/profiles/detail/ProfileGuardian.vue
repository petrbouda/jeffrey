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
      <!-- Summary Panel -->
      <DualPanel
        v-if="analysisRulesFlat.length > 0 || prerequisites.length > 0"
        class="mb-4"
        left-title="Results Overview"
        right-title="Data Quality"
      >
        <template #left>
          <DonutWithLegend
            v-if="analysisRulesFlat.length > 0"
            :data="chartData"
            :tooltip-formatter="(val: number) => val + ' rules'"
          />
          <div v-else class="no-analysis-data">
            <i class="bi bi-slash-circle"></i>
            <span>No analysis data</span>
          </div>
        </template>
        <template #right>
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
                  :class="
                    prereq.severity === 'OK'
                      ? 'bi-check-circle-fill text-success'
                      : 'bi-exclamation-triangle-fill text-warning'
                  "
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
        </template>
      </DualPanel>

      <!-- Rules Table -->
      <DataTable v-if="filteredAndSortedRules.length > 0" table-class="guardian-table">
        <template #toolbar>
          <TableToolbar v-model="searchQuery" search-placeholder="Search rules...">
            <span class="toolbar-count">{{ filteredAndSortedRules.length }} rules</span>
            <Badge v-if="severityCounts.warning > 0" :value="severityCounts.warning + ' warnings'" variant="danger" size="xs" />
            <template #filters>
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
            </template>
          </TableToolbar>
        </template>
        <thead>
          <tr>
            <th style="width: 60px">Status</th>
            <th>Rule</th>
            <th style="width: 140px">Group</th>
            <th style="width: 150px">Category</th>
            <th style="width: 120px">Score</th>
            <th style="width: 80px">Actions</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="rule in filteredAndSortedRules" :key="rule.id">
            <tr
              class="rule-row"
              :class="[`severity-${rule.severity?.toLowerCase() || 'default'}`]"
              @click="toggleRow(rule.id)"
            >
              <td class="text-center">
                <i
                  class="bi"
                  :class="[
                    `bi-${getSeverityIcon(rule.severity)}`,
                    getSeverityTextClass(rule.severity)
                  ]"
                ></i>
              </td>
              <td>{{ rule.rule }}</td>
              <td>
                <span class="group-badge" v-if="rule.group">{{ rule.group }}</span>
                <span v-else class="no-score">-</span>
              </td>
              <td>
                <span class="category-badge">{{ rule.category }}</span>
              </td>
              <td>
                <div class="score-wrapper" v-if="rule.score">
                  <span class="score-text">{{ rule.score }}</span>
                  <div v-if="isPercentageScore(rule.score)" class="mini-progress">
                    <div
                      class="mini-progress-bar"
                      :style="{
                        width: rule.score,
                        backgroundColor: getSeverityColor(rule.severity)
                      }"
                    ></div>
                  </div>
                </div>
                <span v-else class="no-score">-</span>
              </td>
              <td class="text-end">
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
              </td>
            </tr>

            <!-- Expandable Row Details -->
            <tr v-if="expandedRows.has(rule.id)" class="details-row">
              <td colspan="6" class="p-0">
                <div class="row-details">
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
              </td>
            </tr>
          </template>
        </tbody>
      </DataTable>

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
      <GenericModal
        modal-id="flamegraphModal"
        :show="showFlamegraphDialog"
        size="fullscreen"
        :show-footer="false"
        @update:show="showFlamegraphDialog = $event"
      >
        <template #header>
          <button
            type="button"
            class="btn-close"
            @click="showFlamegraphDialog = false"
            aria-label="Close"
          />
        </template>
        <div
          id="scrollable-wrapper"
          class="px-2"
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
      </GenericModal>
    </PageHeader>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

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
import GenericModal from '@/components/GenericModal.vue';
import DualPanel from '@/components/DualPanel.vue';
import DonutWithLegend from '@/components/DonutWithLegend.vue';
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import Badge from '@/components/Badge.vue';
import type { DonutChartData } from '@/components/DonutWithLegend.vue';

// Constants
const PREREQUISITES_CATEGORY = 'Prerequisites';

// Types
interface FlatRule extends GuardAnalysisResult {
  id: string;
  category: string;
}

const route = useRoute();

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
  [
    ...new Set(guards.value.filter(g => g.category !== PREREQUISITES_CATEGORY).map(g => g.category))
  ].sort()
);

// Computed: Unique groups for filter dropdown
const groups = computed(() =>
  [...new Set(allRulesFlat.value.map(r => r.group).filter((g): g is string => g != null))].sort()
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
    rules = rules.filter(
      r =>
        r.rule.toLowerCase().includes(query) ||
        r.category.toLowerCase().includes(query) ||
        r.group?.toLowerCase().includes(query) ||
        r.summary?.toLowerCase().includes(query)
    );
  }

  // Sort by severity (warnings first)
  return rules.sort(
    (a, b) => (severityOrder[a.severity] ?? 99) - (severityOrder[b.severity] ?? 99)
  );
});

// Computed: Severity counts (excluding prerequisites)
const severityCounts = computed(() => ({
  ok: allRulesFlat.value.filter(r => r.severity === 'OK').length,
  warning: allRulesFlat.value.filter(r => r.severity === 'WARNING').length,
  info: allRulesFlat.value.filter(r => r.severity === 'INFO').length,
  na: allRulesFlat.value.filter(r => r.severity === 'NA' || r.severity === 'IGNORE').length
}));

// Computed: Chart data for DonutWithLegend
const chartData = computed<DonutChartData>(() => {
  const items = [
    { label: 'Passed', count: severityCounts.value.ok, color: '#28a745' },
    { label: 'Warnings', count: severityCounts.value.warning, color: '#dc3545' },
    { label: 'Info', count: severityCounts.value.info, color: '#0d6efd' },
    { label: 'N/A', count: severityCounts.value.na, color: '#6c757d' }
  ].filter(i => i.count > 0);

  return {
    series: items.map(i => i.count),
    labels: items.map(i => i.label),
    colors: items.map(i => i.color),
    totalLabel: 'Passed',
    totalValue: `${severityCounts.value.ok}/${allRulesFlat.value.length}`,
    legendItems: items.map(i => ({
      color: i.color,
      label: i.label,
      value: i.count.toString()
    }))
  };
});

// Lifecycle
onMounted(() => {
  new GuardianClient(route.params.profileId as string)
    .list()
    .then((data: GuardResponse[]) => {
      guards.value = data;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
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
    case 'OK':
      return 'check-circle-fill';
    case 'WARNING':
      return 'exclamation-triangle-fill';
    case 'INFO':
      return 'info-circle-fill';
    case 'NA':
      return 'slash-circle-fill';
    case 'IGNORE':
      return 'eye-slash-fill';
    default:
      return 'question-circle-fill';
  }
}

function getSeverityTextClass(severity: string): string {
  switch (severity) {
    case 'OK':
      return 'text-success';
    case 'WARNING':
      return 'text-danger';
    case 'INFO':
      return 'text-primary';
    case 'NA':
    case 'IGNORE':
      return 'text-secondary';
    default:
      return 'text-muted';
  }
}

function getSeverityColor(severity: string): string {
  switch (severity) {
    case 'OK':
      return '#198754';
    case 'WARNING':
      return '#dc3545';
    case 'INFO':
      return '#0d6efd';
    case 'NA':
    case 'IGNORE':
      return '#6c757d';
    default:
      return '#6c757d';
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

function scrollToTop() {
  const wrapper = document.querySelector('.scrollable-wrapper');
  if (wrapper) {
    wrapper.scrollTop = 0;
  }
}
</script>

<style scoped>
.no-analysis-data {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: var(--color-text-light);
  gap: 0.5rem;
}

.no-analysis-data i {
  font-size: 2rem;
  opacity: 0.5;
}

/* Prerequisites */
.prerequisites-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.prereq-item {
  padding: 0.6rem 0.75rem;
  border-radius: 0.5rem;
  background: var(--color-light);
  border-left: 3px solid;
}

.prereq-item.prereq-ok {
  border-left-color: var(--color-success);
  background: linear-gradient(
    135deg,
    var(--color-success-bg-light),
    var(--color-neutral-bg)
  );
}

.prereq-item.prereq-warning {
  border-left-color: var(--color-amber);
  background: linear-gradient(135deg, var(--color-amber-bg), var(--color-neutral-bg));
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
  color: var(--color-text);
}

.prereq-value {
  font-size: 0.75rem;
  color: var(--color-text-muted);
  margin-top: 0.25rem;
  padding-left: 1.35rem;
}

.no-prereq-msg {
  font-size: 0.8rem;
  color: var(--color-text-light);
  font-style: italic;
}

/* Rule Rows */
.rule-row {
  cursor: pointer;
}

.rule-row.severity-warning {
  border-left: 3px solid var(--color-danger);
}

.rule-row.severity-info {
  border-left: 3px solid var(--color-accent-blue);
}

.rule-row.severity-ok {
  border-left: 3px solid var(--color-success);
}

.rule-row.severity-na,
.rule-row.severity-ignore {
  border-left: 3px solid var(--color-text-muted);
}

/* Toolbar */
.toolbar-count {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--color-text);
}

/* Badges */
.group-badge {
  display: inline-block;
  font-size: 0.7rem;
  font-weight: 500;
  color: var(--color-blue-text);
  background: var(--color-blue-bg);
  padding: 0.2rem 0.5rem;
  border-radius: 0.25rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

.category-badge {
  display: inline-block;
  font-size: 0.7rem;
  font-weight: 500;
  color: var(--color-text);
  background: var(--color-border);
  padding: 0.2rem 0.5rem;
  border-radius: 0.25rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
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
  color: var(--color-text);
}

.no-score {
  color: var(--color-text-light);
  font-size: 0.75rem;
}

.mini-progress {
  width: 100%;
  max-width: 80px;
  height: 4px;
  background: var(--color-border);
  border-radius: 2px;
  overflow: hidden;
}

.mini-progress-bar {
  height: 100%;
  border-radius: 2px;
  transition: width 0.3s ease;
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
  background-color: var(--color-orange-bg);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  padding: 0;
  color: var(--color-orange);
}

.flame-btn:hover {
  background-color: var(--color-orange);
  color: white;
  box-shadow: 0 2px 8px rgba(253, 126, 20, 0.25);
}

.expand-icon {
  font-size: 0.8rem;
  color: var(--color-text-light);
  transition: transform 0.2s ease;
}

/* Row Details */
.row-details {
  background: var(--color-light);
  padding: 1rem 1.5rem;
  border-bottom: 1px solid var(--color-border);
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
  color: var(--color-text-muted);
}

.detail-text {
  font-size: 0.85rem;
  color: var(--color-text);
  line-height: 1.5;
  margin: 0;
}

/* Empty State */
.empty-state {
  text-align: center;
  padding: 3rem 1rem;
  color: var(--color-text-muted);
}

.empty-state i {
  font-size: 3rem;
  margin-bottom: 1rem;
  opacity: 0.5;
}

.empty-state h6 {
  margin-bottom: 0.5rem;
  color: var(--color-text);
}

.empty-state p {
  font-size: 0.9rem;
}

/* Details row should not have hover effect */
.details-row:hover {
  background: transparent !important;
}
</style>
