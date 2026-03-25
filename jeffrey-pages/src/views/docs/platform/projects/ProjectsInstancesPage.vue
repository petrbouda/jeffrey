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
            View all instances with statistics including instance counts by status, storage usage, and file type breakdown. Filter and search instances by hostname or status.
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

        <p>Below the stats, instances are listed as cards with color-coded left borders indicating their status. Use the search box to filter by hostname, or the status buttons to show only instances in a specific state.</p>

        <h2 id="instances-timeline">Instances Timeline</h2>
        <p>The timeline provides a visual representation of instance and session activity over time:</p>
        <ul>
          <li><strong>Time range selector</strong> - Choose from 1 hour to 30 days to adjust the visible window</li>
          <li><strong>Instance bars</strong> - Faint background bars showing instance lifespan, with pulsing animation for active instances</li>
          <li><strong>Session bars</strong> - Solid bars overlaid on instance bars showing individual recording session durations</li>
          <li><strong>Tooltips</strong> - Hover over bars to see detailed timing information including start time, end time, and duration</li>
        </ul>

        <DocsCallout type="tip">
          <strong>Investigating gaps:</strong> The timeline is useful for identifying periods when an instance was not recording. Gaps between session bars may indicate application restarts or profiler configuration issues.
        </DocsCallout>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
