<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
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
  { id: 'dual-database-architecture', text: 'Dual Database Architecture', level: 2 },
  { id: 'write-once-read-many', text: 'Write-Once-Read-Many for Profiles', level: 2 },
  { id: 'file-storage', text: 'File Storage', level: 2 }
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
        <p>Jeffrey uses a unique storage architecture designed for <strong>operational simplicity</strong> and <strong>cost efficiency</strong>. Instead of requiring external database servers, all data is stored in local files using DuckDB.</p>

        <h2 id="design-philosophy">Design Philosophy</h2>
        <p>The storage design follows several key principles:</p>

        <div class="principles-grid">
          <div class="principle-card">
            <div class="card-icon"><i class="bi bi-currency-dollar"></i></div>
            <div class="card-content">
              <h4>No External Databases</h4>
              <p>Avoid expensive managed databases. Use in-process DuckDB stored in local files.</p>
            </div>
          </div>
          <div class="principle-card">
            <div class="card-icon"><i class="bi bi-trash3"></i></div>
            <div class="card-content">
              <h4>Fast Deletion</h4>
              <p>Delete a profile instantly by removing a single database file.</p>
            </div>
          </div>
          <div class="principle-card">
            <div class="card-icon"><i class="bi bi-shield-lock"></i></div>
            <div class="card-content">
              <h4>Isolation</h4>
              <p>Each profile has its own database - no contention, no shared state.</p>
            </div>
          </div>
          <div class="principle-card">
            <div class="card-icon"><i class="bi bi-pencil-square"></i></div>
            <div class="card-content">
              <h4>Write Once, Read Many</h4>
              <p>All writes happen during profile initialization. After that, data is read-only.</p>
            </div>
          </div>
          <div class="principle-card">
            <div class="card-icon"><i class="bi bi-file-zip"></i></div>
            <div class="card-content">
              <h4>Compressed Storage</h4>
              <p>JFR files are stored compressed with LZ4, Heap Dumps with GZIP to save disk space.</p>
            </div>
          </div>
        </div>

        <h2 id="dual-database-architecture">Dual Database Architecture</h2>
        <p>Jeffrey uses two types of DuckDB databases with different purposes:</p>

        <div class="database-diagram">
          <div class="db-card platform">
            <div class="card-header">
              <i class="bi bi-database"></i>
              <h4>Platform Database</h4>
              <span class="badge">Single File</span>
            </div>
            <div class="card-body">
              <p class="file-path"><i class="bi bi-file-earmark"></i> <code>jeffrey.db</code></p>
              <p><strong>Purpose:</strong> Manages workspace, project, and recording metadata</p>
              <h5>Contains:</h5>
              <ul>
                <li>Workspaces (Sandbox, Live, Remote)</li>
                <li>Projects within workspaces</li>
                <li>Recordings and recording files</li>
                <li>Profile metadata (but NOT event data)</li>
                <li>Scheduler jobs</li>
                <li>Repository sessions</li>
              </ul>
            </div>
          </div>

          <div class="db-card profile">
            <div class="card-header">
              <i class="bi bi-database-fill"></i>
              <h4>Per-Profile Databases</h4>
              <span class="badge">One Per Profile</span>
            </div>
            <div class="card-body">
              <p class="file-path"><i class="bi bi-folder"></i> <code>profiles/{id}/profile-data.db</code></p>
              <p><strong>Purpose:</strong> Stores all parsed JFR event data for a single profile</p>
              <h5>Contains:</h5>
              <ul>
                <li>JFR Events (CPU samples, allocations, locks, etc.)</li>
                <li>Stacktraces and frames</li>
                <li>Thread information</li>
                <li>Event type metadata</li>
                <li>Cache table for analysis results</li>
              </ul>
            </div>
          </div>
        </div>

        <DocsCallout type="info">
          <strong>Why separate databases?</strong>
          <ul style="margin: 0.5rem 0 0 0; padding-left: 1.25rem;">
            <li><strong>Fast deletion</strong> - Remove a profile by deleting one file instead of running DELETE queries</li>
            <li><strong>No contention</strong> - Profile analysis doesn't block other profiles</li>
            <li><strong>Simple scaling</strong> - Each profile is completely independent</li>
            <li><strong>No complex queries</strong> - No need for profile_id filtering in every query</li>
          </ul>
        </DocsCallout>

        <h2 id="write-once-read-many">Write-Once-Read-Many for Profiles</h2>
        <p>Profile databases follow a strict write-once-read-many (WORM) pattern:</p>

        <div class="worm-diagram">
          <div class="worm-phase write">
            <div class="phase-header">
              <span class="phase-number">1</span>
              <h4>Write Phase</h4>
              <span class="phase-badge">Initialization</span>
            </div>
            <div class="phase-body">
              <ol>
                <li>Parse JFR file</li>
                <li>Create profile database with schema</li>
                <li>Extract and write events using batch inserts</li>
                <li>Store stacktraces, frames, threads</li>
                <li>Run post-processing (caching)</li>
                <li>Mark profile as enabled</li>
              </ol>
              <p class="phase-note"><i class="bi bi-lightning-charge"></i> Uses DuckDB Appender API for efficient bulk inserts (10,000 events per batch)</p>
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
                <li>SQL queries for flamegraph data</li>
                <li>Time series aggregations</li>
                <li>Guardian pattern matching</li>
                <li>Thread and GC analysis</li>
                <li>All other analysis features</li>
              </ul>
              <p class="phase-note"><i class="bi bi-lock"></i> No modifications to event data after initialization</p>
            </div>
          </div>
        </div>

        <h3>Benefits of WORM Pattern</h3>
        <ul>
          <li><strong>Predictable Performance</strong> - No write contention during analysis</li>
          <li><strong>Simplified Caching</strong> - Data never changes, cache is always valid</li>
          <li><strong>Concurrent Access</strong> - Multiple analysis queries can run simultaneously</li>
          <li><strong>Data Integrity</strong> - Original JFR data preserved exactly as parsed</li>
        </ul>

        <h2 id="file-storage">File Storage</h2>
        <p>JFR recordings and artifacts are stored on the filesystem:</p>

        <div class="directory-structure">
          <pre><code>$JEFFREY_HOME/
