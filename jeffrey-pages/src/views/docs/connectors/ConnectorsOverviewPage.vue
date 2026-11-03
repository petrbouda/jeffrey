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
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsLinkCard from '@/components/docs/DocsLinkCard.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'what-it-is', text: 'What a Connector Is', level: 2 },
  { id: 'one-connector', text: 'One Connector, Many Kinds', level: 2 },
  { id: 'layout', text: 'What a Connector Holds', level: 2 },
  { id: 'what-microscope-does', text: 'What Microscope Does With It', level: 2 },
  { id: 'available', text: 'Available Connectors', level: 2 },
  { id: 'hub', text: 'The Hub Is Not on the Path', level: 2 },
  { id: 'where-next', text: 'Where to Go Next', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const layout = `<root-path>/
└── <workspace>/                     workspace-ref-id from the provisioner ($default when unset)
    └── <project>/                   the provisioner's project name
        └── <instance>/              instance-name, HOSTNAME by default
            └── <session-uuidv7>/    one recording session; the newest may still grow
                ├── *.jfr / *.jfr.lz4      JFR chunks
                ├── *.hprof(.gz)           heap dumps
                ├── perf-counters.hsperfdata
                └── *.log, hs_err*.log     logs and crash files`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Connectors"
      icon="bi bi-plug"
    />

    <div class="docs-content">
      <p class="docs-lede">
        A <strong>connector</strong> is how Jeffrey Microscope reaches recordings that are not on its own
        disk: the shared recording volume your deployed JVMs write into. These pages cover what a
        connector is, how to declare one, and how to browse, download and clean up what it holds.
      </p>

      <h2 id="what-it-is">What a Connector Is</h2>
      <p>
        Microscope analyses recordings in two ways. It can take a file you hand it on the
        <router-link to="/docs/microscope/recordings">Recordings</router-link> page, or it can read the
        <strong>recording volume</strong> &mdash; the storage that the
        <router-link to="/docs/provisioner">Jeffrey Provisioner</router-link> lays out and every profiled
        JVM writes its JFR chunks, heap dumps, perf counters and logs into. The connector is the second
        path: Microscope reads the volume directly, lists what it holds, and pulls a recording session
        down when you want to analyse it. The analysis itself always runs locally &mdash; the connector
        only lends the data.
      </p>
      <p>
        Without a connector Microscope is fully offline: it works on uploaded files only, the
        <strong>Connector</strong> page says that none is declared and names the properties to set, and the
        MCP <code>connector_</code> tools answer that none is configured.
      </p>

      <h2 id="one-connector">One Connector, Many Kinds</h2>
      <p>
        Microscope reaches <strong>exactly one</strong> connector, declared in configuration only under
        <code>jeffrey.microscope.connector.*</code>, read once at startup and never stored. The UI and the MCP
        server show it, but nothing in either can connect, change or disconnect one.
      </p>
      <p>
        Which storage it reads is the connector's <strong>kind</strong>: the declaration names one block, such as
        <code>azure-files</code>, and that block says where the volume is and how to sign in. Every kind
        works behind the same browsing, download and MCP features, so the rest of these pages apply to
        whichever one you declare.
      </p>
      <ul>
        <li><router-link to="/docs/connectors/declaring"><strong>Declaring a Connector</strong></router-link> &mdash; the shape every declaration shares, the common properties, writable vs read-only, startup behaviour.</li>
        <li><router-link to="/docs/connectors/available"><strong>Available Connectors</strong></router-link> &mdash; the kinds you can declare, each with its own page.</li>
      </ul>

      <h2 id="layout">What a Connector Holds</h2>
      <p>
        Every connector exposes the same tree, because it is the same volume the provisioner writes and the
        <router-link to="/docs/hub">Jeffrey Hub</router-link> maintains. The folder names <em>are</em> the
        state: there are no marker or description files, so every id Microscope shows is a folder name.
      </p>

      <DocsCodeBlock language="text" :code="layout" />

      <table>
        <thead>
          <tr>
            <th>Level</th>
            <th>What it is</th>
            <th>Where it comes from</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><router-link to="/docs/connectors/workspaces"><strong>Workspace</strong></router-link></td>
            <td>A top-level folder grouping projects; its folder name is its id and its name.</td>
            <td>Created when the provisioner first provisions a project into it.</td>
          </tr>
          <tr>
            <td><router-link to="/docs/connectors/projects"><strong>Project</strong></router-link></td>
            <td>One application or service, with its instances and sessions.</td>
            <td>Created by the first JVM the provisioner starts for it.</td>
          </tr>
          <tr>
            <td><router-link to="/docs/connectors/projects/instances"><strong>Instance</strong></router-link></td>
            <td>One JVM process of the project.</td>
            <td>The provisioner's <code>instance-name</code>, <code>HOSTNAME</code> by default.</td>
          </tr>
          <tr>
            <td><router-link to="/docs/connectors/projects/instances#instance-sessions"><strong>Session</strong></router-link></td>
            <td>One run of that JVM, named by a UUIDv7 whose timestamp is its start; only the newest one may still grow.</td>
            <td>A new one each time the instance starts. See <router-link to="/docs/hub/recording-sessions/lifecycle">Session Lifecycle</router-link>.</td>
          </tr>
        </tbody>
      </table>

      <h2 id="what-microscope-does">What Microscope Does With It</h2>
      <ul>
        <li><strong>Browse</strong> &mdash; the <strong>Connector</strong> page shows the connector's name and <router-link to="/docs/connectors/workspaces#status">status</router-link>, and walks the tree in columns: Workspaces, Projects, Instances, Sessions. Each column reads one level when its parent is picked.</li>
        <li><strong>Download</strong> &mdash; a whole session, or an unbroken run of its chunks, is pulled down one file at a time and lands on the Recordings page as a <router-link to="/docs/microscope/recordings#bundles">bundle</router-link>: the JFR chunks as one recording (never joined), each heap dump and the perf-counters file as recordings of their own, logs and crash files as download-only files.</li>
        <li><strong>Delete</strong> &mdash; on a writable connector, removing a workspace, a project or a session deletes its directory, exactly where the hub's jobs expect it. Nothing else is ever written: Microscope creates nothing on the volume.</li>
        <li><strong>Hand it to a coding agent</strong> &mdash; the MCP <router-link to="/docs/microscope-mcp/tools#connector"><code>connector_</code> tools</router-link> list the sessions across the whole connector, pull one down into the same kind of bundle, and fetch a log or crash file as a path.</li>
      </ul>

      <h2 id="available">Available Connectors</h2>
      <p>
        The kinds Microscope can read today are listed on
        <router-link to="/docs/connectors/available">Available Connectors</router-link>, each with a page of its
        own; the list grows as new kinds are added. Currently:
        <router-link to="/docs/connectors/azure-files">Azure File Share</router-link>.
      </p>

      <h2 id="hub">The Hub Is Not on the Path</h2>
      <p>
        Microscope reads the volume itself; it never talks to a Jeffrey Hub, and the hub has no API for it.
        The hub's part is upkeep &mdash; retention, cleanup of stale instances and LZ4 compression run as its
        scheduled jobs on the same volume. A volume that only Microscope reads gets none of them and only
        grows, so run a hub beside it. The two work side by side: the hub writes an archive whole before it
        removes the original, and a download whose file was just compressed is retried on the archive.
      </p>

      <h2 id="where-next">Where to Go Next</h2>

      <div class="docs-grid docs-grid-2">
        <DocsLinkCard
          to="/docs/connectors/declaring"
          icon="bi bi-sliders"
          title="Declaring a Connector"
          description="The declaration every connector shares: common properties, read-only mode, startup."
        />
        <DocsLinkCard
          to="/docs/connectors/available"
          icon="bi bi-grid"
          title="Available Connectors"
          description="The connector kinds Microscope can read, and what each one's page covers."
        />
        <DocsLinkCard
          to="/docs/connectors/workspaces"
          icon="bi bi-folder2-open"
          title="Workspaces"
          description="The Connector page, its status dot, and what a workspace is on the volume."
        />
        <DocsLinkCard
          to="/docs/connectors/projects/instances"
          icon="bi bi-hdd-stack"
          title="Instances"
          description="Instances, sessions and the session drawer that downloads them."
        />
      </div>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

.docs-lede {
  font-size: 16px;
  color: #5e6e82;
}
</style>
