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
  { id: 'flamegraphs', text: 'Flamegraphs', level: 2 },
  { id: 'subsecond', text: 'Sub-Second Analysis', level: 2 },
  { id: 'differential', text: 'Differential Analysis', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Visualization Section"
        icon="bi bi-bar-chart-line"
      />

      <div class="docs-content">
        <p>The Visualization section provides <strong>profiling graphs and visualizations</strong> for deep performance analysis. This is where you'll find flamegraphs and sub-second analysis tools for understanding exactly where your application spends time and resources.</p>

        <h2 id="overview">Overview</h2>
        <p>Visualization tools help you understand performance patterns that are difficult to see in raw event data:</p>
        <ul>
          <li><strong>Flamegraphs</strong> - Hierarchical view of where time/resources are spent</li>
          <li><strong>Sub-Second Analysis</strong> - Time-windowed flamegraphs for specific intervals</li>
          <li><strong>Differential Analysis</strong> - Compare two profiles to spot changes</li>
        </ul>

        <h2 id="flamegraphs">Flamegraphs</h2>
        <p>Flamegraphs are the primary visualization tool for understanding call hierarchies and resource consumption:</p>

        <div class="docs-feature-list">
          <DocsFeatureItem icon="bi bi-fire" title="Primary">
            Standard flamegraph from a single profile. Select from multiple flamegraph types based on the events captured in your recording - CPU samples, allocations, lock contention, and more.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-file-diff" title="Differential">
            Compare two profiles side-by-side using a differential flamegraph. Red frames show increased time/resources in the primary profile, blue frames show decreases. Requires a secondary profile to be selected.
          </DocsFeatureItem>
        </div>

        <h3>Flamegraph Types</h3>
        <p>Jeffrey generates different flamegraph types based on available JFR events:</p>

        <table>
          <thead>
            <tr>
              <th>Type</th>
              <th>Event Source</th>
              <th>Use Case</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><strong>CPU</strong></td>
              <td>ExecutionSample</td>
              <td>Find hot methods consuming CPU time</td>
            </tr>
            <tr>
              <td><strong>Allocation</strong></td>
              <td>ObjectAllocationInNewTLAB, ObjectAllocationOutsideTLAB</td>
              <td>Identify memory allocation sites</td>
            </tr>
            <tr>
              <td><strong>Lock</strong></td>
              <td>JavaMonitorEnter</td>
              <td>Analyze thread contention patterns</td>
            </tr>
            <tr>
              <td><strong>Wall-Clock</strong></td>
              <td>ThreadPark, JavaMonitorWait</td>
              <td>Find where threads spend time waiting</td>
            </tr>
            <tr>
              <td><strong>Native</strong></td>
              <td>NativeMemoryUsage</td>
              <td>Track native memory allocations</td>
            </tr>
          </tbody>
        </table>

        <DocsCallout type="tip">
          <strong>Search and filter:</strong> Use the search feature to highlight specific method patterns in flamegraphs. You can filter by package name, class name, or method signature to focus on relevant code paths.
        </DocsCallout>

        <h2 id="subsecond">Sub-Second Analysis</h2>
        <p>Sub-second analysis lets you examine specific time windows within your recording:</p>

        <div class="docs-feature-list">
          <DocsFeatureItem icon="bi bi-bar-chart" title="Primary">
            Time-windowed flamegraphs showing sample distribution over time. Select specific intervals on the timeline to generate flamegraphs for just that period. Ideal for analyzing behavior during specific operations or incidents.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-file-bar-graph" title="Differential">
            Compare sub-second patterns between two profiles. Useful for A/B testing or before/after optimization analysis where you need to compare behavior during specific time windows.
          </DocsFeatureItem>
        </div>

        <DocsCallout type="info">
          Sub-second analysis is particularly useful when you know something happened at a specific time (e.g., a slow request) and want to understand what the application was doing during that exact moment.
        </DocsCallout>

        <h2 id="differential">Differential Analysis</h2>
        <p>Differential analysis compares two profiles using a Primary/Secondary model:</p>

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
              <td class="text-danger">Red (increased)</td>
            </tr>
            <tr>
              <td><strong>Secondary Profile</strong></td>
              <td>The "baseline" or "before" version</td>
              <td class="text-primary">Blue (decreased)</td>
            </tr>
          </tbody>
        </table>

        <h3>Setting Up Differential Analysis</h3>
        <ol>
          <li>Open the profile you want to analyze (this becomes the Primary)</li>
          <li>Click the "Secondary Profile" toggle in the top navigation</li>
          <li>Select a profile to compare against (this becomes the Secondary)</li>
          <li>Navigate to Differential Flamegraph or Differential Sub-Second pages</li>
        </ol>

        <DocsCallout type="warning">
          <strong>Secondary profile required:</strong> Differential pages show a lock icon and are inaccessible until a secondary profile is selected. The comparison panel will automatically open if you try to access a differential page without a secondary profile.
        </DocsCallout>

        <DocsCallout type="tip">
          <strong>Regression hunting:</strong> Set your baseline (good) version as Secondary and the potentially regressed version as Primary. Red frames show where performance degraded, blue frames show improvements.
        </DocsCallout>

        <p>For more details on flamegraph interpretation and advanced usage, see the <router-link to="/docs/features/flamegraphs">Flamegraphs</router-link> feature documentation.</p>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
