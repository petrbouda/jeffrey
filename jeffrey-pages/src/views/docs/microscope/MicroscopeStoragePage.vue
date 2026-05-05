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
  { id: 'profile-databases', text: 'Per-Profile Databases', level: 2 },
  { id: 'write-once-read-many', text: 'Write-Once-Read-Many', level: 2 },
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
      <p>Jeffrey Microscope keeps everything on your machine. Workspace and project metadata live in a single platform database; each analyzed profile gets its own isolated DuckDB file. No external database server is required.</p>

      <h2 id="design-philosophy">Design Philosophy</h2>
      <p>Microscope's storage choices follow a few principles:</p>

      <div class="principles-grid">
        <div class="principle-card">
          <div class="card-icon"><i class="bi bi-currency-dollar"></i></div>
          <div class="card-content">
            <h4>No external databases</h4>
            <p>Embedded DuckDB stored in local files — no Postgres, no Redis, nothing to deploy.</p>
          </div>
        </div>
        <div class="principle-card">
          <div class="card-icon"><i class="bi bi-trash3"></i></div>
          <div class="card-content">
            <h4>Fast deletion</h4>
            <p>Removing a profile is one <code>rm</code> on its database file. No DELETE queries.</p>
          </div>
        </div>
        <div class="principle-card">
          <div class="card-icon"><i class="bi bi-shield-lock"></i></div>
          <div class="card-content">
            <h4>Per-profile isolation</h4>
            <p>One profile per database file means no contention and no shared state.</p>
          </div>
        </div>
        <div class="principle-card">
          <div class="card-icon"><i class="bi bi-pencil-square"></i></div>
          <div class="card-content">
            <h4>Write once, read many</h4>
            <p>Every write happens at profile initialization. After that, the data is read-only.</p>
          </div>
        </div>
        <div class="principle-card">
          <div class="card-icon"><i class="bi bi-file-zip"></i></div>
          <div class="card-content">
            <h4>Compressed source files</h4>
            <p>JFR is stored compressed with LZ4, heap dumps with GZIP. Microscope decompresses on read.</p>
          </div>
        </div>
      </div>

      <h2 id="platform-database">Platform Database</h2>
      <p>Microscope's platform database holds your local catalog: workspaces, projects, recordings, profile metadata, and the connections to remote Jeffrey Server instances.</p>

      <div class="db-card platform">
        <div class="card-header">
          <i class="bi bi-database"></i>
          <h4>Platform Database</h4>
          <span class="badge">Single File</span>
        </div>
        <div class="card-body">
          <p class="file-path"><i class="bi bi-file-earmark"></i> <code>~/.jeffrey-microscope/jeffrey-data.db</code></p>
          <p><strong>Purpose:</strong> Manages your local catalog of recordings and profiles, plus pointers to remote workspaces on Jeffrey Server.</p>
          <h5>Contains:</h5>
          <ul>
            <li>Workspaces and projects</li>
            <li>Recordings and profile metadata</li>
            <li>Remote workspace connections (gRPC)</li>
            <li>Local settings and preferences</li>
          </ul>
        </div>
      </div>

      <h2 id="profile-databases">Per-Profile Databases</h2>
      <p>Every profile Microscope analyzes is parsed into its own DuckDB file. This is where the parsed JFR events live and where every flame graph, timeseries, and Guardian rule reads from.</p>

      <div class="db-card profile">
        <div class="card-header">
          <i class="bi bi-database-fill"></i>
          <h4>Profile Database</h4>
          <span class="badge">One Per Profile</span>
        </div>
        <div class="card-body">
          <p class="file-path"><i class="bi bi-folder"></i> <code>~/.jeffrey-microscope/profiles/{profile-id}/profile-data.db</code></p>
          <p><strong>Purpose:</strong> All parsed JFR event data for a single profile.</p>
          <h5>Contains:</h5>
          <ul>
            <li>JFR events (CPU samples, allocations, locks, GC, etc.)</li>
            <li>Stacktraces and frames</li>
            <li>Thread information</li>
            <li>Event-type metadata</li>
            <li>Cached analysis results</li>
          </ul>
        </div>
      </div>

      <DocsCallout type="info">
        <strong>Why a database per profile?</strong>
        <ul style="margin: 0.5rem 0 0 0; padding-left: 1.25rem;">
          <li><strong>Fast deletion</strong> — drop a profile by deleting one file</li>
          <li><strong>No contention</strong> — analysis on one profile never blocks another</li>
          <li><strong>Simple scaling</strong> — each profile is fully independent</li>
          <li><strong>Simpler queries</strong> — no <code>profile_id</code> filter on every query</li>
        </ul>
      </DocsCallout>

      <h2 id="write-once-read-many">Write-Once-Read-Many</h2>
      <p>Profile databases use a strict write-once-read-many (WORM) pattern. Microscope writes the entire profile up-front during parsing; from there on, every analysis feature is a pure read.</p>

      <div class="worm-diagram">
        <div class="worm-phase write">
          <div class="phase-header">
            <span class="phase-number">1</span>
            <h4>Write Phase</h4>
            <span class="phase-badge">Initialization</span>
          </div>
          <div class="phase-body">
            <ol>
              <li>Parse the JFR file</li>
              <li>Create the profile database with the schema</li>
              <li>Bulk-insert events</li>
              <li>Store stacktraces, frames, threads</li>
              <li>Run post-processing (caches)</li>
              <li>Mark the profile as enabled</li>
            </ol>
            <p class="phase-note"><i class="bi bi-lightning-charge"></i> Uses DuckDB's Appender API for efficient bulk inserts (10,000 events per batch).</p>
          </div>
        </div>

        <div class="worm-arrow">
          <i class="bi bi-arrow-right"></i>
        </div>

        <div class="worm-phase read">
          <div class="phase-header">
            <span class="phase-number">2</span>
            <h4>Read Phase</h4>
            <span class="phase-badge">Analysis</span>
          </div>
          <div class="phase-body">
            <ul>
              <li>SQL queries for flame graph data</li>
              <li>Timeseries aggregations</li>
              <li>Guardian pattern matching</li>
              <li>Thread and GC analysis</li>
              <li>Heap-dump browsing</li>
            </ul>
            <p class="phase-note"><i class="bi bi-lock"></i> Event data never changes after initialization.</p>
          </div>
        </div>
      </div>

      <h3>Benefits</h3>
      <ul>
        <li><strong>Predictable performance</strong> — no write contention during analysis</li>
        <li><strong>Caching is safe</strong> — data never changes, caches stay valid</li>
        <li><strong>Concurrent reads</strong> — multiple analysis queries run side-by-side</li>
        <li><strong>Data integrity</strong> — original JFR data preserved exactly as parsed</li>
      </ul>

      <h2 id="directory-layout">Directory Layout</h2>
      <p>Everything Microscope owns lives under a single home directory:</p>

      <div class="directory-structure">
        <pre><code>~/.jeffrey-microscope/                     # Microscope home
├── jeffrey-data.db                       # Platform database
├── temp/                                 # Temporary files during parsing
├── profiles/
│   ├── {profile-id-1}/
│   │   └── profile-data.db               # Profile database
│   └── {profile-id-2}/
│       └── profile-data.db
└── recordings/                           # JFR files (uploaded or downloaded)
    └── {recording-id}/
        ├── recording.jfr                 # or recording.jfr.lz4
        └── artifacts/                    # heap dumps, JVM logs, perf-counters</code></pre>
      </div>

      <DocsCallout type="tip">
        Override the home directory with <code>jeffrey.microscope.home.dir</code> in <code>application.properties</code> or via the <code>JEFFREY_MICROSCOPE_HOME_DIR</code> environment variable.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
@import '@/views/docs/storage-page.css';
</style>
