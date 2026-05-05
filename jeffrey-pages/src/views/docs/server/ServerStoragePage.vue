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
  { id: 'design-philosophy', text: 'Design Philosophy', level: 2 },
  { id: 'platform-database', text: 'Platform Database', level: 2 },
  { id: 'recording-storage', text: 'Recording Storage', level: 2 },
  { id: 'shared-filesystem', text: 'Shared Filesystem', level: 2 },
  { id: 'directory-layout', text: 'Directory Layout', level: 2 }
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
      <p>Jeffrey Server keeps its catalog (workspaces, projects, instances, recording sessions, scheduler jobs) in a single embedded DuckDB file, and stores the JFR recordings themselves on a shared filesystem laid out by workspace and project. There is no managed database, no object store, and no per-profile databases — Server collects and serves; analysis happens in Microscope.</p>

      <h2 id="design-philosophy">Design Philosophy</h2>
      <p>Server's storage choices are driven by deployment realism — fleets, retention, and shared filesystems:</p>

      <div class="principles-grid">
        <div class="principle-card">
          <div class="card-icon"><i class="bi bi-currency-dollar"></i></div>
          <div class="card-content">
            <h4>No external databases</h4>
            <p>Embedded DuckDB stored in a single file. Nothing to provision or maintain.</p>
          </div>
        </div>
        <div class="principle-card">
          <div class="card-icon"><i class="bi bi-hdd-network"></i></div>
          <div class="card-content">
            <h4>Shared filesystem for recordings</h4>
            <p>Producers (CLI + Agent) and Server share an NFS / PVC-backed directory.</p>
          </div>
        </div>
        <div class="principle-card">
          <div class="card-icon"><i class="bi bi-folder2-open"></i></div>
          <div class="card-content">
            <h4>Workspace-shaped paths</h4>
            <p>Recordings are filed under <code>workspace / project / session</code> on disk — the layout mirrors the catalog.</p>
          </div>
        </div>
        <div class="principle-card">
          <div class="card-icon"><i class="bi bi-file-zip"></i></div>
          <div class="card-content">
            <h4>Compressed at rest</h4>
            <p>JFR is stored compressed with LZ4, heap dumps with GZIP. Microscope decompresses on read.</p>
          </div>
        </div>
        <div class="principle-card">
          <div class="card-icon"><i class="bi bi-eye-slash"></i></div>
          <div class="card-content">
            <h4>No event parsing on the server</h4>
            <p>Server never expands JFR into events. That cost lives entirely in Microscope.</p>
          </div>
        </div>
      </div>

      <h2 id="platform-database">Platform Database</h2>
      <p>Server's platform database holds the multi-tenant catalog and all scheduler state. It is the single source of truth for what exists in this deployment.</p>

      <div class="db-card platform">
        <div class="card-header">
          <i class="bi bi-database"></i>
          <h4>Platform Database</h4>
          <span class="badge">Single File</span>
        </div>
        <div class="card-body">
          <p class="file-path"><i class="bi bi-file-earmark"></i> <code>~/.jeffrey-server/jeffrey-data.db</code></p>
          <p><strong>Purpose:</strong> Multi-workspace catalog and scheduler state.</p>
          <h5>Contains:</h5>
          <ul>
            <li>Workspaces and projects</li>
            <li>Instances (Java applications) and recording sessions</li>
            <li>Profiler-settings configurations</li>
            <li>Scheduler jobs and job history</li>
            <li>Alerts, messages, and lifecycle state</li>
          </ul>
        </div>
      </div>

      <DocsCallout type="info">
        Server never builds per-profile databases. Profiles are a Microscope concept — Server's job is to capture JFR files and serve them over gRPC; Microscope is what parses them on demand.
      </DocsCallout>

      <h2 id="recording-storage">Recording Storage</h2>
      <p>The JFR files themselves don't go into the database. Each recording session writes its files into a path derived from its catalog row:</p>

      <div class="directory-structure">
        <pre><code>workspaces/
└── {workspace-id}/
    └── {project-id}/
        └── recordings/
            └── {session-id}/
                ├── recording.jfr           # JFR (or recording.jfr.lz4)
                └── artifacts/              # heap dumps, JVM logs, perf-counters</code></pre>
      </div>

      <p>Each session ends up with one main JFR file and a directory of optional artifacts (heap dumps, JVM logs, perf-counters). The path makes it cheap to enumerate everything for a workspace, project, or session without touching the database.</p>

      <h2 id="shared-filesystem">Shared Filesystem</h2>
      <p>Server is designed to sit next to a shared volume (NFS, PVC, or any POSIX filesystem) that the producer side — Jeffrey CLI plus Jeffrey Agent — also mounts. The producers write JFR files; Server discovers and serves them.</p>

      <div class="db-card profile">
        <div class="card-header">
          <i class="bi bi-hdd-stack"></i>
          <h4>Producer / Server contract</h4>
          <span class="badge">Filesystem</span>
        </div>
        <div class="card-body">
          <h5>Written by Jeffrey CLI:</h5>
          <ul>
            <li>Instance and session directory structure</li>
            <li>Session metadata files</li>
          </ul>
          <h5>Written by Jeffrey Agent:</h5>
          <ul>
            <li>JFR recordings (chunked)</li>
            <li>Heap dumps and JVM artifacts</li>
          </ul>
          <h5>Read by Jeffrey Server:</h5>
          <ul>
            <li>Discovers sessions via filesystem polling</li>
            <li>Streams JFR chunks to Microscope clients over gRPC</li>
          </ul>
        </div>
      </div>

      <DocsCallout type="tip">
        For production deployments, mount the shared volume <em>read-write</em> on the producer pods and <em>read-only</em> on the Server pod. Server only needs to read the JFR files.
      </DocsCallout>

      <h2 id="directory-layout">Directory Layout</h2>
      <p>A typical Server installation looks like this on disk:</p>

      <div class="directory-structure">
        <pre><code>~/.jeffrey-server/                    # Server home
├── jeffrey-data.db                   # Platform database
├── temp/                             # Temporary files
└── workspaces/                       # ── usually a shared volume (NFS / PVC) ──
    └── {workspace-id}/
        └── {project-id}/
            └── recordings/
                └── {session-id}/
                    ├── recording.jfr
                    └── artifacts/</code></pre>
      </div>

      <DocsCallout type="tip">
        Override the home directory with <code>jeffrey.server.home.dir</code> in <code>application.properties</code> or via the <code>JEFFREY_SERVER_HOME_DIR</code> environment variable. The <code>workspaces/</code> directory is typically mounted from a shared volume regardless of where the home itself lives.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
@import '@/views/docs/storage-page.css';
</style>
