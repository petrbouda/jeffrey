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
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsFeatureCard from '@/components/docs/DocsFeatureCard.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'what-are-profiles', text: 'What are Profiles?', level: 2 },
  { id: 'profile-storage', text: 'Profile Storage', level: 2 },
  { id: 'visualization', text: 'Visualization', level: 2 },
  { id: 'jvm-internals', text: 'JVM Internals', level: 2 },
  { id: 'technologies', text: 'Technologies', level: 2 },
  { id: 'heap-dump-analysis', text: 'Heap Dump Analysis', level: 2 },
  { id: 'tools', text: 'Tools', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const folderStructure = `$JEFFREY_HOME/
└── profiles/
    ├── {profile-id-1}/
    │   ├── profile-data.db       # DuckDB database with all events
    │   └── heap-dump-analysis/   # Heap dump analysis data (if available)
    ├── {profile-id-2}/
    │   └── profile-data.db
    └── ...`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Profiles"
      icon="bi bi-speedometer2"
    />

    <div class="docs-content">
      <p>Profiles are the core analysis unit in Jeffrey — they represent <strong>processed and analyzed JFR recordings</strong> optimized for fast querying and interactive visualization. Each profile contains five analysis sections.</p>

      <h2 id="what-are-profiles">What are Profiles?</h2>
      <p>A profile is created when Jeffrey processes a JFR recording. Unlike raw JFR files, profiles are stored in a database format that enables fast querying, pre-computed visualizations, indexed data, and cached analysis. Think of a profile as a "materialized view" of your JFR recording — all the data is there, but organized for analysis.</p>

      <DocsCallout type="tip">
        <strong>Storage strategy:</strong> Keep recordings as your source of truth. Profiles can be deleted and recreated anytime — they're just a processed view of the recording data.
      </DocsCallout>

      <h2 id="profile-storage">Profile Storage</h2>
      <p>Every profile has its own dedicated <strong>DuckDB database</strong> — independent, portable, and easy to clean up.</p>

      <DocsCodeBlock :code="folderStructure" language="text" />

      <h2 id="visualization">Visualization</h2>
      <p>Profiling graphs for deep performance analysis — flamegraphs and sub-second analysis tools.</p>

      <div class="docs-grid docs-grid-2">
        <DocsFeatureCard
          icon="bi bi-fire"
          title="Flamegraphs"
          description="Interactive flame graphs for CPU, allocation, lock contention, and wall-clock analysis. Configurable frame pruning and text rendering modes."
        />
        <DocsFeatureCard
          icon="bi bi-file-diff"
          title="Differential Flamegraphs"
          description="Compare two profiles side-by-side. Red frames show increased time in the primary profile, blue frames show decreases."
        />
        <DocsFeatureCard
          icon="bi bi-bar-chart"
          title="Sub-Second Analysis"
          description="Time-windowed flamegraphs — select specific intervals on a timeline to generate flamegraphs for just that period."
        />
        <DocsFeatureCard
          icon="bi bi-file-bar-graph"
          title="Differential Sub-Second"
          description="Compare sub-second patterns between two profiles. Useful for A/B testing or before/after optimization analysis."
        />
      </div>

      <h2 id="jvm-internals">JVM Internals</h2>
      <p>Core JVM metrics and analysis — how your application interacts with the JVM runtime.</p>

      <div class="docs-grid docs-grid-2">
        <DocsFeatureCard
          icon="bi bi-shield-check"
          title="Guardian"
          description="Automated rules that walk stacktraces and event metrics and flag what warrants attention. Color-coded OK / INFO / WARNING with tuneable thresholds — see the Guardian reference."
        />
        <DocsFeatureCard
          icon="bi bi-collection"
          title="Events & JVM Flags"
          description="Browse all JFR event types, filter raw event data, and review JVM command-line flags and configuration."
        />
        <DocsFeatureCard
          icon="bi bi-clock-history"
          title="Thread Statistics & Timeline"
          description="Per-thread CPU time, allocations, and state distribution. Visual timeline of thread activity to spot synchronization issues."
        />
        <DocsFeatureCard
          icon="bi bi-bar-chart-line"
          title="Memory & Garbage Collection"
          description="Pause distribution, throughput / overhead, longest-pause table with cause attribution, concurrent cycles, and a searchable reference for every GC pause type — plus collector-specific G1 and ZGC deep-dive pages."
        />
        <DocsFeatureCard
          icon="bi bi-lightning"
          title="JIT Compilation"
          description="Method compilation activity, long compilations, compiler queue backlog, code cache occupancy with code-cache-full alerts, and deoptimizations by reason and hot method — see the JIT analysis page."
        />
        <DocsFeatureCard
          icon="bi bi-box-seam"
          title="Class Loading"
          description="Loaded vs. unloaded classes over time, per-class-loader metaspace footprint and hidden classes, slowest class loads, and bytecode-instrumentation activity — see the class-loading analysis page."
        />
        <DocsFeatureCard
          icon="bi bi-exclamation-octagon"
          title="Exceptions"
          description="Exception creation rate over time, top thrown classes with sample messages, and Error throws — spot exception storms and exceptions used as control flow."
        />
        <DocsFeatureCard
          icon="bi bi-memory"
          title="Native Memory"
          description="Resident set size vs. heap usage, direct buffer growth, and loaded native libraries — investigate off-heap memory and container OOM kills."
        />
        <DocsFeatureCard
          icon="bi bi-pie-chart"
          title="Native Memory Tracking"
          description="The JVM's own native-memory accounting — reserved vs committed per category (Thread, Class, Code, GC), category growth for leak detection, and an RSS-vs-tracked gap view."
        />
        <DocsFeatureCard
          icon="bi bi-box"
          title="Allocations"
          description="Allocation rate over time, in-TLAB vs outside-TLAB split, and the top allocated types driving GC pressure."
        />
        <DocsFeatureCard
          icon="bi bi-bug"
          title="Memory Leak Candidates"
          description="Long-lived objects flagged by JFR's old-object sampler — leaked class, size, age, and heap usage at sample time."
        />
        <DocsFeatureCard
          icon="bi bi-speedometer2"
          title="Performance Counters"
          description="Hardware performance metrics — CPU cycles, cache misses, and other low-level counters when available."
        />
        <DocsFeatureCard
          icon="bi bi-box"
          title="Container Configuration"
          description="Container resource limits and settings — CPU quotas, memory limits, and cgroup configuration for containerized deployments."
        />
        <DocsFeatureCard
          icon="bi bi-cpu"
          title="System & Host"
          description="JVM vs. machine CPU, network utilization, context switches, and competing host processes — decide whether the problem is your JVM or the box."
        />
        <DocsFeatureCard
          icon="bi bi-stopwatch"
          title="VM Operations"
          description="JVM-internal stop-the-world activity beyond GC — VM operations and safepoints, with a catalog of operation types and time-to-safepoint."
        />
        <DocsFeatureCard
          icon="bi bi-lock"
          title="Blocking Operations"
          description="Where application threads block — lock contention by monitor class, Object.wait(), thread parks, sleeps, and virtual-thread pinning."
        />
        <DocsFeatureCard
          icon="bi bi-ethernet"
          title="Socket I/O"
          description="Blocking network I/O — read/write throughput over time, the slowest operations, and the busiest peers (host:port)."
        />
        <DocsFeatureCard
          icon="bi bi-file-earmark"
          title="File I/O"
          description="Blocking file I/O — read/write throughput, the slowest operations, and the busiest files and directories."
        />
      </div>

      <p class="docs-read-more">
        <router-link to="/docs/microscope/profiles/guardian">Read the Guardian reference &rarr;</router-link>
        &nbsp;·&nbsp;
        <router-link to="/docs/microscope/profiles/garbage-collection">Read the GC analysis reference &rarr;</router-link>
        &nbsp;·&nbsp;
        <router-link to="/docs/microscope/profiles/garbage-collection/g1">Read the G1 analysis reference &rarr;</router-link>
        &nbsp;·&nbsp;
        <router-link to="/docs/microscope/profiles/garbage-collection/zgc">Read the ZGC analysis reference &rarr;</router-link>
        &nbsp;·&nbsp;
        <router-link to="/docs/microscope/profiles/garbage-collection/string-symbol-tables">Read the String &amp; Symbol Tables reference &rarr;</router-link>
        &nbsp;·&nbsp;
        <router-link to="/docs/microscope/profiles/garbage-collection/finalizers">Read the Finalizers reference &rarr;</router-link>
        &nbsp;·&nbsp;
        <router-link to="/docs/microscope/profiles/class-loading">Read the class-loading reference &rarr;</router-link>
        &nbsp;·&nbsp;
        <router-link to="/docs/microscope/profiles/jit-compilation">Read the JIT compilation reference &rarr;</router-link>
        &nbsp;·&nbsp;
        <router-link to="/docs/microscope/profiles/exceptions">Read the exceptions reference &rarr;</router-link>
        &nbsp;·&nbsp;
        <router-link to="/docs/microscope/profiles/virtual-threads">Read the Virtual Threads reference &rarr;</router-link>
      </p>

      <h2 id="technologies">Technologies</h2>
      <p>Application-specific analysis — how your code interacts with external systems. Requires <router-link to="/docs/events/overview">Jeffrey Events</router-link> library to be added to your application.</p>

      <div class="docs-grid docs-grid-2">
        <DocsFeatureCard
          icon="bi bi-globe"
          title="HTTP Server & Client"
          description="Inbound and outbound HTTP analysis — overview, timeseries, distribution, slowest requests, and per-endpoint drill-down."
        />
        <DocsFeatureCard
          icon="bi bi-diagram-3"
          title="gRPC Server & Client"
          description="Inbound and outbound gRPC analysis — overview, timeseries, per-service breakdown, traffic analysis, and payload size distribution."
        />
        <DocsFeatureCard
          icon="bi bi-database"
          title="Database"
          description="SQL statement analysis — execution times, statement groups, slowest statements, and connection pool metrics."
        />
        <DocsFeatureCard
          icon="bi bi-layers"
          title="Method Tracing"
          description="Wall-clock flamegraphs from traced methods, slowest traces, cumulated analysis, and per-method drill-down."
        />
      </div>

      <h2 id="heap-dump-analysis">Heap Dump Analysis</h2>
      <p>Memory analysis from heap dump snapshots (.hprof files). Requires a heap dump to be associated with the profile.</p>

      <div class="docs-grid docs-grid-2">
        <DocsFeatureCard
          icon="bi bi-list-ol"
          title="Class Histogram & Dominator Tree"
          description="Object counts and memory sizes by class, with per-class instance drill-down. Hierarchical retained-size view to understand memory ownership. Opaque arrays (byte[], char[]) show inferred referrer-class hint pills."
        />
        <DocsFeatureCard
          icon="bi bi-search"
          title="Leak Suspects"
          description="Automated memory leak detection based on retained size analysis and reference chain patterns."
        />
        <DocsFeatureCard
          icon="bi bi-collection"
          title="Biggest Collections"
          description="Find the largest collections by size, capacity, and fill ratio — surface over-allocated maps and lists that waste memory."
        />
        <DocsFeatureCard
          icon="bi bi-fonts"
          title="String Analysis & OQL"
          description="String duplication opportunities. Object Query Language for custom SQL-like heap queries."
        />
        <DocsFeatureCard
          icon="bi bi-signpost-split"
          title="GC Roots & Reference Chains"
          description="Browse GC roots across five tabs — Top Retainers, By Class, By ClassLoader, Native / JNI, and Leak Hints. Trace reference chains from any object back to its root to understand retention."
        />
        <DocsFeatureCard
          icon="bi bi-layers"
          title="Class Loaders"
          description="Loader hierarchy with per-loader unloadability verdict — unloadable, rooted (effectively rooted via the parent chain), or pinned (the classic redeploy-leak signature). Suspicious-loader and duplicate-class tabs surface common metaspace leaks."
        />
        <DocsFeatureCard
          icon="bi bi-stars"
          title="AI Heap Dump Analysis"
          description="Conversational analysis over your heap dump — ask about retained sizes, suspected leaks, dominator chains, and string duplication in natural language."
        />
      </div>

      <p class="docs-read-more">
        <router-link to="/docs/ai/heap-dump-analysis">Read the AI Heap Dump Analysis reference &rarr;</router-link>
      </p>

      <h2 id="tools">Tools</h2>
      <p>Utility operations for modifying profile data — anonymization and simplification.</p>

      <div class="docs-grid docs-grid-2">
        <DocsFeatureCard
          icon="bi bi-pencil-square"
          title="Rename Frames"
          description="Search and replace class name patterns across all frames. Useful for anonymizing proprietary packages before sharing profiles."
        />
        <DocsFeatureCard
          icon="bi bi-arrows-collapse"
          title="Collapse Frames"
          description="Replace consecutive framework frames with a single synthetic frame. Simplifies deep Spring, Hibernate, or Netty call stacks."
        />
      </div>

      <DocsCallout type="warning">
        <strong>Permanent operations:</strong> Rename and Collapse tools modify profile data directly. Both support a preview-then-apply workflow, but changes cannot be undone. Recreate the profile from the original recording to revert.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
