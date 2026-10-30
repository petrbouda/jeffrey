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
import DocsFeatureCard from '@/components/docs/DocsFeatureCard.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'getting-a-snapshot', text: 'Getting a Snapshot', level: 2 },
  { id: 'initialization', text: 'Initialization', level: 2 },
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'headline-figures', text: 'Headline Figures', level: 3 },
  { id: 'jvm', text: 'JVM', level: 3 },
  { id: 'collectors-generations', text: 'Garbage Collectors & Heap Generations', level: 3 },
  { id: 'counters', text: 'Counters', level: 2 },
  { id: 'from-an-agent', text: 'From a Coding Agent', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Perf Counters"
      icon="bi bi-speedometer2"
    />

    <div class="docs-content">
      <p>HotSpot keeps a set of <strong>performance counters</strong> about itself &mdash; uptime, heap and metaspace sizes, collections and their time, safepoints, class loading, JIT compilation, threads &mdash; and can save them as an <code>hsperfdata</code> file: the same data <code>jstat</code> reads from a running JVM. Jeffrey turns such a snapshot into a <strong>Perf Counters profile</strong> of its own, with one mode, <strong>Perf Counters</strong>, and two pages: <a href="#overview">Overview</a> and <a href="#counters">Counters</a>.</p>

      <DocsCallout type="info">
        <strong>A profile of its own, not a page of the JFR profile.</strong> A perf-counters file that came down with a remote session is an item of that session&rsquo;s <router-link to="/docs/microscope/recordings#bundles">bundle</router-link>, beside its JFR recording and heap dumps, and is initialized separately. The JFR profile has no performance-counters page.
      </DocsCallout>

      <h2 id="getting-a-snapshot">Getting a Snapshot</h2>
      <ul>
        <li><strong>Upload</strong> a <code>.hsperfdata</code> file on the <router-link to="/docs/microscope/recordings">Recordings</router-link> page &mdash; the drop zone&rsquo;s <strong>Perf counters</strong> pill lists the extension, and the <strong>Perf Counters</strong> type filter shows only these recordings.</li>
        <li><strong>Download a remote session</strong> provisioned with <router-link to="/docs/provisioner/configuration">perf counters enabled</router-link>: the JVM saves <code>perf-counters.hsperfdata</code> into its session directory on exit (<code>-XX:PerfDataSaveFile</code>), and the download lands it as the bundle&rsquo;s <strong>Perf Counters</strong> item.</li>
        <li><strong>From a coding agent</strong>, <code>recordings_analyzeFile</code> accepts a <code>.hsperfdata</code> path like any other initializable file.</li>
      </ul>

      <h2 id="initialization">Initialization</h2>
      <p>Click <strong>Analyze</strong> on the row. Reading a snapshot takes milliseconds, so the counters are read within the click and the row shows no stage chips: the profile appears initialized, or the row reports why the file could not be read &mdash; and a snapshot that cannot be read leaves no profile behind. The profile&rsquo;s window is the JVM&rsquo;s run as the counters tell it, from the JVM&rsquo;s start to its last safepoint.</p>

      <h2 id="overview">Overview</h2>
      <p>The profile opens at <code>/profiles/{id}/perf-counters/overview</code>: the figures most investigations reach for, read from the snapshot&rsquo;s own counters. A counter the JVM did not write is shown as a dash rather than a zero.</p>

      <h3 id="headline-figures">Headline Figures</h3>
      <div class="docs-grid docs-grid-2">
        <DocsFeatureCard
          icon="bi bi-clock-history"
          title="Uptime"
          description="The JVM's own high-resolution timer when the JVM wrote one; otherwise application time plus safepoint time, which together cover the run up to its last safepoint — the whole run for a snapshot saved at exit — and the tile then reads 'Uptime (to the last safepoint)'. Broken down into application time and VM init."
        />
        <DocsFeatureCard
          icon="bi bi-hdd-stack"
          title="Heap Used"
          description="Used and capacity summed over the heap generations, and the share of capacity in use."
        />
        <DocsFeatureCard
          icon="bi bi-recycle"
          title="GC Time"
          description="Total collection time across every collector, its share of uptime — highlighted as a warning when it gets high — the number of collections, and the last GC cause."
        />
        <DocsFeatureCard
          icon="bi bi-pause-circle"
          title="Safepoints"
          description="How many safepoints the JVM reached, the total time spent in them, and the time it took to reach them."
        />
        <DocsFeatureCard
          icon="bi bi-box"
          title="Metaspace"
          description="Metaspace used and capacity, and the compressed class space used."
        />
        <DocsFeatureCard
          icon="bi bi-layers"
          title="Classes Loaded"
          description="Classes loaded and unloaded, how many came from the CDS archive, and the time spent loading them."
        />
        <DocsFeatureCard
          icon="bi bi-lightning-charge"
          title="JIT Compilations"
          description="Compilations, total compile time, the size of the generated code, and bailouts."
        />
        <DocsFeatureCard
          icon="bi bi-cpu"
          title="Live Threads"
          description="Live threads at the snapshot, the peak, daemon threads, and how many were started over the run."
        />
      </div>

      <h3 id="jvm">JVM</h3>
      <p>Who the snapshot is from: the <strong>VM</strong> name, <strong>VM version</strong>, <strong>Vendor</strong>, <strong>Java version</strong>, <strong>GC policy</strong>, when the JVM <strong>Started</strong>, the <strong>Command</strong> it ran and its <strong>VM arguments</strong>. The last two are shown in full on hover.</p>

      <h3 id="collectors-generations">Garbage Collectors &amp; Heap Generations</h3>
      <p>The <strong>Garbage Collectors</strong> table has one row per collector the JVM wrote counters for &mdash; invocations, total time, the average pause and its share of uptime. Collectors are found by the counters present rather than by the policy&rsquo;s own count, so G1, which declares one collector and writes three, shows all three. The <strong>Heap Generations</strong> table lists each generation with its used, capacity and maximum capacity, and the share of capacity in use.</p>

      <h2 id="counters">Counters</h2>
      <p><code>/profiles/{id}/perf-counters/counters</code> is the full snapshot: every counter, grouped into a tree by subsystem (JIT Compiler, ClassLoader, Garbage Collector, Runtime, Threads, Property, Operating System, &hellip;) with a count of counters and categories above it. A filter matches counter names and category labels and expands the categories it finds; <strong>Expand All</strong> and <strong>Collapse All</strong> open or fold the whole tree, and a counter that carries a description shows it on its question-mark icon.</p>

      <DocsCallout type="tip">
        <strong>Reading it beside a JFR recording.</strong> A snapshot is one moment &mdash; usually the JVM&rsquo;s exit &mdash; and holds totals, not a timeline. When a session also recorded JFR, open its JFR profile for when things happened, and the perf counters for the whole-run totals the JVM kept itself.
      </DocsCallout>

      <h2 id="from-an-agent">From a Coding Agent</h2>
      <p>The <strong>Microscope MCP</strong> server describes a perf-counters profile in one line of <code>profiles_summary</code> &mdash; what the snapshot holds, and that it has no events for the <code>jfr_</code>, <code>flamegraph_</code>, <code>jvm_</code> or <code>heap_</code> families. No tool reads its counters yet; the agent points the user at the profile&rsquo;s <code>uiLink</code>, where both pages above show them.</p>

      <p class="docs-read-more">
        <router-link to="/docs/microscope-mcp/tools">Read the MCP tool reference &rarr;</router-link>
      </p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
