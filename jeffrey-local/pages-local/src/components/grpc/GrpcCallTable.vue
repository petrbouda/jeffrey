<template>
  <div v-if="calls.length > 0" class="slowest-list">
    <div v-for="call in displayedCalls"
         :key="call.timestamp"
         class="slowest-row">
      <div class="left-accent" :class="getAccentClass(call.status)"></div>
      <div class="row-content">
        <div class="row-header">
          <div class="row-header-left">
            <div class="service-path" :title="call.service + '/' + call.method">
              <span class="service-package">{{ getPackageName(call.service) }}</span>
              <span class="service-simple">{{ getSimpleName(call.service) }}</span>
              <span class="method-sep">/</span>
              <span class="method-name">{{ call.method }}</span>
            </div>
          </div>
          <div class="time-bar-wrap">
            <span class="time-bar-value">{{ formatBarValue(call) }}</span>
            <div class="time-bar-track">
              <div class="time-bar-fill" :style="{ width: getBarPercentage(call) + '%' }"></div>
            </div>
          </div>
        </div>
        <div class="row-details">
          <span class="detail-chip"><i class="bi bi-clock"></i> {{ FormattingService.formatTimestamp(call.timestamp).replace('T', ' ') }}</span>
          <span class="detail-dot">&middot;</span>
          <Badge :value="call.status" :variant="getStatusVariant(call.status)" size="xs" borderless />
          <span class="detail-dot">&middot;</span>
          <span v-if="showTotalSize" class="detail-chip"><i class="bi bi-box"></i> {{ FormattingService.formatBytes(call.requestSize + call.responseSize) }}</span>
          <span v-else class="detail-chip"><i class="bi bi-hdd-network"></i> {{ call.host }}:{{ call.port }}</span>
          <span class="detail-dot">&middot;</span>
          <span class="detail-chip"><i class="bi bi-arrow-up"></i> {{ FormattingService.formatBytes(call.requestSize) }}</span>
          <span class="detail-chip"><i class="bi bi-arrow-down"></i> {{ FormattingService.formatBytes(call.responseSize) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import FormattingService from '@/services/FormattingService';
import Badge from '@/components/Badge.vue';
import type {Variant} from '@/types/ui';

interface GrpcCall {
  service: string;
  method: string;
  responseTime: number;
  status: string;
  requestSize: number;
  responseSize: number;
  host?: string;
  port?: number;
  timestamp: number;
}

interface Props {
  title: string;
  icon: string;
  calls: GrpcCall[];
  showTotalSize?: boolean;
  maxDisplayed?: number;
}

const props = withDefaults(defineProps<Props>(), {
  showTotalSize: false,
  maxDisplayed: 20
});

const displayedCalls = computed(() => {
  return props.calls.slice(0, props.maxDisplayed);
});

const getBarValue = (call: GrpcCall): number => {
  return props.showTotalSize ? (call.requestSize + call.responseSize) : call.responseTime;
};

const maxBarValue = computed(() => {
  if (props.calls.length === 0) return 1;
  return Math.max(...props.calls.map(c => getBarValue(c)));
});

const getBarPercentage = (call: GrpcCall): number => {
  return Math.max((getBarValue(call) / maxBarValue.value) * 100, 2);
};

const formatBarValue = (call: GrpcCall): string => {
  return props.showTotalSize
      ? FormattingService.formatBytes(call.requestSize + call.responseSize)
      : FormattingService.formatDuration2Units(call.responseTime);
};

const getPackageName = (fullName: string): string => {
  const lastDot = fullName.lastIndexOf('.');
  return lastDot >= 0 ? fullName.substring(0, lastDot + 1) : '';
};

const getSimpleName = (fullName: string): string => {
  const lastDot = fullName.lastIndexOf('.');
  return lastDot >= 0 ? fullName.substring(lastDot + 1) : fullName;
};

const getAccentClass = (status: string): string => {
  switch (status) {
    case 'OK': return 'accent-success';
    case 'CANCELLED':
    case 'INVALID_ARGUMENT': case 'NOT_FOUND': case 'ALREADY_EXISTS':
    case 'PERMISSION_DENIED': case 'FAILED_PRECONDITION': case 'OUT_OF_RANGE':
    case 'UNAUTHENTICATED': return 'accent-warning';
    default: return 'accent-danger';
  }
};

const getStatusVariant = (status: string): Variant => {
  switch (status) {
    case 'OK': return 'success';
    case 'CANCELLED':
    case 'INVALID_ARGUMENT': case 'NOT_FOUND': case 'ALREADY_EXISTS':
    case 'PERMISSION_DENIED': case 'FAILED_PRECONDITION': case 'OUT_OF_RANGE':
    case 'UNAUTHENTICATED': return 'warning';
    default: return 'danger';
  }
};
</script>

<style scoped>
.slowest-list {
  padding: 0.5rem 1rem;
}

.slowest-row {
  display: flex;
  align-items: stretch;
  border-bottom: 1px solid var(--color-border-light);
  padding: 0.75rem 0;
}

.slowest-row:last-child {
  border-bottom: none;
}

.slowest-row:hover {
  background: var(--color-bg-hover);
}

.left-accent {
  width: 4px;
  border-radius: 2px;
  flex-shrink: 0;
  margin-right: 1rem;
}

.accent-success { background: var(--color-success); }
.accent-warning { background: var(--color-warning); }
.accent-danger { background: var(--color-danger); }

.row-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  min-width: 0;
}

.row-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.row-header-left {
  flex: 1;
  min-width: 0;
}

.service-path {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.8rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.service-package {
  color: var(--color-text-muted);
  font-weight: 400;
}

.service-simple {
  color: var(--color-dark);
  font-weight: 700;
}

.method-sep {
  color: var(--color-text-light);
  font-weight: 400;
  margin: 0 1px;
}

.method-name {
  color: var(--color-dark);
  font-weight: 600;
}

.time-bar-wrap {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.25rem;
  min-width: 120px;
  flex-shrink: 0;
}

.time-bar-track {
  width: 100%;
  height: 6px;
  background: var(--color-lighter);
  border-radius: 3px;
  overflow: hidden;
}

.time-bar-fill {
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, var(--color-primary), var(--color-secondary));
}

.time-bar-value {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--color-dark);
  min-width: 70px;
  text-align: right;
}

.row-details {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.detail-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  font-family: var(--font-family-base);
  font-size: 0.7rem;
  font-weight: 500;
  color: var(--color-text-muted);
  letter-spacing: 0.01em;
}

.detail-chip i {
  font-size: 0.6rem;
  opacity: 0.7;
}

.detail-dot {
  color: var(--color-text-light);
  font-size: 0.8rem;
  line-height: 1;
}

@media (max-width: 768px) {
  .row-header {
    flex-direction: column;
    align-items: stretch;
    gap: 0.5rem;
  }

  .time-bar-wrap {
    min-width: 0;
  }
}
</style>
