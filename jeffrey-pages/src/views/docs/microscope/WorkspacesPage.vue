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
        Getting there is a two-step process: declare the share in Microscope's configuration, then
        create or pick a workspace on it.
      </p>

      <h2 id="declare-remote">Step 1 — Declare the Remote</h2>
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
        it shows the remote's name with a <a href="#status">status</a> dot, and its workspaces beneath.
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
        root (or under its configured root path) is a workspace, named by its workspace marker when
        one is there.
      </p>
      <p>
        Microscope makes your changes on the share, exactly where the Jeffrey Hub's jobs expect them:
        <strong>Create Workspace</strong> writes the workspace's marker (adopting a directory the
        provisioner already wrote into), deleting a workspace or a project removes its directory,
        and deleting a session takes its instance with its last session. Which session of an
        instance is the newest — the only one that may still grow — is read from the session
        directories' UUIDv7 names, by the same rules the hub applies.
      </p>
      <p>
        Declared with <code>read-only=true</code>, the remote carries a <strong>Read-only</strong>
        badge and changes nothing: there is no <strong>Create Workspace</strong>, and no button to
        delete a workspace, a project or a session (a request that reaches the
        backend anyway is refused, saying the remote is read-only).
        Use it with a credential that may only read.
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

      <h2 id="create-workspace">Step 2 — Create a Workspace</h2>
      <p>
        With the remote declared and online, click <strong>Create Workspace</strong> in the workspace
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
              your provisioner's configuration. On the share it becomes the workspace's id and the
              name of its directory on the recording volume; if the provisioner already wrote into a
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
        <li><strong>Renaming a workspace</strong> — the name is set at creation and is not editable.</li>
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
