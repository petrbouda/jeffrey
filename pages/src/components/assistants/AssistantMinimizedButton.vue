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
        <circle
            class="progress-ring-bg"
            cx="28"
            cy="28"
            r="24"
            fill="none"
            stroke-width="3"
        />
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
      <i :class="[icon, { spin: isSpinning }]" class="indicator-icon"></i>

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
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  box-shadow: 0 4px 16px rgba(102, 126, 234, 0.4), 0 2px 8px rgba(118, 75, 162, 0.3);
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.assistant-minimized-button:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 24px rgba(102, 126, 234, 0.5), 0 4px 12px rgba(118, 75, 162, 0.4);
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
  stroke: #86efac;
}

.status-failed .progress-ring-bar {
  stroke: #fca5a5;
}

.status-cancelled .progress-ring-bar {
  stroke: #fde047;
}

/* Icon */
.indicator-icon {
  font-size: 1.25rem;
  z-index: 1;
  color: white;
  margin-top: -6px;
}

.status-completed .indicator-icon {
  color: #86efac;
}

.status-failed .indicator-icon {
  color: #fca5a5;
}

.status-cancelled .indicator-icon {
  color: #fde047;
}

/* Badge */
.indicator-badge {
  position: absolute;
  bottom: -6px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 0.65rem;
  font-weight: 700;
  background: #7c3aed;
  padding: 3px 8px;
  border-radius: 10px;
  box-shadow: 0 2px 6px rgba(124, 58, 237, 0.3);
  color: white;
  border: 2px solid white;
}

.badge-primary,
.badge-purple,
.badge-default {
  background: #7c3aed;
  color: white;
}

.badge-success {
  background: #16a34a;
  color: white;
}

.badge-danger {
  background: #dc2626;
  color: white;
}

.badge-warning {
  background: #ca8a04;
  color: white;
}

/* Animations */
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes pulse {
  0%, 100% {
    transform: scale(1);
    box-shadow: 0 4px 12px rgba(139, 92, 246, 0.25);
    border-color: #a78bfa;
  }
  50% {
    transform: scale(1.05);
    box-shadow: 0 6px 20px rgba(139, 92, 246, 0.4);
    border-color: #8b5cf6;
  }
}

.pulsing,
.status-downloading {
  animation: pulse 1.5s ease-in-out infinite;
}
</style>
