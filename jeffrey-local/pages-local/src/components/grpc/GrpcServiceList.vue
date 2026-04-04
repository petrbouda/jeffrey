<template>
  <div class="service-list">
    <!-- Controls -->
    <div class="service-controls">
      <div class="sort-controls">
        <label class="sort-label">Sort by:</label>
        <div class="btn-group" role="group">
          <button
            v-for="option in sortOptions"
            :key="option.key"
            type="button"
            class="btn btn-outline-secondary btn-sm"
            :class="{ active: currentSort === option.key }"
            @click="onSortChange(option.key)"
          >
            {{ option.label }}
          </button>
        </div>
      </div>
      <button
        v-if="services.length > maxDisplayedServices"
        @click="showAllServices = !showAllServices"
        class="btn btn-sm btn-outline-secondary"
      >
        {{ showAllServices ? 'Show Less' : `Show All (${services.length})` }}
      </button>
    </div>

    <!-- Service Cards -->
    <div class="service-cards">
      <div
        v-for="service in displayedServices"
        :key="service.service"
        class="svc-card"
        @click="handleServiceClick(service)"
      >
        <!-- Left: Call Count Pill -->
        <div class="svc-count-pill">
          <span class="svc-count-num">{{ FormattingService.formatNumber(service.callCount) }}</span>
          <span class="svc-count-label">calls</span>
        </div>

        <!-- Center: Service Name + Metrics -->
        <div class="svc-main">
          <div class="svc-name" :title="service.service">
            <span class="svc-package">{{ getPackageName(service.service) }}</span>
            <span class="svc-simple">{{ getSimpleName(service.service) }}</span>
          </div>
          <div class="svc-metrics">
            <Badge
              key-label="Max"
              :value="FormattingService.formatDuration2Units(service.maxResponseTime)"
              variant="info"
              size="s"
              borderless
            />
            <Badge
              key-label="P99"
              :value="FormattingService.formatDuration2Units(service.p99ResponseTime)"
              variant="info"
              size="s"
              borderless
            />
            <Badge
              key-label="P95"
              :value="FormattingService.formatDuration2Units(service.p95ResponseTime)"
              variant="info"
              size="s"
              borderless
            />
            <Badge
              v-if="service.avgRequestSize >= 0"
              key-label="Avg Req"
              :value="FormattingService.formatBytes(service.avgRequestSize)"
              variant="secondary"
              size="s"
              borderless
            />
            <Badge
              v-if="service.avgResponseSize >= 0"
              key-label="Avg Resp"
              :value="FormattingService.formatBytes(service.avgResponseSize)"
              variant="secondary"
              size="s"
              borderless
            />
          </div>
        </div>

        <!-- Right: Success Rate + Arrow -->
        <div class="svc-right">
          <div
            v-if="service.successRate < 1"
            class="svc-rate"
            :class="service.successRate < 0.95 ? 'rate-danger' : 'rate-warning'"
          >
            <span class="svc-rate-num">{{ ((service.successRate || 0) * 100).toFixed(1) }}%</span>
            <span class="svc-rate-label">success</span>
          </div>
          <i class="bi bi-chevron-right svc-arrow"></i>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import FormattingService from '@/services/FormattingService';
import Badge from '@/components/Badge.vue';
import type { GrpcServiceInfo } from '@/services/api/ProfileGrpcClient';

interface Props {
  services: GrpcServiceInfo[];
  selectedService?: string | null;
}

const props = withDefaults(defineProps<Props>(), {
  selectedService: null
});

const emit = defineEmits<{
  serviceClick: [service: string];
}>();

const showAllServices = ref(false);
const currentSort = ref('maxResponseTime');
const maxDisplayedServices = 10;

const sortOptions = [
  {
    key: 'maxResponseTime',
    label: 'MAX',
    compare: (a: GrpcServiceInfo, b: GrpcServiceInfo) => b.maxResponseTime - a.maxResponseTime
  },
  {
    key: 'p95ResponseTime',
    label: 'P95',
    compare: (a: GrpcServiceInfo, b: GrpcServiceInfo) => b.p95ResponseTime - a.p95ResponseTime
  },
  {
    key: 'callCount',
    label: 'Calls',
    compare: (a: GrpcServiceInfo, b: GrpcServiceInfo) => b.callCount - a.callCount
  },
  {
    key: 'successRate',
    label: 'Success',
    compare: (a: GrpcServiceInfo, b: GrpcServiceInfo) => a.successRate - b.successRate
  }
];

const sortedServices = computed(() => {
  const option = sortOptions.find(o => o.key === currentSort.value);
  if (!option) return props.services;
  return [...props.services].sort(option.compare);
});

const displayedServices = computed(() => {
  return showAllServices.value
    ? sortedServices.value
    : sortedServices.value.slice(0, maxDisplayedServices);
});

const getPackageName = (fullName: string): string => {
  const lastDot = fullName.lastIndexOf('.');
  return lastDot >= 0 ? fullName.substring(0, lastDot + 1) : '';
};

const getSimpleName = (fullName: string): string => {
  const lastDot = fullName.lastIndexOf('.');
  return lastDot >= 0 ? fullName.substring(lastDot + 1) : fullName;
};

const handleServiceClick = (service: GrpcServiceInfo) => {
  emit('serviceClick', service.service);
};

const onSortChange = (key: string) => {
  currentSort.value = key;
};
</script>

<style scoped>
.service-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.75rem;
}

.sort-controls {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.sort-label {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--color-text-muted);
}

.service-cards {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.svc-card {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.875rem 1rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}

.svc-card:hover {
  border-color: var(--color-primary);
  box-shadow: 0 2px 8px rgba(94, 100, 255, 0.1);
}

/* Left: Count Pill */
.svc-count-pill {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.1rem;
  padding: 0.5rem 0.75rem;
  background: var(--color-primary-light);
  border-radius: var(--radius-md);
  min-width: 60px;
  flex-shrink: 0;
}

.svc-count-num {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 1rem;
  font-weight: 700;
  color: var(--color-primary);
}

.svc-count-label {
  font-size: 0.55rem;
  font-weight: 600;
  text-transform: uppercase;
  color: var(--color-text-muted);
  letter-spacing: 0.5px;
}

/* Center: Name + Metrics */
.svc-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.svc-name {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.8rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.svc-package {
  color: var(--color-text-muted);
  font-weight: 400;
}

.svc-simple {
  color: var(--color-dark);
  font-weight: 700;
}

.svc-metrics {
  display: flex;
  gap: 0.35rem;
  flex-wrap: wrap;
}

/* Right: Success Rate + Arrow */
.svc-right {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-shrink: 0;
}

.svc-rate {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.1rem;
  padding: 0.4rem 0.6rem;
  border-radius: var(--radius-base);
  min-width: 50px;
}

.rate-warning {
  background: rgba(245, 128, 62, 0.1);
}

.rate-danger {
  background: rgba(230, 55, 87, 0.1);
}

.rate-warning .svc-rate-num {
  color: var(--color-warning-hover);
}

.rate-danger .svc-rate-num {
  color: var(--color-danger);
}

.svc-rate-num {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.8rem;
  font-weight: 700;
}

.svc-rate-label {
  font-size: 0.5rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--color-text-muted);
}

.svc-arrow {
  color: var(--color-text-light);
  font-size: 1rem;
}

@media (max-width: 768px) {
  .svc-card {
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .service-controls {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }
}
</style>
