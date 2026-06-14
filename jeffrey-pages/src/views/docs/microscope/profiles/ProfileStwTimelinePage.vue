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
  { id: 'timeline', text: 'Timeline & Budget', level: 2 },
  { id: 'lanes', text: 'The Lanes', level: 2 },
  { id: 'inventory', text: 'Inventory & Drawer', level: 2 },
  { id: 'analysis', text: 'Analysis', level: 2 },
  { id: 'events', text: 'Source Events', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader title="Stop-The-World" icon="bi bi-pause-circle" />

    <div class="docs-content">
      <p>The Stop-The-World page is the single place to answer <em>"when was my application frozen, and by what?"</em> It merges every JVM pause source — GC pauses, safepoint VM operations, time-to-safepoint, and per-thread stalls — onto one shared time axis, so a latency spike caused by several pauses landing together becomes obvious instead of being scattered across separate pages.</p>

      <h2 id="overview">Overview</h2>
      <p>A "stop-the-world" (STW) pause freezes every application thread while the JVM does something it cannot do concurrently. This page separates whole-JVM pauses (<em>Global STW</em>) from single-thread stalls (<em>Local Stalls</em>) and lets you brush, zoom, and click into any pause to see what else happened at that moment.</p>

      <h2 id="timeline">Timeline &amp; Budget</h2>
      <p>The <strong>app-stop budget</strong> band charts frozen nanoseconds per second as two series — Global STW (GC + safepoint operations) and Local Stalls (monitor / park / pinning). Brushing the budget zooms the swimlane below it to the same window. The <strong>swimlane</strong> draws each individual pause as a bar on its category's lane, so you can see overlap and clustering at a glance.</p>

      <h2 id="lanes">The Lanes</h2>
      <ul>
        <li><strong>GC Pause</strong> — stop-the-world collection time (uses the per-collection sum-of-pauses, so concurrent GC work is excluded).</li>
        <li><strong>VM Operation</strong> — safepoint operations the JVM stopped to run.</li>
        <li><strong>Time to Safepoint</strong> — how long the JVM waited for all threads to reach the safepoint. Shown as its own lane and <em>excluded</em> from the budget so it is not double-counted with the operation pause that follows.</li>
        <li><strong>Local Stalls</strong> — per-thread, not whole-JVM: monitor contention, thread parking, and virtual-thread pinning.</li>
      </ul>

      <DocsCallout type="tip">
        Time-to-safepoint spikes are one of the most useful and least-known signals: a long sync time means some thread was slow to reach a safepoint (a JNI call, a counted loop, a page fault) while the rest of the JVM waited.
      </DocsCallout>

      <h2 id="inventory">Inventory &amp; Drawer</h2>
      <p>The Inventory tab lists every pause in a searchable table, plus a longest-pauses leaderboard and a cause-attribution breakdown (grouped by category and cause). Clicking any pause — in the swimlane, the leaderboard, or the table — opens a drawer with its detail and every other pause overlapping its window, the latency-outlier drill-down.</p>

      <h2 id="analysis">Analysis</h2>
      <p>The Analysis tab adds three diagnostics computed from the loaded pauses:</p>
      <ul>
        <li><strong>Pause-budget simulator</strong> — drag a max-acceptable-pause slider to see how many pauses violate it, their total time, and the resulting global-STW availability.</li>
        <li><strong>Minimum Mutator Utilization (MMU)</strong> — for each window size, the worst-case fraction of time the application actually ran. Low MMU at small windows means short, frequent pauses; low MMU only at large windows means rare but very long ones.</li>
        <li><strong>Pause density</strong> — total pause time per lane across time buckets, so clustering pops out even when individual pauses are tiny.</li>
      </ul>

      <h2 id="events">Source Events</h2>
      <ul>
        <li><code>jdk.GarbageCollection</code> — stop-the-world GC pause time per collection.</li>
        <li><code>jdk.ExecuteVMOperation</code> — safepoint VM operations.</li>
        <li><code>jdk.SafepointStateSynchronization</code> — time-to-safepoint.</li>
        <li><code>jdk.JavaMonitorEnter</code>, <code>jdk.ThreadPark</code>, <code>jdk.VirtualThreadPinned</code> — per-thread stalls.</li>
      </ul>
      <p>ZGC allocation stalls (<code>jdk.ZAllocationStall</code>) will join as a lane once that event support lands.</p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
