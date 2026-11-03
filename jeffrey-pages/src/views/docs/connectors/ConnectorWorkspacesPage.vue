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
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'connector-page', text: 'The Connector Page', level: 2 },
  { id: 'workspace-folders', text: 'Workspaces on the Volume', level: 2 },
  { id: 'changes', text: 'What Microscope Changes', level: 2 },
  { id: 'status', text: 'Connector Status', level: 2 },
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
        A <strong>workspace</strong> is a folder that lives on the <strong>connector</strong>. It groups
        projects, their JVM instances, and their recording sessions. Microscope reaches exactly one
        <router-link to="/docs/connectors">connector</router-link> &mdash; the recording volume itself, on whichever
        <router-link to="/docs/connectors/available">kind of storage</router-link> is declared &mdash; and lets you browse
        the workspaces it holds. Heavy analysis still runs locally — Microscope just borrows the data.
      </p>
      <p>
        All Microscope needs is the connector, declared in its configuration. Workspaces come from
        provisioning, not from Microscope: a workspace shows up on the Connector page once the
        <router-link to="/docs/provisioner">provisioner</router-link> has provisioned a project into
        it (its <code>project.workspace-ref-id</code>, <code>$default</code> when unset).
      </p>

      <h2 id="connector-page">The Connector Page</h2>
      <p>
        Open the <strong>Connector</strong> page from the sidebar. It shows the connector's name with a
        <a href="#status">status</a> dot &mdash; and a <strong>Read-only</strong> badge for a
        <router-link to="/docs/connectors/declaring#writable-read-only">read-only</router-link> connector &mdash;
        and browses it in columns that follow the volume's folders: workspaces,
        <router-link to="/docs/connectors/projects">projects</router-link>,
        <router-link to="/docs/connectors/projects/instances">instances</router-link> and recording sessions.
        With nothing declared the page says so and names the properties to set; the connector itself is
        declared in configuration only, see <router-link to="/docs/connectors/declaring">Declaring a Connector</router-link>.
      </p>

      <h2 id="workspace-folders">Workspaces on the Volume</h2>
      <p>
        Each directory at the volume's root (for an Azure File Share, the share's root or its configured root path) is a workspace. The folder
        names are the state and nothing on the volume is a marker: a workspace is a visible folder named by
        its reference id, which is also its id and its name &mdash; there is no separate display name. That
        reference id is the provisioner's <code>project.workspace-ref-id</code> (3-64 characters, letters,
        digits and dashes, not starting or ending with a dash), and the folder appears when the
        provisioner first provisions a project into it. The picked workspace's info button opens its
        <strong>Workspace Info</strong> drawer, with the Reference ID (with a copy button) and its
        project count. A project is any visible (non-dot) folder in a workspace, its
        name being its id, and the Projects column shows that name and the folder's creation date when
        the connector lists one. Beyond that project count Microscope shows no counts or aggregates: no
        session count or last data on a project.
      </p>

      <h2 id="changes">What Microscope Changes</h2>
      <p>
        Microscope creates nothing on the volume: workspaces, projects, instances and sessions all
        come from provisioning. The only changes it makes are the deletions you ask for, exactly
        where the Jeffrey Hub's jobs expect them: deleting a workspace or a project removes its
        directory, and deleting a session takes its instance with its last session. Which session
        of an instance is the newest — the only one that may still grow — is read from the session
        directories' UUIDv7 names, by the same rules the hub applies. A read-only connector offers
        none of these deletions.
      </p>

      <DocsCallout type="warning">
        <strong>Keep a hub on the volume for upkeep.</strong> Retention, cleanup of stale instances
        and LZ4 compression of recordings are the scheduled jobs of a
        <router-link to="/docs/hub">Jeffrey Hub</router-link>, because the hub maintains the
        volume. A volume that only Microscope reads gets none of them and only grows. Run a Jeffrey
        Hub on the same volume — Microscope never connects to it — and let Microscope read the
        volume directly. They work side by side: the hub writes an archive whole before it
        removes the original, and a download whose file was just compressed is retried on the
        archive.
      </DocsCallout>

      <h2 id="status">Connector Status</h2>
      <p>
        The dot beside the connector's name tells you whether Microscope could read it. It is checked
        each time the Connector page opens — there is no manual refresh button.
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
            <td><strong>Online</strong></td>
            <td>The connector answered and listed its workspaces.</td>
            <td>Pick a workspace and start working.</td>
          </tr>
          <tr>
            <td><strong>Checking…</strong></td>
            <td>Microscope is asking the connector for its workspaces.</td>
            <td>Wait a moment.</td>
          </tr>
          <tr>
            <td><strong>Unreachable</strong></td>
            <td>Microscope could not read the connector (network, credentials, or the storage is unavailable). The page shows the error.</td>
            <td>Check the connector's declaration in configuration, its credentials and your network/VPN, then reopen the page.</td>
          </tr>
        </tbody>
      </table>

      <h2 id="not-in-ui">What's Not in the UI</h2>
      <p>A handful of things you might look for don't exist today — knowing this saves you a hunt:</p>
      <ul>
        <li><strong>Creating a workspace</strong> — a workspace appears when the provisioner first provisions a project into it.</li>
        <li><strong>Renaming a workspace</strong> — a workspace's name is its reference id, the name of its folder on the volume, and is not editable.</li>
        <li><strong>Renaming a project</strong> — a project's name is its folder on the volume and its id, so it cannot be renamed.</li>
        <li><strong>Connecting, changing or disconnecting the connector</strong> — it is declared in <router-link to="/docs/connectors/declaring">configuration</router-link> only; change it there and restart.</li>
        <li><strong>More than one connector</strong> — Microscope reaches exactly one.</li>
        <li><strong>Per-workspace credentials</strong> — Microscope signs in at the connector level (for an Azure File Share, its SAS token or Entra identity); there's no separate workspace-level credential.</li>
        <li><strong>Retention and compression on the volume</strong> — Microscope makes the changes you ask for, never upkeep; keep a hub on the volume for that.</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
