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
  <MainCard :no-padding="true" class="search-conditions-card">
    <div class="clause-head">
      <span class="clause-title">Search Conditions</span>

      <!--
        The scope is not a preference, it is the question. A trace whose HTTP span carries the
        tenant and whose JDBC span carries the row count matches "anywhere in the trace" and does
        not match "all on one span" — attributes are per-span and are never inherited — so the
        choice is stated in words rather than left for a reader to infer from the results.
      -->
      <SegmentedSwitch
        class="scope-switch"
        :model-value="scope"
        :options="SCOPES"
        group-label="Condition scope"
        @update:model-value="$emit('update:scope', $event)"
      />
    </div>

    <!--
      One condition per row, structured like the WHERE clause it is. The keyword rail carries the
      shape of the query, and each row names its source — a value a developer attached at a call
      site and a field an event type declares are kept apart everywhere else, so they are kept
      apart here too.
    -->
    <div v-for="(condition, index) in conditions" :key="index" class="clause-row">
      <span class="clause-keyword" :class="{ and: index > 0 }">
        {{ index === 0 ? 'WHERE' : 'AND' }}
      </span>

      <span class="clause-expr">
        <span class="expr-key">{{ condition.key }}</span>
        <span
          class="expr-source"
          :class="`source-${condition.source.toLowerCase()}`"
          :title="condition.owner ?? undefined"
        >
          {{ SOURCE_LABELS[condition.source] }}
        </span>
        <span class="expr-operator">{{ OPERATOR_SYMBOLS[condition.operator] }}</span>
        <span v-if="condition.value !== null" class="expr-value">{{ condition.value }}</span>
      </span>

      <button
        type="button"
        class="clause-remove"
        :aria-label="`Remove condition on ${condition.key}`"
        @click="remove(index)"
      >
        <i class="bi bi-x-lg"></i>
      </button>
    </div>

    <div class="clause-add">
      <!-- A real WHERE while the query is empty — it is the first word of the sentence being
           written, not an echo of one written above. Only the trailing AND stays quiet. -->
      <span class="clause-keyword" :class="{ ghost: conditions.length > 0 }">
        {{ conditions.length === 0 ? 'WHERE' : 'AND' }}
      </span>

      <!--
        The shared two-step picker: which event type, then which of the things its carriers held. The
        selection stays a draft here — it becomes real only when the condition is added.
      -->
      <TraceAttributeStepPicker
        :event-types="eventTypes"
        :selected-type="draftType"
        :selected-key-name="draftKey?.key ?? null"
        include-search-only
        @pick-type="onPickType"
        @pick-key="onPickKey"
      />

      <!--
        Not usable until a key is chosen, and marked the way the picker marks its not-yet-takeable
        step: dashed on a grey ground rather than faded out.
      -->
      <span class="builder-rest">
        <select
          v-model="draftOperator"
          class="form-select form-select-sm builder-operator"
          :disabled="draftKey === null"
        >
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
          :disabled="draftKey === null"
          @keyup.enter="add"
        />

        <button
          type="button"
          class="btn btn-sm btn-primary clause-add-btn"
          :disabled="!canAdd"
          @click="add"
        >
          <i class="bi bi-plus-lg"></i> Add
        </button>
      </span>

      <button
        v-if="conditions.length > 0"
        type="button"
        class="btn btn-sm btn-outline-secondary clause-clear"
        @click="$emit('update:conditions', [])"
      >
        Clear all
      </button>
    </div>

    <p v-if="draftKey?.searchOnly" class="builder-note">
      <i class="bi bi-info-circle"></i>
      <span class="mono">{{ draftKey.key }}</span> has
      {{ FormattingService.formatNumber(draftKey.distinctValues) }} values, so it is not broken
      down anywhere — searching it is what it is for.
    </p>
  </MainCard>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import MainCard from '@shared/components/MainCard.vue';
