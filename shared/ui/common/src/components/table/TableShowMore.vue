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

<template>
  <div v-if="matchCount > pageSize" class="table-show-more">
    <span class="show-more-info">
      Showing {{ FormattingService.formatNumber(shown) }} of
      {{ FormattingService.formatNumber(matchCount) }}
      <span v-if="matchCount < total" class="show-more-filtered">
        (filtered from {{ FormattingService.formatNumber(total) }})
      </span>
    </span>
    <button type="button" class="btn btn-sm btn-link show-more-btn" @click="$emit('toggle')">
      {{ expanded ? 'Show less' : `Show all (${FormattingService.formatNumber(matchCount)})` }}
    </button>
  </div>
</template>

<script setup lang="ts">
import FormattingService from '@shared/services/FormattingService';

defineProps<{
  shown: number;
  matchCount: number;
  total: number;
  expanded: boolean;
  pageSize: number;
}>();

defineEmits<{
  toggle: [];
}>();
</script>

<style scoped>
.table-show-more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  border-top: 1px solid var(--color-border);
  background-color: var(--color-light);
  font-size: 0.8rem;
}

.show-more-info {
  color: var(--color-text-muted);
}

.show-more-filtered {
  color: var(--color-text-light);
}

.show-more-btn {
  padding: 0;
  font-size: 0.8rem;
  font-weight: 600;
  text-decoration: none;
}
</style>
