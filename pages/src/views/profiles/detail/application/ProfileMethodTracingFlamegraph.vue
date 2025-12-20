<!--
  ~ Jeffrey
  ~ Copyright (C) 2025 Petr Bouda
  ~
  ~ This program is free software: you can redistribute it and/or modify
  ~ it under the terms of the GNU Affero General Public License as published by
  ~ the Free Software Foundation, either version 3 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU Affero General Public License for more details.
  ~
  ~ You should have received a copy of the GNU Affero General Public License
  ~ along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<template>
  <div>
    <PageHeader title="Method Tracing Flamegraph" icon="bi-fire" />

    <div class="dashboard-container">
      <!-- Controls Bar -->
      <div class="controls-bar mb-3">
        <div class="d-flex align-items-center gap-3 flex-wrap">
          <!-- Thread Mode Toggle -->
          <div class="control-group">
            <label class="control-label">View Mode</label>
            <div class="segmented-control">
              <button
                class="segment"
                :class="{ active: threadMode === 'aggregate' }"
                @click="threadMode = 'aggregate'"
              >
                <i class="bi bi-layers"></i>
                Aggregate
              </button>
              <button
                class="segment"
                :class="{ active: threadMode === 'by-thread' }"
                @click="threadMode = 'by-thread'"
              >
                <i class="bi bi-list-task"></i>
                By Thread
              </button>
            </div>
          </div>

          <!-- Weight Toggle -->
          <div class="control-group">
            <label class="control-label">Weight</label>
            <div class="segmented-control">
              <button
                class="segment"
                :class="{ active: weightMode === 'time' }"
                @click="weightMode = 'time'"
              >
                <i class="bi bi-clock"></i>
                Time
              </button>
              <button
                class="segment"
                :class="{ active: weightMode === 'count' }"
                @click="weightMode = 'count'"
              >
                <i class="bi bi-hash"></i>
                Count
              </button>
            </div>
          </div>

          <!-- Search Box -->
          <div class="control-group flex-grow-1">
            <label class="control-label">Search</label>
            <div class="input-group input-group-sm">
              <span class="input-group-text"><i class="bi bi-search"></i></span>
              <input
                type="text"
                class="form-control"
                placeholder="Filter by method name..."
                v-model="searchQuery"
              />
              <button
                v-if="searchQuery"
                class="btn btn-outline-secondary"
                @click="searchQuery = ''"
              >
                <i class="bi bi-x"></i>
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Time Range Navigator -->
      <ChartSection title="Time Range Selection" icon="clock-history" :full-width="true" container-class="apex-chart-container-compact">
        <ApexTimeSeriesChart
          :primary-data="mockDurationTimeseries"
          primary-title="Method Duration"
          :secondary-data="mockCountTimeseries"
          secondary-title="Invocation Count"
          :visible-minutes="60"
          :independentSecondaryAxis="true"
          primary-axis-type="durationInNanos"
          secondary-axis-type="number"
          :height="150"
        />
      </ChartSection>

      <!-- Flamegraph Legend -->
      <div class="flamegraph-legend mb-3">
        <div class="legend-item">
          <i class="bi bi-stack me-1"></i>
          <span class="legend-label">Total Samples:</span>
          <span class="legend-value">{{ FormattingService.formatNumber(mockStats.totalSamples) }}</span>
        </div>
        <div class="legend-item">
          <i class="bi bi-stopwatch me-1"></i>
          <span class="legend-label">Total Duration:</span>
          <span class="legend-value">{{ FormattingService.formatDuration2Units(mockStats.totalDuration) }}</span>
        </div>
        <div class="legend-item">
          <i class="bi bi-calendar-range me-1"></i>
          <span class="legend-label">Time Range:</span>
          <span class="legend-value">{{ timeRangeLabel }}</span>
        </div>
      </div>

      <!-- Flamegraph Placeholder -->
      <ChartSection title="Method Trace Flamegraph" icon="fire" :full-width="true">
        <div class="flamegraph-placeholder">
          <div class="placeholder-content">
            <i class="bi bi-fire display-1 text-warning mb-3"></i>
            <h4 class="text-muted">Flamegraph Visualization</h4>
            <p class="text-muted mb-0">
              The interactive flamegraph will be rendered here once connected to backend data.
            </p>
            <p class="text-muted small mt-2">
              <i class="bi bi-info-circle me-1"></i>
              Current mode: <strong>{{ threadMode }}</strong> | Weight: <strong>{{ weightMode }}</strong>
              <span v-if="searchQuery"> | Search: <strong>"{{ searchQuery }}"</strong></span>
            </p>
          </div>
        </div>
      </ChartSection>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import PageHeader from '@/components/layout/PageHeader.vue';
import ChartSection from '@/components/ChartSection.vue';
import ApexTimeSeriesChart from '@/components/ApexTimeSeriesChart.vue';
import FormattingService from '@/services/FormattingService';

// Control states
const threadMode = ref<'aggregate' | 'by-thread'>('aggregate');
const weightMode = ref<'time' | 'count'>('time');
const searchQuery = ref('');

// Mock stats
const mockStats = {
  totalSamples: 12345,
  totalDuration: 45_200_000_000 // 45.2s in nanos
};

// Generate mock timeseries data
const now = Date.now();
const generateTimeseries = (baseValue: number, variance: number) => {
  const data: number[][] = [];
  for (let i = 60; i >= 0; i--) {
    const timestamp = now - i * 60000;
    const value = baseValue + Math.random() * variance - variance / 2;
    data.push([timestamp, Math.max(0, value)]);
  }
  return data;
};

const mockDurationTimeseries = generateTimeseries(800_000_000, 400_000_000);
const mockCountTimeseries = generateTimeseries(200, 100);

const timeRangeLabel = computed(() => {
  const start = new Date(now - 60 * 60000);
  const end = new Date(now);
  return `${start.toLocaleTimeString()} - ${end.toLocaleTimeString()}`;
});
</script>

<style scoped>
.dashboard-container {
  padding: 0;
}

.controls-bar {
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 8px;
  padding: 1rem;
}

.control-group {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.control-label {
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  color: #6c757d;
  letter-spacing: 0.5px;
}

.segmented-control {
  display: inline-flex;
  background: #e9ecef;
  border-radius: 8px;
  padding: 4px;
  gap: 4px;
}

.segmented-control .segment {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: none;
  background: transparent;
  border-radius: 6px;
  font-size: 0.8rem;
  font-weight: 500;
  color: #5f6368;
  cursor: pointer;
  transition: all 0.2s ease;
}

.segmented-control .segment:hover:not(.active) {
  background: rgba(0, 0, 0, 0.04);
  color: #202124;
}

.segmented-control .segment.active {
  background: #fff;
  color: #1a73e8;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1), 0 1px 2px rgba(0, 0, 0, 0.06);
}

.segmented-control .segment i {
  font-size: 0.85rem;
}

.flamegraph-legend {
  display: flex;
  gap: 2rem;
  padding: 0.75rem 1rem;
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 8px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.875rem;
}

.legend-label {
  color: #6c757d;
}

.legend-value {
  font-weight: 600;
  color: #212529;
}

.flamegraph-placeholder {
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
  border: 2px dashed #dee2e6;
  border-radius: 8px;
}

.placeholder-content {
  text-align: center;
  padding: 2rem;
}

:deep(.apex-chart-container-compact) {
  min-height: 150px !important;
}
</style>
