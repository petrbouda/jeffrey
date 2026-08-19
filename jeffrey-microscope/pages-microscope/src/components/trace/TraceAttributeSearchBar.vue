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

<template>
  <MainCard>
    <div class="search-bar">
      <div class="bar-row">
        <span class="bar-label">Match traces where</span>

        <!--
          The scope is not a preference, it is the question. A trace whose HTTP span carries the
          tenant and whose JDBC span carries the row count matches "anywhere in the trace" and does
          not match "all on one span" — attributes are per-span and are never inherited — so the
          choice is stated in words rather than left for a reader to infer from the results.
        -->
        <div class="btn-group btn-group-sm scope-toggle" role="group" aria-label="Condition scope">
          <button
            v-for="option in SCOPES"
            :key="option.scope"
            type="button"
            class="btn"
            :class="scope === option.scope ? 'btn-primary' : 'btn-outline-secondary'"
            :title="option.hint"
            @click="$emit('update:scope', option.scope)"
          >
            {{ option.label }}
          </button>
        </div>
      </div>

      <div class="bar-row conditions">
        <template v-for="(condition, index) in conditions" :key="index">
          <span v-if="index > 0" class="conjunction">AND</span>
          <span class="condition-chip">
            <span class="chip-key" :class="`chip-${condition.source.toLowerCase()}`">
              {{ condition.key }}
            </span>
            <span class="chip-operator">{{ OPERATOR_SYMBOLS[condition.operator] }}</span>
            <span v-if="condition.value !== null" class="chip-value">{{ condition.value }}</span>
            <button
              type="button"
              class="chip-remove"
              :aria-label="`Remove condition on ${condition.key}`"
              @click="remove(index)"
            >
              <i class="bi bi-x"></i>
            </button>
          </span>
        </template>

        <span v-if="conditions.length === 0" class="no-conditions">
          every trace — pick a key on the left, or add a condition
        </span>
      </div>

      <div class="bar-row builder">
        <!--
          Grouped and annotated because this is now the only place a key is picked for a search: the
          rail beside the breakdown pages does not appear here, so what it used to say about a key —
          where it came from, how many values it has, whether it is too wide to break down — has to
          be readable at the moment of choosing.
        -->
        <select v-model="draftKey" class="form-select form-select-sm builder-key">
          <option :value="null" disabled>Choose a key…</option>
          <optgroup v-for="group in keyGroups" :key="group.source" :label="group.label">
            <option v-for="key in group.keys" :key="keyOf(key)" :value="keyOf(key)">
              {{ optionLabel(key) }}
            </option>
          </optgroup>
        </select>

        <select v-model="draftOperator" class="form-select form-select-sm builder-operator">
          <option v-for="operator in availableOperators" :key="operator" :value="operator">
            {{ OPERATOR_SYMBOLS[operator] }}&nbsp;{{ OPERATOR_LABELS[operator] }}
          </option>
        </select>

        <input
          v-if="draftOperator !== 'EXISTS'"
          v-model="draftValue"
          type="text"
          class="form-control form-control-sm builder-value"
          :placeholder="valuePlaceholder"
          @keyup.enter="add"
        />

        <button type="button" class="btn btn-sm btn-primary" :disabled="!canAdd" @click="add">
          <i class="bi bi-plus-lg"></i> Add condition
        </button>

        <button
          v-if="conditions.length > 0"
          type="button"
          class="btn btn-sm btn-outline-secondary"
          @click="$emit('update:conditions', [])"
        >
          Clear
        </button>
      </div>

      <p v-if="draftKeyRow?.searchOnly" class="builder-note">
        <i class="bi bi-info-circle"></i>
        <span class="mono">{{ draftKeyRow.key }}</span> has
        {{ FormattingService.formatNumber(draftKeyRow.distinctValues) }} values, so it is not broken
        down anywhere — searching it is what it is for.
      </p>
    </div>
  </MainCard>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import MainCard from '@shared/components/MainCard.vue';
