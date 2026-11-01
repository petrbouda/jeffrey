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
  { id: 'declare-remote', text: 'Declare the Remote', level: 2 },
  { id: 'azure-files', text: 'Working on an Azure File Share', level: 3 },
  { id: 'status', text: 'Remote Status', level: 2 },
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
        projects, their JVM instances, and their recording sessions. Microscope reaches exactly one
        remote &mdash; the recording volume itself on an Azure file share &mdash; and lets you browse
        the workspaces it holds. Heavy analysis still runs locally — Microscope just borrows the data.
      </p>
      <p>
        All Microscope needs is the share, declared in its configuration. Workspaces come from
        provisioning, not from Microscope: a workspace shows up on the Remote page once the
        <router-link to="/docs/provisioner">provisioner</router-link> has provisioned a project into
        it (its <code>project.workspace-ref-id</code>, <code>$default</code> when unset).
      </p>

      <h2 id="declare-remote">Declare the Remote</h2>
      <p>
        The remote is declared in configuration only, with
        <router-link to="/docs/microscope/configuration/application-properties#remote"><code>jeffrey.microscope.remote.*</code></router-link>,
        and read when Microscope starts. It names the Azure file share that holds the recording
        volume and how to sign in to it:
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
            <td>How the remote is named in Microscope's Remote page. Defaults to the share's name.</td>
          </tr>
          <tr>
            <td><code>azure-files.endpoint</code></td>
            <td>The storage account's file endpoint, e.g. <code>https://&lt;account&gt;.file.core.windows.net</code>.</td>
          </tr>
          <tr>
            <td><code>azure-files.share</code></td>
            <td>The file share the provisioner writes into.</td>
          </tr>
          <tr>
            <td><code>azure-files.auth</code></td>
            <td><code>sas</code> (the default) with a <code>sas-token</code>, or <code>entra</code> for Microsoft Entra ID.</td>
          </tr>
          <tr>
            <td><code>azure-files.read-only</code></td>
            <td><code>true</code> to refuse every change to the share (<a href="#azure-files">below</a>).</td>
          </tr>
        </tbody>
      </table>

      <p>
        The <router-link to="/docs/microscope/configuration/application-properties#remote-azure-files">configuration reference</router-link>
        lists the rest (root path, protocol). Open the <strong>Remote</strong> page from the sidebar:
        it shows the remote's name with a <a href="#status">status</a> dot, and browses it in columns
        that follow the share's folders: workspaces, projects, instances and recording sessions.
        With nothing declared the page says so and names the properties to set. Microscope holds
        exactly one remote, and the UI never connects, changes or removes it — to point Microscope at
        another share, change the configuration and restart.
        No connection is attempted at startup, so a share that is unreachable when Microscope boots
        starts working as soon as it can be reached. A declaration that cannot be used (a missing
        endpoint or share, …) is logged and ignored rather than failing startup.
      </p>

      <h3 id="azure-files">Working on an Azure File Share</h3>
      <p>
        The remote is the recording volume itself — the Azure file share (SMB or NFS) the provisioner
        writes into — and Microscope works with it over the Azure Files REST API, signing in with a
        SAS token or with Microsoft Entra ID. Its workspaces, projects, instances and recording
        sessions are browsed and downloaded straight off the share. Each directory at the share's
        root (or under its configured root path) is a workspace. The folder names are the state and
        nothing on the volume is a marker: a workspace is a visible folder named by its reference id,
        which is also its id and its name &mdash; there is no separate display name. That reference
        id is the provisioner's <code>project.workspace-ref-id</code> (3-64 characters, letters,
        digits and dashes, not starting or ending with a dash), and the folder appears when the
        provisioner first provisions a project into it. The picked workspace's info button opens its
        <strong>Workspace Info</strong> drawer, with the Reference ID (with a copy button) and its
        project count. A project is any visible (non-dot) folder in a workspace, its
        name being its id, and the Projects column shows that name and the folder's creation date when
        the share lists one. Beyond that project count Microscope shows no counts or aggregates: no
        session count or last data on a project.
      </p>
      <p>
        Microscope creates nothing on the share: workspaces, projects, instances and sessions all
        come from provisioning. The only changes it makes are the deletions you ask for, exactly
        where the Jeffrey Hub's jobs expect them: deleting a workspace or a project removes its
        directory, and deleting a session takes its instance with its last session. Which session
        of an instance is the newest — the only one that may still grow — is read from the session
        directories' UUIDv7 names, by the same rules the hub applies.
      </p>
      <p>
        Declared with <code>read-only=true</code>, the remote carries a <strong>Read-only</strong>
        badge and only browses and downloads: there is no button to delete a workspace, a project
        or a session (a request that reaches the backend anyway is refused, saying the remote is
        read-only). Use it with a credential that may only read and list.
      </p>

      <DocsCallout type="warning">
        <strong>Keep a hub on the share for upkeep.</strong> Retention, cleanup of stale instances
        and LZ4 compression of recordings are the scheduled jobs of a
        <router-link to="/docs/hub">Jeffrey Hub</router-link>, because the hub maintains the
        volume. A share that only Microscope reads gets none of them and only grows. Run a Jeffrey
        Hub on the same volume — Microscope never connects to it — and let Microscope read the
        share directly. They work side by side: the hub writes an archive whole before it
        removes the original, and a download whose file was just compressed is retried on the
        archive.
      </DocsCallout>

      <h2 id="status">Remote Status</h2>
      <p>
        The dot beside the remote's name tells you whether Microscope could read it. It is checked
        each time the Remote page opens — there is no manual refresh button.
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
            <td>The remote answered and listed its workspaces.</td>
            <td>Pick a workspace and start working.</td>
          </tr>
          <tr>
            <td><strong>Checking…</strong></td>
            <td>Microscope is asking the remote for its workspaces.</td>
            <td>Wait a moment.</td>
          </tr>
          <tr>
            <td><strong>Unreachable</strong></td>
            <td>Microscope could not read the remote (network, credentials, or the share is unavailable). The page shows the error.</td>
            <td>Check the remote's declaration in configuration, its credentials and your network/VPN, then reopen the page.</td>
          </tr>
        </tbody>
      </table>

      <h2 id="not-in-ui">What's Not in the UI</h2>
      <p>A handful of things you might look for don't exist today — knowing this saves you a hunt:</p>
      <ul>
        <li><strong>Creating a workspace</strong> — a workspace appears when the provisioner first provisions a project into it.</li>
        <li><strong>Renaming a workspace</strong> — a workspace's name is its reference id, the name of its folder on the share, and is not editable.</li>
        <li><strong>Renaming a project</strong> — a project's name is its folder on the share and its id, so it cannot be renamed.</li>
        <li><strong>Connecting, changing or disconnecting the remote</strong> — it is declared in configuration only; change it there and restart.</li>
        <li><strong>More than one remote</strong> — Microscope reaches exactly one.</li>
        <li><strong>Per-workspace credentials</strong> — Microscope authenticates at the remote level (the SAS token or Entra identity of the Azure file share); there's no separate workspace-level token.</li>
        <li><strong>Retention and compression on a share</strong> — Microscope makes the changes you ask for, never upkeep; keep a hub on the volume for that.</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
