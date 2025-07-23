<template>
  <section class="dashboard-section">
    <MetricsList
      :items="displayedGroups"
      :metrics="groupMetrics"
      :sort-options="sortOptions"
      :default-sort="'maxExecutionTime'"
      :item-key="'group'"
      :item-class="getGroupClass"
      title-key="group"
      :loading="false"
      loading-text="Loading JDBC groups..."
      empty-text="No JDBC groups found"
      :show-controls="true"
      :show-metrics="true"
      :show-subtitle="false"
      :sortable="true"
      :selectable="true"
      @item-click="handleGroupClick"
      @sort-change="onSortChange"
    >
      <template #controls-right>
        <button 
          v-if="getAllGroups().length > maxDisplayedGroups"
          @click="showAllGroups = !showAllGroups"
          class="btn btn-sm btn-outline-secondary"
        >
          {{ showAllGroups ? 'Show Less' : `Show All (${getAllGroups().length})` }}
        </button>
      </template>
      
      <template #item-title="{ item }">
        <div class="group-name-display">
          <span class="group-name">{{ item.group }}</span>
        </div>
      </template>
    </MetricsList>
  </section>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import JdbcGroup from '@/services/profile/custom/jdbc/JdbcGroup.ts';
import FormattingService from "@/services/FormattingService.ts";
import MetricsList from '@/components/MetricsList.vue';
import type { MetricDefinition, SortOption } from '@/components/MetricsList.vue';

interface Props {
  groups: JdbcGroup[];
  selectedGroup?: string | null;
}

const props = withDefaults(defineProps<Props>(), {
  selectedGroup: null
});

const emit = defineEmits<{
  groupClick: [group: string]
}>();

// Reactive state
const showAllGroups = ref(false);
const currentSort = ref('maxExecutionTime');
const maxDisplayedGroups = 10;

// Metrics configuration
const groupMetrics: MetricDefinition[] = [
  {
    key: 'count',
    label: 'Executions',
    type: 'number',
    class: 'metric-primary'
  },
  {
    key: 'maxExecutionTime',
    label: 'Max',
    formatter: (value: number) => FormattingService.formatDuration2Units(value || 0),
    class: 'metric-info'
  },
  {
    key: 'p99ExecutionTime',
    label: 'P99',
    formatter: (value: number) => FormattingService.formatDuration2Units(value || 0),
    class: 'metric-info'
  },
  {
    key: 'p95ExecutionTime',
    label: 'P95',
    formatter: (value: number) => FormattingService.formatDuration2Units(value || 0),
    class: 'metric-info'
  },
  {
    key: 'totalRowsProcessed',
    label: 'Rows',
    formatter: (value: number) => FormattingService.formatNumber(value),
    class: 'metric-secondary'
  },
  {
    key: 'errorCount',
    label: 'Errors',
    type: 'number',
    class: (value: number) => value > 0 ? 'metric-danger' : 'metric-success'
  }
];

// Sort options
const sortOptions: SortOption[] = [
  {
    key: 'maxExecutionTime',
    label: 'MAX',
    compare: (a: JdbcGroup, b: JdbcGroup) => b.maxExecutionTime - a.maxExecutionTime
  },
  {
    key: 'p99ExecutionTime',
    label: 'P99',
    compare: (a: JdbcGroup, b: JdbcGroup) => b.p99ExecutionTime - a.p99ExecutionTime
  },
  {
    key: 'p95ExecutionTime',
    label: 'P95',
    compare: (a: JdbcGroup, b: JdbcGroup) => b.p95ExecutionTime - a.p95ExecutionTime
  },
  {
    key: 'errorCount',
    label: 'Errors',
    compare: (a: JdbcGroup, b: JdbcGroup) => b.errorCount - a.errorCount
  },
  {
    key: 'count',
    label: 'Executions',
    compare: (a: JdbcGroup, b: JdbcGroup) => b.count - a.count
  }
];

const getAllGroups = () => {
  return props.groups || [];
};

const displayedGroups = computed(() => {
  const allGroups = getAllGroups();
  return showAllGroups.value ? allGroups : allGroups.slice(0, maxDisplayedGroups);
});

const getGroupClass = (group: JdbcGroup) => {
  const classes = [];
  
  if (props.selectedGroup === group.group) {
    classes.push('selected');
  }
  
  if (group.errorCount > 0) {
    classes.push('has-errors');
  }
  
  return classes.join(' ');
};

const handleGroupClick = (group: JdbcGroup) => {
  emit('groupClick', group.group);
};

const onSortChange = (sortKey: string) => {
  currentSort.value = sortKey;
};
</script>

<style scoped>
.dashboard-section {
  margin-bottom: 2rem;
}

/* Group name display styling */
.group-name-display {
  font-family: 'Poppins', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  font-size: 0.875rem;
  font-weight: 500;
  font-style: italic;
  background: #f7fafc;
  padding: 0.5rem 0.75rem;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.125rem;
  max-width: 100%;
}

.group-name {
  color: #2d3748;
  font-weight: 500;
  font-style: italic;
}

/* Item state styling */
:deep(.metrics-item.selected) {
  background: #f8faff;
  border-left: 4px solid #667eea;
}

:deep(.metrics-item.has-errors) {
  background: #fef2f2 !important;
}

:deep(.metrics-item.selected.has-errors) {
  background: #fceded !important;
  border-left: 4px solid #667eea;
}
</style>
