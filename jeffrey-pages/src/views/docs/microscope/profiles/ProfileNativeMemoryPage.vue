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
  { id: 'rss-vs-heap', text: 'RSS vs Heap', level: 2 },
  { id: 'direct-buffers', text: 'Direct Buffers', level: 2 },
  { id: 'native-libraries', text: 'Native Libraries', level: 2 },
  { id: 'events', text: 'Source Events', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Native Memory"
      icon="bi bi-memory"
    />

    <div class="docs-content">
      <p>The Native Memory page tracks the process's <em>resident set size</em> (what the operating system actually charges the process) against the Java heap, plus direct-buffer usage and loaded native libraries. It is the page for the classic "the heap looks fine but the container got OOMKilled" investigation — when memory grows <em>outside</em> the heap.</p>

      <h2 id="overview">Overview</h2>
      <p>The header strip shows peak and final RSS, RSS growth across the recording, current direct-buffer memory and count, and the number of loaded native libraries.</p>

      <DocsCallout type="tip">
        <strong>Reading the gap:</strong> RSS minus heap usage is everything the JVM holds natively — metaspace, thread stacks, code cache, GC bookkeeping, direct buffers, and JNI/native allocations. A widening gap over time while the heap stays flat means native memory is growing.
      </DocsCallout>

      <h2 id="rss-vs-heap">RSS vs Heap</h2>
      <p>The headline chart: resident set size and heap used over the recording, from periodic <code>jdk.ResidentSetSize</code> samples and <code>jdk.GCHeapSummary</code> events. Compare the two trends — heap-driven growth shows in both lines; native growth shows only in RSS.</p>

      <h2 id="direct-buffers">Direct Buffers</h2>
      <p>Direct (off-heap NIO) buffer memory and buffer count over time, from <code>jdk.DirectBufferStatistics</code>. Steadily climbing buffer memory with a stable count suggests growing individual buffers; a climbing count is the classic NIO/Netty buffer-leak pattern (buffers retained and never released back).</p>

      <h2 id="native-libraries">Native Libraries</h2>
      <p>All native libraries mapped into the process with their mapped address-range sizes, from <code>jdk.NativeLibrary</code>. Useful to confirm which JNI-backed dependencies are present when chasing a native leak suspect.</p>

      <h2 id="events">Source Events</h2>
      <ul>
        <li><code>jdk.ResidentSetSize</code> — periodic RSS samples with current and peak values.</li>
        <li><code>jdk.GCHeapSummary</code> — heap usage at GC boundaries (the heap overlay).</li>
        <li><code>jdk.DirectBufferStatistics</code> — periodic direct-buffer count, capacity, and memory used.</li>
        <li><code>jdk.NativeLibrary</code> — loaded native libraries with mapped address ranges.</li>
      </ul>

      <DocsCallout type="info">
        <strong>Going deeper:</strong> when RSS growth points to native allocations, the async-profiler
        <em>Malloc</em> / <em>Native Leak</em> flamegraph events (when recorded) attribute the allocations to call sites.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
