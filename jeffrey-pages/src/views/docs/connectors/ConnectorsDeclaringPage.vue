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
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'shape', text: 'The Shape of a Declaration', level: 2 },
  { id: 'common-properties', text: 'Common Properties', level: 2 },
  { id: 'one-connector', text: 'Exactly One Connector', level: 2 },
  { id: 'writable-read-only', text: 'Writable or Read-only', level: 2 },
  { id: 'startup', text: 'Startup Behaviour', level: 2 },
  { id: 'changing', text: 'Changing or Removing It', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const shapeExample = `# Common to every connector
jeffrey.microscope.connector.name=Production recordings

# Exactly one block, named after the connector kind
jeffrey.microscope.connector.<kind>.<property>=...`;

const azureExample = `jeffrey.microscope.connector.name=Production recordings
jeffrey.microscope.connector.azure-files.endpoint=https://jeffreyprod.file.core.windows.net
jeffrey.microscope.connector.azure-files.share=recordings
jeffrey.microscope.connector.azure-files.sas-token=\${AZURE_FILES_SAS}`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Declaring a Connector"
      icon="bi bi-sliders"
    />

    <div class="docs-content">
      <p>
        Every connector is declared the same way: in Microscope's configuration, under
        <code>jeffrey.microscope.connector.*</code>. This page covers what all connectors share; each
        <router-link to="/docs/connectors/available">connector kind</router-link> documents its own block of
        properties.
      </p>

      <h2 id="shape">The Shape of a Declaration</h2>
      <p>
        A declaration is the common properties plus <strong>one block named after the connector
        kind</strong>, which says where the recording volume is and how to sign in to it:
      </p>

      <DocsCodeBlock language="properties" :code="shapeExample" />

      <p>For example, a volume on an Azure file share:</p>

      <DocsCodeBlock language="properties" :code="azureExample" />

      <p>
        Every property has the usual Spring Boot environment-variable form
        (<code>JEFFREY_MICROSCOPE_CONNECTOR_NAME</code>, …), so a credential can come from a secret rather than
        a file.
      </p>

      <h2 id="common-properties">Common Properties</h2>
      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey.<wbr>microscope.<wbr>connector.<wbr>name</code></td>
            <td>set by the connector kind</td>
            <td>
              Display name shown on the Connector page and in the MCP <code>connector_status</code> answer.
              When unset, the connector kind supplies one &mdash; an Azure File Share uses the share's name, a
              Local Directory the directory's name.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.<wbr>microscope.<wbr>connector.<wbr>&lt;kind&gt;.<wbr>*</code></td>
            <td>&mdash;</td>
            <td>
              The block of the one connector Microscope reads. See
              <router-link to="/docs/connectors/available">Available Connectors</router-link> for each kind's
              block name and its properties.
            </td>
          </tr>
        </tbody>
      </table>

      <h2 id="one-connector">Exactly One Connector</h2>
      <p>
        Microscope reaches exactly one connector. It is declared in configuration only, read once at startup
        and never stored in Microscope's database. The UI and the MCP server show it, but neither can
        connect, change or disconnect one, and neither offers a second. Declare one kind's block: a
        configuration that declares two (<code>azure-files</code> and <code>local</code>) is logged and
        ignored, as if none were declared.
      </p>
      <p>
        Without a declaration Microscope is fully offline: it works on uploaded files only, the
        <strong>Connector</strong> page says that none is declared and names the properties to set, and the
        MCP <code>connector_status</code> tool answers <code>NOT_CONFIGURED</code>.
      </p>

      <h2 id="writable-read-only">Writable or Read-only</h2>
      <p>
        A connector is either writable or read-only, set in its kind's block. Microscope never creates
        anything on a volume either way; the difference is the deletions you ask for.
      </p>
      <table>
        <thead>
          <tr>
            <th>Mode</th>
            <th>What Microscope does</th>
            <th>Credential needs</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><strong>Writable</strong></td>
            <td>Browses, downloads, and deletes the workspaces, projects and sessions you ask it to.</td>
            <td>Read, list and delete.</td>
          </tr>
          <tr>
            <td><strong>Read-only</strong></td>
            <td>Browses and downloads only. The Connector page shows a <strong>Read-only</strong> badge and no delete buttons; a delete that reaches the backend anyway is refused.</td>
            <td>Read and list.</td>
          </tr>
        </tbody>
      </table>

      <h2 id="startup">Startup Behaviour</h2>
      <ul>
        <li><strong>No connection is attempted at startup.</strong> A declared connector is taken as it is whether or not it answers, so a volume that is unreachable when Microscope boots starts working as soon as it can be reached. Its <router-link to="/docs/connectors/workspaces#status">status</router-link> is checked each time the Connector page opens.</li>
        <li><strong>An unusable declaration is ignored, not fatal.</strong> A block missing a required property is logged as a warning and Microscope runs as if no connector were declared: a typo in an optional connector never costs you your local recordings.</li>
        <li><strong>Credentials stay in configuration.</strong> They are never written to Microscope's database and never logged.</li>
      </ul>

      <h2 id="changing">Changing or Removing It</h2>
      <p>
        Edit the configuration and restart Microscope. Downloads already taken stay on the
        <router-link to="/docs/microscope/recordings">Recordings</router-link> page, each tagged with the
        connector it came from (<code>origin.connector</code> and <code>origin.connectorAddress</code>).
      </p>

      <DocsCallout type="tip">
        <strong>The MCP side has its own switches.</strong> Whether a coding agent may use the connector at
        all, and how long its <code>connector_</code> tools wait, are MCP server settings under
        <code>jeffrey.microscope.mcp.connector.*</code>; see
        <router-link to="/docs/microscope/configuration/application-properties#mcp-server">MCP Server properties</router-link>.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* Property names break after each dot. A share of the width, not a fixed minimum, keeps them
   to a few lines without the long descriptions squeezing them, and never widens the table. */
.docs-content td:first-child {
  width: 32%;
}
</style>
