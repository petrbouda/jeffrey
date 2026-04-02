<template>
  <div class="group-list">
    <!-- Controls -->
    <div class="group-controls">
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
        v-if="groups.length > maxDisplayedGroups"
        @click="showAllGroups = !showAllGroups"
        class="btn btn-sm btn-outline-secondary"
      >
        {{ showAllGroups ? 'Show Less' : `Show All (${groups.length})` }}
      </button>
    </div>

    <!-- Group Cards -->
    <div class="group-cards">
      <div
        v-for="group in displayedGroups"
        :key="group.group"
        class="grp-card"
        @click="handleGroupClick(group)"
      >
        <!-- Left: Execution Count Pill -->
        <div class="grp-count-pill">
          <span class="grp-count-num">{{ FormattingService.formatNumber(group.count) }}</span>
          <span class="grp-count-label">executions</span>
        </div>

        <!-- Center: Group Name + Metrics -->
        <div class="grp-main">
          <div class="grp-name" :title="group.group">{{ group.group }}</div>
          <div class="grp-metrics">
            <Badge
              key-label="Max"
              :value="FormattingService.formatDuration2Units(group.maxExecutionTime)"
              variant="info"
              size="s"
              borderless
            />
            <Badge
              key-label="P99"
              :value="FormattingService.formatDuration2Units(group.p99ExecutionTime)"
              variant="info"
              size="s"
              borderless
            />
            <Badge
              key-label="P95"
              :value="FormattingService.formatDuration2Units(group.p95ExecutionTime)"
              variant="info"
              size="s"
              borderless
            />
            <Badge
              key-label="Rows"
              :value="FormattingService.formatNumber(group.totalRowsProcessed)"
              variant="secondary"
              size="s"
              borderless
            />
          </div>
        </div>

        <!-- Right: Error Box + Arrow -->
        <div class="grp-right">
          <div v-if="group.errorCount > 0" class="grp-err">
            <span class="grp-err-num">{{ group.errorCount }}</span>
            <span class="grp-err-label">errors</span>
          </div>
          <i class="bi bi-chevron-right grp-arrow"></i>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import JdbcGroup from '@/services/api/model/JdbcGroup.ts';
import FormattingService from '@/services/FormattingService.ts';
import Badge from '@/components/Badge.vue';

interface Props {
  groups: JdbcGroup[];
  selectedGroup?: string | null;
}

const props = withDefaults(defineProps<Props>(), {
  selectedGroup: null
});

const emit = defineEmits<{
  groupClick: [group: string];
}>();

const showAllGroups = ref(false);
const currentSort = ref('maxExecutionTime');
const maxDisplayedGroups = 10;

const sortOptions = [
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
  { key: 'count', label: 'Executions', compare: (a: JdbcGroup, b: JdbcGroup) => b.count - a.count }
];

const sortedGroups = computed(() => {
  const option = sortOptions.find(o => o.key === currentSort.value);
  if (!option) return props.groups;
  return [...props.groups].sort(option.compare);
});

const displayedGroups = computed(() => {
  return showAllGroups.value ? sortedGroups.value : sortedGroups.value.slice(0, maxDisplayedGroups);
});

const handleGroupClick = (group: JdbcGroup) => {
  emit('groupClick', group.group);
};

const onSortChange = (key: string) => {
  currentSort.value = key;
};
</script>

<style scoped>
.group-controls {
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

.group-cards {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.grp-card {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.875rem 1rem;
  border: 1px solid var(--card-border-color);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}

.grp-card:hover {
  border-color: var(--color-primary);
  box-shadow: 0 2px 8px rgba(94, 100, 255, 0.1);
}

/* Left: Count Pill */
.grp-count-pill {
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

.grp-count-num {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 1rem;
  font-weight: 700;
  color: var(--color-primary);
}

.grp-count-label {
  font-size: 0.55rem;
  font-weight: 600;
  text-transform: uppercase;
  color: var(--color-text-muted);
  letter-spacing: 0.5px;
}

/* Center: Name + Metrics */
.grp-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.grp-name {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.8rem;
  font-weight: 500;
  color: var(--color-dark);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.grp-metrics {
  display: flex;
  gap: 0.35rem;
  flex-wrap: wrap;
}

/* Right: Error Box + Arrow */
.grp-right {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-shrink: 0;
}

.grp-err {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.1rem;
  padding: 0.4rem 0.6rem;
  border-radius: var(--radius-base);
  min-width: 40px;
  background: rgba(230, 55, 87, 0.1);
}

.grp-err-num {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--color-danger);
}

.grp-err-label {
  font-size: 0.5rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--color-danger);
}

.grp-arrow {
  color: var(--color-text-light);
  font-size: 1rem;
}

@media (max-width: 768px) {
  .grp-card {
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .group-controls {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }
}
</style>
