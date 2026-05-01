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
    <PageHeader title="Auto Analysis" icon="bi-robot">
      <!-- Idle State: Hero CTA -->
      <div v-if="phase === 'idle'" class="hero-card">
        <div class="hero-icon">
          <i class="bi bi-robot"></i>
        </div>
        <h3 class="hero-title">Auto Analysis</h3>
        <p class="hero-desc">
          Run JMC rules against your recording to automatically detect performance issues,
          misconfigurations, and optimization opportunities.
        </p>
        <button class="btn-run" @click="runAnalysis">
          <i class="bi bi-play-fill"></i>
          Run Analysis
        </button>
      </div>

      <!-- Running State -->
      <div v-else-if="phase === 'running'" class="running-card">
        <div class="running-icon">
          <div class="spinner-border text-primary" role="status">
            <span class="visually-hidden">Running...</span>
          </div>
        </div>
        <h4 class="running-title">Running Analysis...</h4>
        <p class="running-subtitle">Evaluating JMC rules against recording events</p>
        <div class="progress-track">
          <div class="progress-fill"></div>
        </div>
      </div>

      <!-- Error State -->
      <div v-else-if="phase === 'error'" class="hero-card">
        <div class="hero-icon hero-icon-error">
          <i class="bi bi-exclamation-triangle"></i>
        </div>
        <h3 class="hero-title">Analysis Failed</h3>
        <p class="hero-desc">{{ errorMessage }}</p>
        <button class="btn-run" @click="runAnalysis">
          <i class="bi bi-arrow-clockwise"></i>
          Retry Analysis
        </button>
      </div>

      <!-- Results State -->
      <template v-else-if="phase === 'results'">
        <!-- Summary Panel -->
        <DualPanel v-if="rules.length > 0" class="mb-4" left-title="Results Overview">
          <template #left>
            <DonutWithLegend
              :data="chartData"
              :tooltip-formatter="(val: number) => val + ' rules'"
              legend-position="right"
            />
          </template>
        </DualPanel>

        <!-- Rules Table -->
        <DataTable v-if="filteredAndSortedRules.length > 0" table-class="rules-table">
          <template #toolbar>
            <TableToolbar v-model="searchQuery" search-placeholder="Search rules...">
              <span class="toolbar-count">{{ filteredAndSortedRules.length }} rules</span>
              <Badge v-if="severityCounts.warning > 0" :value="severityCounts.warning + ' warnings'" variant="danger" size="xs" />
              <template #filters>
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
                  <th class="col-rule">Rule</th>
                  <th
                    class="col-score"
                    title="Severity score (0-100). Higher values indicate more significant findings."
                  >
                    Severity
                  </th>
                  <th class="col-actions text-end">Details</th>
                </tr>
              </thead>
              <tbody>
                <template
                  v-for="(rule, index) in filteredAndSortedRules"
                  :key="`${index}-${rule.rule}`"
                >
                  <tr
                    class="rule-row"
                    :class="[`severity-${rule.severity?.toLowerCase() || 'default'}`]"
                    @click="toggleRow(index)"
                  >
                    <td>
                      <div class="rule-cell">
                        <i
                          class="bi severity-icon"
                          :class="[
                            `bi-${getSeverityIcon(rule.severity)}`,
                            getSeverityTextClass(rule.severity)
                          ]"
                        ></i>
                        <span class="rule-name">{{ rule.rule }}</span>
                      </div>
                    </td>
                    <td class="col-score">
                      <div class="severity-bar-wrapper" v-if="parseScore(rule.score) != null">
                        <div class="severity-bar-track">
                          <div
                            class="severity-bar-fill"
                            :style="{
                              width: parseScore(rule.score) + '%',
                              backgroundColor: getSeverityColor(rule.severity)
                            }"
                          ></div>
                        </div>
                        <span class="severity-bar-value">{{ parseScore(rule.score) }}</span>
                      </div>
                      <span v-else class="no-score">-</span>
                    </td>
                    <td class="col-actions text-end">
                      <i
                        class="bi expand-icon"
                        :class="expandedRows.has(index) ? 'bi-chevron-up' : 'bi-chevron-down'"
                      ></i>
                    </td>
                  </tr>

                  <!-- Expandable Row Details -->
                  <tr v-if="expandedRows.has(index)" class="details-row">
                    <td colspan="3" class="p-0">
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
        <div v-else-if="rules.length > 0" class="empty-state">
          <i class="bi bi-funnel"></i>
          <h6>No Matching Rules</h6>
          <p>Try adjusting your filters or search query.</p>
        </div>

        <!-- Empty State (analysis ran but no rules) -->
        <div v-if="rules.length === 0" class="empty-state">
          <i class="bi bi-inbox"></i>
          <h6>No Analysis Results</h6>
          <p>No auto analysis rules were evaluated for this profile.</p>
        </div>
      </template>
    </PageHeader>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import AutoAnalysisClient from '@/services/api/AutoAnalysisClient';
import AnalysisResult from '@/services/api/model/AnalysisResult';
import PageHeader from '@/components/layout/PageHeader.vue';
import LoadingState from '@/components/LoadingState.vue';
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import Badge from '@/components/Badge.vue';
import DualPanel from '@/components/DualPanel.vue';
import DonutWithLegend from '@/components/DonutWithLegend.vue';
import type { DonutChartData } from '@/components/DonutWithLegend.vue';

const route = useRoute();

