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
  The body of an expanded exception row: the messages it was thrown with and how many rarer ones the
  server left out, then whatever the host table adds below (the type table nothing, the site table
  the stack). One component, so the two tables of the Exceptions page cannot drift apart.
-->
<template>
  <div class="row-details">
    <template v-if="messages.length > 0">
      <span class="detail-label">
        Messages
        <span class="detail-count">{{ messages.length + omittedMessages }}</span>
      </span>
      <SearchableMessageList :messages="messages" />
      <p v-if="omittedMessages > 0" class="omitted-note">
        {{ FormattingService.formatNumber(omittedMessages) }} rarer messages not shown
      </p>
    </template>
    <slot></slot>
  </div>
</template>

<script setup lang="ts">
import FormattingService from '@shared/services/FormattingService';
import SearchableMessageList from '@/components/SearchableMessageList.vue';
import type { ExceptionMessageCount } from '@/services/api/model/ExceptionsModels';

defineProps<{
  messages: ExceptionMessageCount[];
  /** Distinct messages the server's cap left out of `messages`. */
  omittedMessages: number;
}>();
</script>

<style scoped>
.row-details {
  background: var(--color-light);
  padding: 0.85rem 1.25rem 1rem;
  border-bottom: 1px solid var(--color-border);
}

.detail-label {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--color-text-muted);
}

.detail-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.1rem;
  padding: 0 0.3rem;
  border-radius: var(--radius-sm);
  background: var(--color-secondary-bg);
  color: var(--color-text-muted);
  font-size: 0.65rem;
}

.omitted-note {
  margin: 0.4rem 0 0;
  font-size: 0.75rem;
  color: var(--color-text-muted);
}
</style>
