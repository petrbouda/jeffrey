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
  { id: 'allocation-stalls', text: 'Allocation Stalls', level: 2 },
  { id: 'cycles', text: 'GC Cycles', level: 2 },
  { id: 'pages', text: 'Pages & Memory', level: 2 },
  { id: 'relocation', text: 'Relocation', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader title="ZGC Analysis" icon="bi bi-cpu" />

    <div class="docs-content">
      <p>The ZGC Analysis page is a collector-specific deep dive into the generational ZGC collector. Because ZGC is concurrent and its stop-the-world pauses are sub-millisecond, the signal that actually matters for latency is the <strong>allocation stall</strong> — and that is where this page starts. It shows an empty state when the recording was not produced with ZGC.</p>

      <h2 id="overview">Overview</h2>
      <p>A header strip surfaces young / old cycle counts, allocation-stall count with total and max stall time, total bytes of pages allocated, and memory uncommitted back to the OS.</p>

      <h2 id="allocation-stalls">Allocation Stalls</h2>
      <p>When the application allocates faster than ZGC can reclaim memory, mutator threads are stalled until a page becomes available. This tab plots stall count and total stall time per second (<code>jdk.ZAllocationStall</code>), and breaks stalls down by page type (Small / Medium / Large) and by the threads that stalled the most. Sustained stalls mean the collector cannot keep up with the allocation rate.</p>

      <DocsCallout type="tip">
        <strong>Where to start:</strong> if you are chasing latency on ZGC, this is the first tab to open — ordinary GC pauses will look tiny, but allocation stalls show the real application impact.
      </DocsCallout>

      <h2 id="cycles">GC Cycles</h2>
      <p>Generational ZGC runs separate young and old collections. This tab lists each cycle (<code>jdk.ZYoungGarbageCollection</code>, <code>jdk.ZOldGarbageCollection</code>) with its duration, generation, and — for young cycles — the tenuring threshold that drives promotion to the old generation.</p>

      <h2 id="pages">Pages &amp; Memory</h2>
      <p>Page-allocation throughput over time (<code>jdk.ZPageAllocation</code>) shows how hard the allocator is working; high sustained allocation alongside stalls confirms the collector is behind. A table of uncommit events (<code>jdk.ZUncommit</code>) shows memory returned to the operating system.</p>

      <h2 id="relocation">Relocation</h2>
      <p>Relocation-set composition per cycle (<code>jdk.ZRelocationSet</code>) — total, empty, and relocated pages. Empty pages are reclaimed without relocation; large relocation sets increase the amount of concurrent copying work.</p>

      <DocsCallout type="info">
        <strong>Read the JFR canonical event list:</strong> the
        <a href="https://sap.github.io/jfrevents/" target="_blank" rel="noopener">SAP JFR Events catalog</a>
        documents every ZGC event the JDK emits, including the exact field names used here.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
