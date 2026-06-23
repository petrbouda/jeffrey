<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
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

<!--
  Tinted icon-tile status badge used in the #right slot of the entity-list cards
  (MetricCardList): HTTP 4xx/5xx, JDBC errors, gRPC success rate. A solid colored
  icon tile sits next to a stacked value/label, on a soft tint of the same color.
-->
<template>
  <div class="status-badge" :class="variant">
    <div class="status-tile"><i class="bi" :class="displayIcon"></i></div>
    <div class="status-meta">
      <span class="status-value">{{ value }}</span>
      <span class="status-label">{{ label }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

type StatusVariant = 'ok' | 'warn' | 'danger';

const DEFAULT_ICONS: Record<StatusVariant, string> = {
  ok: 'bi-check-lg',
  warn: 'bi-exclamation-triangle-fill',
  danger: 'bi-exclamation-octagon-fill'
};

interface Props {
  value: string | number;
  label: string;
  variant?: StatusVariant;
  icon?: string;
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'warn',
  icon: undefined
});

const displayIcon = computed(() => props.icon ?? DEFAULT_ICONS[props.variant]);
</script>

<style scoped>
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  padding: 9px 17px 9px 9px;
  border-radius: var(--radius-md);
  flex-shrink: 0;
}

.status-tile {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-base);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-white);
  font-size: 1.05rem;
  flex-shrink: 0;
}

.status-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.18;
}

.status-value {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 1.1rem;
  font-weight: 700;
}

.status-label {
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.4px;
  color: var(--color-text-muted);
}

.status-badge.ok {
  background: var(--color-success-light);
}
.status-badge.ok .status-tile {
  background: var(--color-success);
}
.status-badge.ok .status-value {
  color: var(--color-success-hover);
}

.status-badge.warn {
  background: var(--color-warning-light);
}
.status-badge.warn .status-tile {
  background: var(--color-warning);
}
.status-badge.warn .status-value {
  color: var(--color-warning-hover);
}

.status-badge.danger {
  background: var(--color-danger-light);
}
.status-badge.danger .status-tile {
  background: var(--color-danger);
}
.status-badge.danger .status-value {
  color: var(--color-danger);
}
</style>
