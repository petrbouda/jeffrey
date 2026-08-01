<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ Licensed under the GNU Affero General Public License v3.
  -->

<!--
  Heap-dump adapter over the generic @shared ProcessingTimeline: it supplies the heap-dump phase
  grouping and step labels and forwards the live/persisted step data. All the layout, timing and
  sub-phase accordion live in the shared component, reused by the Advisor and future staged features.
-->
<script lang="ts">
// Re-exported so existing importers (ProfileHeapDumpSettings) keep `import { type TimelineStep }` here.
export type { TimelineStep } from '@shared/types/processing';
</script>

<script setup lang="ts">
import ProcessingTimeline from '@shared/components/ProcessingTimeline.vue';
import type { TimelinePhase, TimelineStep } from '@shared/types/processing';

withDefaults(
  defineProps<{
    steps: TimelineStep[];
    tickNow: number;
    title?: string;
    subtitle?: string;
    showProgressBar?: boolean;
  }>(),
  { showProgressBar: true }
);

const DEFAULT_SUBTITLE = 'Please wait while we analyze the heap structure...';

/**
 * The canonical heap-dump pipeline: phase grouping + per-step labels. Source of truth for the layout —
 * the backend stores only step ids + status + duration.
 */
const HEAP_DUMP_PHASES: TimelinePhase[] = [
  {
    id: 'indexing',
    name: 'Heap Indexing',
    description: 'Loading and parsing the heap file',
    steps: [
      { id: 'load', label: 'Loading heap dump' },
      { id: 'parse', label: 'Parsing heap structure' },
      { id: 'index', label: 'Building indexes' }
    ]
  },
  {
    id: 'analysis',
    name: 'Memory Analysis',
    description: 'Strings, threads, dominator tree, leaks',
    steps: [
      { id: 'strings', label: 'Analyzing strings' },
      { id: 'dominator', label: 'Computing dominator tree' },
      { id: 'threads', label: 'Analyzing threads' },
      { id: 'biggest', label: 'Finding biggest objects' },
      { id: 'collections', label: 'Analyzing collections' },
      { id: 'leaks', label: 'Detecting leak suspects' }
    ]
  },
  {
    id: 'hotspots',
    name: 'Hotspots',
    description: 'Class loaders, biggest collections, waste',
    steps: [
      { id: 'classloaders', label: 'Analyzing class loaders' },
      { id: 'consumers', label: 'Aggregating memory consumers' },
      { id: 'duplicates', label: 'Detecting duplicate data' },
      { id: 'biggest-collections', label: 'Finding biggest collections' }
    ]
  }
];
</script>

<template>
  <ProcessingTimeline
    :phases="HEAP_DUMP_PHASES"
    :steps="steps"
    :tick-now="tickNow"
    :title="title ?? 'Initializing Heap Dump'"
    :subtitle="subtitle ?? DEFAULT_SUBTITLE"
    :show-progress-bar="showProgressBar"
  />
</template>
