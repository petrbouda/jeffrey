<template>
  <ChartSection v-if="calls.length > 0" :title="title" :icon="icon" :full-width="true">
    <div class="table-responsive">
      <table class="table table-hover grpc-table">
        <thead>
        <tr>
          <th>Service / Method</th>
          <th class="text-center">Response Time</th>
          <th class="text-center">Data Transferred</th>
          <th v-if="showTotalSize" class="text-center">Total Size</th>
          <th v-else>Host</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="call in displayedCalls"
            :key="call.timestamp"
            class="call-row">
          <td class="service-cell">
            <div class="call-display">
              <div class="service-name-display" :title="call.service">
                <span class="service-package">{{ getPackageName(call.service) }}</span>
                <span class="service-simple">{{ getSimpleName(call.service) }}</span>
                <span class="method-separator">/</span>
                <span class="method-name">{{ call.method }}</span>
              </div>
            </div>
            <div class="call-meta">
              <Badge
                  :value="FormattingService.formatTimestamp(call.timestamp).replace('T', ' ')"
                  icon="bi-clock"
                  variant="grey"
                  size="s"
              />
              <Badge :value="call.status" :variant="getStatusVariant(call.status)" size="s"/>
            </div>
          </td>
          <td class="text-center">{{ FormattingService.formatDuration2Units(call.responseTime) }}</td>
          <td class="text-center">
            <div class="data-transferred">
              <div class="data-item">
                <span class="data-label">Req:</span>
                <span class="data-value">{{ FormattingService.formatBytes(call.requestSize) }}</span>
              </div>
              <div class="data-item">
                <span class="data-label">Resp:</span>
                <span class="data-value">{{ FormattingService.formatBytes(call.responseSize) }}</span>
              </div>
            </div>
          </td>
          <td v-if="showTotalSize" class="text-center fw-semibold">
            {{ FormattingService.formatBytes(call.requestSize + call.responseSize) }}
          </td>
          <td v-else>{{ call.host }}:{{ call.port }}</td>
        </tr>
        </tbody>
      </table>
    </div>
  </ChartSection>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import FormattingService from '@/services/FormattingService';
import Badge from '@/components/Badge.vue';
import ChartSection from '@/components/ChartSection.vue';
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
  totalSize?: number;
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

const getPackageName = (fullName: string): string => {
  const lastDot = fullName.lastIndexOf('.');
  return lastDot >= 0 ? fullName.substring(0, lastDot + 1) : '';
};

const getSimpleName = (fullName: string): string => {
  const lastDot = fullName.lastIndexOf('.');
  return lastDot >= 0 ? fullName.substring(lastDot + 1) : fullName;
};

const getStatusVariant = (status: string): Variant => {
  switch (status) {
    case 'OK':
      return 'green';
    case 'NOT_FOUND':
    case 'INVALID_ARGUMENT':
    case 'ALREADY_EXISTS':
    case 'PERMISSION_DENIED':
    case 'FAILED_PRECONDITION':
    case 'OUT_OF_RANGE':
    case 'UNAUTHENTICATED':
    case 'CANCELLED':
      return 'yellow';
    case 'INTERNAL':
    case 'UNKNOWN':
    case 'DEADLINE_EXCEEDED':
    case 'RESOURCE_EXHAUSTED':
    case 'ABORTED':
    case 'UNIMPLEMENTED':
    case 'UNAVAILABLE':
    case 'DATA_LOSS':
      return 'red';
    default:
      return 'grey';
  }
};
</script>

<style scoped>
.grpc-table {
  width: 100%;
  table-layout: fixed;
}

.grpc-table th:nth-child(1) { width: 50%; }
.grpc-table th:nth-child(2) { width: 17%; }
.grpc-table th:nth-child(3) { width: 15%; }
.grpc-table th:nth-child(4) { width: 18%; }

.service-cell {
  font-size: 0.85rem;
}

.call-display {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.service-name-display {
  font-size: 0.85rem;
  background: #f7fafc;
  padding: 0.375rem 0.625rem;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
  display: flex;
  align-items: baseline;
  gap: 1px;
  flex: 1;
  min-height: 2rem;
}

.service-package {
  color: #64748b;
  font-weight: 400;
  font-style: italic;
}

.service-simple {
  color: #1e293b;
  font-weight: 700;
}

.method-separator {
  color: #94a3b8;
  font-weight: 400;
  margin: 0 2px;
}

.method-name {
  color: #334155;
  font-weight: 600;
}

.call-meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.375rem;
}

.call-row:hover {
  background-color: #f8f9fa;
}

.data-transferred {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.75rem;
}

.data-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.data-label {
  font-weight: 600;
  color: #6b7280;
  margin-right: 0.5rem;
}

.data-value {
  font-weight: 500;
  color: #374151;
}
</style>