import FormattingService from '@shared/services/FormattingService';
import type {
  TraceAttributeConditionModel,
  TraceAttributeKeyRow,
  TraceAttributeOperator,
  TraceAttributeScope,
  TraceAttributeSource
} from '@/services/api/model/trace/TraceAttributeModels';

const props = defineProps<{
  keys: TraceAttributeKeyRow[];
  conditions: TraceAttributeConditionModel[];
  scope: TraceAttributeScope;
}>();

const emit = defineEmits<{
  'update:conditions': [conditions: TraceAttributeConditionModel[]];
  'update:scope': [scope: TraceAttributeScope];
}>();

const SCOPES: Array<{ scope: TraceAttributeScope; label: string; hint: string }> = [
  {
    scope: 'TRACE',
    label: 'Anywhere in the trace',
    hint: 'Each condition may be satisfied by a different span'
  },
  {
    scope: 'SPAN',
    label: 'All on one span',
    hint: 'Every condition has to be satisfied by the same single span'
  }
];

const OPERATOR_SYMBOLS: Record<TraceAttributeOperator, string> = {
  EQ: '=',
  NOT_EQ: '≠',
  CONTAINS: '⊃',
  GT: '>',
  GTE: '≥',
  LT: '<',
  LTE: '≤',
  EXISTS: '∃'
};

const OPERATOR_LABELS: Record<TraceAttributeOperator, string> = {
  EQ: 'is',
  NOT_EQ: 'is not',
  CONTAINS: 'contains',
  GT: 'greater than',
  GTE: 'at least',
  LT: 'less than',
  LTE: 'at most',
  EXISTS: 'is present'
};

/** Comparisons read the numeric column, which only a numeric value is ever stored in. */
const NUMERIC_OPERATORS: TraceAttributeOperator[] = ['GT', 'GTE', 'LT', 'LTE'];
const TEXT_OPERATORS: TraceAttributeOperator[] = ['EQ', 'NOT_EQ', 'CONTAINS'];

const draftKey = ref<string | null>(null);
const draftOperator = ref<TraceAttributeOperator>('EQ');
const draftValue = ref('');

function keyOf(key: TraceAttributeKeyRow): string {
  return `${key.source}~${key.owner ?? ''}~${key.key}`;
}

const draftKeyRow = computed<TraceAttributeKeyRow | null>(
  () => props.keys.find(key => keyOf(key) === draftKey.value) ?? null
);

/** The order the groups appear in: attached by hand, declared by the event, then the span's own. */
const KEY_GROUPS: Array<{ source: TraceAttributeSource; label: string }> = [
  { source: 'ATTRIBUTE', label: 'Span attributes' },
  { source: 'EVENT_FIELD', label: 'Event fields' },
  { source: 'SPAN_SHAPE', label: 'Span shape' }
];

const keyGroups = computed(() =>
  KEY_GROUPS.map(group => ({
    ...group,
    keys: props.keys.filter(key => key.source === group.source)
  })).filter(group => group.keys.length > 0)
);

/**
 * The key, its owner where it has one, and what its values look like. A search-only key says so
 * instead of showing a count: the number is the reason it is search-only, and "41,208 values" reads
 * as a promise of a breakdown that no page will give.
 */
function optionLabel(key: TraceAttributeKeyRow): string {
  const name = key.owner ? `${key.key} · ${key.owner}` : key.key;
  const shape = key.searchOnly
    ? 'search only'
    : `${FormattingService.formatNumber(key.distinctValues)} values`;
  return `${name} — ${shape}`;
}

/**
 * Only the operators the key's values can answer. Offering `>` on a string key produces a condition
 * that matches nothing and explains nothing, and offering `contains` on a two-valued boolean reads
 * as a mistake.
 */
