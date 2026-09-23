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
  Presentational entity-name renderer shared by the metric lists (Span Tags, HTTP Endpoints, gRPC
  Services, JDBC Groups). Each list parses its own name into NameSegments (see services/metricName.ts);
  this component owns the one consistent visual vocabulary: distinct group, dimmed path, faint
  separators, highlighted variables, bold leaf.
-->
<template>
  <span class="metric-name" :title="title ?? plainText">
    <span v-for="(seg, i) in segments" :key="i" :class="`mn-${seg.kind}`">{{ seg.text }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { NameSegment } from '@/services/metricName';

const props = defineProps<{
  segments: NameSegment[];
  title?: string;
}>();

const plainText = computed(() => props.segments.map(s => s.text).join(''));
</script>

<style scoped>
.metric-name {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.86rem;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Leading category token — highlighted (brand color), bold italic like the rest. */
.mn-group {
  color: var(--color-primary);
  font-weight: 700;
  font-style: italic;
}

/* The remainder after the group — bold italic, matching the emphasised segment treatment. */
.mn-name {
  color: var(--color-dark);
  font-weight: 700;
  font-style: italic;
}

/* Dimmed connective text (gRPC package) — italic, subordinate to the simple name. */
.mn-path {
  color: var(--color-text-light);
  font-weight: 500;
  font-style: italic;
}

/* Emphasised static URI segment — bold italic. */
.mn-segment {
  color: var(--color-dark);
  font-weight: 700;
  font-style: italic;
}

/* Grey URI separators (slashes). */
.mn-sep {
  color: var(--color-text-muted);
  font-weight: 400;
}

/* Highlighted variables (HTTP path params like {id}) — purple italic. */
.mn-var {
  color: var(--color-purple);
  font-weight: 500;
  font-style: italic;
}

/* The final, most specific segment (gRPC simple name) — bold italic. */
.mn-leaf {
  color: var(--color-dark);
  font-weight: 700;
  font-style: italic;
}
</style>
