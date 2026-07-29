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
  { id: 'lanes', text: 'Lanes and Grouping', level: 2 },
  { id: 'bands', text: 'Bands and Resolution', level: 2 },
  { id: 'tooltips', text: 'Tooltips', level: 2 },
  { id: 'ordering', text: 'Ordering and Filtering', level: 2 },
  { id: 'flamegraphs', text: 'Flamegraphs from a Lane', level: 2 },
  { id: 'events', text: 'Source Events', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Thread Timeline"
      icon="bi bi-clock-history"
    />

    <div class="docs-content">
      <p>A row per thread across the whole recording, with every thread's parks, monitor blocks, waits, sleeps, socket reads and writes, and file reads and writes drawn in place. Where the thread-dump views sample the JVM every minute or so, this is built from individual events — it is the view for "what was this thread actually doing, and when".</p>

      <h2 id="overview">Overview</h2>
      <p>Each lane is a canvas spanning the recording from start to finish. A green underlay marks the thread's lifespan; coloured blocks above it mark activity, one colour per category. A lane that is solid with one colour is a thread saturated by that kind of work.</p>

      <h2 id="lanes">Lanes and Grouping</h2>
      <p>Threads whose names differ only by a trailing number are collapsed into one lane: <code>http-nio-8080-exec-1</code> through <code>-19</code> become <code>http-nio-8080-exec-*</code>, badged with the number of threads behind it. A recording can hold thousands of pool workers, and one lane each would bury everything else. Expanding a lane pages through its members; a group of one is never collapsed and keeps its real identity.</p>
      <p>The grouping is by trailing number only, so <code>pool-3-thread-1</code> and <code>pool-4-thread-1</code> stay separate — they are different pools, not two workers of one.</p>

      <h2 id="bands">Bands and Resolution</h2>
      <p>Events are merged server-side into the blocks the timeline can actually draw. Two events closer together than a pixel are the same rectangle on screen, so shipping them separately would cost a JSON object each for no visible difference — a busy thread can emit tens of thousands per category.</p>

      <DocsCallout type="info">
        <strong>A solid lane is real, not a rounding artefact.</strong> Merging only ever bridges gaps too small to see. If a Socket Read lane looks continuous, that thread genuinely was reading with only sub-pixel gaps between calls.
      </DocsCallout>

      <p>Merging chains, though: a block keeps growing while the next event starts within a pixel of its end. On a saturated thread that means <em>one</em> block covering the entire recording. A block's own event count is therefore the total for a whole run of activity, and says nothing about any position inside it — which is exactly why the tooltip does not use it.</p>

      <h2 id="tooltips">Tooltips</h2>
      <p>Hovering a lane describes the <strong>slice of time under the pointer</strong>, not the block beneath it: how many events of that category start in that slice, and the fields of one of them. Move along the lane and both change.</p>
      <p>Two details worth knowing when reading the number:</p>
      <ul>
        <li><strong>An event belongs to the slice it starts in.</strong> This keeps the per-slice counts adding up to the lane's total, with nothing counted twice. The cost is that the middle of a long park or monitor wait contains no start of its own — there, the tooltip falls back to the event still running through the slice and labels it as in progress.</li>
        <li><strong>Events are stored at millisecond resolution.</strong> A slice narrower than a millisecond is widened to one, so on a short recording several neighbouring positions legitimately report the same count. The tooltip prints the window it actually covered, so the number is never shown as finer-grained than it is.</li>
      </ul>
      <p>On a collapsed lane the count spans every thread in the group, which is what makes a pool's aggregate load visible without expanding it.</p>

      <h2 id="ordering">Ordering and Filtering</h2>
      <p>Lanes come busiest-first by default (<em>Event Count</em>), or by <em>Lifespan</em>, or <em>Alphabetically</em>. Ordering and filtering both happen on the server: a page holds fifty lanes, and a client that holds one page could only ever sort and filter that page. The name filter matches a lane's own name or any member's, so searching <code>exec-17</code> finds the group it lives in.</p>

      <h2 id="flamegraphs">Flamegraphs from a Lane</h2>
      <p>A lane opens a flamegraph scoped to its thread — or, for a collapsed lane, to every thread in the group at once.</p>

      <h2 id="events">Source Events</h2>
      <ul>
        <li><code>jdk.ThreadStart</code>, <code>jdk.ThreadEnd</code> — the lifespan underlay, reconstructed from start/end pairs.</li>
        <li><code>jdk.ThreadPark</code> — parks (<code>LockSupport</code>, and so most pool idling).</li>
        <li><code>jdk.JavaMonitorEnter</code>, <code>jdk.JavaMonitorWait</code> — blocked on a monitor, and waiting on one.</li>
        <li><code>jdk.ThreadSleep</code> — explicit sleeps.</li>
        <li><code>jdk.SocketRead</code>, <code>jdk.SocketWrite</code> — socket I/O, with remote host, address, port and byte counts.</li>
        <li><code>jdk.FileRead</code>, <code>jdk.FileWrite</code> — file I/O, with path and byte counts.</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
