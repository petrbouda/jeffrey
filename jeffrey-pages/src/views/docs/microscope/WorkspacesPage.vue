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
  { id: 'declare-remote', text: 'Step 1 — Declare the Remote', level: 2 },
  { id: 'azure-files', text: 'Working on an Azure File Share', level: 3 },
  { id: 'create-workspace', text: 'Step 2 — Create a Workspace', level: 2 },
  { id: 'status', text: 'Workspace Status', level: 2 },
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
        A <strong>workspace</strong> is a folder that lives on the <strong>remote</strong>. It groups
        projects, their JVM instances, and their recording sessions. Microscope connects to exactly
        one remote &mdash; a Jeffrey Hub reached over gRPC, or the recording volume itself on an Azure
        file share &mdash; and lets you browse the workspaces it holds. Heavy analysis still runs
        locally — Microscope just borrows the data.
      </p>
      <p>
        Getting there is a two-step process: declare the remote in Microscope's configuration, then
        create or pick a workspace on it.
      </p>

      <h2 id="declare-remote">Step 1 — Declare the Remote</h2>
      <p>
        The remote is declared in configuration only, with
        <router-link to="/docs/microscope/configuration/application-properties#remote"><code>jeffrey.microscope.remote.*</code></router-link>,
        and read when Microscope starts. A Jeffrey Hub needs its host, and optionally a name, port and
        plaintext switch:
      </p>

      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>What it means</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>name</code></td>
            <td>How the remote is named in Microscope — its header and the breadcrumb. Defaults to the hostname.</td>
          </tr>
          <tr>
            <td><code>hostname</code></td>
            <td>The host that runs Jeffrey Hub (e.g. <code>jeffrey.internal</code> or an IP).</td>
          </tr>
          <tr>
            <td><code>port</code></td>
            <td>The gRPC port the hub listens on (<code>9090</code> by default).</td>
          </tr>
          <tr>
            <td><code>plaintext</code></td>
            <td><code>false</code> for a TLS connection (the default, recommended); <code>true</code> only for an in-cluster or local hub without TLS.</td>
          </tr>
        </tbody>
      </table>

      <p>
        Open the <strong>Remote</strong> page from the sidebar: it shows the remote's name with a
        status indicator, and its workspaces beneath. With nothing declared the page says so and
        names the properties to set. Microscope holds exactly one remote, and the UI never connects,
        changes or disconnects it — to switch to another hub, change the configuration and restart.
        No connection is attempted at startup, so a hub that boots after Microscope starts working as
        soon as it comes up. A declaration that cannot be used (both a hostname and a share, a port out
        of range, …) is logged and ignored rather than failing startup.
      </p>

      <h3 id="azure-files">Working on an Azure File Share</h3>
      <p>
        The remote does not have to be a Jeffrey Hub. Declared with
        <router-link to="/docs/microscope/configuration/application-properties#remote-azure-files"><code>jeffrey.microscope.remote.azure-files.*</code></router-link>,
        it points at the recording volume itself — the Azure file share (SMB or NFS) the provisioner
        writes into — and Microscope works with it over the Azure Files REST API, signing in with a
        SAS token or with Microsoft Entra ID. Its header carries an <strong>Azure Files</strong>
        badge, and its workspaces, projects, instances and recording sessions are browsed and
        downloaded exactly as through a hub. Each directory at the share's root (or under its
        configured root path) is a workspace, named by the hub's workspace marker when one is there.
      </p>
      <p>
        The changes you make through a hub work here too, and land on the share exactly as a hub
        would write them: <strong>Create Workspace</strong> writes the workspace's marker (adopting
        a directory the provisioner already wrote into), deleting a workspace or a project removes
        its directory, deleting a session takes its instance with its last session, and pinning
        writes the <code>.retained</code> flag a hub's retention honours. Whether a session is still
        recording is decided from its heartbeat file, by the same rules the hub applies.
      </p>
      <p>
        Declared with <code>read-only=true</code>, the remote carries a <strong>Read-only</strong>
        badge and changes nothing: there is no <strong>Create Workspace</strong>, and no button to
        delete a workspace or a project or to delete or pin a session (a request that reaches the
        backend anyway is refused, saying the remote is read-only). Pins a hub set are still shown.
        Use it with a credential that may only read.
      </p>

      <DocsCallout type="warning">
        <strong>Keep a hub on the share for upkeep.</strong> Retention, cleanup of stale instances
        and LZ4 compression of finished recordings are the hub's jobs, because the hub owns the
        volume. A share that only Microscope reads gets none of them and only grows. Run a Jeffrey
        Hub on the same volume — it needs no connection from Microscope — and let Microscope read
        the share directly. They work side by side: the hub writes an archive whole before it
        removes the original, and a download whose file was just compressed is retried on the
        archive.
      </DocsCallout>

      <h2 id="create-workspace">Step 2 — Create a Workspace</h2>
      <p>
        With the remote connected, click <strong>Create Workspace</strong> in the workspace
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
              A stable identifier the workspace exposes to <code>provisioner</code>. The provisioner uses
              this ID to route uploads to the right workspace, so it has to match the value in
              your provisioner's configuration. On the hub it becomes the workspace's id and the
              name of its directory on the shared volume; if the provisioner already wrote into a
              directory of that name, creating the workspace adopts it with its projects.
            </td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="warning">
        <strong>Reference ID is permanent.</strong> It cannot be changed after creation, and
        any provisioner clients that already point at the old value will stop uploading. Pick it
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
            <td>Microscope reached the remote but the workspace itself is not responding.</td>
            <td>Check the hub logs. The badge clears automatically once the workspace recovers.</td>
          </tr>
          <tr>
            <td><strong>Unavailable</strong></td>
            <td>The workspace existed before but the remote no longer reports it (likely deleted there).</td>
            <td>Either restore it on the remote or remove the stale entry from Microscope.</td>
          </tr>
          <tr>
            <td><strong>Unknown</strong></td>
            <td>Microscope can't reach the remote at all (network, TLS, or the hub is down).</td>
            <td>Check the remote's status on the Remote page, its declaration in configuration, and your network/VPN.</td>
          </tr>
        </tbody>
      </table>

      <h2 id="not-in-ui">What's Not in the UI</h2>
      <p>A handful of things you might look for don't exist today — knowing this saves you a hunt:</p>
      <ul>
        <li><strong>Renaming a workspace</strong> — the name is set at creation and is not editable.</li>
        <li><strong>Connecting, changing or disconnecting the remote</strong> — it is declared in configuration only; change it there and restart.</li>
        <li><strong>More than one remote</strong> — Microscope reaches exactly one.</li>
        <li><strong>Per-workspace credentials</strong> — Microscope authenticates at the remote level (TLS, or the SAS token / Entra identity of an Azure file share); there's no separate workspace-level token.</li>
        <li><strong>Retention and compression on a share</strong> — Microscope makes the changes you ask for, never upkeep; keep a hub on the volume for that.</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
