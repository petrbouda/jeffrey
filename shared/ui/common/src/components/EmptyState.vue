<!--
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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
  <div class="empty-state">
    <div class="empty-state-content">
      <i class="bi" :class="icon"></i>
      <span class="empty-state-title">{{ title }}</span>
      <span v-if="description" class="empty-state-description">{{ description }}</span>
      <slot name="action"></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  icon: string;
  title: string;
  description?: string;
}

defineProps<Props>();
</script>

<style scoped>
.empty-state {
  padding: 12px;
}

.empty-state-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px 20px;
  gap: 6px;
}

.empty-state-content i {
  font-size: 2rem;
  color: var(--color-primary);
  opacity: 0.4;
  margin-bottom: 4px;
}

.empty-state-title {
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--color-text);
}

.empty-state-description {
  font-size: 0.82rem;
  line-height: 1.5;
  max-width: 560px;
  text-align: center;
  color: var(--color-text-muted);
}
</style>
