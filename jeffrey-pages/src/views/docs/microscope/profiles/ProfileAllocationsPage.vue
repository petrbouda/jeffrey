<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
