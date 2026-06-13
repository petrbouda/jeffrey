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
  { id: 'operations', text: 'VM Operations', level: 2 },
  { id: 'safepoints', text: 'Safepoints', level: 2 },
  { id: 'types', text: 'Types of VM Operation', level: 2 },
  { id: 'events', text: 'Source Events', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader title="VM Operations" icon="bi bi-stopwatch" />

    <div class="docs-content">
      <p>The VM Operations page surfaces JVM-internal stop-the-world activity beyond GC. Many JVM tasks need every Java thread paused at a <strong>safepoint</strong>; the JVM waits for the slowest thread to get there (time-to-safepoint), runs the <strong>VM operation</strong>, then releases everyone. None of this appears in a CPU profile.</p>

      <DocsCallout type="tip">
        <strong>Where to look:</strong> a long <em>Longest Pause</em> that isn't a GC operation (a <code>ThreadDump</code>, <code>RedefineClasses</code> or heap inspection) points at tooling or agents stopping the JVM; a high time-to-safepoint points at a thread slow to reach a poll point.
      </DocsCallout>

      <h2 id="overview">Overview</h2>
      <p>The header strip shows total safepoint pause time and VM-operation count, the longest single pause with its operation name, and the number of distinct operation types.</p>

      <h2 id="operations">VM Operations</h2>
      <p>Every <code>jdk.ExecuteVMOperation</code> grouped by operation, with counts and total/max durations. Operations flagged <em>Safepoint</em> stop every Java thread; <em>Blocking</em> means the requesting thread waited for completion.</p>

      <h2 id="safepoints">Safepoints</h2>
      <p>A per-second timeline of safepoint pause time (the time spent <em>at</em> safepoints, always available) plus — when <code>jdk.SafepointStateSynchronization</code> is enabled — a <em>time-to-safepoint</em> timeline: the wall-clock spent waiting for all threads to reach a safepoint before the operation can begin. High TTSP points at threads slow to poll (long counted loops, JNI), not at the operation itself.</p>

      <h2 id="types">Types of VM Operation</h2>
      <ul>
        <li><strong>GC collections</strong> (<code>G1CollectForAllocation</code>, …) — the stop-the-world phases of garbage collection.</li>
        <li><strong>Deoptimization</strong> (<code>Deoptimize</code>) — discarding JIT-optimized code whose speculation failed.</li>
        <li><strong>Bias revocation</strong> (<code>RevokeBias</code> / <code>BulkRevokeBias</code>) — undoing biased locking under contention (pre-JDK 15).</li>
        <li><strong>Thread dumps &amp; stacks</strong> (<code>ThreadDump</code>, <code>GetThreadListStackTraces</code>, <code>FindDeadlocks</code>) — from <code>jstack</code>, profilers and APM agents.</li>
        <li><strong>Class redefinition</strong> (<code>RedefineClasses</code> / <code>RetransformClasses</code>) — instrumentation agents rewriting bytecode.</li>
        <li><strong>Heap inspection</strong> (<code>HeapDumpOperation</code>, <code>HeapInspection</code>) — walking the whole heap; rare but long.</li>
      </ul>

      <h2 id="events">Source Events</h2>
      <ul>
        <li><code>jdk.ExecuteVMOperation</code> — every VM operation with safepoint/blocking flags (enabled by default).</li>
        <li><code>jdk.SafepointStateSynchronization</code> / <code>SafepointBegin</code> / <code>SafepointEnd</code> — time-to-safepoint and safepoint phases (off by default).</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
