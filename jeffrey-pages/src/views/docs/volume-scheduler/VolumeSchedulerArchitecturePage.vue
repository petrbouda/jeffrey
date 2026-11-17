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
import DocsArchDiagram from '@/components/docs/DocsArchDiagram.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'role', text: 'Role of the Volume Scheduler', level: 2 },
  { id: 'components', text: 'Components', level: 2 },
  { id: 'jobs', text: 'Jobs', level: 2 },
  { id: 'rest-api', text: 'REST API', level: 2 },
  { id: 'microscope', text: 'Microscope and the Volume Scheduler', level: 2 },
  { id: 'storage', text: 'Storage', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Architecture"
      icon="bi bi-diagram-3"
    />

    <div class="docs-content">
      <p>
        The Volume Scheduler is a small maintenance service for the shared recording volume. It has
        no database and no API for Microscope: what's running (workspaces, projects, instances,
        recording sessions) and the JFR recordings themselves live on a shared filesystem, and the
        volume scheduler's job is to keep that filesystem within its limits by running two scheduled
        jobs on it. Analysis happens in Microscope, which reads the same volume itself.
      </p>

      <h2 id="role">Role of the Volume Scheduler</h2>
      <p>
        The volume scheduler runs as a long-lived process next to the shared volume, mounted at the
        directory <router-link to="/docs/volume-scheduler/configuration#volume"><code>jeffrey.volume-scheduler.volume.path</code></router-link>
        names. Async-Profiler running inside your applications writes JFR chunks to the volume on
        every recording loop; the jobs walk the volume — the project, instance and session
        directories the Provisioner created, whose names are their ids and whose UUIDv7 session
        names order each instance's sessions — to drop what retention no longer allows and to
        compress every recording but the newest one of each instance's newest session. The profiler
        gets its configuration (event toggles, sampling intervals, output paths) from the Provisioner
        that started the JVM — its <code>profiler-command</code> setting or the
        <code>JEFFREY_PROFILER_COMMAND</code> environment variable — never from the volume scheduler.
      </p>

      <DocsCallout type="info">
        <strong>Full profile analysis stays in Microscope.</strong> Flame graphs, heap dump
        forensics and the MCP endpoint run in Microscope. The volume scheduler never reads a
        recording's contents and serves no files — <router-link to="/docs/microscope-mcp/tools#connector">Microscope’s <code>connector_</code> tools</router-link>
        pull them straight off the volume for analysis.
      </DocsCallout>

      <h2 id="components">Components</h2>
      <p>
        The service is one Spring Boot application, built as a GraalVM native binary. Its parts:
      </p>
      <DocsArchDiagram variant="volume-scheduler" />
      <ul>
        <li>
          <strong>Configuration</strong> — <code>VolumeSchedulerProperties</code> binds
          <code>jeffrey.volume-scheduler.*</code> into typed records and fails at startup on a missing
          volume path or a non-positive value. Its retention block is mapped into a
          <code>RetentionPolicy</code>, the domain record the retention rules read, so no binding type
          reaches the jobs.
        </li>
        <li>
          <strong>Volume access</strong> — <code>VolumeProjects</code> lists every project on the volume;
          a <code>VolumeProject</code> lists its sessions (newest first, files included) and instances,
          and deletes a session, a session's files or an instance.
        </li>
        <li>
          <strong>Scheduler</strong> — one <code>JobScheduler</code> runs each enabled job on a fixed
          delay and runs any job by hand. Each job has one lock, shared by its scheduled and its
          manual runs, so the same job never runs twice at once; the two jobs do run in parallel.
          A failing run is logged and the next one still comes.
        </li>
        <li>
          <strong>Retention</strong> — a pass per project runs the retention rules in order over one
          listing of the project, and measures the project's storage from the same listing.
        </li>
        <li>
          <strong>Compression</strong> — <code>RecordingCompressor</code> compresses one session's
          finished recordings.
        </li>
        <li>
          <strong>Storage overview</strong> — <code>LatestStorageOverview</code> holds the figures the
          last retention pass measured, which <code>GET /api/v1/storage</code> returns.
        </li>
      </ul>
      <p>
        Around it, the <router-link to="/docs/provisioner">Jeffrey Provisioner</router-link>
        prepares the Async-Profiler agent's JVM arguments before the application starts, and the
        profiled application writes JFR recordings to the shared filesystem the volume scheduler
        maintains. The <router-link to="/docs/jib">Jeffrey JIB</router-link> module packages the
        Provisioner and async-profiler into your application image.
      </p>

      <h2 id="jobs">Jobs</h2>
      <p>
        Two jobs run on the whole volume, project by project, each configured under
        <router-link to="/docs/volume-scheduler/configuration"><code>jeffrey.volume-scheduler.*</code></router-link>.
        A failure in one project is logged and the job moves on to the next.
      </p>
      <div class="service-chips">
        <div class="service-chip"><i class="bi bi-trash3"></i> retention</div>
        <div class="service-chip"><i class="bi bi-file-zip"></i> compression</div>
      </div>
      <ul>
        <li>
          <strong>Retention</strong> (<code>retention</code>, every 5 minutes by default) applies its
          rules in this order: drop sessions older than the session retention (a session is aged by
          its newest recording's start), keep at most the configured number of sessions per instance,
          trim the newest session's files older than the newest-session file retention (never the
          recording still being written), keep the project within its disk budget by freeing the
          oldest sessions first, and remove stale instances — created longer ago than the instance
          retention and holding no session.
        </li>
        <li>
          <strong>Compression</strong> (<code>compression</code>, every minute by default)
          LZ4-compresses the finished <code>.jfr</code> chunks in each instance's two newest sessions,
          never the newest recording of the newest session. Each chunk is written to a scratch file
          and renamed over its final <code>.lz4</code> name atomically. Compression happens only here,
          never on a read path. A <code>.jfr</code> newer than its same-named <code>.lz4</code> archive
          is a name collision, not a leftover of a finished compression, so the job keeps it and logs
          a warning.
        </li>
      </ul>

      <h2 id="rest-api">REST API</h2>
      <p>
        The volume scheduler has no web UI. On HTTP <code>8080</code> it serves a small JSON
        <router-link to="/docs/volume-scheduler/api">REST API</router-link> under <code>/api/v1</code>,
        for operators and scripts — Microscope never calls it:
      </p>
      <ul>
        <li>
          <strong>Storage</strong> — what the last retention pass measured per workspace and
          project: size, files and last activity
          (<code>GET /api/v1/storage</code>).
        </li>
        <li>
          <strong>Jobs</strong> — lists both jobs with their schedule and settings
          (<code>GET /api/v1/jobs</code>), and runs either one now
          (<code>POST /api/v1/jobs/{job}/run</code>).
        </li>
      </ul>

      <h2 id="microscope">Microscope and the Volume Scheduler</h2>
      <p>
        Microscope never connects to the volume scheduler. Its one <strong>connector</strong> is the
        recording volume — on an Azure file share
        (<router-link to="/docs/connectors/azure-files#properties"><code>jeffrey.microscope.connector.azure-files.*</code></router-link>)
        or in a local directory
        (<router-link to="/docs/connectors/local"><code>jeffrey.microscope.connector.local.*</code></router-link>),
        declared in its configuration. Microscope lists workspaces, projects, instances and sessions
        straight off the volume, pulls recordings on demand, and makes the user's changes there unless
        the connector is declared read-only. Retention, cleanup and compression stay with the volume
        scheduler's jobs on that same volume — keep one running on it, or the volume only grows. See
        the <router-link to="/docs/connectors">Connectors</router-link> docs for the client-side flow.
      </p>

      <h2 id="storage">Storage</h2>
      <p>
        The shared volume is the volume scheduler's only state: workspaces, projects, instances and
        sessions are its directories (no marker files: a workspace is a root folder named by its
        reference id), and the JFR recordings sit inside them, laid out by workspace and project.
        There is no database, no object store, and no per-profile databases. See the
        <router-link to="/docs/volume-scheduler/storage">Storage</router-link> page for the directory
        layout and design philosophy.
      </p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* ===== Job chips ===== */
.service-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  margin: 1rem 0 1.25rem;
}

.service-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.4rem 0.7rem;
  font-size: 0.78rem;
  font-weight: 500;
  color: #4338ca;
  background: #ede9fe;
  border: 1px solid #c4b5fd;
  border-radius: 6px;
}

.service-chip i { font-size: 0.85rem; }

</style>
