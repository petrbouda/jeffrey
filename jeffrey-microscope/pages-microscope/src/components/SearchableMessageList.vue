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
  <div class="message-list-wrapper">
    <div v-if="messages.length > initialCount" class="message-search">
      <i class="bi bi-search"></i>
      <input
        v-model="view.query"
        type="text"
        class="form-control form-control-sm"
        placeholder="Filter messages..."
      />
      <button v-if="view.query" class="btn-clear" @click="view.query = ''">
        <i class="bi bi-x-lg"></i>
      </button>
    </div>

    <ul class="message-list">
      <li v-for="(msg, i) in view.visible" :key="i">
        <span class="message-text">{{ msg.message }}</span>
        <Badge
          :value="'×' + FormattingService.formatNumber(msg.count)"
          variant="secondary"
          size="xs"
          borderless
        />
      </li>
    </ul>

    <TableShowMore
      :shown="view.visible.length"
      :match-count="view.matchCount"
      :total="view.total"
      :expanded="view.expanded"
      :page-size="view.pageSize"
      @toggle="view.toggle"
    />
  </div>
</template>

<script setup lang="ts">
import Badge from '@shared/components/Badge.vue';
import TableShowMore from '@/components/table/TableShowMore.vue';
import FormattingService from '@shared/services/FormattingService';
import { useTableView } from '@/composables/useTableView';
import type { ExceptionMessageCount } from '@/services/api/model/ExceptionsModels';

const props = withDefaults(
  defineProps<{
    messages: ExceptionMessageCount[];
    initialCount?: number;
  }>(),
  {
    initialCount: 10
  }
);

const view = useTableView<ExceptionMessageCount>(() => props.messages, {
  searchableText: msg => msg.message,
  pageSize: props.initialCount
});
</script>

<style scoped>
.message-search {
  position: relative;
  margin-bottom: 0.5rem;
  max-width: 320px;
}

.message-search i.bi-search {
  position: absolute;
  left: 0.6rem;
  top: 50%;
  transform: translateY(-50%);
  color: var(--color-text-light);
  font-size: 0.75rem;
  pointer-events: none;
}

.message-search .form-control {
  padding-left: 1.8rem;
  padding-right: 1.8rem;
  font-size: 0.8rem;
}

.btn-clear {
  position: absolute;
  right: 0.4rem;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  color: var(--color-text-light);
  font-size: 0.6rem;
  cursor: pointer;
  padding: 2px;
  line-height: 1;
}

.btn-clear:hover {
  color: var(--color-text);
}

.message-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.message-list li {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.2rem 0;
}

.message-text {
  font-family: ui-monospace, 'SF Mono', Menlo, Consolas, monospace;
  font-size: 0.78rem;
  color: var(--color-text);
  line-height: 1.5;
  word-break: break-word;
}
</style>
