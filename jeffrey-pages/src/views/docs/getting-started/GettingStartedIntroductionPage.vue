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
import DocsFeatureCard from '@/components/docs/DocsFeatureCard.vue';
import DocsLinkCard from '@/components/docs/DocsLinkCard.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'what-is-jeffrey', text: 'What is Jeffrey?', level: 2 },
  { id: 'core-objectives', text: 'Core Objectives', level: 2 },
  { id: 'key-features', text: 'Key Features', level: 2 },
  { id: 'why-jeffrey', text: 'Why Jeffrey?', level: 2 },
  { id: 'components', text: 'Jeffrey Components', level: 2 },
  { id: 'update-notifications', text: 'Update Notifications', level: 2 }
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

      <div class="docs-content">
        <p>Jeffrey is a powerful <strong>Java profiling and diagnostics tool</strong> designed to help developers identify and resolve performance bottlenecks in Java applications quickly and efficiently. It analyzes both <strong>JFR (Java Flight Recorder) recordings</strong> and <strong>Heap Dumps</strong>.</p>

        <DocsCallout type="info">
          <strong>Jeffrey makes performance analysis accessible to everyone</strong>, from beginners to experienced performance engineers.
        </DocsCallout>

        <h2 id="what-is-jeffrey">What is Jeffrey?</h2>
        <p>Jeffrey provides advanced visualization and analysis capabilities for JFR recordings and Heap Dumps. It transforms complex performance data into intuitive flamegraphs, timeseries charts, and actionable insights.</p>
        <p>Jeffrey is designed for <strong>small and medium-sized projects and companies</strong> that need a solution that can be quickly deployed and ready to use immediately — no complex infrastructure requirements or lengthy setup processes. Self-hosted profiling without dependency on external services.</p>
        <p>Visit the official website at <a href="https://www.jeffrey-analyst.cafe/" target="_blank">jeffrey-analyst.cafe</a> or check out the <a href="https://github.com/petrbouda/jeffrey" target="_blank">GitHub repository</a>.</p>

        <h2 id="core-objectives">Core Objectives</h2>
        <p>Jeffrey addresses three distinct profiling workflows:</p>

        <div class="docs-grid docs-grid-stack">
          <DocsFeatureCard
            icon="bi bi-house"
            title="Quick Local Analysis"
            description="Quickly analyze locally available JFR recordings. Upload a file and start investigating within seconds — no workspace or project setup required."
          />
          <DocsFeatureCard
            icon="bi bi-cloud"
            title="Recording Collection Infrastructure"
            description="Conveniently collect JFR recordings and artifacts from running applications, especially in containerized cloud environments like Kubernetes."
          />
          <DocsFeatureCard
            icon="bi bi-pc-display"
            title="Avoid Cloud Computation Costs"
            description="Offload expensive profile processing from cloud infrastructure to local machines. Download recordings remotely and analyze them locally."
          />
        </div>

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

        <div class="docs-grid docs-grid-stack">
          <DocsFeatureCard
            icon="bi bi-hdd"
            title="Filesystem & Blob Storage"
            description="Use cheaper storage systems instead of expensive managed databases. Recordings are stored on disk or blob storage."
            color="neutral"
          />
          <DocsFeatureCard
            icon="bi bi-database"
            title="In-Process Database (DuckDB)"
            description="Use file-based, in-process databases to avoid deploying and maintaining separate database servers."
            color="neutral"
          />
          <DocsFeatureCard
            icon="bi bi-box-seam"
            title="Minimal Components"
            description="No additional services to deploy. Jeffrey runs as a single application with everything included."
            color="neutral"
          />
        </div>

        <p>Jeffrey provides visualizations that are clear for <strong>all developer levels</strong> — from junior to senior. Minimal JVM knowledge required to start analyzing, with explanations of JVM internals provided where needed. Progressive disclosure: start with overviews, drill down into details as needed.</p>

        <h3>Auto Analysis</h3>
        <p>Tools for quick insights <strong>before</strong> deep and time-consuming investigation:</p>

        <div class="docs-grid docs-grid-2">
          <DocsFeatureCard
            icon="bi bi-shield-check"
            title="Guardian"
            description="Automated analysis of flamegraphs. Traverses stacktraces to find suspicious patterns — infinite loops, excessive locking, problematic allocations."
          />
          <DocsFeatureCard
            icon="bi bi-graph-up"
            title="Auto-Analysis"
            description="Basic analysis of JFR recording data. Rule-based insights with color-coded results (OK, Warning, Critical) about application behavior."
          />
        </div>

        <h2 id="components">Jeffrey Components</h2>
        <p>Jeffrey consists of several components that work together. Choose the one that fits your workflow:</p>

        <div class="docs-grid docs-grid-2">
          <DocsLinkCard
            title="Jeffrey Local"
            description="The standalone analysis tool. Run on your machine to analyze JFR recordings and heap dumps with full visualization and AI features."
            to="/docs/local/overview"
            icon="bi bi-pc-display"
          />
          <DocsLinkCard
            title="Jeffrey Server"
            description="The recording collection server for production/cloud environments. Continuously collects JFR recordings from running applications."
            to="/docs/server/overview"
            icon="bi bi-cloud"
          />
          <DocsLinkCard
            title="Jeffrey CLI"
            description="Command-line tool for automated container profiling. Initializes profiler agent configuration for containerized Java applications."
            to="/docs/cli/overview"
            icon="bi bi-terminal"
          />
          <DocsLinkCard
            title="Jeffrey Events"
            description="JFR event library for your applications. Emit custom events for HTTP, JDBC, and gRPC monitoring dashboards in Jeffrey."
            to="/docs/events/overview"
            icon="bi bi-activity"
          />
        </div>

        <h2 id="update-notifications">Update Notifications</h2>
        <p>Jeffrey automatically checks for new releases on GitHub. When a newer version is available, a toast notification appears in the top-right corner showing your current version and the latest version available.</p>
        <ul>
          <li><strong>Minor updates</strong> appear as a blue notification</li>
          <li><strong>Major updates</strong> appear as an orange warning, indicating potential breaking changes</li>
        </ul>
        <p>The notification includes a link to the release notes on GitHub and a download button for <code>jeffrey.jar</code> when the release includes it as an asset. Dismissing the notification stores the preference per version, so it won't reappear for the same release.</p>

        <DocsCallout type="info">
          <strong>Disable update checks:</strong> Set <code>jeffrey.local.update-check.enabled=false</code> in your configuration to turn off automatic version checks.
        </DocsCallout>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
