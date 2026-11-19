<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     https://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

<!--
  Sampled throws grouped by class — the All Exceptions and the Errors tables of the Exceptions page,
  one component so the two cannot drift. A row expands into the messages the class was thrown with.
  With `originFilter` the toolbar also narrows by origin, and a class every throw of which came from
  the JVM's own resolution and linking carries a "JVM internal" badge.
-->
<template>
  <DataTable>
    <template #toolbar>
      <TableToolbar v-model="view.query" :search-placeholder="searchPlaceholder">
        <span class="toolbar-info">{{ title }}</span>
        <template #filters>
          <SegmentedSwitch
            v-if="originFilter"
            v-model="origin"
            :options="originOptions"
            group-label="Throw origin"
          />
          <Badge
            key-label="Total"
            :value="view.matchCount"
            :variant="totalVariant"
            size="s"
            borderless
          />
        </template>
      </TableToolbar>
    </template>
    <thead>
      <tr>
        <th>{{ classHeader }}</th>
        <th class="text-end">Throws</th>
        <th class="text-end">Threads</th>
        <th class="chevron-col"></th>
      </tr>
    </thead>
    <tbody>
      <tr v-if="view.visible.length === 0" class="empty-row">
        <td colspan="4">No exception types match this origin or filter.</td>
      </tr>
      <template v-for="type in view.visible" :key="type.thrownClass">
        <tr
          class="exception-row"
          :class="{
            expandable: hasMessages(type),
            'row-expanded': expanded.has(type.thrownClass)
          }"
          @click="toggle(type)"
        >
          <td>
            <div class="class-with-badge">
              <ClassNameDisplay :class-name="type.thrownClass" />
              <Badge
                v-if="originFilter && isJvmInternalOnly(type)"
                value="JVM internal"
                variant="secondary"
                size="xs"
                borderless
                class="type-badge"
                title="Thrown by the JVM while loading classes, reflecting or linking call sites"
              />
            </div>
          </td>
          <td class="text-end">{{ FormattingService.formatNumber(type.count) }}</td>
          <td class="text-end">{{ FormattingService.formatNumber(type.threadCount) }}</td>
          <td class="text-end">
            <i
              v-if="hasMessages(type)"
              class="bi expand-icon"
              :class="expanded.has(type.thrownClass) ? 'bi-chevron-up' : 'bi-chevron-down'"
            ></i>
          </td>
        </tr>
        <tr v-if="expanded.has(type.thrownClass)" class="details-row">
          <td colspan="4" class="p-0">
            <ExceptionMessagesDetail
              :messages="type.messages"
              :omitted-messages="type.omittedMessages"
            />
          </td>
        </tr>
      </template>
    </tbody>
    <template #footer>
      <TableShowMore
        :shown="view.visible.length"
        :match-count="view.matchCount"
        :total="view.total"
        :expanded="view.expanded"
        :page-size="view.pageSize"
        @toggle="view.toggle"
      />
    </template>
  </DataTable>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';

import Badge from '@shared/components/Badge.vue';
import DataTable from '@shared/components/table/DataTable.vue';
import SegmentedSwitch from '@shared/components/SegmentedSwitch.vue';
import type { SegmentedOption } from '@shared/components/SegmentedSwitch.vue';
import TableShowMore from '@shared/components/table/TableShowMore.vue';
import TableToolbar from '@shared/components/table/TableToolbar.vue';
import FormattingService from '@shared/services/FormattingService';
import ClassNameDisplay from '@/components/heap/ClassNameDisplay.vue';
import ExceptionMessagesDetail from '@/components/exceptions/ExceptionMessagesDetail.vue';
import { useTableView } from '@/composables/useTableView';
import type { ExceptionTypeStat } from '@/services/api/model/ExceptionsModels';
import {
  filterTypesByOrigin,
  isJvmInternalOnly,
  type OriginFilter
} from '@/services/exceptions/exceptionViews';

const props = defineProps<{
  types: ExceptionTypeStat[];
  title: string;
  classHeader: string;
  searchPlaceholder: string;
  totalVariant: 'secondary' | 'danger';
  /** Offer the origin switch and mark the JVM-internal classes. */
  originFilter?: boolean;
}>();

const originOptions: SegmentedOption<OriginFilter>[] = [
  { id: 'ALL', label: 'All', title: 'Every sampled throw' },
  {
    id: 'APPLICATION',
    label: 'Application',
    title: "The application's throws, libraries included"
  },
  {
    id: 'JVM_INTERNAL',
    label: 'JVM internal',
    title: 'Thrown by the JVM while loading classes, reflecting or linking call sites'
  }
];

const origin = ref<OriginFilter>('ALL');
const expanded = ref<Set<string>>(new Set());

const filtered = computed<ExceptionTypeStat[]>(() =>
  filterTypesByOrigin(props.types, origin.value)
);

const view = useTableView<ExceptionTypeStat>(filtered, {
  searchableText: type => type.thrownClass
});

const hasMessages = (type: ExceptionTypeStat): boolean => type.messages.length > 0;

const toggle = (type: ExceptionTypeStat): void => {
  if (!hasMessages(type)) {
    return;
  }
  const next = new Set(expanded.value);
  if (next.has(type.thrownClass)) {
    next.delete(type.thrownClass);
  } else {
    next.add(type.thrownClass);
  }
  expanded.value = next;
};
</script>

<style scoped>
.toolbar-info {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--color-text);
}

/* Keep the badges aligned with the class name (top line of the cell). */
.class-with-badge {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
}

.type-badge {
  margin-top: 1px;
}

.chevron-col {
  width: 40px;
}

.exception-row.expandable {
  cursor: pointer;
}

.exception-row.row-expanded > td {
  background-color: var(--color-bg-hover-alt);
}

.expand-icon {
  font-size: 0.8rem;
  color: var(--color-text-light);
}

.details-row:hover > td {
  background: transparent;
}

.empty-row > td {
  padding: 1rem;
  text-align: center;
  font-size: 0.85rem;
  color: var(--color-text-muted);
}
</style>
