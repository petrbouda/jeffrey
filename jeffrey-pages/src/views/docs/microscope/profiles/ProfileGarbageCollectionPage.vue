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
  { id: 'pause-distribution', text: 'Pause Distribution', level: 2 },
  { id: 'gc-efficiency', text: 'GC Efficiency', level: 2 },
  { id: 'longest-pauses', text: 'Longest Pauses', level: 2 },
  { id: 'concurrent-cycles', text: 'Concurrent Cycles', level: 2 },
  { id: 'pause-types', text: 'Pause Types Reference', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Garbage Collection"
      icon="bi bi-recycle"
    />

    <div class="docs-content">
      <p>The Garbage Collection page summarises every GC event JFR captured during the recording — pause durations, throughput, collector activity, and the reasons each collection was triggered. The view is split into five tabs that build from a high-level overview down to per-event detail.</p>

      <h2 id="overview">Overview</h2>
      <p>A header strip at the top of the page surfaces the headline numbers — total collections, young vs. old splits, pause-time percentiles (max, p99, p95), throughput percentage, and collection frequency — so you can judge GC health at a glance before drilling into any tab.</p>

      <DocsCallout type="tip">
        <strong>Where to start:</strong> open <em>Pause Distribution</em> first to see whether pauses are short and tight or long-tailed, then jump to <em>Longest Pauses</em> to investigate the worst events.
      </DocsCallout>

      <h2 id="pause-distribution">Pause Distribution</h2>
      <p>A bar chart that buckets every pause by duration (e.g. 0–5&nbsp;ms, 5–10&nbsp;ms, 10–25&nbsp;ms, …). The shape of the histogram tells you whether pauses cluster around a tight value (healthy) or spread across a wide range (problematic tail latencies). Useful for spotting outliers that the headline percentiles smooth over.</p>

      <h2 id="gc-efficiency">GC Efficiency</h2>
      <p>A donut chart and side panel breaking total wall time into <strong>application time</strong> vs. <strong>GC time</strong>, plus the throughput / overhead percentages and collection frequency. This is the page to point at when you need to demonstrate "we spend N% of the JVM's life in GC" to a stakeholder.</p>

      <h2 id="longest-pauses">Longest Pauses</h2>
      <p>A sortable table of the slowest individual pause events, including the GC ID, cause, collector name, sum-of-pauses, total duration, before / after heap occupancy, the absolute and percent difference, and a memory-reclaim efficiency bar. Click any row to open a detail modal with the per-event metadata.</p>

      <p>Each row's <strong>Cause</strong> badge has a hover tooltip describing what the JVM was responding to. The full taxonomy of causes lives on the <em>Pause Types</em> tab below.</p>

      <h2 id="concurrent-cycles">Concurrent Cycles</h2>
      <p>For collectors that perform concurrent work (G1, ZGC, Shenandoah, CMS), this tab lists the longest concurrent cycle events with their timestamp, collector name, total duration, and sum of stop-the-world sub-pauses. Collectors that don't support concurrent cycles (e.g. Serial, Parallel) display an information notice instead.</p>

      <h2 id="pause-types">Pause Types Reference</h2>
      <p>A searchable, category-filterable reference for every GC cause the JVM may emit. Use the search input to filter by cause name, or click the category chips to narrow to a single group:</p>

      <ul>
        <li><strong>Allocation-Driven Pauses</strong> — Allocation Failure, G1 Evacuation Pause, G1 Humongous Allocation, To-space Exhausted, Promotion Failed.</li>
        <li><strong>Concurrent Cycles</strong> — Concurrent Mark Start, Concurrent Mode Failure.</li>
        <li><strong>Memory Pressure &amp; Failure Modes</strong> — Last Ditch Collection, Metadata GC Threshold, Metadata GC Clear Soft References.</li>
        <li><strong>JVM-Initiated Tuning</strong> — Ergonomics, Proactive, Warmup, Timer.</li>
        <li><strong>External / Diagnostic Triggers</strong> — System.gc(), Diagnostic Command, JFR Periodic, Heap Inspection/Dump, GCLocker Initiated GC.</li>
      </ul>

      <p>The same descriptions back the hover tooltips on the <em>Longest Pauses</em> table — there is one source of truth for cause copy, so the tab and the per-event tooltip can never disagree.</p>

      <DocsCallout type="info">
        <strong>Read the JFR canonical event list:</strong> the
        <a href="https://sap.github.io/jfrevents/" target="_blank" rel="noopener">SAP JFR Events catalog</a>
        documents every GC-related event the JDK emits, including the exact cause strings.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
