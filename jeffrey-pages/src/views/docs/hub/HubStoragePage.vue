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
  { id: 'design-philosophy', text: 'Design Philosophy', level: 2 },
  { id: 'volume-state', text: 'The Volume Is the State', level: 2 },
  { id: 'recording-storage', text: 'Recording Storage', level: 2 },
  { id: 'shared-filesystem', text: 'Shared Filesystem', level: 2 },
  { id: 'directory-layout', text: 'Directory Layout', level: 2 },
  { id: 'upgrading', text: 'Upgrading from a Hub with a Database', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Storage"
      icon="bi bi-database"
    />

    <div class="docs-content">
      <p>Jeffrey Hub has no database. Workspaces, projects, instances, recording sessions and the JFR recordings themselves all live on a shared filesystem laid out by workspace and project, and every call reads that volume when it is asked. There is no managed database, no object store, no cache and no per-profile databases — Server collects and serves; analysis happens in Microscope.</p>

      <h2 id="design-philosophy">Design Philosophy</h2>
      <p>Server's storage choices are driven by deployment realism — fleets, retention, and shared filesystems:</p>

      <div class="principles-grid">
        <div class="principle-card">
          <div class="card-icon"><i class="bi bi-currency-dollar"></i></div>
          <div class="card-content">
            <h4>No database at all</h4>
            <p>The shared volume is the only state. Nothing to provision, migrate or keep in step with the files.</p>
          </div>
        </div>
        <div class="principle-card">
          <div class="card-icon"><i class="bi bi-hdd-network"></i></div>
          <div class="card-content">
            <h4>Shared filesystem for recordings</h4>
            <p>Producers (Provisioner + Agent) and Server share an NFS / PVC-backed directory.</p>
          </div>
        </div>
        <div class="principle-card">
          <div class="card-icon"><i class="bi bi-folder2-open"></i></div>
          <div class="card-content">
            <h4>Workspace-shaped paths</h4>
            <p>Recordings are filed under <code>workspace / project / instance / session</code> on disk — the directory names are the ids.</p>
          </div>
        </div>
        <div class="principle-card">
          <div class="card-icon"><i class="bi bi-file-zip"></i></div>
          <div class="card-content">
            <h4>Compressed at rest</h4>
            <p>JFR is stored compressed with LZ4, heap dumps with GZIP. Microscope decompresses on read.</p>
          </div>
        </div>
      </div>

      <h2 id="volume-state">The Volume Is the State</h2>
      <p>The workspaces directory is the single source of truth for what exists in this deployment. Every listing, download and job reads the marker files there when it runs; nothing is copied into a store of the Hub's own, so nothing can drift from the files. Scheduler jobs are <em>not</em> stored anywhere either — they are resolved from configuration at startup, so changing a job requires editing properties and restarting.</p>

      <div class="db-card platform">
        <div class="card-header">
          <i class="bi bi-folder2-open"></i>
          <h4>Marker Files</h4>
          <span class="badge">Filesystem</span>
        </div>
        <div class="card-body">
          <p class="file-path"><i class="bi bi-folder"></i> <code>$JEFFREY_HOME/workspaces/</code></p>
          <p><strong>Purpose:</strong> Describe every entity the Hub lists.</p>
          <h5>Contains:</h5>
          <ul>
            <li><code>.workspace-info.json</code> — a workspace's name and creation time, written by the Hub when the workspace is created</li>
            <li><code>.project-info.json</code>, <code>.instance-info.json</code>, <code>.session-info.json</code> — written by the Provisioner</li>
            <li><code>.heartbeat/</code> — a session's liveness files, from which the Hub derives its status on every read</li>
            <li><code>.retained</code> — an empty flag file pinning a session against retention</li>
          </ul>
          <p>Ids come from the volume: a workspace's id is its directory name (the reference id the Provisioner names), a project's is the <code>projectId</code> in its marker, and an instance's and a session's are their directory names.</p>
        </div>
      </div>

      <DocsCallout type="info">
        Server never builds per-profile databases. Profiles are a Microscope concept — Server's job is to capture JFR files and serve them over gRPC; Microscope is what parses them on demand.
      </DocsCallout>

      <h2 id="recording-storage">Recording Storage</h2>
      <p>Each recording session writes its files into its own directory, under its instance, project and workspace:</p>

      <div class="directory-structure">
        <pre><code>workspaces/
└── {workspace-id}/
    └── {project}/
        └── {instance-id}/
            └── {session-id}/
                ├── profile-20260220-120500.jfr   # a chunk (or .jfr.lz4 once compressed)
                └── ...                           # heap dumps, JVM logs, perf-counters</code></pre>
      </div>

      <p>Each session ends up with its rotated JFR chunks and any optional artifacts (heap dumps, JVM logs, perf-counters). The path makes it cheap to enumerate everything for a workspace, project, instance or session.</p>

      <h2 id="shared-filesystem">Shared Filesystem</h2>
      <p>Server is designed to sit next to a shared volume (NFS, PVC, or any POSIX filesystem) that the producer side — Jeffrey Provisioner and the profiled application — also mounts. The producers write JFR files; Server discovers and serves them.</p>

      <div class="db-card profile">
        <div class="card-header">
          <i class="bi bi-hdd-stack"></i>
          <h4>Producer / Server contract</h4>
          <span class="badge">Filesystem</span>
        </div>
        <div class="card-body">
          <h5>Written by Jeffrey Provisioner:</h5>
          <ul>
            <li>Project, instance and session directory structure</li>
            <li>Project, instance and session marker files</li>
          </ul>
          <h5>Written by the profiled application:</h5>
          <ul>
            <li>JFR recordings (chunked)</li>
            <li>Heap dumps and JVM artifacts</li>
          </ul>
          <h5>Read by Jeffrey Hub:</h5>
          <ul>
            <li>Lists workspaces, projects, instances and sessions from the marker files on every request</li>
            <li>Derives each session's status from its heartbeat files when it reads it</li>
            <li>Streams JFR chunks to Microscope clients over gRPC</li>
          </ul>
        </div>
      </div>

      <DocsCallout type="tip">
        The shared volume must be mounted <em>read-write</em> on the Server pod as well as on the producer pods. The Server does not only read it: the compression job replaces finished chunks with their LZ4 archives, the retention job deletes sessions and instance directories, creating a workspace writes its <code>.workspace-info.json</code>, retaining a session writes its <code>.retained</code> flag, and the delete RPCs remove files on request. A read-only mount leaves every one of those failing quietly.
      </DocsCallout>

      <h2 id="directory-layout">Directory Layout</h2>
      <p>A typical Server installation looks like this on disk:</p>

      <div class="directory-structure">
        <pre><code>$JEFFREY_HOME/                        # Server home
└── workspaces/                       # ── usually a shared volume (NFS / PVC) ──
    └── {workspace-ref-id}/
        ├── .workspace-info.json      # written by the Hub when the workspace is created
        └── {project}/
            ├── .project-info.json
            └── {instance}/
                ├── .instance-info.json
                └── {session}/
                    ├── .session-info.json
                    ├── .heartbeat/                      # heartbeat + clean-exit marker
                    ├── .retained                        # only while pinned against retention
                    ├── profile-20260220-120500.jfr      # a chunk (or .jfr.lz4 once compressed)
                    └── ...                              # heap dumps, JVM logs, perf-counters</code></pre>
      </div>

      <DocsCallout type="tip">
        Override the home directory with <code>jeffrey.hub.home.dir</code> in <code>application.properties</code> or via the <code>JEFFREY_HUB_HOME_DIR</code> environment variable. The <code>workspaces/</code> directory is typically mounted from a shared volume regardless of where the home itself lives.
      </DocsCallout>

      <h2 id="upgrading">Upgrading from a Hub with a Database</h2>
      <p>Earlier Hubs kept a copy of the volume in an embedded DuckDB file. Moving to a Hub without one is a one-time break:</p>
      <ul>
        <li><strong>The old database is ignored.</strong> An existing <code>$JEFFREY_HOME/jeffrey-data.db</code> (and its <code>.wal</code>) is never opened and can be deleted.</li>
        <li><strong>Workspaces need their marker.</strong> A workspace created over gRPC before the upgrade has no <code>.workspace-info.json</code>, so it is not listed. Create it again with the same reference id — creating adopts the existing directory with its projects — or set <code>jeffrey.hub.workspaces.auto-create=true</code> to list every workspace directory.</li>
        <li><strong>Microscope loses its links to older downloads.</strong> Recordings and profiles Microscope downloaded before the upgrade carry the old hub UUIDs, so they no longer link to their hub project: they are not recognised as already downloaded and do not appear in that project's profile list. Download them again to restore the link.</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
@import '@/views/docs/storage-page.css';
</style>
