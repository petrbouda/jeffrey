<template>
  <div v-if="statements.length > 0" class="slowest-list">
    <div v-for="statement in sortedStatements"
         :key="statement.timestamp"
         class="slowest-row"
         @click="handleSqlButtonClick(statement)">
      <div class="left-accent" :class="getAccentClass(statement.operation)"></div>
      <div class="row-content">
        <div class="row-header">
          <div class="row-header-left">
            <JdbcOperationBadge :operation="statement.operation" size="s" borderless />
            <div class="group-text" :title="statement.statementGroup">
              {{ statement.statementGroup }}
            </div>
          </div>
          <div class="time-bar-wrap">
            <span class="time-bar-value">{{ FormattingService.formatDuration2Units(statement.executionTime) }}</span>
            <div class="time-bar-track">
              <div class="time-bar-fill" :style="{ width: getTimePercentage(statement.executionTime) + '%' }"></div>
            </div>
          </div>
        </div>
        <div class="row-details">
          <span class="detail-chip"><i class="bi bi-clock"></i> {{ FormattingService.formatTimestamp(statement.timestamp).replace('T', ' ') }}</span>
          <span class="detail-dot">&middot;</span>
          <span class="detail-chip"><i class="bi bi-list-ol"></i> {{ FormattingService.formatNumber(statement.rowsProcessed) }} rows</span>
          <template v-if="statement.isBatch || statement.isLob">
            <span class="detail-dot">&middot;</span>
            <Badge v-if="statement.isBatch" value="BATCH" variant="blue" size="xs" />
            <Badge v-if="statement.isLob" value="LOB" variant="yellow" size="xs" />
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import JdbcOperationBadge from '@/components/jdbc/JdbcOperationBadge.vue';
import Badge from '@/components/Badge.vue';
import JdbcSlowStatement from '@/services/api/model/JdbcSlowStatement.ts';
import JdbcUtils from '@/services/api/model/JdbcUtils.ts';
import FormattingService from '@/services/FormattingService.ts';

interface Props {
  statements: JdbcSlowStatement[];
}

const props = defineProps<Props>();

const sortedStatements = computed(() =>
    [...props.statements].sort((a, b) => b.executionTime - a.executionTime)
);

const maxExecutionTime = computed(() => {
  if (props.statements.length === 0) return 1;
  return Math.max(...props.statements.map(s => s.executionTime));
});

const getTimePercentage = (executionTime: number): number => {
  return Math.max((executionTime / maxExecutionTime.value) * 100, 2);
};

const getAccentClass = (operation: string): string => {
  const op = JdbcUtils.cleanOperationName(operation).toLowerCase();
  const mapping: Record<string, string> = {
    query: 'accent-blue', select: 'accent-blue',
    insert: 'accent-green',
    update: 'accent-orange',
    delete: 'accent-red'
  };
  return mapping[op] || 'accent-purple';
};

const emit = defineEmits<{
  sqlButtonClick: [statement: JdbcSlowStatement]
}>();

const handleSqlButtonClick = (statement: JdbcSlowStatement) => {
  emit('sqlButtonClick', statement);
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
  cursor: pointer;
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

.accent-blue { background: #1565c0; }
.accent-green { background: var(--color-success); }
.accent-orange { background: var(--color-warning); }
.accent-red { background: var(--color-danger); }
.accent-purple { background: var(--color-purple); }

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
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
  flex: 1;
}

.group-text {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.8rem;
  font-weight: 500;
  color: var(--color-dark);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
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

.sql-button {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  font-family: var(--font-family-base);
  font-size: 0.65rem;
  font-weight: 600;
  color: var(--color-primary);
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.sql-button:hover:not(:disabled) {
  color: var(--color-primary-hover);
  text-decoration: underline;
}

.sql-button:disabled {
  color: var(--color-text-light);
  cursor: not-allowed;
}

.sql-button i {
  font-size: 0.6rem;
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
