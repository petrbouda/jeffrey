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
import { useDocsNavigation } from '@/composables/useDocsNavigation';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { adjacentPages } = useDocsNavigation();
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'what-are-profiles', text: 'What are Profiles?', level: 2 },
  { id: 'profile-structure', text: 'Profile Structure', level: 2 },
  { id: 'analysis-features', text: 'Analysis Features', level: 2 },
  { id: 'differential-analysis', text: 'Differential Analysis', level: 2 },
  { id: 'profile-vs-recording', text: 'Profile vs Recording', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <nav class="docs-breadcrumb">
        <router-link to="/docs" class="breadcrumb-item">
          <i class="bi bi-book me-1"></i>Docs
        </router-link>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item">Concepts</span>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item active">Profiles</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-person-lines-fill"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Profiles</h1>
          <p class="docs-section-badge">Concepts</p>
        </div>
      </header>

      <div class="docs-content">
        <p>Profiles are the core analysis unit in Jeffrey - they represent <strong>processed and analyzed JFR recordings</strong> optimized for fast querying and interactive visualization.</p>

        <h2 id="what-are-profiles">What are Profiles?</h2>
        <p>A profile is created when Jeffrey processes a JFR recording. Unlike raw JFR files, profiles are stored in a database format (DuckDB) that enables:</p>
        <ul>
          <li><strong>Fast querying</strong> - SQL-based access to all events</li>
          <li><strong>Pre-computed visualizations</strong> - Flamegraphs and charts ready instantly</li>
          <li><strong>Indexed data</strong> - Quick filtering and searching</li>
          <li><strong>Cached analysis</strong> - Complex computations stored for reuse</li>
        </ul>

        <p>Think of a profile as a "materialized view" of your JFR recording - all the data is there, but organized for analysis rather than storage.</p>

        <h2 id="profile-structure">Profile Structure</h2>
        <p>Each profile contains several components:</p>

        <h3>Event Database</h3>
        <p>All JFR events are parsed and stored in a DuckDB database with:</p>
        <ul>
          <li>Event timestamps and durations</li>
          <li>Stack traces (for sampling events)</li>
          <li>Event-specific fields (allocation size, thread state, etc.)</li>
          <li>Metadata (event types, categories)</li>
        </ul>

        <h3>Pre-computed Caches</h3>
        <p>To enable instant visualization, profiles pre-compute:</p>
        <ul>
          <li><strong>Flamegraph trees</strong> - Aggregated stack traces for CPU, allocation, lock events</li>
          <li><strong>Timeseries data</strong> - Bucketed metrics over time</li>
          <li><strong>Thread statistics</strong> - Per-thread aggregations</li>
          <li><strong>Event summaries</strong> - Counts and distributions by type</li>
        </ul>

        <h3>Profile Metadata</h3>
        <ul>
          <li>Recording time range</li>
          <li>JVM information (version, flags, arguments)</li>
          <li>Available event types</li>
          <li>Profile creation timestamp</li>
        </ul>

        <h2 id="analysis-features">Analysis Features</h2>
        <p>Once a profile is created, you can access powerful analysis tools organized into categories:</p>

        <h3>Visualization</h3>
        <div class="feature-list">
          <div class="feature-item">
            <strong>Flamegraphs</strong>
            <p>Interactive flame graphs for CPU sampling, allocation tracking, and lock contention analysis. Identify hot methods and understand where your application spends time or resources.</p>
          </div>
          <div class="feature-item">
            <strong>Sub-Second Analysis</strong>
            <p>Generate flamegraphs for specific time windows. Zoom into interesting periods to understand behavior during specific operations or incidents.</p>
          </div>
          <div class="feature-item">
            <strong>Differential Flamegraphs</strong>
            <p>Compare two profiles side-by-side using differential flame graphs. Easily spot performance regressions or improvements between versions.</p>
          </div>
          <div class="feature-item">
            <strong>Differential Sub-Second</strong>
            <p>Compare sub-second patterns between two profiles. Useful for A/B testing or before/after optimization analysis.</p>
          </div>
        </div>

        <h3>JVM Internals</h3>
        <div class="feature-list">
          <div class="feature-item">
            <strong>Guardian Analysis</strong>
            <p>Automated health checks that traverse stacktraces to find suspicious patterns - infinite loops, excessive locking, problematic allocations, and more.</p>
          </div>
          <div class="feature-item">
            <strong>Auto Analysis</strong>
            <p>Rule-based automated analysis that examines JFR events and provides color-coded insights (OK, Warning, Critical) about your application's behavior.</p>
          </div>
          <div class="feature-item">
            <strong>Thread Analysis</strong>
            <p>Thread lifecycle visualization showing thread states over time. Understand thread activity, blocking, and concurrency patterns.</p>
          </div>
          <div class="feature-item">
            <strong>GC Analysis</strong>
            <p>Comprehensive garbage collection analysis including pause times, collection frequency, heap usage patterns, and GC configuration review.</p>
          </div>
          <div class="feature-item">
            <strong>JIT Compilation</strong>
            <p>Just-In-Time compiler activity showing method compilations, deoptimizations, and code cache usage.</p>
          </div>
          <div class="feature-item">
            <strong>Event Viewer</strong>
            <p>Browse and filter all JFR events with comprehensive categorization. Search, filter, and explore raw event data.</p>
          </div>
          <div class="feature-item">
            <strong>JVM Flags</strong>
            <p>View and analyze JVM command-line flags and their values. Understand how your JVM is configured.</p>
          </div>
        </div>

        <h3>Application Monitoring</h3>
        <div class="feature-list">
          <div class="feature-item">
            <strong>HTTP Server</strong>
            <p>Server endpoint metrics showing request counts, response times, and error rates. Drill down into individual endpoint performance.</p>
          </div>
          <div class="feature-item">
            <strong>HTTP Client</strong>
            <p>Outbound HTTP request analysis including latency distribution, target hosts, and connection patterns.</p>
          </div>
          <div class="feature-item">
            <strong>Database Statements</strong>
            <p>SQL execution analysis with statement grouping, execution times, and frequency. Identify slow queries and optimization opportunities.</p>
          </div>
          <div class="feature-item">
            <strong>Tracing</strong>
            <p>Method tracing with flamegraphs showing the slowest execution paths. Analyze cumulative time spent in specific code paths.</p>
          </div>
        </div>

        <h2 id="differential-analysis">Differential Analysis</h2>
        <p>Jeffrey supports comparing two profiles using a Primary/Secondary model:</p>

        <table>
          <thead>
            <tr>
              <th>Designation</th>
              <th>Purpose</th>
              <th>Color in Diff</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><strong>Primary Profile</strong></td>
              <td>The "current" or "after" version</td>
              <td>Red (increased)</td>
            </tr>
            <tr>
              <td><strong>Secondary Profile</strong></td>
              <td>The "baseline" or "before" version</td>
              <td>Blue (decreased)</td>
            </tr>
          </tbody>
        </table>

        <p>In differential flamegraphs:</p>
        <ul>
          <li><strong class="text-danger">Red frames</strong> indicate code paths that take MORE time/resources in the Primary profile</li>
          <li><strong class="text-primary">Blue frames</strong> indicate code paths that take LESS time/resources in the Primary profile</li>
        </ul>

        <DocsCallout type="tip">
          <strong>Regression hunting:</strong> Set your baseline (good) version as Secondary and the potentially regressed version as Primary. Red frames show where performance degraded.
        </DocsCallout>

        <h2 id="profile-vs-recording">Profile vs Recording</h2>
        <p>Understanding the difference between recordings and profiles is important:</p>

        <table>
          <thead>
            <tr>
              <th>Aspect</th>
              <th>Recording</th>
              <th>Profile</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>Format</td>
              <td>Raw JFR file (.jfr)</td>
              <td>DuckDB database</td>
            </tr>
            <tr>
              <td>Purpose</td>
              <td>Storage of original data</td>
              <td>Fast analysis and visualization</td>
            </tr>
            <tr>
              <td>Size</td>
              <td>Compact binary format</td>
              <td>Larger (indexed + caches)</td>
            </tr>
            <tr>
              <td>Portability</td>
              <td>Standard JFR format</td>
              <td>Jeffrey-specific</td>
            </tr>
            <tr>
              <td>Creation</td>
              <td>JVM, async-profiler, agents</td>
              <td>Created in Jeffrey from recordings</td>
            </tr>
          </tbody>
        </table>

        <DocsCallout type="info">
          <strong>Storage strategy:</strong> Keep recordings as your source of truth. Profiles can be deleted and recreated anytime - they're just a processed view of the recording data.
        </DocsCallout>

        <p>For information about creating and managing profiles within projects, see <router-link to="/docs/concepts/projects/profiles">Projects / Profiles</router-link>.</p>
      </div>

      <nav class="docs-nav-footer">
        <router-link
          v-if="adjacentPages.prev"
          :to="`/docs/${adjacentPages.prev.category}/${adjacentPages.prev.path}`"
          class="nav-link prev"
        >
          <i class="bi bi-arrow-left"></i>
          <div class="nav-text">
            <span class="nav-label">Previous</span>
            <span class="nav-title">{{ adjacentPages.prev.title }}</span>
          </div>
        </router-link>
        <div v-else class="nav-spacer"></div>
        <router-link
          v-if="adjacentPages.next"
          :to="`/docs/${adjacentPages.next.category}/${adjacentPages.next.path}`"
          class="nav-link next"
        >
          <div class="nav-text">
            <span class="nav-label">Next</span>
            <span class="nav-title">{{ adjacentPages.next.title }}</span>
          </div>
          <i class="bi bi-arrow-right"></i>
        </router-link>
      </nav>
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

.feature-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin: 1rem 0 1.5rem 0;
}

.feature-item {
  padding: 1rem;
  background: #f8fafc;
  border-radius: 6px;
  border-left: 3px solid #5e64ff;
}

.feature-item strong {
  display: block;
  margin-bottom: 0.25rem;
  color: #343a40;
}

.feature-item p {
  margin: 0;
  font-size: 0.9rem;
  color: #5e6e82;
}

.text-danger {
  color: #dc3545;
}

.text-primary {
  color: #0d6efd;
}
</style>
