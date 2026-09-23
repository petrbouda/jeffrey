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
