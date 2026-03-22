Coul<!--
  - Jeffrey
  - Copyright (C) 2024 Petr Bouda
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
  <div :class="['flamegraph-card', getCategoryClass()]">
    <!-- Card Header -->
    <div class="card-header">
      <div class="card-icon">
        <i :class="getIconClass()"></i>
      </div>
      <div class="title-section">
        <h3 class="card-title">{{ title }}</h3>
      </div>
    </div>

    <!-- Settings Panel -->
    <div class="settings-panel">
      <!-- Event Details -->
      <div class="event-details">
        <div class="detail-row">
          <span class="detail-label">Type:</span>
          <span class="detail-value" v-if="containsSecondary() && !isSameType()">
            <span class="primary-value">{{ event.primary.code }}</span>
            <span class="delimiter"> → </span>
            <span class="secondary-value">{{ event.secondary?.code }}</span>
            <span class="calculated-indicator" v-if="Utils.parseBoolean(event.primary.calculated)">(calculated)</span>
          </span>
          <span class="detail-value" v-else>
            {{ event.primary.code }}
            <span class="calculated-indicator" v-if="Utils.parseBoolean(event.primary.calculated)">(calculated)</span>
          </span>
        </div>

        <div class="detail-row" v-if="event.primary.subtype != null">
          <span class="detail-label">Sub-Type:</span>
          <span class="detail-value" v-if="containsSecondary() && !isSameType()">
            <span class="primary-value">{{ event.primary.subtype }}</span>
            <span class="delimiter"> → </span>
            <span class="secondary-value">{{ event.secondary?.subtype }}</span>
          </span>
          <span class="detail-value" v-else>
            {{ event.primary.subtype }}
          </span>
        </div>

        <div class="detail-row">
          <span class="detail-label">Source:</span>
          <span class="detail-value" v-if="containsSecondary() && !isSameSource()">
            <span class="primary-value">{{ event.primary.source }}</span>
            <span class="delimiter"> → </span>
            <span class="secondary-value">{{ event.secondary?.source }}</span>
          </span>
          <span class="detail-value" v-else>
            {{ event.primary.source }}
          </span>
        </div>

        <div class="detail-row">
          <span class="detail-label">Samples:</span>
          <span class="detail-value" v-if="containsSecondary()">
            <span class="primary-value">{{ FormattingService.formatNumber(event.primary.samples) }}</span>
            <span class="delimiter"> → </span>
            <span class="secondary-value">{{ FormattingService.formatNumber(event.secondary?.samples || 0) }}</span>
          </span>
          <span class="detail-value" v-else>
            {{ FormattingService.formatNumber(event.primary.samples) }}
          </span>
        </div>

        <div class="detail-row" v-if="weightDesc != null">
          <span class="detail-label">{{ weightDesc }}:</span>
          <span class="detail-value" v-if="containsSecondary()">
            <span class="primary-value">{{ weightFormatter(event.primary.weight) }}</span>
            <span class="delimiter"> → </span>
            <span class="secondary-value">{{ weightFormatter(event.secondary?.weight || 0) }}</span>
          </span>
          <span class="detail-value" v-else>
            {{ weightFormatter(event.primary.weight) }}
          </span>
        </div>

        <div class="detail-row" v-if="Utils.isNotNull(event.primary.extras?.sample_interval)">
          <span class="detail-label">Sample Interval:</span>
          <span class="detail-value" v-if="containsSecondary()">
            <span class="primary-value">{{ weightFormatter(event.primary.extras.sample_interval) }}</span>
            <span class="delimiter"> → </span>
            <span class="secondary-value">{{ weightFormatter(event.secondary?.extras.sample_interval) }}</span>
          </span>
          <span class="detail-value" v-else>
            {{ weightFormatter(event.primary.extras.sample_interval) }}
          </span>
        </div>
      </div>

      <!-- Settings Checkboxes -->
      <div class="settings-options">
        <!-- Thread mode checkbox -->
        <div class="setting-item" v-if="threadModeOpt">
          <input
            class="setting-checkbox"
            type="checkbox"
            :id="'threadMode_' + event.code"
            v-model="useThreadMode"
          >
          <label class="setting-label" :for="'threadMode_' + event.code">
            Use Thread-mode
          </label>
        </div>

        <!-- Weight option checkbox -->
        <div class="setting-item" v-if="weightOpt">
          <input
            class="setting-checkbox"
            type="checkbox"
            :id="'useWeight_' + event.code"
            v-model="useWeight"
          >
          <label class="setting-label" :for="'useWeight_' + event.code">
            Use {{ weightDescription }}
          </label>
        </div>

        <!-- Exclude Idle Samples -->
        <div class="setting-item" v-if="excludeIdleSamplesOpt">
          <input
            class="setting-checkbox"
            type="checkbox"
            :id="'excludeIdle_' + event.code"
            v-model="excludeIdleSamples"
            @click="switchIdleSamples()"
          >
          <label class="setting-label" :for="'excludeIdle_' + event.code">
            Exclude Idle Samples
            <i class="bi bi-info-circle setting-tooltip" title="Excludes samples that are parked in thread-pools"></i>
          </label>
        </div>

        <!-- Exclude non-Java Samples -->
        <div class="setting-item" v-if="excludeNonJavaSamplesOpt">
          <input
            class="setting-checkbox"
            type="checkbox"
            :id="'excludeNonJava_' + event.code"
            v-model="excludeNonJavaSamples"
          >
          <label class="setting-label" :for="'excludeNonJava_' + event.code">
            Exclude non-Java Samples
            <i class="bi bi-info-circle setting-tooltip" title="Excludes samples belonging to JIT, Garbage Collector, and other non-Java threads"></i>
          </label>
        </div>

        <!-- Only Unsafe Allocation Samples -->
        <div class="setting-item" v-if="onlyUnsafeAllocationSamplesOpt">
          <input
            class="setting-checkbox"
            type="checkbox"
            :id="'unsafeAlloc_' + event.code"
            v-model="onlyUnsafeAllocationSamples"
          >
          <label class="setting-label" :for="'unsafeAlloc_' + event.code">
            Only Allocations with Unsafe
            <i class="bi bi-info-circle setting-tooltip" title="Filters out all JVM-specific allocations and let only the relevant ones"></i>
          </label>
        </div>
      </div>
    </div>

    <!-- Action Zone -->
    <div class="card-actions">
      <button
        @click="navigateToFlamegraph"
        :disabled="!enabled"
        class="btn btn-primary btn-full"
      >
        <i class="bi bi-fire"></i>
        {{ buttonText || 'View Flamegraph' }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router';
import { ref } from 'vue';
import EventSummary from '@/services/api/model/EventSummary';
import Utils from '@/services/Utils';
import FormattingService from '@/services/FormattingService';

interface Props {
  title: string;
  color: string;
  icon: string;
  graphMode: string;
  threadModeOpt: boolean;
  threadModeSelected: boolean;
  weightOpt: boolean;
  weightSelected: boolean;
  weightDesc: string | null;
  weightFormatter: (bytes: number) => string;
  excludeNonJavaSamplesOpt: boolean;
  excludeNonJavaSamplesSelected: boolean;
  excludeIdleSamplesOpt: boolean;
  excludeIdleSamplesSelected: boolean;
  onlyUnsafeAllocationSamplesOpt: boolean;
  onlyUnsafeAllocationSamplesSelected: boolean;
  event: EventSummary;
  enabled: boolean;
  routeName?: string;
  buttonText?: string;
}

const props = defineProps<Props>();
const router = useRouter();
const route = useRoute();

// Reactive settings
const useThreadMode = ref(Utils.parseBoolean(props.threadModeSelected));
const useWeight = ref(Utils.parseBoolean(props.weightSelected));
const excludeNonJavaSamples = ref(Utils.parseBoolean(props.excludeNonJavaSamplesSelected));
const excludeIdleSamples = ref(Utils.parseBoolean(props.excludeIdleSamplesSelected));
const onlyUnsafeAllocationSamples = ref(Utils.parseBoolean(props.onlyUnsafeAllocationSamplesSelected));
const weightDescription = ref(props.weightDesc);


// Get category-specific styling
const getCategoryClass = () => {
  const color = props.color.toLowerCase();
  return `flamegraph-card--${color}`;
};

const getIconClass = () => {
  const icon = props.icon.toLowerCase();
  if (icon.includes('sprint') || icon.includes('cpu')) return 'bi bi-cpu';
  if (icon.includes('alarm') || icon.includes('clock')) return 'bi bi-clock';
  if (icon.includes('memory')) return 'bi bi-memory';
  if (icon.includes('lock')) return 'bi bi-lock';
  return 'bi bi-fire';
};

const containsSecondary = () => {
  return props.event.secondary != null;
};

const isSameType = () => {
  return props.event.secondary != null && props.event.primary.code === props.event.secondary.code;
};

const isSameSource = () => {
  return props.event.secondary != null && props.event.primary.source === props.event.secondary.source;
};

const navigateToFlamegraph = () => {
  if (!props.enabled) return;

  const query: any = {
    eventType: props.event.code,
    graphMode: props.graphMode
  };

  // Apply current settings
  if (useThreadMode.value) query.useThreadMode = useThreadMode.value;
  if (useWeight.value) query.useWeight = useWeight.value;
  if (excludeNonJavaSamples.value) query.excludeNonJavaSamples = excludeNonJavaSamples.value;
  if (excludeIdleSamples.value) query.excludeIdleSamples = excludeIdleSamples.value;
  if (onlyUnsafeAllocationSamples.value) query.onlyUnsafeAllocationSamples = onlyUnsafeAllocationSamples.value;

  router.push({
    name: props.routeName || 'flamegraph',
    params: {
      workspaceId: route.params.workspaceId,
      projectId: route.params.projectId,
      profileId: route.params.profileId,
    },
    query
  });
};

const switchIdleSamples = () => {
  if (excludeIdleSamples.value) {
    excludeIdleSamples.value = false;
    excludeNonJavaSamples.value = false;
  } else {
    excludeIdleSamples.value = true;
    excludeNonJavaSamples.value = true;
  }
};

</script>

<style scoped>
.flamegraph-card {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 1rem;
  transition: all 0.2s ease;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 300px;
  overflow: hidden;
}

.flamegraph-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

/* Category-specific styling */
.flamegraph-card--blue {
  border-left: 3px solid #3b82f6;
}

.flamegraph-card--purple {
  border-left: 3px solid #8b5cf6;
}

.flamegraph-card--green {
  border-left: 3px solid #10b981;
}

.flamegraph-card--pink {
  border-left: 3px solid #f59e0b;
}

.flamegraph-card--red {
  border-left: 3px solid #ef4444;
}

/* Header */
.card-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  position: relative;
  height: 45px;
  flex-shrink: 0;
  padding-top: 0;
}

