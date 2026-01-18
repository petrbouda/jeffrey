<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
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
  { id: 'what-is-jeffrey', text: 'What is Jeffrey?', level: 2 },
  { id: 'key-features', text: 'Key Features', level: 2 },
  { id: 'why-jeffrey', text: 'Why Jeffrey?', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Introduction to Jeffrey"
        icon="bi bi-file-earmark-text"
      />

      <!-- Content -->
      <div class="docs-content">
        <p>Jeffrey is a powerful <strong>Java profiling and diagnostics tool</strong> designed to help developers identify and resolve performance bottlenecks in Java applications quickly and efficiently. It analyzes both <strong>JFR (Java Flight Recorder) recordings</strong> and <strong>Heap Dumps</strong>.</p>

        <DocsCallout type="info">
          <strong>Jeffrey makes performance analysis accessible to everyone</strong>, from beginners to experienced performance engineers.
        </DocsCallout>

        <h2 id="what-is-jeffrey">What is Jeffrey?</h2>
        <p>Jeffrey provides advanced visualization and analysis capabilities for JFR recordings and Heap Dumps. It transforms complex performance data into intuitive flamegraphs, timeseries charts, and actionable insights.</p>
        <p>Visit the official website at <a href="https://www.jeffrey-analyst.cafe/" target="_blank">jeffrey-analyst.cafe</a> or check out the <a href="https://github.com/petrbouda/jeffrey" target="_blank">GitHub repository</a>.</p>

        <h2 id="key-features">Key Features</h2>
        <p>Jeffrey organizes analysis into four main modes:</p>

        <h3>Visualization</h3>
        <ul>
          <li><strong>Flamegraphs</strong> - Interactive flame graphs for CPU, allocation, and lock contention analysis</li>
          <li><strong>Differential Flamegraphs</strong> - Compare two profiles side-by-side to identify performance regressions</li>
          <li><strong>Sub-Second Analysis</strong> - Zoom into specific time windows for detailed investigation</li>
          <li><strong>Differential Sub-Second</strong> - Compare sub-second patterns between profiles</li>
        </ul>

        <h3>JVM Internals</h3>
        <ul>
          <li><strong>Guardian Analysis</strong> - Automated health checks that traverse stacktraces to find suspicious behavior</li>
          <li><strong>Auto Analysis</strong> - Rule-based automated analysis with color-coded insights</li>
          <li><strong>Event Types & Viewer</strong> - Browse and filter all JFR events with comprehensive categorization</li>
          <li><strong>JVM Flags</strong> - View and analyze JVM command-line flags configuration</li>
          <li><strong>Thread Statistics & Timeline</strong> - Thread lifecycle information and activity over time</li>
          <li><strong>Heap Memory Timeseries</strong> - Java Heap usage patterns over time</li>
          <li><strong>Garbage Collection</strong> - GC overview, timeseries, configuration, and pause analysis</li>
          <li><strong>JIT Compilation</strong> - Just-In-Time compiler activity and optimization insights</li>
          <li><strong>Performance Counters</strong> - Low-level CPU performance metrics</li>
          <li><strong>Container Configuration</strong> - Container resource limits and settings</li>
        </ul>

        <h3>Application Monitoring</h3>
        <ul>
          <li><strong>HTTP Server</strong> - Server endpoint metrics and individual endpoint analysis</li>
          <li><strong>HTTP Client</strong> - Outbound HTTP request metrics and details</li>
          <li><strong>Database Statements</strong> - SQL statement execution times, counts, and grouping</li>
          <li><strong>Connection Pools</strong> - JDBC connection pool usage and health</li>
          <li><strong>Tracing</strong> - Method tracing with flamegraphs, slowest traces, and cumulated analysis</li>
        </ul>

        <h3>Heap Dump Analysis</h3>
        <ul>
          <li><strong>Heap Dump Overview</strong> - Summary of heap dump contents</li>
          <li><strong>Class Histogram</strong> - Memory allocation breakdown by class</li>
          <li><strong>String Analysis</strong> - String object analysis and deduplication opportunities</li>
          <li><strong>OQL Query</strong> - Object Query Language for custom heap analysis</li>
          <li><strong>GC Roots</strong> - Objects preventing garbage collection</li>
          <li><strong>Thread Stacks</strong> - Thread stack information from heap dump</li>
        </ul>

        <h2 id="why-jeffrey">Why Jeffrey?</h2>
        <p>Traditional Java profiling tools can be complex and overwhelming. Jeffrey simplifies this process by:</p>
        <ol>
          <li><strong>Providing clear visualizations</strong> - See exactly where your application spends time</li>
          <li><strong>Offering automated insights</strong> - Get recommendations without being an expert</li>
          <li><strong>Enabling comparison</strong> - Easily compare before/after performance with differential analysis</li>
          <li><strong>Supporting modern workflows</strong> - Web-based interface, Docker-ready, no complex installation</li>
        </ol>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
