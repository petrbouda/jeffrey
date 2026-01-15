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

<script setup lang="ts">
import { computed } from 'vue';

type CalloutType = 'info' | 'warning' | 'tip' | 'danger';

interface CalloutConfig {
  icon: string;
  defaultTitle: string;
  colorClass: string;
}

interface Props {
  type?: CalloutType;
  title?: string;
}

const props = withDefaults(defineProps<Props>(), {
  type: 'info',
  title: ''
});

const config = computed((): CalloutConfig => {
  const configs: Record<CalloutType, CalloutConfig> = {
    info: {
      icon: 'bi-info-circle-fill',
      defaultTitle: 'Info',
      colorClass: 'callout-info'
    },
    warning: {
      icon: 'bi-exclamation-triangle-fill',
      defaultTitle: 'Warning',
      colorClass: 'callout-warning'
    },
    tip: {
      icon: 'bi-lightbulb-fill',
      defaultTitle: 'Tip',
      colorClass: 'callout-tip'
    },
    danger: {
      icon: 'bi-x-octagon-fill',
      defaultTitle: 'Danger',
      colorClass: 'callout-danger'
    }
  };
  return configs[props.type];
});

const displayTitle = computed(() => props.title || config.value.defaultTitle);
</script>

<template>
  <div class="callout" :class="config.colorClass">
    <div class="callout-header">
      <i class="bi" :class="config.icon"></i>
      <span class="callout-title">{{ displayTitle }}</span>
    </div>
    <div class="callout-content">
      <slot></slot>
    </div>
  </div>
</template>

<style scoped>
.callout {
  padding: 1rem 1.25rem;
  border-radius: 8px;
  margin: 1.5rem 0;
  border-left: 4px solid;
}

.callout-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 600;
  margin-bottom: 0.5rem;
}

.callout-content {
  font-size: 0.95rem;
  line-height: 1.6;
}

.callout-content :deep(p:last-child) {
  margin-bottom: 0;
}

/* Info - Blue */
.callout-info {
  background-color: #e7f3ff;
  border-left-color: #0d6efd;
}

.callout-info .callout-header {
  color: #0d6efd;
}

/* Warning - Orange */
.callout-warning {
  background-color: #fff3cd;
  border-left-color: #ffc107;
}

.callout-warning .callout-header {
  color: #997404;
}

/* Tip - Green */
.callout-tip {
  background-color: #d1e7dd;
  border-left-color: #198754;
}

.callout-tip .callout-header {
  color: #198754;
}

/* Danger - Red */
.callout-danger {
  background-color: #f8d7da;
  border-left-color: #dc3545;
}

.callout-danger .callout-header {
  color: #dc3545;
}
</style>