const availableOperators = computed<TraceAttributeOperator[]>(() => {
  const kind = draftKeyRow.value?.valueKind;
  if (kind === 'NUMBER') {
    return [...TEXT_OPERATORS, ...NUMERIC_OPERATORS, 'EXISTS'];
  }
  if (kind === 'BOOLEAN') {
    return ['EQ', 'NOT_EQ', 'EXISTS'];
  }
  return [...TEXT_OPERATORS, 'EXISTS'];
});

// A key changed under a comparison it cannot answer leaves the operator select showing something
// the list below no longer offers, which then submits as-is.
watch(availableOperators, operators => {
  if (!operators.includes(draftOperator.value)) {
    draftOperator.value = operators[0];
  }
});

const valuePlaceholder = computed(() => {
  const kind = draftKeyRow.value?.valueKind;
  if (kind === 'NUMBER') {
    return 'a number';
  }
  if (kind === 'BOOLEAN') {
    return 'true or false';
  }
  return 'a value';
});

const canAdd = computed(() => {
  if (draftKeyRow.value === null) {
    return false;
  }
  if (draftOperator.value === 'EXISTS') {
    return true;
  }
  if (draftValue.value.trim() === '') {
    return false;
  }
  return !NUMERIC_OPERATORS.includes(draftOperator.value) || !isNaN(Number(draftValue.value));
});

function add(): void {
  const key = draftKeyRow.value;
  if (key === null || !canAdd.value) {
    return;
  }

  emit('update:conditions', [
    ...props.conditions,
    {
      source: key.source,
      owner: key.owner,
      key: key.key,
      operator: draftOperator.value,
      value: draftOperator.value === 'EXISTS' ? null : draftValue.value.trim()
    }
  ]);

  draftValue.value = '';
}

function remove(index: number): void {
  emit(
    'update:conditions',
    props.conditions.filter((_, position) => position !== index)
  );
}

/** Lets the rail put a key into the builder without the parent reaching into this component. */
defineExpose({
  useKey(key: TraceAttributeKeyRow): void {
    draftKey.value = keyOf(key);
  }
});
</script>

<style scoped>
.search-bar {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-3);
}

.bar-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  flex-wrap: wrap;
}

.bar-label {
  font-size: var(--font-size-sm);
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.scope-toggle {
  margin-left: auto;
}

.conditions {
  min-height: 30px;
}

.conjunction {
  font-size: var(--font-size-xs);
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--color-text-muted);
}

.no-conditions {
  font-size: var(--font-size-base);
  color: var(--color-text-muted);
}

.condition-chip {
  display: inline-flex;
  align-items: stretch;
  border: 1px solid var(--color-border-input);
  border-radius: var(--radius-base);
  overflow: hidden;
  background: var(--color-white);
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-base);
}

.chip-key,
.chip-operator,
.chip-value {
  padding: 3px var(--spacing-2);
  display: inline-flex;
  align-items: center;
}

.chip-key {
  background: var(--color-light);
  color: var(--color-dark);
  font-weight: 500;
}

.chip-key.chip-event_field {
  color: var(--color-info-text);
}

.chip-key.chip-span_shape {
  color: var(--color-text-muted);
}

.chip-operator {
  color: var(--color-primary);
  font-weight: 700;
  border-left: 1px solid var(--color-border-light);
  border-right: 1px solid var(--color-border-light);
}

.chip-value {
  color: var(--color-dark);
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chip-remove {
  border: 0;
  background: transparent;
  color: var(--color-text-light);
  padding: 0 var(--spacing-2) 0 0;
  cursor: pointer;
}

.chip-remove:hover {
  color: var(--color-danger);
}

.builder-key {
  max-width: 260px;
}

.builder-operator {
  max-width: 170px;
}

.builder-value {
  max-width: 260px;
  font-family: var(--font-family-monospace);
}

.builder-note {
  margin: 0;
  font-size: var(--font-size-base);
  color: var(--color-text-muted);
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
}

.mono {
  font-family: var(--font-family-monospace);
  color: var(--color-dark);
}
</style>
