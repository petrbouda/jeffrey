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
import { onMounted } from 'vue';
import DocsCallout from '@/components/docs/DocsCallout.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'rate', text: 'Allocation Rate', level: 2 },
  { id: 'types', text: 'Top Allocated Types', level: 2 },
  { id: 'events', text: 'Source Events', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader title="Allocations" icon="bi bi-box" />

    <div class="docs-content">
      <p>The Allocations page shows where heap allocation pressure comes from: the allocation rate over time, the split between in-TLAB and outside-TLAB allocations, and — most usefully — the classes responsible for the most allocated bytes.</p>

      <h2 id="overview">Overview</h2>
      <p>Total allocated bytes, the in-TLAB vs outside-TLAB split (large/uncommon allocations bypass the TLAB), the number of distinct allocated classes, and the dominant class. When only the sampled event is available the page notes <em>Sampled</em> and omits the TLAB split.</p>

      <DocsCallout type="tip">
        <strong>Allocation drives GC.</strong> Reducing the allocation rate of the top types is usually the most direct way to cut young-GC frequency. Outside-TLAB allocations are large objects worth scrutinising.
      </DocsCallout>

      <h2 id="rate">Allocation Rate</h2>
      <p>Bytes allocated per second across the recording, summed from the allocation events' sizes. Sustained high rates correlate with frequent young collections; spikes often precede a GC pause.</p>

      <h2 id="types">Top Allocated Types</h2>
      <p>Classes ranked by total allocated bytes, with the allocation event count and a share bar relative to the top type. This is the actionable view — the classes here are the ones to pool, cache, or stop creating.</p>

      <h2 id="events">Source Events</h2>
      <ul>
        <li><code>jdk.ObjectAllocationInNewTLAB</code> / <code>jdk.ObjectAllocationOutsideTLAB</code> — per-allocation events (preferred; give the TLAB split).</li>
        <li><code>jdk.ObjectAllocationSample</code> — the lower-overhead sampled fallback (no TLAB split).</li>
      </ul>

      <DocsCallout type="info">
        For allocation <em>call sites</em> (which code allocated), use the allocation flamegraph on the Visualization pages — this page answers <em>which classes</em>, the flamegraph answers <em>where</em>.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
