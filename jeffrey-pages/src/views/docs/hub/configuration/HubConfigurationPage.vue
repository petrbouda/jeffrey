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
import { useDocHeadings } from '@/composables/useDocHeadings';
import DocsCallout from '@/components/docs/DocsCallout.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'server', text: 'Server', level: 2 },
  { id: 'core-directories', text: 'Core Directories', level: 2 },
  { id: 'grpc', text: 'gRPC', level: 2 },
  { id: 'logging', text: 'Logging', level: 2 },
  { id: 'job-scheduler', text: 'Job Scheduler', level: 2 },
  { id: 'storage', text: 'Project/Recording Storage', level: 2 },
  { id: 'live-workspace', text: 'Server Collection Mode', level: 2 },
  { id: 'profiler', text: 'Profiler Agent Settings', level: 2 },
  { id: 'database', text: 'Database Persistence', level: 2 },
  { id: 'container', text: 'Container Deployment', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Configuration"
      icon="bi bi-gear"
    />

    <div class="docs-content">
      <h2 id="overview">Overview</h2>
      <p>
        Jeffrey Hub (<code>jeffrey-hub.jar</code>) is configured through a single
        <code>application.properties</code> file. All properties have sensible code defaults,
        so you only need to override what you want to change. Frequently-tuned settings
        (ports, directories, gRPC) come first; advanced tuning (jobs, storage paths, profiler
        defaults, database, container deployment) follows.
      </p>

      <DocsCallout type="info">
        <strong>Optional file.</strong> All properties have sensible code defaults. You only
        need to provide an <code>application.properties</code> file if you want to override
        them.
      </DocsCallout>

      <h2 id="server">Server</h2>
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
            <td><code>server.port</code></td>
            <td><code>8080</code></td>
            <td>HTTP server port for the web interface</td>
          </tr>
        </tbody>
      </table>

      <h2 id="core-directories">Core Directories</h2>
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
            <td><code>jeffrey.hub.home.dir</code></td>
            <td><code>${user.home}/.jeffrey-hub</code></td>
            <td>
              Base directory for all Jeffrey Hub data. Env var: <code>JEFFREY_HUB_HOME_DIR</code>
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.hub.temp.dir</code></td>
            <td><code>${jeffrey.hub.home.dir}/temp</code></td>
            <td>Directory for temporary files</td>
          </tr>
        </tbody>
      </table>

      <h2 id="grpc">gRPC</h2>
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
            <td><code>spring.grpc.server.port</code></td>
            <td><code>9090</code></td>
            <td>gRPC server port for Jeffrey Microscope connections</td>
          </tr>
        </tbody>
      </table>

      <h2 id="logging">Logging</h2>
      <p>Application logging and JFR event monitoring settings.</p>

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
            <td><code>logging.level.cafe.jeffrey.platform</code></td>
            <td><code>DEBUG</code></td>
            <td>Log level for Jeffrey platform classes</td>
          </tr>
          <tr>
            <td><code>jeffrey.logging.http-access.enabled</code></td>
            <td><code>false</code></td>
            <td>Enable HTTP access logging</td>
          </tr>
          <tr>
            <td><code>jeffrey.logging.jfr-events.application.enabled</code></td>
            <td><code>true</code></td>
            <td>Enable internal JFR event logging for HTTP and JDBC latency</td>
          </tr>
          <tr>
            <td><code>jeffrey.logging.jfr-events.application.threshold</code></td>
            <td><code>1s</code></td>
            <td>Only log events taking longer than this threshold</td>
          </tr>
        </tbody>
      </table>

      <h2 id="job-scheduler">Job Scheduler</h2>
      <p>
        Background job configuration. Every job is keyed by the lower-kebab-case form of its job type
        under the <code>jeffrey.hub.scheduler.jobs</code> prefix, with three settings each:
        <code>.enabled</code>, <code>.period</code>, and any number of <code>.params.*</code> entries.
        Built-in defaults live in <code>scheduler-defaults.properties</code>; redeclaring a key in
        <code>application.properties</code> overrides it.
      </p>
      <p>
        Job configuration is resolved once at startup and is not stored in the database — changing a
        period or retention window requires a server restart. The <strong>Scheduler</strong> page in the
        UI is a read-only view of what the server resolved.
      </p>

      <h3>Global settings</h3>

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
            <td><code>jeffrey.hub.scheduler.fan-out-pool-size</code></td>
            <td><code>2</code></td>
            <td>Threads for project/workspace fan-out jobs. Global jobs always get their own dedicated thread.</td>
          </tr>
        </tbody>
      </table>

      <h3>Jobs</h3>
      <p>
        Durations accept <code>s</code>, <code>m</code>, <code>h</code>, and <code>d</code> shorthand.
        Retention windows expressed as <code>duration</code> + <code>time-unit</code> take a number and a
        <code>ChronoUnit</code> name (for example <code>7</code> / <code>Days</code>). Sizes accept binary
        suffixes <code>K</code>, <code>M</code>, <code>G</code>, <code>T</code>, or plain bytes.
      </p>

      <table>
        <thead>
          <tr>
            <th>Job key</th>
            <th>Period</th>
            <th>Params (default)</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>workspace-reconciler</code></td>
            <td><code>5s</code></td>
            <td>—</td>
            <td>
              Scans the workspace directory tree and materializes new projects, instances and
              sessions from the provisioner's marker files. Create-only: removing directories
              from the volume never deletes hub state.
            </td>
          </tr>
          <tr>
            <td><code>temp-directory-cleaner</code></td>
            <td><code>5m</code></td>
            <td><code>retention=30m</code></td>
            <td>Removes scratch entries leaked by crashed operations</td>
          </tr>
          <tr>
            <td><code>deleted-projects-cleaner</code></td>
            <td><code>1h</code></td>
            <td><code>retention=7d</code></td>
            <td>Purges soft-deleted projects. Doubles as the window in which a deleted project can be restored.</td>
          </tr>
          <tr>
            <td><code>storage-overview-refresher</code></td>
            <td><code>5m</code></td>
            <td>—</td>
            <td>
              Recomputes the storage overview shown on the Workspaces dashboard into an in-memory
              cache. The first tick runs at startup; the dashboard serves the cached snapshot and
              offers a manual refresh.
            </td>
          </tr>
          <tr>
            <td><code>profiler-settings-synchronizer</code></td>
            <td><code>5m</code></td>
            <td><code>max-versions=5</code></td>
            <td>Uploads effective profiler settings and prunes old versions</td>
          </tr>
          <tr>
            <td><code>project-instance-session-cleaner</code></td>
            <td><code>1h</code></td>
            <td><code>duration=7</code> / <code>time-unit=Days</code> / <code>max-sessions=10</code></td>
            <td>
              Deletes finished sessions past the retention window, and caps each instance at
              <code>max-sessions</code> logical sessions — a consecutive run of failed (0-byte)
              sessions counts as one, the live session occupies a slot, retained sessions are
              exempt. Oldest logical sessions beyond the cap are deleted even before the
              retention window expires.
            </td>
          </tr>
          <tr>
            <td><code>project-instance-recording-cleaner</code></td>
            <td><code>1h</code></td>
            <td><code>duration=3</code> / <code>time-unit=Days</code></td>
            <td>Trims finished chunks inside the live session so it stays bounded</td>
          </tr>
          <tr>
            <td><code>project-storage-quota-cleaner</code></td>
            <td><code>15m</code></td>
            <td><code>max-size=20G</code></td>
            <td>Caps total disk per project, reclaiming oldest-first</td>
          </tr>
          <tr>
            <td><code>expired-instance-cleaner</code></td>
            <td><code>1h</code></td>
            <td><code>duration=14</code> / <code>time-unit=Days</code></td>
            <td>Deletes EXPIRED instances and abandoned PENDING instances, including their directories on disk</td>
          </tr>
          <tr>
            <td><code>repository-jfr-compression</code></td>
            <td><code>15m</code></td>
            <td>—</td>
            <td>LZ4-compresses finished JFR files and deletes the originals</td>
          </tr>
          <tr>
            <td><code>session-finished-detector</code></td>
            <td><code>30s</code></td>
            <td>—</td>
            <td>Marks sessions finished from heartbeat staleness</td>
          </tr>
        </tbody>
      </table>

      <h2 id="storage">Project/Recording Storage</h2>
      <p>File system storage locations for recordings and repository data.</p>

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
            <td><code>jeffrey.project.recording-storage.path</code></td>
            <td><code>${jeffrey.hub.home.dir}/recordings</code></td>
            <td>Directory for storing JFR recordings</td>
          </tr>
        </tbody>
      </table>

      <h2 id="live-workspace">Server Collection Mode</h2>
      <p>
        Settings for Jeffrey Hub collection mode. Jeffrey Hub is designed for recording
        collection and does not perform profile analysis.
      </p>

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
            <td><code>jeffrey.project.live.collector-only-mode-enabled</code></td>
            <td><code>true</code></td>
            <td>Controls UI visibility of analysis features. When enabled, Profiles and Recordings navigation are hidden, and Merge/Download actions are disabled. Use Remote workspaces in Jeffrey Microscope for analysis.</td>
          </tr>
        </tbody>
      </table>

      <h2 id="profiler">Profiler Agent Settings</h2>
      <p>Global settings for the Jeffrey profiler agent.</p>

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
            <td><code>jeffrey.profiler.global-settings.create-if-not-exists</code></td>
            <td><code>true</code></td>
            <td>Automatically create global profiler settings</td>
          </tr>
          <tr>
            <td><code>jeffrey.profiler.global-settings.command</code></td>
            <td><em>(see below)</em></td>
            <td>Default profiler agent command with placeholders</td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="tip">
        <strong>Default Profiler Command:</strong>
        <code>-agentpath:&lt;&lt;JEFFREY:PROFILER_PATH&gt;&gt;=start,alloc,lock,event=ctimer,jfrsync=default,loop=15m,chunksize=5m,file=&lt;&lt;JEFFREY:CURRENT_SESSION&gt;&gt;/profile-%t.jfr</code>
      </DocsCallout>

      <h2 id="database">Database Persistence</h2>
      <p>DuckDB database connection and pool settings.</p>

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
            <td><code>jeffrey.persistence.database.url</code></td>
            <td><code>jdbc:duckdb:${jeffrey.hub.home.dir}/jeffrey-data.db</code></td>
            <td>DuckDB database connection URL</td>
          </tr>
          <tr>
            <td><code>jeffrey.persistence.database.pool-size</code></td>
            <td><code>25</code></td>
            <td>Database connection pool size</td>
          </tr>
          <tr>
            <td><code>jeffrey.persistence.database.batch-size</code></td>
            <td><code>10000</code></td>
            <td>Batch size for bulk database operations</td>
          </tr>
        </tbody>
      </table>

      <h2 id="container">Container Deployment</h2>
      <p>Settings for running Jeffrey Hub in containers.</p>

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
            <td><code>jeffrey.copy-libs.enabled</code></td>
            <td><code>false</code></td>
            <td>Enable copying libraries from container image</td>
          </tr>
          <tr>
            <td><code>jeffrey.copy-libs.source</code></td>
            <td><code>/jeffrey-libs</code></td>
            <td>Source path for libraries (inside container)</td>
          </tr>
          <tr>
            <td><code>jeffrey.copy-libs.target</code></td>
            <td><code>${jeffrey.home.dir}/libs</code></td>
            <td>Target path for copied libraries</td>
          </tr>
          <tr>
            <td><code>jeffrey.copy-libs.max-kept-versions</code></td>
            <td><code>10</code></td>
            <td>Maximum number of versioned library directories to keep</td>
          </tr>
        </tbody>
      </table>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
