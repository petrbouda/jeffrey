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
import { computed } from 'vue';
import PluginKindTag from '@/components/plugin/PluginKindTag.vue';
import { formatOffset, usePluginUsage } from '@/composables/usePluginUsage';
import type { ReplayStep } from '@/types/pluginUsage';

// The call at the playback position: what it called, who it is charged to, and its input.

const props = defineProps<{ step: ReplayStep }>();

const model = usePluginUsage();

const tool = computed(() => (props.step.kind === 'jeffrey' ? model.tools.get(props.step.name) : undefined));

const facts = computed<string[]>(() => {
  const list = [formatOffset(props.step.t)];
  if (tool.value) {
    list.push(tool.value.destructive ? 'destructive' : tool.value.readOnly ? 'read-only' : 'writes');
    if (tool.value.cost) {
      list.push(`cost ${tool.value.cost.toLowerCase()}`);
    }
  }
  return list;
});
</script>

<template>
  <div class="step-card">
    <div class="step-card-name"><PluginKindTag :kind="step.kind" /> {{ step.name }}</div>
    <p v-if="tool?.title" class="step-card-title">{{ tool.title }}</p>
    <p class="step-card-meta">
      <template v-if="step.by">
        by <span :class="`step-card-by--${step.by[0]}`">{{ step.by[1] }}</span> ·
      </template>
      {{ facts.join(' · ') }}
    </p>
    <pre v-if="step.input" class="step-card-input">{{ step.input }}</pre>
  </div>
</template>

<style scoped>
.step-card {
  padding: 12px 14px;
  border: 1px solid var(--site-line);
  border-radius: var(--ap-radius-md);
  background: var(--site-surface);
}

.step-card-name {
  font-family: var(--site-font-mono);
  font-size: 13px;
  font-weight: 600;
  word-break: break-all;
}

.step-card-title {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--site-muted);
}

.step-card-meta {
  margin: 6px 0 0;
  font-size: 12.5px;
  color: var(--site-faint);
}

.step-card-by--skill {
  color: var(--ap-skill);
}

.step-card-by--agent {
  color: var(--ap-agent);
}

.step-card-input {
  margin: 10px 0 0;
  padding: 8px 10px;
  border: 1px solid var(--site-line);
  border-radius: var(--site-radius-sm);
  background: var(--site-paper);
  font-family: var(--site-font-mono);
  font-size: 11.5px;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--site-muted);
}
</style>
