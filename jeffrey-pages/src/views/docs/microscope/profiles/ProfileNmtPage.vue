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
  { id: 'categories', text: 'Categories', level: 2 },
  { id: 'totals', text: 'Reserved vs Committed', level: 2 },
  { id: 'rss', text: 'RSS vs Tracked', level: 2 },
  { id: 'enabling', text: 'Enabling NMT', level: 2 },
  { id: 'events', text: 'Source Events', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader title="Native Memory Tracking" icon="bi bi-pie-chart" />

    <div class="docs-content">
      <p>The Native Memory Tracking page shows the JVM's own accounting of memory <em>outside</em> the Java heap — thread stacks, JIT-compiled code, class metadata, GC structures and internal buffers — broken down by category with <strong>reserved</strong> (address space) vs <strong>committed</strong> (physically backed) bytes. It complements the Native Memory page (RSS, direct buffers, native libraries) with the JVM-internal view.</p>

      <DocsCallout type="tip">
        <strong>When to use it:</strong> the heap looks healthy but RSS keeps climbing or the container gets OOM-killed. A category growing steadily over the recording is the native-memory leak — growing <code>Thread</code> means leaking thread stacks, growing <code>Class</code> a classloader leak, growing <code>Code</code> JIT code-cache churn.
      </DocsCallout>

      <h2 id="overview">Overview</h2>
      <p>The header strip shows total committed and reserved native memory, the peak committed seen across the recording, the largest category, and "Untracked" — resident set size minus total NMT committed, which approximates memory NMT cannot account for (raw <code>malloc</code>, mappings outside its view).</p>

      <h2 id="categories">Categories</h2>
      <p>A stacked-area timeline of committed bytes per category (the top categories by peak, with the remainder grouped as "Other") plus a breakdown table — reserved, committed, share of committed, and growth (last − first committed) per category, sorted by committed.</p>

      <h2 id="totals">Reserved vs Committed</h2>
      <p>The total reserved-vs-committed timeline from <code>jdk.NativeMemoryUsageTotal</code>. A large reserved/committed gap is normal (the JVM reserves address space lazily); climbing committed is what drives RSS up.</p>

      <h2 id="rss">RSS vs Tracked</h2>
      <p>Resident set size (what the OS sees) overlaid on total NMT committed. The gap between the two approximates untracked memory — a steadily widening gap points at native allocations the JVM isn't tracking.</p>

      <h2 id="enabling">Enabling NMT</h2>
      <p>NMT is off by default. Launch the JVM with <code>-XX:NativeMemoryTracking=summary</code> (or <code>detail</code>) and capture the recording with the <code>profile</code> settings so the NMT JFR events are included, then re-import. There is a small runtime overhead (~5–10%), so it is usually enabled only while investigating a native-memory problem. Until a recording with NMT enabled is imported, the page shows an explanatory notice on each data tab.</p>

      <h2 id="events">Source Events</h2>
      <ul>
        <li><code>jdk.NativeMemoryUsage</code> — per-category reserved/committed bytes (<code>type</code>, <code>reserved</code>, <code>committed</code>).</li>
        <li><code>jdk.NativeMemoryUsageTotal</code> — total reserved/committed bytes.</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
