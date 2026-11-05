<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
-->

<script setup lang="ts">
import { computed, ref } from 'vue';
import { formatCount, nodeId, usePluginUsage, type AreaLens } from '@/composables/usePluginUsage';
import type { AreaName } from '@/types/pluginUsage';

// Every tool of the server: which skills name it, which agents may call it, and how often the runs
// did. Sortable, searchable, and a row opens the tool on the map.

const props = defineProps<{ lens: AreaLens }>();
const emit = defineEmits<{ (e: 'show', tool: string): void }>();

type SortKey = 'name' | 'area' | 'family' | 'named' | 'agents' | 'calls' | 'cases' | 'access' | 'cost';
type Usage = 'all' | 'called' | 'never';

interface ToolRow {
  name: string;
  area: AreaName;
  areaLabel: string;
  family: string;
  title: string | null;
  named: string[];
  agents: string[];
  calls: number;
  /** The eval cases whose runs called it. */
  cases: string[];
  access: string;
  cost: string | null;
}

interface Column {
  key: SortKey;
  label: string;
  numeric?: boolean;
}

const COLUMNS: Column[] = [
  { key: 'name', label: 'Tool' },
  { key: 'area', label: 'Area' },
  { key: 'family', label: 'Family' },
  { key: 'named', label: 'Named by skills' },
  { key: 'agents', label: 'Agents' },
  { key: 'calls', label: 'Calls in runs', numeric: true },
  { key: 'cases', label: 'Use cases', numeric: true },
  { key: 'access', label: 'Access' },
  { key: 'cost', label: 'Cost' }
];

const USAGES: { usage: Usage; label: string }[] = [
  { usage: 'all', label: 'All' },
  { usage: 'called', label: 'Called' },
  { usage: 'never', label: 'Never called' }
];

const USAGE_FILTERS: Record<Usage, (row: ToolRow) => boolean> = {
  all: () => true,
  called: (row) => row.calls > 0,
  never: (row) => row.calls === 0
};

const model = usePluginUsage();

const rows: ToolRow[] = model.data.tools.map((tool) => ({
  name: tool.name,
  area: model.areaOfNode(nodeId('tool', tool.name)),
  areaLabel: model.areas.get(model.areaOfNode(nodeId('tool', tool.name)))?.label ?? '',
  family: tool.family,
  title: tool.title,
  named: model.namedBy(tool.name),
  agents: model.agentsAllowing(tool.name),
  calls: model.toolCalls(tool.name),
  cases: model.casesUsing((step) => step.kind === 'jeffrey' && step.name === tool.name).map((use) => use.replay.case),
  access: tool.destructive ? 'destructive' : tool.readOnly ? 'read-only' : 'writes',
  cost: tool.cost ? tool.cost.toLowerCase() : null
}));

const SORT_VALUES: Record<SortKey, (row: ToolRow) => string | number> = {
  name: (row) => row.name,
  area: (row) => row.areaLabel,
  family: (row) => row.family,
  named: (row) => row.named.length,
  agents: (row) => row.agents.length,
  calls: (row) => row.calls,
  cases: (row) => row.cases.length,
  access: (row) => row.access,
  cost: (row) => model.costOrder(row.cost)
};

const query = ref('');
const usage = ref<Usage>('all');
const sortKey = ref<SortKey>('calls');
const descending = ref(true);

function sortBy(key: SortKey): void {
  if (sortKey.value === key) {
    descending.value = !descending.value;
  } else {
    sortKey.value = key;
    descending.value = key === 'calls' || key === 'named' || key === 'agents';
  }
}

const shown = computed<ToolRow[]>(() => {
  const needle = query.value.trim().toLowerCase();
  const value = SORT_VALUES[sortKey.value];
  const direction = descending.value ? -1 : 1;
  return rows
    .filter((row) => model.inLens(props.lens, row.area))
    .filter(USAGE_FILTERS[usage.value])
    .filter((row) => !needle || row.name.toLowerCase().includes(needle) || row.family.includes(needle)
      || row.named.some((skill) => skill.includes(needle)) || (row.title ?? '').toLowerCase().includes(needle))
    .sort((a, b) => {
      const x = value(a);
      const y = value(b);
      if (x === y) {
        return a.name.localeCompare(b.name);
      }
      return (x < y ? -1 : 1) * direction;
    });
});

const ariaSort = (key: SortKey): 'ascending' | 'descending' | undefined => {
  if (sortKey.value !== key) {
    return undefined;
  }
  return descending.value ? 'descending' : 'ascending';
};

const summary = computed(() => {
  const inLens = rows.filter((row) => model.inLens(props.lens, row.area));
  const families = new Set(inLens.map((row) => row.family)).size;
  const called = inLens.filter((row) => row.calls > 0).length;
  const scope = ` in ${model.areas.get(props.lens)?.label} and the shared families`;
  return `${inLens.length} tools in ${families} families${scope} · ${called} called at least once across ${formatCount(model.totals.runs)} recorded runs, ${inLens.length - called} never.`;
});
</script>

