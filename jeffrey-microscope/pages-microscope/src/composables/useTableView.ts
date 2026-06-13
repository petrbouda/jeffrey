/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import { computed, reactive, ref, toValue, watch, type MaybeRefOrGetter } from 'vue';

export interface TableViewOptions<T> {
  /**
   * Returns the text used for full-text filtering of a row. Omit for numeric-only tables that should
   * only be row-limited (no search box).
   */
  searchableText?: (row: T) => string;
  /** Rows shown before the user expands to "Show all". Defaults to 50. */
  pageSize?: number;
}

/**
 * Reactive view over a table's rows providing full-text filtering and a "show N then all" window.
 * All members are unwrapped (the result is a `reactive` object), so `view.query` works with
 * `v-model` and `view.visible` can be used directly as a `v-for` source.
 */
export interface TableView<T> {
  /** Current full-text query (two-way bindable). */
  query: string;
  /** Rows after applying the query (the full match set). */
  filtered: T[];
  /** Rows currently rendered (filtered, then limited to pageSize unless expanded). */
  visible: T[];
  /** Total rows in the source. */
  total: number;
  /** Rows matching the query. */
  matchCount: number;
  /** Whether there are more matches than the page size. */
  canExpand: boolean;
  /** Whether the full match set is currently shown. */
  expanded: boolean;
  /** Rows shown before expanding. */
  pageSize: number;
  /** Toggle between the limited window and the full match set. */
  toggle: () => void;
}

export function useTableView<T>(
  source: MaybeRefOrGetter<T[]>,
  options: TableViewOptions<T> = {}
): TableView<T> {
  const pageSize = options.pageSize ?? 50;
  const searchableText = options.searchableText;

  const query = ref('');
  const expanded = ref(false);

  const filtered = computed<T[]>(() => {
    const rows = toValue(source) ?? [];
    const q = query.value.trim().toLowerCase();
    if (q.length === 0 || !searchableText) {
      return rows;
    }
    return rows.filter(row => searchableText(row).toLowerCase().includes(q));
  });

  const visible = computed<T[]>(() =>
    expanded.value ? filtered.value : filtered.value.slice(0, pageSize)
  );
  const total = computed(() => (toValue(source) ?? []).length);
  const matchCount = computed(() => filtered.value.length);
  const canExpand = computed(() => matchCount.value > pageSize);

  // Collapse back to the first page whenever the filter changes.
  watch(query, () => {
    expanded.value = false;
  });

  const toggle = (): void => {
    expanded.value = !expanded.value;
  };

  return reactive({
    query,
    filtered,
    visible,
    total,
    matchCount,
    canExpand,
    expanded,
    pageSize,
    toggle
  }) as TableView<T>;
}
