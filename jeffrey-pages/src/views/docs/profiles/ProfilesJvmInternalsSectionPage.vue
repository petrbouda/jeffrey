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
import DocsFeatureItem from '@/components/docs/DocsFeatureItem.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'analysis', text: 'Analysis', level: 2 },
  { id: 'events', text: 'Events', level: 2 },
  { id: 'threads', text: 'Threads', level: 2 },
  { id: 'memory', text: 'Memory', level: 2 },
  { id: 'compiler', text: 'Compiler', level: 2 },
  { id: 'infrastructure', text: 'Infrastructure', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="JVM Internals Section"
        icon="bi bi-cpu"
      />

      <div class="docs-content">
        <p>The JVM Internals section provides <strong>core JVM metrics and analysis</strong> - everything you need to understand how your Java application interacts with the JVM runtime, including memory management, threading, compilation, and configuration.</p>

        <h2 id="overview">Overview</h2>
        <p>This section focuses on JVM-level behavior rather than application-specific logic. It's the default section when opening a profile and contains the most fundamental analysis tools:</p>
        <ul>
          <li><strong>Configuration review</strong> - JVM settings and recording information</li>
          <li><strong>Automated analysis</strong> - Guardian and Auto Analysis for quick insights</li>
          <li><strong>Event exploration</strong> - Browse all JFR events captured in the recording</li>
          <li><strong>Thread analysis</strong> - Understand thread behavior and state transitions</li>
          <li><strong>Memory analysis</strong> - Heap usage and garbage collection patterns</li>
          <li><strong>JIT compilation</strong> - Compiler activity and optimizations</li>
        </ul>

        <h2 id="analysis">Analysis</h2>
        <p>The Analysis section provides automated insights and configuration review:</p>

        <div class="docs-feature-list">
          <DocsFeatureItem icon="bi bi-gear" title="Configuration">
            Displays the profile's configuration including JVM information, recording settings, and system properties. This is your starting point for understanding the environment where the recording was captured.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-shield-check" title="Guardian Analysis">
            Automated health checks that traverse stacktraces to find suspicious patterns. Guardian looks for infinite loops, excessive locking, problematic allocations, and other anti-patterns. Results are categorized by severity with a warning badge in the sidebar.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-robot" title="Auto Analysis">
            Rule-based automated analysis that examines JFR events and provides color-coded insights (OK, Warning, Critical). Unlike Guardian which focuses on stacktraces, Auto Analysis evaluates event metrics and thresholds.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-speedometer2" title="Performance Counters">
            Hardware performance metrics when available. Shows CPU cycles, cache misses, and other low-level counters that can reveal performance bottlenecks not visible at the Java level.
          </DocsFeatureItem>
        </div>

        <DocsCallout type="tip">
          <strong>Quick triage:</strong> Start with Guardian Analysis and Auto Analysis for immediate insights. Warning badges in the sidebar indicate issues that need attention.
        </DocsCallout>

        <h2 id="events">Events</h2>
        <p>The Events section provides direct access to all JFR events captured in the recording:</p>

        <div class="docs-feature-list">
          <DocsFeatureItem icon="bi bi-list-check" title="Event Types">
            Browse all available JFR event types in the recording. See event counts, categories, and which events were enabled during recording. Useful for understanding what data is available for analysis.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-collection" title="Event Viewer">
            Filter and search raw event data with comprehensive categorization. Drill down into individual events to see all fields and values. Essential for detailed investigation when other views don't show what you need.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-flag" title="JVM Flags">
            View JVM command-line flags and their values. Understand how the JVM was configured - GC settings, heap sizes, experimental features, and diagnostic options.
          </DocsFeatureItem>
        </div>

        <h2 id="threads">Threads</h2>
        <p>Thread analysis helps understand concurrency patterns and identify threading issues:</p>

        <div class="docs-feature-list">
          <DocsFeatureItem icon="bi bi-graph-up" title="Statistics">
            Per-thread aggregations showing CPU time, allocations, and state distribution. Identify which threads consume the most resources and how they spend their time.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-clock-history" title="Timeline">
            Visual timeline of thread activity over time. See when threads are running, blocked, waiting, or sleeping. Spot patterns like thundering herds, lock convoys, or thread starvation.
          </DocsFeatureItem>
        </div>

        <DocsCallout type="info">
          Thread Timeline is particularly useful for identifying synchronization issues and understanding the temporal relationship between thread activities.
        </DocsCallout>

        <h2 id="memory">Memory</h2>
        <p>Memory analysis covers heap usage and garbage collection behavior:</p>

        <h3>Heap Memory</h3>
        <div class="docs-feature-list">
          <DocsFeatureItem icon="bi bi-graph-up-arrow" title="Timeseries">
            Memory usage trends over time showing used heap, committed heap, and maximum heap. Identify memory leaks (steadily increasing used heap) or sizing issues (frequent committed heap changes).
          </DocsFeatureItem>
        </div>

        <h3>Garbage Collection</h3>
        <div class="docs-feature-list">
          <DocsFeatureItem icon="bi bi-bar-chart-line" title="Overview">
            GC pause distribution and statistics. See pause time percentiles, collection counts, and heap reclamation efficiency. Identify if GC is causing latency issues.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-graph-up-arrow" title="Timeseries">
            GC activity over time showing pause durations and collection frequency. Correlate GC events with application behavior to understand their impact.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-gear" title="Configuration">
            GC algorithm settings and tuning parameters. Review the collector in use (G1, ZGC, Shenandoah, etc.) and its configuration options.
          </DocsFeatureItem>
        </div>

        <h2 id="compiler">Compiler</h2>
        <p>JIT compilation analysis shows how the JVM optimizes your code at runtime:</p>

        <div class="docs-feature-list">
          <DocsFeatureItem icon="bi bi-lightning" title="JIT Compilation">
            Method compilation activity including compilation times, code sizes, and optimization levels. Track deoptimizations that may indicate unstable code patterns. Monitor code cache usage to ensure compiled code isn't being evicted.
          </DocsFeatureItem>
        </div>

        <h2 id="infrastructure">Infrastructure</h2>
        <p>Infrastructure analysis is relevant when running in containerized environments:</p>

        <div class="docs-feature-list">
          <DocsFeatureItem icon="bi bi-server" title="Container Configuration">
            Container limits and resource quotas when available. Shows CPU limits, memory limits, and how the JVM perceives them. Important for understanding resource constraints in Kubernetes or Docker deployments.
          </DocsFeatureItem>
        </div>

        <DocsCallout type="warning">
          <strong>Feature availability:</strong> Some features like Performance Counters and Container Configuration may be disabled if the recording doesn't contain the required events. Disabled features appear grayed out in the sidebar.
        </DocsCallout>

        <p>For detailed information about specific features, see the <router-link to="/docs/features/guardian">Guardian</router-link> and <router-link to="/docs/features/auto-analysis">Auto Analysis</router-link> documentation.</p>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
