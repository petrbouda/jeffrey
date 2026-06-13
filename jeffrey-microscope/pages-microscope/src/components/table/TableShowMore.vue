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
import FormattingService from '@/services/FormattingService';

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
