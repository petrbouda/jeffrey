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
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'declare', text: 'Declare It in Microscope', level: 2 },
  { id: 'properties', text: 'Properties', level: 2 },
  { id: 'layout', text: 'The Directory Layout', level: 2 },
  { id: 'live', text: 'Read Live', level: 2 },
  { id: 'links', text: 'Links Outside the Directory', level: 2 },
  { id: 'read-only', text: 'Read-only Mode', level: 2 },
  { id: 'local-loop', text: 'Provisioner, Hub and Microscope on One Machine', level: 2 },
  { id: 'startup', text: 'Startup Behaviour', level: 2 },
  { id: 'troubleshooting', text: 'Troubleshooting', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const localPropertiesExample = `jeffrey.microscope.connector.name=Scratch volume
jeffrey.microscope.connector.local.path=/tmp/jeffrey/workspaces
jeffrey.microscope.connector.local.read-only=false`;

const localEnvExample = `JEFFREY_MICROSCOPE_CONNECTOR_LOCAL_PATH=/tmp/jeffrey/workspaces
JEFFREY_MICROSCOPE_CONNECTOR_LOCAL_READONLY=true`;

const layoutExample = `/tmp/jeffrey/workspaces/          <- local.path
  prod/                             workspace
    orders/                         project
      pod-a/                        instance
        019c7b8e-...-8000.../       session (UUIDv7: its start time)
          rec-20260220-121000+0000.jfr
          rec-20260220-121500+0000.jfr
          gc.log`;

const localLoopExample = `# One home directory shared by all three
export JEFFREY_HOME=/tmp/jeffrey          # the provisioner writes <home>/workspaces/...
java -Djeffrey.hub.home.dir=/tmp/jeffrey -jar hub.jar
java -jar microscope.jar --jeffrey.microscope.connector.local.path=/tmp/jeffrey/workspaces`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Local Directory"
      icon="bi bi-folder2-open"
    />

    <div class="docs-content">
      <h2 id="overview">Overview</h2>
      <p>
        The Local Directory is one of the <router-link to="/docs/connectors/available">connector kinds</router-link>
        Microscope can read; its block is <code>jeffrey.microscope.connector.local.*</code>. It serves a directory
        on the machine Microscope runs on, holding the recording volume in the same layout as an
        <router-link to="/docs/connectors/azure-files">Azure File Share</router-link>: workspaces, projects,
        instances and sessions. Microscope lists, downloads and deletes there exactly as it does on a share.
      </p>
      <p>Use it to:</p>
      <ul>
        <li>try the Connector page and the <code>connector_</code> MCP tools without Azure;</li>
        <li>run the <router-link to="/docs/provisioner">provisioner</router-link>, the
          <router-link to="/docs/hub">hub</router-link> and Microscope together on one machine
          (<a href="#local-loop">below</a>);</li>
        <li>work on a copy of part of another volume, copied into the directory by whatever tool you like.</li>
      </ul>

      <h2 id="declare">Declare It in Microscope</h2>
      <p>
        Point <code>local.path</code> at the directory that holds the workspaces &mdash; the
        <code>workspaces</code> directory under a Jeffrey home &mdash; and restart Microscope:
      </p>
      <DocsCodeBlock language="properties" :code="localPropertiesExample" />
      <p>The same as environment variables:</p>
      <DocsCodeBlock language="bash" :code="localEnvExample" />
      <p>
        Declare one connector kind: a configuration that declares both <code>azure-files</code> and
        <code>local</code> is logged and ignored, and Microscope runs with no connector.
      </p>

      <h2 id="properties">Properties</h2>
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
            <td><code>jeffrey.microscope.connector.local.path</code></td>
            <td><em>required</em></td>
            <td>Absolute path of the directory holding the workspaces. A relative path is refused.</td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.connector.local.read-only</code></td>
            <td><code>false</code></td>
            <td>Refuse every change to the directory; see <a href="#read-only">Read-only Mode</a>.</td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.connector.name</code></td>
            <td>the directory's name</td>
            <td>What the UI and the MCP answers call the connector; common to every kind
              (<router-link to="/docs/connectors/declaring">Declaring a Connector</router-link>).</td>
          </tr>
        </tbody>
      </table>
      <p>
        There is nothing to sign in with: Microscope reads the directory with the rights of the user it
        runs as.
      </p>

      <h2 id="layout">The Directory Layout</h2>
      <p>
        The directory is the volume itself; folder names are the state, as the provisioner writes them:
      </p>
      <DocsCodeBlock language="text" :code="layoutExample" />
      <p>
        Hidden entries (names starting with a dot) are not part of the volume. MCP answers and downloads
        name this connector <code>local:&lt;absolute path&gt;</code>, so a recording downloaded from it is
        recognised as this connector's.
      </p>

      <h2 id="live">Read Live</h2>
      <p>
        Nothing is cached: every page and every MCP call lists the directory again. A session the
        profiler starts, a chunk it closes, or a session the hub's retention removes shows up on the next
        read &mdash; press refresh on the Connector page.
      </p>

      <h2 id="links">Links Outside the Directory</h2>
      <p>
        A symbolic link inside the directory that points to somewhere else inside it reads as what it
        points to. A link whose target lies <strong>outside</strong> the directory &mdash; anywhere along an
        entry's path &mdash; is not part of the volume: it is left out of listings, reads as absent, and a
        delete never follows it, so removing a workspace can never reach the rest of the machine.
      </p>

      <h2 id="read-only">Read-only Mode</h2>
      <p>
        With <code>local.read-only=true</code>, the directory lists and downloads as usual, but Microscope
        refuses to delete a workspace, project or session on it, and the UI hides those actions. Use it for
        a copy you want to keep as it is.
      </p>

      <h2 id="local-loop">Provisioner, Hub and Microscope on One Machine</h2>
      <p>
        The provisioner and the hub both keep the volume under <code>&lt;home&gt;/workspaces</code>. Give
        them one home and point the local connector at its <code>workspaces</code> directory, and the whole
        chain runs on a laptop: the provisioner writes sessions, the hub's retention, cleanup and
        compression jobs act on them, and Microscope reads what is there right now.
      </p>
      <DocsCodeBlock language="bash" :code="localLoopExample" />

      <DocsCallout type="info">
        <strong>The hub stays off Microscope's path.</strong> Pointing both at one directory shares the volume,
        not a connection: the hub still has no API to Microscope, exactly as with a share.
      </DocsCallout>

      <h2 id="startup">Startup Behaviour</h2>
      <p>
        Microscope checks the declaration at startup but opens nothing. A missing or relative
        <code>path</code> is logged and the connector is left out. A directory that is not there yet is
        still declared: it reads as <strong>Unreachable</strong> (and <code>connector_status</code> answers
        <code>UNREACHABLE</code>) until it appears, and is read from then on without a restart.
      </p>

      <h2 id="troubleshooting">Troubleshooting</h2>
      <ul>
        <li><strong>The connector is Unreachable</strong> &mdash; the directory is not there, is a file, or
          cannot be read by the user Microscope runs as. The log line names the path.</li>
        <li><strong>No workspaces</strong> &mdash; <code>local.path</code> points one level too high:
          it must be the <code>workspaces</code> directory, whose children are the workspaces.</li>
        <li><strong>A workspace is missing</strong> &mdash; it is a hidden directory, or a link to somewhere
          outside <code>local.path</code> (see <a href="#links">Links Outside the Directory</a>).</li>
        <li><strong>No connector at all</strong> &mdash; both <code>azure-files</code> and <code>local</code>
          are declared, or the path is relative; the startup log says which.</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
