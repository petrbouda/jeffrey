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

<!--
  How much of a server-paged list is on screen, and a button to fetch the next page.

  The counterpart to TableShowMore, which toggles between a slice and the whole of a list the caller
  already holds. This one is for a list the caller does not hold: there is no "show less" because the
  rest was never fetched, and the count is what the server said matched rather than what arrived.
-->
<template>
  <div class="load-more">
    <span class="load-more-info">
      Showing {{ FormattingService.formatNumber(shown) }} of
      {{ FormattingService.formatNumber(total) }}{{ noun ? ' ' + noun : '' }}
    </span>
    <button
      v-if="shown < total"
      type="button"
      class="btn btn-sm btn-link load-more-btn"
      :disabled="loading"
      @click="$emit('loadMore')"
    >
      <span v-if="loading" class="spinner-border spinner-border-sm" role="status"></span>
      {{ loading ? 'Loading…' : 'Load more' }}
    </button>
  </div>
</template>

<script setup lang="ts">
import FormattingService from '@shared/services/FormattingService';

withDefaults(
  defineProps<{
    /** Rows currently on screen. */
    shown: number;
    /** Rows the server said match the current filter, which may be far more than are on screen. */
    total: number;
    /** Plural noun for the counts, e.g. `traces`. Omitted leaves the numbers to speak for themselves. */
    noun?: string;
    loading?: boolean;
  }>(),
  { noun: undefined, loading: false }
);

defineEmits<{
  loadMore: [];
}>();
</script>

<style scoped>
.load-more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  border-top: 1px solid var(--color-border);
  background-color: var(--color-light);
  font-size: 0.8rem;
}

.load-more-info {
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

.load-more-btn {
  padding: 0;
  font-size: 0.8rem;
  text-decoration: none;
}
</style>
