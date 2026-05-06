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
  { id: 'event-types', text: 'Event Types', level: 2 },
  { id: 'filtering', text: 'Filtering and Search', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Event Log"
        icon="bi bi-list-ul"
      />

      <div class="docs-content">
        <p>The Event Log provides a <strong>centralized audit trail</strong> of workspace activity. It shows events from remote workspaces in a timeline format, allowing you to track project lifecycle changes, instance activity, and session events.</p>

        <h2 id="overview">Overview</h2>
        <p>Each workspace exposes an <strong>Event Log</strong> tab next to its Projects tab. Pick a workspace from the Workspaces page, then switch to the Event Log tab to see its activity:</p>

        <div class="docs-feature-list">
          <DocsFeatureItem icon="bi bi-grid" title="Workspace Selection">
            Select a workspace from the card grid to view its events. Each workspace card shows a badge with the total event count.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-clock-history" title="Event Timeline">
            Events are displayed in chronological order with color-coded badges indicating the event type. Each event shows its timestamp, creator, and relevant details.
          </DocsFeatureItem>
          <DocsFeatureItem icon="bi bi-info-circle" title="Event Details">
            Click on any event to view the full event data in a detail modal, including all attributes and the raw JSON representation.
          </DocsFeatureItem>
        </div>

        <DocsCallout type="info">
          <strong>Server-side origin:</strong> Events are produced by Jeffrey Server as projects, instances, and sessions change. Microscope fetches them on demand — there is no local event store.
        </DocsCallout>

        <h2 id="event-types">Event Types</h2>
        <p>The following event types are tracked:</p>

        <table>
          <thead>
            <tr>
              <th>Event Type</th>
              <th>Description</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><code>PROJECT_CREATED</code></td>
              <td>A new project was created in the workspace</td>
            </tr>
            <tr>
              <td><code>PROJECT_DELETED</code></td>
              <td>A project was deleted from the workspace</td>
            </tr>
            <tr>
              <td><code>PROJECT_INSTANCE_CREATED</code></td>
              <td>A new application instance connected to a project</td>
            </tr>
            <tr>
              <td><code>PROJECT_INSTANCE_SESSION_CREATED</code></td>
              <td>A new recording session was started on an instance</td>
            </tr>
            <tr>
              <td><code>PROJECT_INSTANCE_SESSION_DELETED</code></td>
              <td>A recording session was removed</td>
            </tr>
            <tr>
              <td><code>PROJECT_INSTANCE_SESSION_FINISHED</code></td>
              <td>A recording session completed</td>
            </tr>
          </tbody>
        </table>

        <h2 id="filtering">Filtering and Search</h2>
        <p>The Event Log provides filtering and search capabilities to find specific events:</p>
        <ul>
          <li><strong>Type filter</strong> - Filter events by type using the dropdown (e.g., show only <code>PROJECT_CREATED</code> events)</li>
          <li><strong>Text search</strong> - Search across event descriptions, event IDs, and project IDs</li>
        </ul>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
