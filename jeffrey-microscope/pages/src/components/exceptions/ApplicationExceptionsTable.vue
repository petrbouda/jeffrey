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
  The application's throw sites — each exception class with the frame that contained the `throw` —
  with the JVM's own resolution and linking throws already left out. A row expands into the messages
  and the site's commonest stack, the same stack view the trace drill-down uses, so the frame that
  threw is marked and library frames fold away.
-->
<template>
  <DataTable>
    <template #toolbar>
      <TableToolbar v-model="view.query" search-placeholder="Filter by class or frame...">
        <span class="toolbar-info">Application throw sites</span>
        <template #filters>
          <Badge
            key-label="Sites"
            :value="view.matchCount"
            variant="secondary"
            size="s"
            borderless
          />
        </template>
      </TableToolbar>
    </template>
    <thead>
      <tr>
        <th>Exception Class</th>
        <th>Thrown At</th>
        <th class="text-end">Throws</th>
        <th class="text-end">Threads</th>
        <th class="chevron-col"></th>
      </tr>
    </thead>
    <tbody>
      <template v-for="site in view.visible" :key="siteKey(site)">
        <tr
          class="exception-row expandable"
          :class="{ 'row-expanded': expanded.has(siteKey(site)) }"
          @click="toggle(site)"
        >
          <td>
            <div class="class-with-badge">
              <ClassNameDisplay :class-name="site.thrownClass" />
              <Badge
                v-if="site.error"
                value="Error"
                variant="danger"
                size="xs"
                borderless
                class="type-badge"
              />
            </div>
          </td>
          <td>
            <code class="frame-label" :class="{ 'no-stack': !site.throwingFrame }">
              {{ throwFrameLabel(site.throwingFrame) }}
            </code>
          </td>
          <td class="text-end">{{ FormattingService.formatNumber(site.count) }}</td>
          <td class="text-end">{{ FormattingService.formatNumber(site.threadCount) }}</td>
          <td class="text-end">
            <i
              class="bi expand-icon"
              :class="expanded.has(siteKey(site)) ? 'bi-chevron-up' : 'bi-chevron-down'"
            ></i>
          </td>
        </tr>
        <tr v-if="expanded.has(siteKey(site))" class="details-row">
          <td colspan="5" class="p-0">
            <ExceptionMessagesDetail
              :messages="site.messages"
              :omitted-messages="site.omittedMessages"
            >
              <div class="site-stack">
                <TraceStackTrace
                  :profile-id="profileId"
                  :stacktrace-id="site.stacktraceId"
                  :thrown-class="site.thrownClass"
                  :message="site.messages[0]?.message ?? null"
                />
              </div>
            </ExceptionMessagesDetail>
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
import TableShowMore from '@shared/components/table/TableShowMore.vue';
import TableToolbar from '@shared/components/table/TableToolbar.vue';
import FormattingService from '@shared/services/FormattingService';
import ClassNameDisplay from '@/components/heap/ClassNameDisplay.vue';
import ExceptionMessagesDetail from '@/components/exceptions/ExceptionMessagesDetail.vue';
import TraceStackTrace from '@/components/trace/TraceStackTrace.vue';
import { useTableView } from '@/composables/useTableView';
import type { ExceptionSiteStat } from '@/services/api/model/ExceptionsModels';
import {
  applicationSites,
  siteKey,
  siteSearchText,
  throwFrameLabel
} from '@/services/exceptions/exceptionViews';

const props = defineProps<{
  profileId: string;
  /** Every site; the table keeps the application's. */
  sites: ExceptionSiteStat[];
}>();

const expanded = ref<Set<string>>(new Set());

const rows = computed<ExceptionSiteStat[]>(() => applicationSites(props.sites));

const view = useTableView<ExceptionSiteStat>(rows, { searchableText: siteSearchText });

const toggle = (site: ExceptionSiteStat): void => {
  const key = siteKey(site);
  const next = new Set(expanded.value);
  if (next.has(key)) {
    next.delete(key);
  } else {
    next.add(key);
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

.class-with-badge {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
}

.type-badge {
  margin-top: 1px;
}

.frame-label {
  font-size: 0.78rem;
  color: var(--color-text);
  word-break: break-all;
}

.frame-label.no-stack {
  color: var(--color-text-muted);
  font-style: italic;
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

.site-stack {
  margin-top: 0.75rem;
}
</style>