import SegmentedSwitch from '@shared/components/SegmentedSwitch.vue';
import type { SegmentedOption } from '@shared/components/SegmentedSwitch.vue';
import FormattingService from '@shared/services/FormattingService';
import TraceAttributeStepPicker from '@/components/trace/TraceAttributeStepPicker.vue';
import type {
  TraceAttributeConditionModel,
  TraceAttributeKeyRow,
  TraceAttributeOperator,
  TraceAttributeScope,
  TraceAttributeSource,
  TraceEventTypeRow
} from '@/services/api/model/trace/TraceAttributeModels';

const props = defineProps<{
  eventTypes: TraceEventTypeRow[];
  conditions: TraceAttributeConditionModel[];
  scope: TraceAttributeScope;
}>();

const emit = defineEmits<{
  'update:conditions': [conditions: TraceAttributeConditionModel[]];
  'update:scope': [scope: TraceAttributeScope];
}>();

const SCOPES: SegmentedOption<TraceAttributeScope>[] = [
  {
    id: 'TRACE',
    label: 'Anywhere in the trace',
    title: 'Each condition may be satisfied by a different span or notification'
  },
  {
    id: 'SPAN',
    label: 'All on one span',
    title: 'Every condition on the same single span, and every notification condition on the same notification'
  }
];

/** How a row names where its key came from — worded for a reader, not after the enum. */
const SOURCE_LABELS: Record<TraceAttributeSource, string> = {
  ATTRIBUTE: 'attribute',
  EVENT_FIELD: 'event field',
  SPAN_SHAPE: 'span shape',
  NOTIFICATION_ATTRIBUTE: 'notification attribute',
  // Worded as the carrier rather than after the enum, so the notification field spelled `source`
  // does not render as `source · source`.
  NOTIFICATION_SHAPE: 'notification'
};

const OPERATOR_SYMBOLS: Record<TraceAttributeOperator, string> = {
  EQ: '=',
  NOT_EQ: '≠',
  NONE_EQ: '∄',
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
  NONE_EQ: 'is never',
  CONTAINS: 'contains',
  GT: 'greater than',
  GTE: 'at least',
  LT: 'less than',
  LTE: 'at most',
  EXISTS: 'is present'
};

/** Comparisons read the numeric column, which only a numeric value is ever stored in. */
const NUMERIC_OPERATORS: TraceAttributeOperator[] = ['GT', 'GTE', 'LT', 'LTE'];
// NOT_EQ and NONE_EQ answer different questions: "some carrier has another value" against
// "no carrier has this one" -- status ≠ ERROR versus status never ERROR.
const TEXT_OPERATORS: TraceAttributeOperator[] = ['EQ', 'NOT_EQ', 'NONE_EQ', 'CONTAINS'];

const draftType = ref<string | null>(null);
const draftKey = ref<TraceAttributeKeyRow | null>(null);
const draftOperator = ref<TraceAttributeOperator>('EQ');
const draftValue = ref('');

/*
 * A different event type may not carry the chosen key at all, and the ones that do carry it under
 * different counts — so the key is dropped rather than kept pointing at another type's numbers.
 */
function onPickType(type: TraceEventTypeRow): void {
  draftType.value = type.eventType;
  draftKey.value = null;
}

function onPickKey(key: TraceAttributeKeyRow): void {
  draftKey.value = key;
}

/**
 * Only the operators the key's values can answer. Offering `>` on a string key produces a condition
 * that matches nothing and explains nothing, and offering `contains` on a two-valued boolean reads
 * as a mistake.
 */
