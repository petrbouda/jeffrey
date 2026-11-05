<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
-->

<script setup lang="ts">
import { formatCount, type NodeKind } from '@/composables/usePluginUsage';

// A skill, agent, tool family or tool as a pill: coloured by its kind, with an optional count.

withDefaults(defineProps<{ kind: NodeKind; label: string; count?: number | null; mono?: boolean }>(), {
  count: null,
  mono: true
});

defineEmits<{ (e: 'pick'): void }>();
</script>

<template>
  <button type="button" class="node-chip" :class="[`node-chip--${kind}`, { 'node-chip--mono': mono }]" @click="$emit('pick')">
    <span class="node-chip-label">{{ label }}</span>
    <span v-if="count !== null" class="node-chip-count">{{ formatCount(count) }}</span>
  </button>
</template>

<style scoped>
.node-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  padding: 3px 9px;
  border: 1px solid transparent;
  border-radius: var(--ap-radius-pill);
  font-size: 12px;
  line-height: 1.5;
  cursor: pointer;
  transition: border-color 0.15s;
}

.node-chip:hover,
.node-chip:focus-visible {
  border-color: currentColor;
}

.node-chip--mono .node-chip-label {
  font-family: var(--site-font-mono);
}

.node-chip-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-chip-count {
  color: var(--site-faint);
  font-variant-numeric: tabular-nums;
}

.node-chip--skill {
  background: var(--ap-skill-bg);
  color: var(--ap-skill);
}

.node-chip--agent {
  background: var(--ap-agent-bg);
  color: var(--ap-agent);
}

.node-chip--family,
.node-chip--tool {
  background: var(--ap-tool-bg);
  color: var(--ap-tool);
}
</style>
