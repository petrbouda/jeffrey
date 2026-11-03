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
import DocsLinkCard from '@/components/docs/DocsLinkCard.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'catalog', text: 'Connector Kinds', level: 2 },
  { id: 'same-everywhere', text: 'What Is the Same for Every Kind', level: 2 },
  { id: 'differs', text: 'What Each Kind Documents', level: 2 },
  { id: 'pages', text: 'Connector Pages', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Available Connectors"
      icon="bi bi-grid"
    />

    <div class="docs-content">
      <p>
        A connector kind is one way of reaching the recording volume: one storage, one way to sign in.
        Microscope reads exactly one connector at a time, and you pick its kind by the block you
        <router-link to="/docs/connectors/declaring">declare</router-link> under
        <code>jeffrey.microscope.connector.*</code>. The list grows as new kinds are added.
      </p>

      <h2 id="catalog">Connector Kinds</h2>
      <table>
        <thead>
          <tr>
            <th>Connector</th>
            <th>Block</th>
            <th>Storage</th>
            <th>Sign-in</th>
            <th>Modes</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><router-link to="/docs/connectors/azure-files"><strong>Azure File Share</strong></router-link></td>
            <td><code>azure-files</code></td>
            <td>An Azure Files share (SMB or NFS), read over the Azure Files REST API, so Microscope needs no mount. In AKS, the volume mounted through the <code>azurefile-csi</code> storage class.</td>
            <td>SAS token or Microsoft Entra ID</td>
            <td>Writable or read-only</td>
          </tr>
          <tr>
            <td><router-link to="/docs/connectors/local"><strong>Local Directory</strong></router-link></td>
            <td><code>local</code></td>
            <td>A directory on the machine Microscope runs on, read live. For testing, a provisioner and hub on the same machine, or a copy of another volume.</td>
            <td>None &mdash; the rights of the user Microscope runs as</td>
            <td>Writable or read-only</td>
          </tr>
        </tbody>
      </table>

      <h2 id="same-everywhere">What Is the Same for Every Kind</h2>
      <p>
        Every connector kind is a backend behind the same features, so you use Microscope the same way
        whichever one is declared:
      </p>
      <ul>
        <li><strong>The volume layout</strong> &mdash; workspaces, projects, instances and UUIDv7-named sessions, the tree the <router-link to="/docs/provisioner">provisioner</router-link> writes and the <router-link to="/docs/hub">hub</router-link> maintains. Folder names are the state.</li>
        <li><strong>The Connector page</strong> &mdash; <router-link to="/docs/connectors/workspaces">Workspaces</router-link>, <router-link to="/docs/connectors/projects">Projects</router-link> and <router-link to="/docs/connectors/projects/instances">Instances</router-link> work the same on every kind.</li>
        <li><strong>Downloads</strong> &mdash; a session lands on the Recordings page as a <router-link to="/docs/microscope/recordings#bundles">bundle</router-link>, pulled one file at a time, its chunks never joined.</li>
        <li><strong>The MCP tools</strong> &mdash; the <router-link to="/docs/microscope-mcp/tools#connector"><code>connector_</code> tools</router-link> list, download and fetch the same way.</li>
        <li><strong>Declaration rules</strong> &mdash; one connector, the common <code>name</code>, writable or read-only, no connection at startup; see <router-link to="/docs/connectors/declaring">Declaring a Connector</router-link>.</li>
      </ul>

      <h2 id="differs">What Each Kind Documents</h2>
      <p>Each connector's page covers what is specific to its storage:</p>
      <ul>
        <li>its block's properties: where the volume is, and the required ones;</li>
        <li>how it signs in, and the rights a writable or read-only connector needs;</li>
        <li>anything about its storage that changes what Microscope can show or how fast;</li>
        <li>its loggers, for diagnosing a slow or failing volume.</li>
      </ul>

      <DocsCallout type="info">
        <strong>Hub and connector are independent.</strong> A connector is how Microscope reads the volume.
        The <router-link to="/docs/hub">Jeffrey Hub</router-link> maintains that same volume with its own
        jobs and has no API to Microscope, whichever connector kind you use.
      </DocsCallout>

      <h2 id="pages">Connector Pages</h2>
      <div class="docs-grid docs-grid-2">
        <DocsLinkCard
          to="/docs/connectors/azure-files"
          icon="bi bi-cloud"
          title="Azure File Share"
          description="An Azure Files share over SMB or NFS, signed in with a SAS token or Microsoft Entra ID."
        />
        <DocsLinkCard
          to="/docs/connectors/local"
          icon="bi bi-folder2-open"
          title="Local Directory"
          description="A directory on this machine in the same layout, read live: for testing or a copied tree."
        />
      </div>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
