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
  Consistent notice for visualizations that are unavailable because a JFR event is disabled in the
  recording. Renders a calm info-accent callout: title + explanation (default slot), an optional
  copyable enable command, and an optional rich "how to enable" block (#action slot).
-->
<template>
  <div class="disabled-events-notice">
    <div class="den-head">
      <i class="bi" :class="icon"></i>
      <span class="den-title">{{ title }}</span>
    </div>

    <div class="den-desc">
      <slot></slot>
    </div>

    <div v-if="actionLabel" class="den-action-label">{{ actionLabel }}</div>

    <div v-if="command" class="den-command" title="Click to copy" @click="copy">
      <code>{{ command }}</code>
      <i class="bi bi-clipboard den-copy"></i>
    </div>

    <div v-if="$slots.action" class="den-action">
      <slot name="action"></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
import ToastService from '@shared/services/ToastService';

const props = withDefaults(
  defineProps<{
    title: string;
    actionLabel?: string;
    icon?: string;
    command?: string;
  }>(),
  {
    actionLabel: undefined,
    icon: 'bi-info-circle-fill',
    command: undefined
  }
);

const copy = async (): Promise<void> => {
  if (!props.command) {
    return;
  }
  try {
    await navigator.clipboard.writeText(props.command);
    ToastService.success('Copied!', 'Command copied to clipboard');
  } catch (error) {
    console.error('Failed to copy:', error);
    ToastService.error('Copy Failed', 'Could not copy to clipboard');
  }
};
</script>

<style scoped>
.disabled-events-notice {
  border: 1px solid var(--color-border);
  border-left: 4px solid var(--color-info);
  background: var(--color-info-light);
  border-radius: var(--radius-md);
  padding: 18px 20px;
  color: var(--color-text);
}

.den-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.den-head i {
  color: var(--color-info);
  font-size: 1.2rem;
}

.den-title {
  font-size: 0.92rem;
  font-weight: 700;
  color: var(--color-dark);
}

.den-desc {
  margin-top: 10px;
  font-size: 0.83rem;
  line-height: 1.6;
}

.den-action-label {
  margin-top: 16px;
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.4px;
  color: var(--color-text-muted);
}

.den-command {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
  padding: 10px 14px;
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: border-color 0.15s ease-in-out;
}

.den-command:hover {
  border-color: var(--color-primary);
}

.den-command code {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.8rem;
  color: var(--color-text);
  overflow-x: auto;
  white-space: nowrap;
}

.den-copy {
  flex: none;
  color: var(--color-text-light);
  font-size: 0.85rem;
  transition: color 0.15s ease-in-out;
}

.den-command:hover .den-copy {
  color: var(--color-primary);
}

.den-action {
  margin-top: 8px;
  font-size: 0.83rem;
  line-height: 1.6;
}

/* Slotted rich content (description + action) */
.den-desc :deep(code),
.den-action :deep(code) {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.8em;
  color: var(--color-text);
  background: rgba(94, 100, 255, 0.07);
  padding: 1px 5px;
  border-radius: var(--radius-sm);
}

.den-action :deep(p) {
  margin: 0 0 8px;
}

.den-action :deep(p:last-child) {
  margin-bottom: 0;
}

.den-action :deep(ul) {
  margin: 8px 0;
  padding-left: 18px;
}

.den-action :deep(li) {
  line-height: 1.7;
}
</style>
