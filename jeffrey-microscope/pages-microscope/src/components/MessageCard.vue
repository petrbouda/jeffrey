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

<script setup lang="ts">
import type { ImportantMessage } from '@/services/api/model/ImportantMessage';
import FormattingService from '@/services/FormattingService';

defineProps<{
  message: ImportantMessage;
}>();

function formatTime(createdAt: number): string {
  return FormattingService.formatTimestampUTC(createdAt);
}
</script>

<template>
  <div class="message-card" :class="'severity-bg-' + message.severity.toLowerCase()">
    <div class="message-card-title">{{ message.title }}</div>
    <div class="message-card-meta">
      <span class="meta-badge" :class="'severity-badge-' + message.severity.toLowerCase()">{{
        message.category
      }}</span>
      <span class="meta-item"><i class="bi bi-geo-alt"></i> {{ message.source }}</span>
      <span class="meta-item"><i class="bi bi-clock"></i> {{ formatTime(message.createdAt) }}</span>
    </div>
    <div class="message-card-body">{{ message.message }}</div>
  </div>
</template>

<style scoped>
.message-card {
  border-radius: 0;
  border-left: 4px solid;
  padding: 0.6rem 0.85rem;
}

.message-card.severity-bg-critical {
  background-color: var(--color-danger-bg-lighter);
  border-left-color: var(--color-danger);
}

.message-card.severity-bg-high {
  background-color: var(--color-warning-bg);
  border-left-color: var(--color-orange);
}

.message-card.severity-bg-medium {
  background-color: var(--color-amber-bg);
  border-left-color: var(--color-amber);
}

.message-card.severity-bg-low {
  background-color: var(--color-success-bg);
  border-left-color: var(--color-info);
}

.message-card-title {
  font-weight: 600;
  font-size: 0.85rem;
  color: var(--color-dark);
}

.message-card-meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.25rem;
}

.meta-badge {
  display: inline-block;
  padding: 0.1rem 0.4rem;
  font-size: 0.65rem;
  font-weight: 700;
  text-transform: uppercase;
  border-radius: 4px;
  border: 1px solid;
  white-space: nowrap;
}

.severity-badge-critical {
  color: var(--color-danger-hover);
  border-color: var(--color-danger-hover);
  background-color: rgba(220, 38, 38, 0.08);
}

.severity-badge-high {
  color: var(--color-warning-hover);
  border-color: var(--color-warning-hover);
  background-color: rgba(234, 88, 12, 0.08);
}

.severity-badge-medium {
  color: var(--color-amber);
  border-color: var(--color-amber);
  background-color: rgba(202, 138, 4, 0.08);
}

.severity-badge-low {
  color: var(--color-info);
  border-color: var(--color-info);
  background-color: rgba(8, 145, 178, 0.08);
}

.meta-item {
  font-size: 0.78rem;
  color: var(--color-text-muted);
  white-space: nowrap;
}

.meta-item i {
  font-size: 0.7rem;
  margin-right: 0.15rem;
}

.message-card-body {
  font-size: 0.8rem;
  color: var(--color-text-muted);
  line-height: 1.4;
  margin-top: 0.3rem;
}

@media (max-width: 768px) {
  .message-card-meta {
    flex-wrap: wrap;
  }
}
</style>
