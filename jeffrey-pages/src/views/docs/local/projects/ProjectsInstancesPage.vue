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
import DocsFeatureItem from '@/components/docs/DocsFeatureItem.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'instance-lifecycle', text: 'Instance Lifecycle', level: 2 },
  { id: 'instances-overview', text: 'Instances Overview', level: 2 },
  { id: 'instances-timeline', text: 'Instances Timeline', level: 2 }
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
        <p><strong>Instances</strong> represent individual JVM processes connected to a project. When an application starts with the Jeffrey profiler agent, it registers as an instance and begins creating recording sessions.</p>

        <h2 id="overview">Overview</h2>
        <p>The Instances section in the project sidebar provides visibility into all application instances connected to a project:</p>

        <div class="docs-feature-list">
          <DocsFeatureItem icon="bi bi-list-ul" title="Instances Overview">
            View all instances with statistics including instance counts by status, storage usage, and file type breakdown. Filter and search instances by name or status.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-calendar-range" title="Instances Timeline">
            Visual timeline showing when instances and their recording sessions were active. Select different time ranges (1H, 6H, 24H, 7D, 30D) to zoom in on specific periods.
          </DocsFeatureItem>
        </div>

        <DocsCallout type="info">
          <strong>Remote workspaces only:</strong> Instances are available for projects in remote workspaces connected to Jeffrey Server. Local projects that use manual recording uploads do not have instances.
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
              <td>Instance registered but not yet actively recording</td>
            </tr>
            <tr>
              <td><strong>Active</strong></td>
              <td>Instance is running and producing recording sessions</td>
            </tr>
            <tr>
              <td><strong>Finished</strong></td>
              <td>Instance has stopped recording (application shut down gracefully)</td>
            </tr>
            <tr>
              <td><strong>Expired</strong></td>
              <td>Instance was removed after the retention period elapsed</td>
            </tr>
          </tbody>
        </table>

        <h2 id="instances-overview">Instances Overview</h2>
        <p>The overview page shows three stat cards summarizing the project's instances:</p>
        <ul>
          <li><strong>Instances</strong> - Counts by status (pending, active, finished, expired), total sessions, and uptime range</li>
          <li><strong>Storage</strong> - Total storage size, file count, and largest session size</li>
          <li><strong>File Types</strong> - Breakdown by type: JFR recordings, heap dumps, JVM logs, application logs, JVM error logs</li>
        </ul>

        <p>Below the stats, instances are listed as cards with color-coded left borders indicating their status. Use the search box to filter by instance name, or the status buttons to show only instances in a specific state.</p>

        <h2 id="instances-timeline">Instances Timeline</h2>
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