const availableOperators = computed<TraceAttributeOperator[]>(() => {
  const kind = draftKey.value?.valueKind;
  if (kind === 'NUMBER') {
    return [...TEXT_OPERATORS, ...NUMERIC_OPERATORS, 'EXISTS'];
  }
  if (kind === 'BOOLEAN') {
    return ['EQ', 'NOT_EQ', 'NONE_EQ', 'EXISTS'];
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
  const kind = draftKey.value?.valueKind;
  if (kind === 'NUMBER') {
    return 'a number';
  }
  if (kind === 'BOOLEAN') {
    return 'true or false';
  }
  return 'a value';
});

const canAdd = computed(() => {
  if (draftKey.value === null) {
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
  const key = draftKey.value;
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

  // The event type survives the add — the next condition usually reads the same spans — but the
  // key does not: a chip still naming the just-added key invites adding it twice.
  draftKey.value = null;
  draftValue.value = '';
}

function remove(index: number): void {
  emit(
    'update:conditions',
    props.conditions.filter((_, position) => position !== index)
  );
}
</script>

<style scoped>
/*
 * The card must not clip the pickers' dropdowns, so the shared overflow:hidden is lifted and the
 * header strip clips its own top corners instead.
 */
.search-conditions-card {
  overflow: visible;
}

.clause-head {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  flex-wrap: wrap;
  padding: var(--spacing-3) var(--spacing-5);
  background: var(--color-light);
  border-bottom: 1px solid var(--color-border);
  border-radius: var(--radius-md) var(--radius-md) 0 0;
}

.clause-title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

/* The switcher's own look belongs to SegmentedSwitch; only where it sits is this card's business. */
.scope-switch {
  margin-left: auto;
}

.clause-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  padding: var(--spacing-2) var(--spacing-5);
}

.clause-row + .clause-row {
  border-top: 1px solid var(--color-border-light);
}

.clause-row:hover {
  background: var(--color-primary-lighter);
}

.clause-keyword {
  width: 52px;
  flex-shrink: 0;
  text-align: right;
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-xs);
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--color-primary);
}

.clause-keyword.and,
.clause-keyword.ghost {
  color: var(--color-text-muted);
}

.clause-expr {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--spacing-2);
  flex: 1;
  min-width: 0;
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-base);
}

.expr-key {
  font-weight: 600;
  color: var(--color-dark);
}

.expr-source {
  font-family: var(--font-family-base);
  font-size: var(--font-size-xs);
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  border-radius: var(--radius-sm);
  padding: 1px var(--spacing-2);
  color: var(--color-text-muted);
  background: var(--color-lighter);
}

.expr-source.source-attribute {
  color: var(--color-primary);
  background: var(--color-primary-light);
}

.expr-source.source-event_field {
  color: var(--color-info-text);
  background: var(--color-info-light);
}

/* The notification sources share a tint of their own, so a carrier is told apart at a glance. */
.expr-source.source-notification_attribute,
.expr-source.source-notification_shape {
  color: var(--color-warning-text);
  background: var(--color-warning-light);
}

.expr-operator {
  color: var(--color-primary);
  font-weight: 700;
}

.expr-value {
  color: var(--color-dark);
  max-width: 420px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.clause-remove {
  margin-left: auto;
  border: 0;
  background: transparent;
  color: var(--color-text-light);
  padding: var(--spacing-1);
  border-radius: var(--radius-sm);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
}

.clause-remove:hover {
  color: var(--color-danger);
}

.clause-add {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  flex-wrap: wrap;
  padding: var(--spacing-3) var(--spacing-5);
  border-top: 1px dashed var(--color-border-input);
}

/* No conditions means no rows above — the header's own border is the only divider needed. */
.clause-head + .clause-add {
  border-top: 0;
}

.clause-add .clause-keyword {
  margin-right: var(--spacing-1);
}

/* Icon and word on one centreline — the baseline gap read as the label sitting too low. */
.clause-add-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-1);
}

.clause-clear {
  margin-left: auto;
}

.builder-rest {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-2);
  flex-wrap: wrap;
}

/*
 * Not yet usable, but never faded — the same contract as the picker's not-yet-takeable step: a
 * dashed border on a grey ground, text left at full contrast, with the dashes carrying the
 * "not yet". The 0.4 opacity this replaces dropped the whole group below reading contrast,
 * including the operator list, which is the clearest statement of what a condition can ask.
 */
.builder-operator:disabled,
.builder-value:disabled,
.clause-add-btn:disabled {
  cursor: default;
  opacity: 1;
  border: 1px dashed var(--color-border-input);
  background: var(--color-lighter);
  color: var(--color-slate-text);
}

/* Fixed rather than max widths: Bootstrap's width:100% inside the inline-flex group wraps the row. */
.builder-operator {
  width: 170px;
}

.builder-value {
  width: 260px;
  font-family: var(--font-family-monospace);
}

.builder-note {
  margin: 0;
  padding: var(--spacing-2) var(--spacing-5) var(--spacing-3);
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
