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
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'add-server', text: 'Step 1 — Add a Remote Server', level: 2 },
  { id: 'create-workspace', text: 'Step 2 — Create a Workspace', level: 2 },
  { id: 'status', text: 'Workspace Status', level: 2 },
  { id: 'tabs', text: 'Per-Workspace Tabs', level: 2 },
  { id: 'not-in-ui', text: 'What\'s Not in the UI', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Workspaces"
      icon="bi bi-folder2-open"
    />

    <div class="docs-content">
      <h2 id="overview">Overview</h2>
      <p>
        A <strong>workspace</strong> is a folder that lives on a Jeffrey Server. It groups projects,
        their JVM instances, and their recording sessions. Microscope connects to one or more
        Jeffrey Servers and lets you browse the workspaces hosted there. Heavy analysis still runs
        locally — Microscope just borrows the data.
      </p>
      <p>
        Connecting Microscope to a server is a two-step process: register the server, then
        create or pick a workspace inside it.
      </p>

      <h2 id="add-server">Step 1 — Add a Remote Server</h2>
      <p>
        Open the <strong>Workspaces</strong> page from the top navigation. The left rail lists the
        servers Microscope knows about; click the <strong>+</strong> button to add a new one.
      </p>

      <table>
        <thead>
          <tr>
            <th>Field</th>
            <th>What it means</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><strong>Name</strong></td>
            <td>Display label shown in the rail. Free text — pick something you'll recognize.</td>
          </tr>
          <tr>
            <td><strong>Hostname</strong></td>
            <td>The host that runs Jeffrey Server (e.g. <code>jeffrey.internal</code> or an IP).</td>
          </tr>
          <tr>
            <td><strong>Port</strong></td>
            <td>The gRPC port the server listens on.</td>
          </tr>
          <tr>
            <td><strong>Plaintext</strong></td>
            <td>Toggle <em>off</em> for a TLS connection (recommended). Toggle <em>on</em> only for local development against a non-TLS server.</td>
          </tr>
        </tbody>
      </table>

      <p>
        Microscope contacts the server once to confirm it's reachable. If it isn't, the form
        surfaces a clear error (most often "cannot connect — check hostname/port/TLS").
        On success the server appears in the rail with a status indicator.
      </p>

      <DocsCallout type="tip">
        <strong>Removing a server</strong> only removes Microscope's pointer to it — the server
        and its workspaces stay intact. Re-adding the same hostname/port restores access.
      </DocsCallout>

      <h2 id="create-workspace">Step 2 — Create a Workspace</h2>
      <p>
        Pick a server in the rail, then click <strong>Create Workspace</strong> in the workspace
        column. Microscope opens a side drawer with two fields:
      </p>

      <table>
        <thead>
          <tr>
            <th>Field</th>
            <th>What it means</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><strong>Workspace Name</strong></td>
            <td>Display label for the workspace.</td>
          </tr>
          <tr>
            <td><strong>Reference ID</strong></td>
            <td>
              A stable identifier the workspace exposes to <code>jeffrey-cli</code>. The CLI uses
              this ID to route uploads to the right workspace, so it has to match the value in
              your CLI's configuration.
            </td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="warning">
        <strong>Reference ID is permanent.</strong> It cannot be changed after creation, and
        any CLI clients that already point at the old value will stop uploading. Pick it
        deliberately — typically a short slug like <code>prod-eu</code> or <code>staging</code>.
      </DocsCallout>

      <h2 id="status">Workspace Status</h2>
      <p>
        Each workspace card shows a status badge. Microscope re-checks status on every page
        load and tab switch — there is no manual refresh button.
      </p>

      <table>
        <thead>
          <tr>
            <th>Status</th>
            <th>Meaning</th>
            <th>What to do</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><strong>Available</strong></td>
            <td>Workspace is reachable and healthy.</td>
            <td>Open it and start working.</td>
          </tr>
          <tr>
            <td><strong>Offline</strong></td>
            <td>Microscope reached the server but the workspace itself is not responding.</td>
            <td>Check the server logs. The badge clears automatically once the workspace recovers.</td>
          </tr>
          <tr>
            <td><strong>Unavailable</strong></td>
            <td>The workspace existed before but the server no longer reports it (likely deleted on the server).</td>
            <td>Either restore it on the server or remove the stale entry from Microscope.</td>
          </tr>
          <tr>
            <td><strong>Unknown</strong></td>
            <td>Microscope can't reach the parent server at all (network, TLS, or the server is down).</td>
            <td>Verify the server entry in the rail and your network/VPN.</td>
          </tr>
        </tbody>
      </table>

      <h2 id="tabs">Per-Workspace Tabs</h2>
      <p>
        Once you pick a workspace, the right pane exposes three tabs:
      </p>
      <ul>
        <li>
          <strong><router-link to="/docs/microscope/projects">Projects</router-link></strong> —
          the list of projects in the workspace, with drill-down into instances, sessions,
          recordings, and the repository.
        </li>
        <li>
          <strong><router-link to="/docs/microscope/event-log">Event Log</router-link></strong> —
          a chronological audit trail of project, instance, and session events for this
          workspace.
        </li>
        <li>
          <strong><router-link to="/docs/microscope/profiler-settings">Profiler Settings</router-link></strong> —
          the Async-Profiler configuration scoped to this workspace (overrides the global
          defaults; can be overridden per project).
        </li>
      </ul>

      <h2 id="not-in-ui">What's Not in the UI</h2>
      <p>A handful of things you might look for don't exist today — knowing this saves you a hunt:</p>
      <ul>
        <li><strong>Renaming a workspace</strong> — the name is set at creation and is not editable.</li>
        <li><strong>Editing a server's hostname or port</strong> — remove and re-add the server entry instead.</li>
        <li><strong>Per-workspace credentials</strong> — Microscope authenticates at the server level (TLS); there's no separate workspace-level token.</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
