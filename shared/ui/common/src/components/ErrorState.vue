<!--
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
