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
  <Teleport to="#assistant-minimized-container">
    <div
      class="assistant-minimized-button"
      :class="[statusClass, { pulsing: isPulsing }]"
      :style="buttonStyle"
      @click="$emit('click')"
      :title="title"
    >
      <!-- Progress Ring (SVG) -->
      <svg v-if="progress !== undefined" class="progress-ring" viewBox="0 0 56 56">
        <circle class="progress-ring-bg" cx="28" cy="28" r="24" fill="none" stroke-width="3" />
        <circle
          class="progress-ring-bar"
          cx="28"
          cy="28"
          r="24"
          fill="none"
          stroke-width="3"
          stroke-linecap="round"
          :style="progressRingStyle"
        />
      </svg>

      <!-- Icon -->
      <i :class="[icon, { spin: isSpinning, 'has-badge': badgeText }]" class="indicator-icon"></i>

      <!-- Badge -->
      <span v-if="badgeText" class="indicator-badge" :class="badgeClass">
        {{ badgeText }}
      </span>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed } from 'vue';

interface Props {
  // Visual
  icon: string;
  progress?: number;
  badgeText?: string;
  badgeVariant?: 'primary' | 'success' | 'danger' | 'warning' | 'purple' | 'default';

  // State
  status?: 'downloading' | 'processing' | 'completed' | 'failed' | 'cancelled' | 'default';
  isSpinning?: boolean;
  isPulsing?: boolean;

  // Ordering (lower = closer to right edge)
  order?: number;

  // Tooltip
  title?: string;
}

const props = withDefaults(defineProps<Props>(), {
  badgeVariant: 'default',
  status: 'default',
  isSpinning: false,
  isPulsing: false,
  order: 10,
  title: 'Click to expand'
});

const buttonStyle = computed(() => ({
  order: props.order
}));

defineEmits<{
  (e: 'click'): void;
}>();

const statusClass = computed(() => `status-${props.status}`);

const badgeClass = computed(() => `badge-${props.badgeVariant}`);

const progressRingStyle = computed(() => {
  if (props.progress === undefined) return {};
  const circumference = 2 * Math.PI * 24; // r = 24
  const offset = circumference - (props.progress / 100) * circumference;
  return {
    strokeDasharray: `${circumference}`,
    strokeDashoffset: `${offset}`
  };
});
</script>

<style scoped>
.assistant-minimized-button {
  position: relative;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(
    135deg,
    var(--color-gradient-start) 0%,
    var(--color-gradient-end) 100%
  );
  box-shadow:
    0 4px 16px rgba(102, 126, 234, 0.4),
    0 2px 8px rgba(118, 75, 162, 0.3);
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.assistant-minimized-button:hover {
  transform: scale(1.1);
  box-shadow:
    0 6px 24px rgba(102, 126, 234, 0.5),
    0 4px 12px rgba(118, 75, 162, 0.4);
}

/* Progress Ring */
.progress-ring {
  position: absolute;
  top: -2px;
  left: -2px;
  width: 60px;
  height: 60px;
  transform: rotate(-90deg);
}

.progress-ring-bg {
  stroke: rgba(255, 255, 255, 0.3);
}

.progress-ring-bar {
  stroke: white;
  transition: stroke-dashoffset 0.3s ease;
}

/* Status-based colors for progress ring */
.status-downloading .progress-ring-bar,
.status-processing .progress-ring-bar,
.status-default .progress-ring-bar {
  stroke: white;
}

.status-completed .progress-ring-bar {
  stroke: var(--color-success);
}

.status-failed .progress-ring-bar {
  stroke: var(--color-danger-border-light);
}

.status-cancelled .progress-ring-bar {
  stroke: var(--color-amber-badge-border);
}

/* Icon */
.indicator-icon {
  font-size: 1.25rem;
  z-index: 1;
  color: white;
}

.indicator-icon.has-badge {
  margin-top: -6px;
}

.status-completed .indicator-icon {
  color: var(--color-success);
}

.status-failed .indicator-icon {
  color: var(--color-danger-border-light);
}

.status-cancelled .indicator-icon {
  color: var(--color-amber-badge-border);
}

/* Badge */
.indicator-badge {
  position: absolute;
  bottom: -6px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 0.65rem;
  font-weight: 700;
  background: var(--color-violet-dark);
  padding: 3px 8px;
  border-radius: 10px;
  box-shadow: 0 2px 6px rgba(124, 58, 237, 0.3);
  color: var(--color-white);
  border: 2px solid var(--color-white);
}

.badge-primary,
.badge-purple,
.badge-default {
  background: var(--color-violet-dark);
  color: var(--color-white);
}

.badge-success {
  background: var(--color-success-hover);
  color: var(--color-white);
}

.badge-danger {
  background: var(--color-danger-hover);
  color: var(--color-white);
}

.badge-warning {
  background: var(--color-amber);
  color: var(--color-white);
}

/* Animations */
@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes pulse {
  0%,
  100% {
    transform: scale(1);
    box-shadow: 0 4px 12px rgba(139, 92, 246, 0.25);
    border-color: var(--color-indigo-accent);
  }
  50% {
    transform: scale(1.05);
    box-shadow: 0 6px 20px rgba(139, 92, 246, 0.4);
    border-color: var(--color-violet);
  }
}

.pulsing,
.status-downloading {
  animation: pulse 1.5s ease-in-out infinite;
}
</style>
