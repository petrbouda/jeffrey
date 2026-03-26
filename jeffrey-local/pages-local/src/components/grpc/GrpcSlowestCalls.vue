<template>
  <ChartSection v-if="calls.length > 0" title="Slowest gRPC Calls" icon="clock-history" :full-width="true">
    <div class="table-responsive">
      <table class="table table-hover grpc-table">
        <thead>
        <tr>
          <th>Service.Method</th>
          <th class="text-center">Response Time</th>
          <th class="text-center">Status</th>
          <th class="text-center">Data Transferred</th>
          <th>Remote</th>
          <th>Timestamp</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="call in displayedCalls"
            :key="call.timestamp"
            class="call-row">
          <td class="service-cell">
            <div class="service-method-display">
              <span class="service-part">{{ call.service }}</span>
              <span class="method-separator">/</span>
              <span class="method-part">{{ call.method }}</span>
            </div>
          </td>
          <td class="text-center">{{ FormattingService.formatDuration2Units(call.responseTime) }}</td>
          <td class="text-center">
            <Badge :value="call.status" :variant="getStatusVariant(call.status)" size="s"/>
          </td>
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
          <td>{{ call.host }}:{{ call.port }}</td>
          <td>
            <Badge
                :value="FormattingService.formatTimestamp(call.timestamp)"
                icon="bi-clock"
                variant="grey"
                size="s"
            />
          </td>
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
import type {GrpcSlowCall} from '@/services/api/ProfileGrpcClient';

interface Props {
  calls: GrpcSlowCall[];
  totalCallCount: number;
  maxDisplayed?: number;
}

const props = withDefaults(defineProps<Props>(), {
  maxDisplayed: 20
});

const displayedCalls = computed(() => {
  return props.calls.slice(0, props.maxDisplayed);
});

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

.grpc-table th:nth-child(1) { width: 35%; }
.grpc-table th:nth-child(2) { width: 13%; }
.grpc-table th:nth-child(3) { width: 13%; }
.grpc-table th:nth-child(4) { width: 13%; }
.grpc-table th:nth-child(5) { width: 12%; }
.grpc-table th:nth-child(6) { width: 14%; }

.service-cell {
  font-family: 'Courier New', monospace;
  font-size: 0.85rem;
}

.service-method-display {
  display: flex;
  align-items: center;
  gap: 0.125rem;
  flex-wrap: wrap;
}

.service-part {
  color: #2d3748;
  font-weight: 600;
}

.method-separator {
  color: #718096;
  font-weight: 400;
}

.method-part {
  color: #4a5568;
  font-weight: 500;
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
