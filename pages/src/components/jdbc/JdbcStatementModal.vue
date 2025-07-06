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
  <GenericModal
    :modal-id="modalId"
    :show="show"
    title="SQL Statement Details"
    icon="bi-database"
    size="xl"
    @update:show="$emit('update:show', $event)">
          <div v-if="statement" class="statement-details">
            <!-- Statement Info Header -->
            <div class="statement-info-header mb-4">
              <div class="d-flex align-items-center gap-3 mb-2">
                <span class="statement-group-badge">{{ statement.statementGroup }}</span>
                <JdbcOperationBadge :operation="statement.operation" />
                <span v-if="statement.isBatch" class="statement-method-badge method-batch">Batch</span>
                <span v-if="statement.isLob" class="statement-method-badge method-lob">LOB</span>
              </div>
              <div class="statement-metrics">
                <div class="metric-item">
                  <i class="bi bi-clock"></i>
                  <span class="metric-label">Execution Time:</span>
                  <span class="metric-value text-danger fw-bold">{{
                      FormattingService.formatDuration2Units(statement.executionTime)
                    }}</span>
                </div>
                <div class="metric-item">
                  <i class="bi bi-list-ol"></i>
                  <span class="metric-label">Rows Processed:</span>
                  <span class="metric-value">{{ FormattingService.formatNumber(statement.rowsProcessed) }}</span>
                </div>
                <div class="metric-item">
                  <i class="bi bi-calendar-event"></i>
                  <span class="metric-label">Timestamp:</span>
                  <span class="metric-value">{{ FormattingService.formatTimestamp(statement.timestamp) }}</span>
                </div>
              </div>
            </div>

            <!-- SQL Statement -->
            <div class="sql-section mb-4">
              <div class="section-header">
                <h6><i class="bi bi-code-slash me-2"></i>SQL Statement</h6>
                <button type="button" class="btn btn-sm btn-outline-secondary" @click="copySql">
                  <i class="bi bi-clipboard me-1"></i>Copy SQL
                </button>
              </div>
              <div class="sql-container">
                <pre class="sql-content"><code>{{ formatSql(statement.sql) }}</code></pre>
              </div>
            </div>

            <!-- Parameters -->
            <div class="parameters-section" v-if="statement.parameters && statement.parameters !== '[]'">
              <div class="section-header">
                <h6><i class="bi bi-gear me-2"></i>Parameters</h6>
                <button type="button" class="btn btn-sm btn-outline-secondary" @click="copyParameters">
                  <i class="bi bi-clipboard me-1"></i>Copy Parameters
                </button>
              </div>
              <div class="parameters-container">
                <pre class="parameters-content"><code>{{ formatParameters(statement.parameters) }}</code></pre>
              </div>
            </div>

            <!-- No Parameters Message -->
            <div class="parameters-section" v-else>
              <div class="section-header">
                <h6><i class="bi bi-gear me-2"></i>Parameters</h6>
              </div>
              <div class="no-parameters">
                <i class="bi bi-info-circle me-2"></i>
                No parameters for this statement
              </div>
            </div>
          </div>
  </GenericModal>
</template>

<script setup lang="ts">
import GenericModal from '@/components/GenericModal.vue';
import JdbcSlowStatement from '@/services/profile/custom/jdbc/JdbcSlowStatement.ts';
import FormattingService from "@/services/FormattingService.ts";
import JdbcOperationBadge from '@/components/jdbc/JdbcOperationBadge.vue';

interface Props {
  statement: JdbcSlowStatement | null;
  modalId: string;
  show: boolean;
}

const props = defineProps<Props>();

defineEmits(['update:show']);