type Phase = 'idle' | 'running' | 'results' | 'error';

const rules = ref<AnalysisResult[]>([]);
const loading = ref(true);
const phase = ref<Phase>('idle');
const errorMessage = ref('');

const severityFilter = ref('');
const searchQuery = ref('');
const expandedRows = ref<Set<number>>(new Set());

const severityOrder: Record<string, number> = {
  WARNING: 0,
  INFO: 1,
  OK: 2,
  NA: 3,
  IGNORE: 4
};

const filteredAndSortedRules = computed(() => {
  let result = [...rules.value];

  if (severityFilter.value) {
    result = result.filter(r => r.severity === severityFilter.value);
  }

  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase();
    result = result.filter(
      r => r.rule.toLowerCase().includes(query) || r.summary?.toLowerCase().includes(query)
    );
  }

  return result.sort(
    (a, b) => (severityOrder[a.severity] ?? 99) - (severityOrder[b.severity] ?? 99)
  );
});

const severityCounts = computed(() => ({
  ok: rules.value.filter(r => r.severity === 'OK').length,
  warning: rules.value.filter(r => r.severity === 'WARNING').length,
  info: rules.value.filter(r => r.severity === 'INFO').length,
  na: rules.value.filter(r => r.severity === 'NA' || r.severity === 'IGNORE').length
}));

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
    totalValue: `${severityCounts.value.ok}/${rules.value.length}`,
    legendItems: items.map(i => ({
      color: i.color,
      label: i.label,
      value: i.count.toString()
    }))
  };
});

const client = new AutoAnalysisClient(route.params.profileId as string);

onMounted(async () => {
  try {
    const data = await client.rules();
    if (data.length > 0) {
      rules.value = data;
      phase.value = 'results';
    } else {
      phase.value = 'idle';
    }
  } catch {
    phase.value = 'idle';
  } finally {
    loading.value = false;
  }
});

async function runAnalysis() {
  phase.value = 'running';
  try {
    const data = await client.generate();
    rules.value = data;
    phase.value = 'results';
  } catch (e) {
    errorMessage.value = e instanceof Error ? e.message : 'An unexpected error occurred';
    phase.value = 'error';
  }
}

function toggleRow(index: number) {
  if (expandedRows.value.has(index)) {
    expandedRows.value.delete(index);
  } else {
    expandedRows.value.add(index);
  }
  expandedRows.value = new Set(expandedRows.value);
}

function parseScore(score: string | null): number | null {
  if (score == null) return null;
  const parsed = parseFloat(score);
  return isNaN(parsed) ? null : Math.round(parsed * 10) / 10;
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
</script>

<style scoped>
/* Hero CTA Card */
.hero-card {
  max-width: 480px;
  margin: 48px auto;
  text-align: center;
  padding: 48px 40px;
  background: var(--color-bg-card);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-base);
}

.hero-icon {
  width: 64px;
  height: 64px;
  background: var(--color-primary-light);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20px;
  color: var(--color-primary);
  font-size: 28px;
}

.hero-icon-error {
  background: var(--color-danger-light);
  color: var(--color-danger);
}

.hero-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-dark);
  margin-bottom: 8px;
}

.hero-desc {
  font-size: 13px;
  color: var(--color-text-muted);
  line-height: 1.6;
  margin-bottom: 28px;
  max-width: 360px;
  margin-left: auto;
  margin-right: auto;
}

.btn-run {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 28px;
  background: var(--color-primary);
  color: var(--color-white);
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.2s;
}

.btn-run:hover {
  background: var(--color-primary-hover);
}

.btn-run i {
  font-size: 16px;
}

/* Running State */
.running-card {
  max-width: 480px;
  margin: 48px auto;
  text-align: center;
  padding: 48px 40px;
  background: var(--color-bg-card);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-base);
}

.running-icon {
  margin-bottom: 16px;
}

.running-icon .spinner-border {
  width: 2.5rem;
  height: 2.5rem;
  color: var(--color-primary);
}

.running-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-dark);
  margin-bottom: 4px;
}

.running-subtitle {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-bottom: 0;
}

@keyframes progress-indeterminate {
  0% {
    width: 5%;
    margin-left: 0;
  }
  50% {
    width: 40%;
    margin-left: 30%;
  }
  100% {
    width: 5%;
    margin-left: 95%;
  }
}

.progress-track {
  height: 4px;
  background: var(--color-border);
  border-radius: 4px;
  overflow: hidden;
  margin-top: 24px;
}

.progress-fill {
  height: 100%;
  background: var(--color-primary);
  border-radius: 4px;
  animation: progress-indeterminate 2s ease-in-out infinite;
}

.toolbar-count {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--color-text);
}

/* Rules Table */
.rules-table {
  width: 100%;
  table-layout: fixed;
}

.rules-table .col-rule {
  width: 50%;
}

.rule-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.severity-icon {
  font-size: 1rem;
  flex-shrink: 0;
}

.rules-table .col-score {
  width: 180px;
}

.rules-table .col-actions {
  width: 80px;
}

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

.rule-name {
  font-weight: 500;
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
  background: var(--color-border);
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
  color: var(--color-text);
  min-width: 28px;
  text-align: right;
  flex-shrink: 0;
}

.no-score {
  color: var(--color-text-light);
  font-size: 0.75rem;
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
    border-top: 1px solid var(--color-border);
    padding-left: 0;
    padding-top: 1rem;
    margin-top: 1rem;
  }
}

</style>
