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
  { id: 'file-events', text: 'Recording and Artifact File Events', level: 2 },
  { id: 'live-updates', text: 'Live Updates', level: 2 },
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
          <strong>Server-side origin:</strong> Events are produced by Jeffrey Hub as projects, instances, and sessions change. Microscope fetches them on demand — there is no local event store.
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
            <tr>
              <td><code>PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED</code></td>
              <td>A JFR recording chunk finished writing and is ready to download</td>
            </tr>
            <tr>
              <td><code>PROJECT_INSTANCE_SESSION_ARTIFACT_FILE_CREATED</code></td>
              <td>
                A supplementary file finished writing — heap dump, JVM log, application log,
                performance counters, or a crash log
              </td>
            </tr>
            <tr>
              <td><code>PROJECT_INSTANCE_FINISHED</code></td>
              <td>An instance's last unfinished session closed</td>
            </tr>
            <tr>
              <td><code>PROJECT_INSTANCE_EXPIRED</code></td>
              <td>An instance's sessions were reclaimed or its last session deleted</td>
            </tr>
          </tbody>
        </table>

        <h2 id="file-events">Recording and Artifact File Events</h2>

        <p>
          File events let a client react to each rotated JFR chunk instead of waiting for a whole
          session to finish. A detector job scans recent sessions and announces a file once it is
          complete. Two rules decide when that is:
        </p>

        <ul>
          <li>
            <strong>Recordings</strong> — the newest chunk of a live session is marked
            <code>ACTIVE</code> while async-profiler is still writing it, and is only announced
            once it rotates. Empty chunks are skipped entirely.
          </li>
          <li>
            <strong>Artifacts</strong> — a heap dump has no such marker, so it must have been
            untouched for a short settle period before it counts as complete.
          </li>
        </ul>

        <p>
          The <code>sizeBytes</code> in a file event is the size when the file was
          <em>first seen</em>. Compressing a chunk to <code>.jfr.lz4</code> later changes the file
          on disk without producing a new event, so this field is a record of what happened rather
          than a live file inventory.
        </p>

        <h2 id="live-updates">Live Updates</h2>

        <p>
          The event log opens a live stream after its initial load, so new events appear without a
          refresh. An indicator next to the toolbar shows whether the stream is
          <strong>Live</strong>, <strong>Reconnecting</strong>, or <strong>Paused</strong>.
        </p>

        <p>
          Each delivery carries the highest event offset the hub has read. If the connection drops,
          the client reconnects with backoff and resumes from that offset, so no event is missed
          and none is shown twice. Events already on screen from the initial load are de-duplicated
          against the stream's catch-up phase.
        </p>

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
