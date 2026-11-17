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
      <p>The Volume Scheduler has no database. Workspaces, projects, instances, recording sessions and the JFR recordings themselves all live on a shared filesystem laid out by workspace and project, and every job reads that volume when it runs. There is no managed database, no object store and no per-profile databases — the volume scheduler maintains the volume; analysis happens in Microscope, which reads the same volume through its connector.</p>

      <h2 id="design-philosophy">Design Philosophy</h2>
      <p>The volume scheduler's storage choices are driven by deployment realism — fleets, retention, and shared filesystems:</p>

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
            <p>Producers (Provisioner + the profiled JVM) and the volume scheduler share an NFS / PVC-backed directory.</p>
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
            <p>Finished JFR chunks are LZ4-compressed by the compression job; other artifacts stay as the JVM wrote them. Microscope decompresses a chunk when it downloads it.</p>
          </div>
        </div>
      </div>

      <h2 id="volume-state">The Volume Is the State</h2>
      <p>The workspaces directory is the single source of truth for what exists in this deployment. Every job — and Microscope, from its side — reads its directories when it runs, and the folder names are the state; nothing is copied into a store of the volume scheduler's own, so nothing can drift from the files. The jobs are <em>not</em> stored anywhere either — they are read from configuration at startup, so changing a job requires editing properties and restarting. The only figures it keeps are the last retention pass's storage measurements, in memory, for <code>GET /api/v1/storage</code>.</p>

      <div class="db-card platform">
        <div class="card-header">
          <i class="bi bi-folder2-open"></i>
          <h4>Folders Are the State</h4>
          <span class="badge">Filesystem</span>
        </div>
        <div class="card-body">
          <p class="file-path"><i class="bi bi-folder"></i> <code>&lt;volume.path&gt;/workspaces/</code></p>
          <p><strong>Purpose:</strong> Describe every entity the volume scheduler lists.</p>
          <h5>Contains:</h5>
          <ul>
            <li>A workspace is any visible directory at the volume's root except the filesystem's own <code>lost+found</code> — Microscope and the volume scheduler read the same set; it is created only by the Provisioner, when it first provisions a project into it; neither the volume scheduler nor Microscope creates one</li>
            <li>No marker files anywhere — a project or an instance is any visible (non-dot) child directory, a session is a visible directory named with a UUIDv7; anything else under an instance is ignored</li>
          </ul>
          <p>Ids are folder names: a workspace's id is its directory name (the reference id the Provisioner names), a project's is its directory name (the project <code>name</code> the Provisioner was configured with), an instance's is its directory name (<code>instance-name</code>, <code>HOSTNAME</code> by default), and a session's is its directory name, a UUIDv7 whose timestamp is the session's start.</p>
        </div>
      </div>

      <DocsCallout type="info">
        The volume scheduler never builds per-profile databases and never serves a recording. Profiles are a Microscope concept — the volume scheduler's job is to keep the volume within its retention limits and compress its chunks; Microscope downloads the files straight off the volume and parses them on demand.
      </DocsCallout>

      <h2 id="recording-storage">Recording Storage</h2>
      <p>Each recording session writes its files into its own directory, under its instance, project and workspace:</p>

      <div class="directory-structure">
        <pre><code>workspaces/
└── {workspace-id}/
    └── {project}/
        └── {instance-id}/
            └── {session-id}/
                ├── rec-20260220-120500+0000_0.jfr # a chunk (or .jfr.lz4 once compressed)
                └── ...                               # heap dumps, JVM logs, perf-counters</code></pre>
      </div>

      <p>A chunk's name carries the UTC offset of the machine that wrote it and a sequence number (<code>rec-%t&lt;offset&gt;_%n.jfr</code> &mdash; <code>+0000</code> here, <code>+0100</code> on a machine in Prague in winter), and its timestamp is read at that offset. The number starts at <code>0</code> for a JVM's first chunk and goes up by one with each loop, so names never repeat, not even in the hour a daylight-saving switch repeats, and a session's chunks are ordered by it. A name without the number is ordered by its timestamp alone; a name without an offset is read as UTC. See <router-link to="/docs/connectors/recording-sessions/lifecycle">Session Lifecycle</router-link>.</p>

      <p>Each session ends up with its rotated JFR chunks and any optional artifacts (heap dumps, JVM logs, perf-counters). The path makes it cheap to enumerate everything for a workspace, project, instance or session.</p>

      <h2 id="shared-filesystem">Shared Filesystem</h2>
      <p>The volume scheduler is designed to sit next to a shared volume (NFS, PVC, an Azure file share, or any POSIX filesystem) that the producer side — Jeffrey Provisioner and the profiled application — also mounts. The producers write JFR files; the volume scheduler retains and compresses them, and Microscope reads them from the same volume through its connector.</p>

      <div class="db-card profile">
        <div class="card-header">
          <i class="bi bi-hdd-stack"></i>
          <h4>Producer / Volume Scheduler contract</h4>
          <span class="badge">Filesystem</span>
        </div>
        <div class="card-body">
          <h5>Written by Jeffrey Provisioner:</h5>
          <ul>
            <li>Project, instance and session directory structure</li>
          </ul>
          <h5>Written by the profiled application:</h5>
          <ul>
            <li>JFR recordings (chunked)</li>
            <li>Heap dumps and JVM artifacts</li>
          </ul>
          <h5>Maintained by the Volume Scheduler:</h5>
          <ul>
            <li>Lists workspaces, projects, instances and sessions from their directories on every job run</li>
            <li>Orders each instance's sessions by their UUIDv7 directory names; the newest one may still grow</li>
            <li>Deletes what retention no longer allows and LZ4-compresses JFR chunks, never the newest recording of an instance's newest session</li>
          </ul>
        </div>
      </div>

      <DocsCallout type="tip">
        The shared volume must be mounted <em>read-write</em> on the volume scheduler's pod as well as on the producer pods. The volume scheduler does not only read it: the compression job rewrites chunks into their LZ4 archives and the retention job deletes sessions and instance directories. A read-only mount leaves both failing quietly. The volume scheduler never creates a folder — workspaces come from the Provisioner. The user's own changes — deleting a workspace, a project or a session — are made by Microscope through its connector, not by the volume scheduler.
      </DocsCallout>

      <h2 id="directory-layout">Directory Layout</h2>
      <p>A typical volume looks like this on disk:</p>

      <div class="directory-structure">
        <pre><code>&lt;volume.path&gt;/                      # e.g. /mnt/jeffrey
└── workspaces/                       # ── usually a shared volume (NFS / PVC) ──
    └── {workspace-ref-id}/           # the workspace is its folder, no marker
        └── {project}/                    # the folder names are the ids, no markers anywhere
            └── {instance}/
                └── {session}/            # a UUIDv7
                    ├── rec-20260220-120500+0000_0.jfr # a chunk (or .jfr.lz4 once compressed)
                    └── ...                              # heap dumps, JVM logs, perf-counters</code></pre>
      </div>

      <DocsCallout type="tip">
        Point the volume scheduler at the directory that holds <code>workspaces/</code> with
        <router-link to="/docs/volume-scheduler/configuration#volume"><code>jeffrey.volume-scheduler.volume.path</code></router-link>
        (or the <code>JEFFREY_VOLUMESCHEDULER_VOLUME_PATH</code> environment variable). It is required,
        and startup fails if the directory does not exist — an unmounted volume is caught instead of
        silently maintaining an empty directory. A missing <code>workspaces/</code> underneath only
        means there are no projects yet.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
@import '@/views/docs/storage-page.css';
</style>
