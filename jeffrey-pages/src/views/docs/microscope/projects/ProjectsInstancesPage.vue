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
import DocsFeatureItem from '@/components/docs/DocsFeatureItem.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'instance-lifecycle', text: 'Instance Lifecycle', level: 2 },
  { id: 'instances-tab', text: 'Instances Tab', level: 2 },
  { id: 'timeline-tab', text: 'Timeline Tab', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Instances"
        icon="bi bi-hdd-stack"
      />

      <div class="docs-content">
        <p><strong>Instances</strong> represent individual JVM processes connected to a project. When an application starts with the Jeffrey profiler agent it registers as an instance and starts producing recording sessions.</p>

        <h2 id="overview">Overview</h2>
        <p>Two tabs in the project page work together to show instance activity:</p>

        <div class="docs-feature-list">
          <DocsFeatureItem icon="bi bi-grid" title="Instances">
            All instances registered in the project, grouped by status, with the project's storage size — the bytes every session of the project occupies on the remote, as the hub&rsquo;s last retention pass measured them.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-bar-chart-steps" title="Timeline">
            A swimlane view of when each instance and its sessions were active. Pick a time range (1H, 6H, 24H, 7D, 30D) to focus on the period you care about.
          </DocsFeatureItem>
        </div>

        <DocsCallout type="info">
          <strong>Remote workspaces only:</strong> Instances appear only for projects inside the workspaces of the configured remote. Locally-uploaded JFR files have no instance concept — they live on the global Recordings page instead.
        </DocsCallout>

        <h2 id="instance-lifecycle">Instance Lifecycle</h2>
        <p>Each instance follows a lifecycle represented by its status:</p>

        <table>
          <thead>
            <tr>
              <th>Status</th>
              <th>Description</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><strong>Pending</strong></td>
              <td>Instance registered but has no recording session yet</td>
            </tr>
            <tr>
              <td><strong>Active</strong></td>
              <td>At least one of the instance's sessions is still recording</td>
            </tr>
            <tr>
              <td><strong>Finished</strong></td>
              <td>Every session of the instance has finished; its finish time is the latest session's</td>
            </tr>
          </tbody>
        </table>

        <p>The status is derived by the hub from the instance's sessions each time it is listed. When retention (or a manual delete) removes an instance's last session, the instance's directory goes with it, so an instance simply disappears from the list rather than lingering in an expired state.</p>

        <h2 id="instances-tab">Instances Tab</h2>
        <p>On the Instances tab, the right end of the tab row carries a one-line summary of the project: active and finished instances (pending too, when there are any), sessions and storage size. Click it to open the full breakdown:</p>
        <ul>
          <li><strong>Instances</strong> — counts by status (pending, active, finished), total instances, total sessions, and uptime range.</li>
          <li><strong>Storage</strong> — the bytes the project occupies on the remote, as the hub's last retention pass measured them. Opening the tab never walks the project's files for it; until the first pass has run the breakdown says the project is not measured yet.</li>
        </ul>
        <p>Every view here loads one level of the remote's tree: the instance list carries no sessions, an instance's page reads that instance and its sessions, and a session's files are fetched only when the session is expanded or its drawer is opened.</p>

        <p>Below the tab row, instances are listed as cards with colour-coded left borders for their status. Use the search box to filter by name, or the status buttons to show only one state at a time.</p>

        <h2 id="timeline-tab">Timeline Tab</h2>
        <p>A swimlane view of instance and session activity over the selected time range (1h–30d). Each instance gets one row; session bars inside the lane are green when finished, amber when active.</p>

        <figure class="docs-figure">
          <img src="/images/docs/instances/instance-timeline.png" alt="Instance Timeline with session bars per swimlane" />
        </figure>

        <p>Clicking a row opens an <strong>Instance drawer</strong> with instance metadata. Clicking a session bar opens a <strong>Session drawer</strong> with that session's JVM environment cards (JVM, CPU, GC, Container, Shutdown, …) — only one drawer per row at a time. OS and Virtualization share a single <strong>OS + Virtualization</strong> card, and any missing environment type renders as a <em>No Data</em> placeholder.</p>

        <figure class="docs-figure">
          <img src="/images/docs/instances/instance-timeline-session-detail.png" alt="Session drawer with environment cards" />
        </figure>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