const formatSql = (sql: string): string => {
  if (!sql) return '';

  // Basic SQL formatting - add line breaks after major keywords
  return sql
      .replace(/\bSELECT\b/gi, '\nSELECT')
      .replace(/\bFROM\b/gi, '\nFROM')
      .replace(/\bWHERE\b/gi, '\nWHERE')
      .replace(/\bAND\b/gi, '\n  AND')
      .replace(/\bOR\b/gi, '\n  OR')
      .replace(/\bORDER BY\b/gi, '\nORDER BY')
      .replace(/\bGROUP BY\b/gi, '\nGROUP BY')
      .replace(/\bHAVING\b/gi, '\nHAVING')
      .replace(/\bJOIN\b/gi, '\nJOIN')
      .replace(/\bLEFT JOIN\b/gi, '\nLEFT JOIN')
      .replace(/\bRIGHT JOIN\b/gi, '\nRIGHT JOIN')
      .replace(/\bINNER JOIN\b/gi, '\nINNER JOIN')
      .replace(/\bINSERT INTO\b/gi, '\nINSERT INTO')
      .replace(/\bVALUES\b/gi, '\nVALUES')
      .replace(/\bUPDATE\b/gi, '\nUPDATE')
      .replace(/\bSET\b/gi, '\nSET')
      .replace(/\bDELETE FROM\b/gi, '\nDELETE FROM')
      .trim();
};

const formatParameters = (parameters: string): string => {
  if (!parameters || parameters === '[]') return 'No parameters';

  try {
    // Try to parse as JSON array and format nicely
    const parsed = JSON.parse(parameters);
    if (Array.isArray(parsed)) {
      return parsed.map((param, index) => `[${index}] ${param}`).join('\n');
    }
    return parameters;
  } catch {
    // If not valid JSON, return as-is
    return parameters;
  }
};

const copySql = async () => {
  if (props.statement?.sql) {
    try {
      await navigator.clipboard.writeText(props.statement.sql);
    } catch (err) {
      console.error('Failed to copy SQL:', err);
    }
  }
};

const copyParameters = async () => {
  if (props.statement?.parameters) {
    try {
      await navigator.clipboard.writeText(props.statement.parameters);
    } catch (err) {
      console.error('Failed to copy parameters:', err);
    }
  }
};
</script>

<style scoped>
.statement-details {
  font-size: 0.9rem;
}

.statement-info-header {
  background: #f8f9fa;
  padding: 1rem;
  border-radius: 8px;
  border: 1px solid #e9ecef;
}

.statement-method-badge {
  padding: 0.375rem 0.625rem;
  border-radius: 5px;
  font-size: 0.8rem;
  font-weight: 600;
  text-transform: uppercase;
}

.statement-group-badge {
  padding: 0.375rem 0.625rem;
  border-radius: 5px;
  font-size: 0.8rem;
  font-weight: 600;
  font-style: italic;
  background-color: #f3e5f5;
  color: #7b1fa2;
  border: 1px solid #e1bee7;
}


.statement-method-badge.method-batch {
  background-color: #cce5ff;
  color: #004085;
}

.statement-method-badge.method-lob {
  background-color: #fff3cd;
  color: #856404;
}

.statement-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 1.5rem;
  margin-top: 0.75rem;
}

.metric-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.metric-item i {
  color: #6c757d;
  font-size: 0.9rem;
}

.metric-label {
  color: #6c757d;
  font-weight: 500;
}

.metric-value {
  font-weight: 600;
  color: #495057;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.section-header h6 {
  margin: 0;
  font-weight: 600;
  color: #495057;
}

.sql-container,
.parameters-container {
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 6px;
  overflow: hidden;
}

.sql-content,
.parameters-content {
  margin: 0;
  padding: 1rem;
  background: #2d3748;
  color: #e2e8f0;
  font-family: 'SF Mono', 'Monaco', monospace;
  font-size: 0.85rem;
  line-height: 1.4;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow-y: auto;
}

.no-parameters {
  padding: 1rem;
  text-align: center;
  color: #6c757d;
  font-style: italic;
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 6px;
}


@media (max-width: 768px) {
  .statement-metrics {
    flex-direction: column;
    gap: 0.75rem;
  }

  .section-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }

  .sql-content,
  .parameters-content {
    font-size: 0.75rem;
    max-height: 200px;
  }
}
</style>