.card-icon {
  width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.8rem;
  color: #6b7280;
  flex-shrink: 0;
}

.title-section {
  min-width: 0;
  flex: 1;
}

.card-title {
  font-size: 0.95rem;
  font-weight: 600;
  color: #212529;
  margin: 0;
  line-height: 1.2;
}

/* Actions */
.card-actions {
  margin-top: auto;
}

.btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.6rem 1rem;
  border: 1px solid transparent;
  border-radius: 6px;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  text-decoration: none;
  width: 100%;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-primary {
  background-color: #3b82f6;
  color: white;
  border-color: #3b82f6;
}

.btn-primary:hover:not(:disabled) {
  background-color: #2563eb;
  border-color: #2563eb;
}

.btn-full {
  width: 100%;
}

/* Card Actions */
.card-actions {
  margin-top: auto;
  flex-shrink: 0;
  padding-top: 0.75rem;
}

/* Information Panel */
.settings-panel {
  border-top: 1px solid #e5e7eb;
  padding-top: 0.75rem;
  flex-shrink: 0;
}

/* Event Details */
.event-details {
  margin-bottom: 0.75rem;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.25rem 0;
  font-size: 0.75rem;
}

.detail-label {
  color: #6b7280;
  font-weight: 500;
  flex: 0 0 auto;
  margin-right: 0.75rem;
}

.detail-value {
  text-align: right;
  font-weight: 600;
  flex: 1;
  max-width: 70%;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 0.8rem;
}

.primary-value {
  color: #3b82f6;
}

.secondary-value {
  color: #6b7280;
}

.delimiter {
  color: #9ca3af;
  font-weight: 400;
  font-size: 0.75rem;
}

.calculated-indicator {
  color: #f59e0b;
  font-size: 0.65rem;
  margin-left: 0.25rem;
}

/* Settings Options */
.settings-options {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.setting-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.setting-checkbox {
  width: 14px;
  height: 14px;
  border: 1px solid #d1d5db;
  border-radius: 3px;
  cursor: pointer;
}

.setting-checkbox:checked {
  background-color: #3b82f6;
  border-color: #3b82f6;
}

.setting-label {
  font-size: 0.75rem;
  color: #374151;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 0.25rem;
  flex: 1;
}

.setting-tooltip {
  color: #6b7280;
  font-size: 0.65rem;
  cursor: help;
}

/* Responsive design */
@media (max-width: 768px) {
  .detail-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.25rem;
  }

  .detail-value {
    text-align: left;
    max-width: 100%;
  }
}
</style>