<template>
  <div class="tools-tab">
    <div class="tools-toolbar">
      <label class="tools-search">
        <i class="bi bi-search" aria-hidden="true"></i>
        <span class="visually-hidden">Find a tool</span>
        <input v-model="query" type="search" placeholder="Find a tool, e.g. code_callSites" />
      </label>
      <div class="tools-usage" role="radiogroup" aria-label="Show">
        <button
          v-for="item in USAGES"
          :key="item.usage"
          type="button"
          role="radio"
          class="tools-usage-option"
          :class="{ 'tools-usage-option--on': item.usage === usage }"
          :aria-checked="item.usage === usage"
          @click="usage = item.usage"
        >{{ item.label }}</button>
      </div>
      <p class="tools-summary">{{ summary }} Click a row to find it on the map.</p>
    </div>

    <div class="tools-card">
      <div class="tools-scroll">
        <table class="tools-table">
          <thead>
            <tr>
              <th
                v-for="column in COLUMNS"
                :key="column.key"
                :class="{ 'tools-num': column.numeric }"
                :aria-sort="ariaSort(column.key)"
              >
                <button type="button" class="tools-sort" @click="sortBy(column.key)">
                  {{ column.label }}
                  <i
                    v-if="sortKey === column.key"
                    class="bi"
                    :class="descending ? 'bi-arrow-down' : 'bi-arrow-up'"
                    aria-hidden="true"
                  ></i>
                </button>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in shown"
              :key="row.name"
              :class="{ 'tools-row--unused': !row.calls }"
              tabindex="0"
              @click="emit('show', row.name)"
              @keydown.enter="emit('show', row.name)"
            >
              <td class="tools-mono" :title="row.title ?? undefined">{{ row.name }}</td>
              <td><span class="tools-area" :class="`tools-area--${row.area}`">{{ row.areaLabel }}</span></td>
              <td class="tools-mono tools-faint">{{ row.family }}</td>
              <td>{{ row.named.length ? row.named.join(', ') : '–' }}</td>
              <td>{{ row.agents.length ? row.agents.join(', ') : '–' }}</td>
              <td class="tools-num">{{ row.calls ? formatCount(row.calls) : '0' }}</td>
              <td class="tools-num" :title="row.cases.join(', ') || undefined">{{ row.cases.length }}</td>
              <td>{{ row.access }}</td>
              <td>{{ row.cost ?? '–' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="!shown.length" class="tools-empty">No tool matches “{{ query }}”.</p>
    </div>
  </div>
</template>

<style scoped>
.tools-tab {
  padding: 24px 28px 48px;
}

.tools-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 14px;
  margin-bottom: 16px;
}

.tools-search {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 300px;
  padding: 7px 10px;
  border: 1px solid var(--site-line-strong);
  border-radius: var(--site-radius-sm);
  background: var(--site-surface);
  color: var(--site-faint);
}

.tools-search input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--site-ink);
  font-size: 13px;
}

.tools-usage {
  display: inline-flex;
  border: 1px solid var(--site-line-strong);
  border-radius: var(--site-radius-sm);
  overflow: hidden;
}

.tools-usage-option {
  padding: 7px 13px;
  border: 0;
  background: var(--site-surface);
  color: var(--site-muted);
  font-size: 13px;
  cursor: pointer;
}

.tools-usage-option + .tools-usage-option {
  border-left: 1px solid var(--site-line-strong);
}

.tools-usage-option--on {
  background: var(--site-ink);
  color: var(--site-surface);
}

.tools-summary {
  flex: 1 1 100%;
  margin: 0;
  font-size: 12.5px;
  color: var(--site-faint);
}

.tools-card {
  border: 1px solid var(--site-line);
  border-radius: var(--ap-radius-md);
  background: var(--site-surface);
  overflow: hidden;
}

.tools-scroll {
  overflow-x: auto;
}

.tools-table {
  width: 100%;
  min-width: 980px;
  border-collapse: collapse;
  font-size: 13px;
}

.tools-table th {
  padding: 0;
  border-bottom: 1px solid var(--site-line);
  background: var(--site-paper);
  text-align: left;
}

.tools-sort {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  padding: 10px 12px;
  border: 0;
  background: transparent;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--site-faint);
  white-space: nowrap;
  cursor: pointer;
}

.tools-num .tools-sort {
  justify-content: flex-end;
}

.tools-table td {
  padding: 9px 12px;
  border-bottom: 1px solid var(--site-line);
  vertical-align: top;
  color: var(--site-ink);
}

.tools-table tbody tr {
  cursor: pointer;
}

.tools-table tbody tr:hover,
.tools-table tbody tr:focus-visible {
  background: var(--ap-hover);
  outline: none;
}

.tools-row--unused td {
  color: var(--site-faint);
}

.tools-mono {
  font-family: var(--site-font-mono);
  font-size: 12.5px;
}

.tools-faint {
  color: var(--site-faint) !important;
}

.tools-area {
  display: inline-block;
  padding: 0 8px;
  border-radius: var(--ap-radius-pill);
  background: var(--ap-hover);
  color: var(--site-muted);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.tools-area--jfr {
  background: var(--ap-jfr-band);
  color: var(--ap-jfr);
}

.tools-area--heap {
  background: var(--ap-heap-band);
  color: var(--ap-heap);
}

.tools-num {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.tools-empty {
  margin: 0;
  padding: 18px;
  color: var(--site-faint);
  font-size: 13px;
}

@media (max-width: 1199px) {
  .tools-tab {
    padding: 22px 24px 40px;
  }
}

@media (max-width: 859px) {
  .tools-tab {
    padding: 16px 16px 32px;
  }

  .tools-search {
    min-width: 0;
    flex: 1 1 100%;
  }
}
</style>