├── jeffrey.db                    # Platform database
├── profiles/
│   ├── {profile-id-1}/
│   │   └── profile-data.db      # Profile database
│   ├── {profile-id-2}/
│   │   └── profile-data.db
│   └── ...
└── workspaces/
    └── {workspace-id}/
        └── {project-id}/
            └── recordings/
                └── {recording-id}/
                    ├── recording.jfr    # Main JFR file
                    └── artifacts/       # Heap dumps, logs, etc.</code></pre>
        </div>

        <h3>Recording Storage</h3>
        <p>Each recording is stored in its own directory:</p>
        <ul>
          <li><strong>One main recording file</strong> - The JFR file (<code>.jfr</code> or <code>.jfr.lz4</code>)</li>
          <li><strong>Multiple artifacts</strong> - Heap dumps, JVM logs, perf-counters</li>
        </ul>

        <DocsCallout type="tip">
          <strong>Storage efficiency:</strong> JFR files are stored compressed with LZ4 (<code>.jfr.lz4</code>), Heap Dumps with GZIP (<code>.hprof.gz</code>). Jeffrey decompresses them automatically during parsing.
        </DocsCallout>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* Principles Grid */
.principles-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
  margin: 1.5rem 0;
}

.principle-card {
  display: flex;
  gap: 0.75rem;
  padding: 1rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.card-icon {
  width: 40px;
  height: 40px;
  min-width: 40px;
  border-radius: 8px;
  background: #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.card-icon i {
  font-size: 1.1rem;
  color: #5e64ff;
}

.card-content {
  flex: 1;
}

.card-content h4 {
  margin: 0 0 0.25rem 0;
  font-size: 0.9rem;
  font-weight: 600;
  color: #343a40;
}

.card-content p {
  margin: 0;
  font-size: 0.8rem;
  color: #5e6e82;
}

/* Database Diagram */
.database-diagram {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1.5rem;
  margin: 1.5rem 0;
}

.db-card {
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
}

.db-card .card-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
  flex-wrap: wrap;
}

