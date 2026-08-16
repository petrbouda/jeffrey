<!--
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
 -->

<template>
  <div class="error-state">
    <div class="alert alert-danger d-flex align-items-center">
      <i class="bi" :class="icon"></i>
      <span>{{ message }}</span>
      <!--
        Rendered only when someone is actually listening. Call sites across the app already wire
        @retry — for a long time this component silently ignored them, leaving every error state a
        dead end whose only recovery was reloading the page.
      -->
      <button v-if="hasRetryListener" type="button" class="error-retry" @click="emit('retry')">
        Try again
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, useAttrs } from 'vue';

interface Props {
  message?: string;
  icon?: string;
}

withDefaults(defineProps<Props>(), {
  message: 'Failed to load data',
  icon: 'bi-exclamation-triangle-fill me-2'
});

const emit = defineEmits<{ (event: 'retry'): void }>();

const attrs = useAttrs();
const hasRetryListener = computed(() => typeof attrs.onRetry === 'function');
</script>

<style scoped>
.error-state {
  text-align: center;
  padding: 3rem 1rem;
}

.error-state .alert {
  display: inline-flex;
  margin: 0;
  gap: 0.5rem;
}

.error-state i {
  font-size: 1rem;
}

.error-retry {
  padding: 0.15rem 0.6rem;
  border: 1px solid currentcolor;
  border-radius: var(--radius-sm);
  background: transparent;
  color: inherit;
  font: inherit;
  font-size: var(--font-size-sm);
  cursor: pointer;
}

.error-retry:hover {
  background: var(--color-danger);
  border-color: var(--color-danger);
  color: var(--color-bg-card);
}

.error-retry:focus-visible {
  outline: 2px solid var(--color-danger);
  outline-offset: 1px;
}
</style>
