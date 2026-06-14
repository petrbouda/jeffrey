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
  { id: 'timeline', text: 'State Timeline', level: 2 },
  { id: 'activity', text: 'Activity', level: 2 },
  { id: 'locks', text: 'Locks & Deadlocks', level: 2 },
  { id: 'stuck', text: 'Stuck Threads', level: 2 },
  { id: 'heatmap', text: 'Heatmap', level: 2 },
  { id: 'browse', text: 'Browse', level: 2 },
  { id: 'events', text: 'Source Events', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Thread Dumps"
      icon="bi bi-file-earmark-text"
    />

    <div class="docs-content">
      <p>The JVM periodically emits a full textual thread dump (<code>jdk.ThreadDump</code>, the same content as <code>jstack</code>): every thread's state, stack, and held/awaited monitors. Jeffrey parses each dump and correlates them across the recording, turning a pile of text snapshots into trends, contention analysis, and hang detection.</p>

      <DocsCallout type="tip">
        <strong>Sampled, not continuous:</strong> dumps are periodic (~every 60s by default), so this is a coarse sample — ideal for hangs, deadlocks, and pool saturation, but not for sub-second spikes (use the flamegraph / wall-clock views for those).
      </DocsCallout>

      <h2 id="overview">Overview</h2>
      <p>The header strip shows the number of dumps, peak thread count, deadlocks detected, and stuck threads found.</p>

      <h2 id="timeline">State Timeline</h2>
      <p>A stacked area of thread counts by state (RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, …) across the dumps, carried forward between samples. A BLOCKED spike is a lock storm; a steadily climbing total is a thread leak; a pool sitting in WAITING is idle capacity.</p>

      <h2 id="activity">Activity</h2>
      <p>The stack frames threads sit at most often, aggregated across all dumps, with occurrences and distinct-thread counts. Many threads at one frame reveal an idle pool (parked), an I/O wait, or a genuine hotspot — a poor-man's wall-clock snapshot.</p>

      <h2 id="locks">Locks &amp; Deadlocks</h2>
      <p>Any JVM-reported "Found one Java-level deadlock" section is surfaced with its involved threads, plus a lock-contention table for the worst dump: the most-contended monitors with their waiter count and owning thread (parsed from <code>locked</code> / <code>waiting to lock</code> / <code>parking to wait for</code> lines).</p>

      <h2 id="stuck">Stuck Threads</h2>
      <p>Threads whose top stack frames stayed identical across three or more consecutive dumps — a hung, slow, or deadlocked thread. The longer the run (and the larger "stuck for"), the more suspicious.</p>

      <h2 id="heatmap">Heatmap</h2>
      <p>A grid of each tracked thread's state across the dump sequence (BLOCKED and stuck threads first). Spot a thread that turns and stays BLOCKED, or a pool that gradually fills with BLOCKED.</p>

      <h2 id="browse">Browse</h2>
      <p>A dump selector loads any single dump on demand: threads grouped with state badges and foldable stacks, filterable by state and name, plus a toggle to view the verbatim raw dump text.</p>

      <h2 id="events">Source Events</h2>
      <ul>
        <li><code>jdk.ThreadDump</code> — periodic full textual thread dump (the <code>result</code> field).</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