.db-card .card-header i {
  font-size: 1.1rem;
}

.db-card.platform .card-header i { color: #5e64ff; }
.db-card.profile .card-header i { color: #10b981; }

.db-card .card-header h4 {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: #343a40;
  flex: 1;
}

.db-card .badge {
  font-size: 0.65rem;
  padding: 0.15rem 0.5rem;
  border-radius: 4px;
  background: #e2e8f0;
  color: #495057;
}

.db-card .card-body {
  padding: 1rem;
  background: #fff;
}

.file-path {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.8rem;
  color: #5e6e82;
  margin-bottom: 0.75rem !important;
  padding: 0.5rem;
  background: #f8fafc;
  border-radius: 4px;
}

.file-path i {
  color: #6c757d;
}

.db-card .card-body p {
  margin: 0 0 0.75rem 0;
  font-size: 0.85rem;
  color: #5e6e82;
}

.db-card .card-body h5 {
  margin: 0.75rem 0 0.5rem 0;
  font-size: 0.8rem;
  font-weight: 600;
  color: #343a40;
}

.db-card .card-body ul {
  margin: 0;
  padding-left: 1.25rem;
  font-size: 0.8rem;
  color: #5e6e82;
}

.db-card .card-body li {
  margin-bottom: 0.15rem;
}

/* WORM Diagram */
.worm-diagram {
  display: flex;
  align-items: stretch;
  gap: 1rem;
  margin: 1.5rem 0;
}

.worm-phase {
  flex: 1;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
}

.phase-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
  flex-wrap: wrap;
}

.phase-number {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.75rem;
  font-weight: 600;
  color: #fff;
}

.worm-phase.write .phase-number { background: #f59e0b; }
.worm-phase.read .phase-number { background: #10b981; }

.phase-header h4 {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 600;
  color: #343a40;
  flex: 1;
}

.phase-badge {
  font-size: 0.65rem;
  padding: 0.15rem 0.5rem;
  border-radius: 4px;
  background: #e2e8f0;
  color: #495057;
}

.phase-body {
  padding: 1rem;
  background: #fff;
}

.phase-body ol,
.phase-body ul {
  margin: 0;
  padding-left: 1.25rem;
  font-size: 0.8rem;
  color: #5e6e82;
}

.phase-body li {
  margin-bottom: 0.25rem;
}

.phase-note {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.75rem !important;
  padding-top: 0.75rem;
  border-top: 1px solid #e2e8f0;
  font-size: 0.75rem;
  color: #6c757d;
  font-style: italic;
}

.phase-note i {
  font-size: 0.9rem;
}

.worm-arrow {
  display: flex;
  align-items: center;
  color: #6c757d;
  font-size: 1.5rem;
}

/* Directory Structure */
.directory-structure {
  margin: 1.5rem 0;
  background: #1e293b;
  border-radius: 8px;
  overflow: hidden;
}

.directory-structure pre {
  margin: 0;
  padding: 1rem;
  overflow-x: auto;
}

.directory-structure code {
  color: #e2e8f0;
  font-size: 0.8rem;
  line-height: 1.6;
}

@media (max-width: 992px) {
  .database-diagram {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .principles-grid {
    grid-template-columns: 1fr;
  }

  .worm-diagram {
    flex-direction: column;
  }

  .worm-arrow {
    justify-content: center;
    transform: rotate(90deg);
  }
}
</style>
