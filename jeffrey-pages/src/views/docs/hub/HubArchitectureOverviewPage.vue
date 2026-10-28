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
  { id: 'role', text: 'Role of the Hub', level: 2 },
  { id: 'components', text: 'Components', level: 2 },
  { id: 'scheduler-jobs', text: 'Scheduler Jobs', level: 2 },
  { id: 'web-ui', text: 'Web UI', level: 2 },
  { id: 'microscope', text: 'Microscope and the Hub', level: 2 },
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
        Jeffrey Hub is the maintenance service of the shared recording volume. It has no
        database and no API for Microscope: what's running (workspaces, projects, instances,
        recording sessions) and the JFR recordings themselves live on a shared filesystem, and
        the hub's job is to keep that filesystem within its limits by running scheduler jobs on
        it. Analysis happens in Microscope, which reads the same volume itself.
      </p>

      <h2 id="role">Role of the Hub</h2>
      <p>
        Hub runs as a long-lived process next to the shared volume. Async-Profiler running
        inside your applications writes JFR chunks to the volume on every recording loop; the
        hub's scheduler jobs walk the volume — the project, instance and session directories the
        provisioner created, whose names are their ids and whose UUIDv7 session names order each
        instance's sessions — to drop what
        retention no longer allows and to compress every recording but the newest one of each
        instance's newest session. The profiler gets its configuration
        (event toggles, sampling intervals, output paths) from the provisioner that started the
        JVM — its <code>profiler-command</code> setting or the
        <code>JEFFREY_PROFILER_COMMAND</code> environment variable.
      </p>

      <DocsCallout type="info">
        <strong>Full profile analysis stays in Microscope.</strong> Flame graphs and heap dump
        forensics and the MCP endpoint run in Microscope. Hub never reads a recording's
        contents and serves no files — <router-link to="/docs/microscope-mcp/tools#remote">Microscope’s <code>remote_</code> tools</router-link>
        pull them straight off the volume for analysis.
      </DocsCallout>

      <h2 id="components">Components</h2>
      <p>
        The hub's domain breaks into three layers: domain entities, the scheduler with its jobs,
        and storage.
      </p>

      <DocsArchDiagram variant="hub" />

      <p>
        Around the hub, the <router-link to="/docs/provisioner/overview">Jeffrey Provisioner</router-link>
        prepares the Async-Profiler agent's JVM arguments before the application starts, and the
        profiled application writes JFR recordings to the shared filesystem the hub maintains. The
        <router-link to="/docs/jib">Jeffrey JIB</router-link> module packages the provisioner
        and async-profiler into your application image.
      </p>

      <h2 id="scheduler-jobs">Scheduler Jobs</h2>
      <p>
        Two jobs run per project on a schedule, each configured under
        <router-link to="/docs/hub/configuration"><code>jeffrey.hub.scheduler.jobs.*</code></router-link>:
      </p>

      <div class="service-chips">
        <div class="service-chip"><i class="bi bi-trash3"></i> Project Retention</div>
        <div class="service-chip"><i class="bi bi-file-zip"></i> Repository Compression</div>
      </div>

      <ul>
        <li>
          <strong>Project retention</strong> (<code>ProjectRetentionJob</code>) drops sessions by
          age and by a per-project session cap, trims each instance's newest session, enforces the storage quota,
          and cleans up stale instances that never got a session.
        </li>
        <li>
          <strong>Repository compression</strong> (<code>RepositoryCompressionProjectJob</code>)
          LZ4-compresses JFR chunks once they are finished. Compression happens only here, never
          on a read path.
        </li>
      </ul>

      <h2 id="web-ui">Web UI</h2>
      <p>
        The hub keeps a small web UI with two pages:
      </p>
      <ul>
        <li>
          <strong>Storage</strong> — what the last retention pass measured per workspace and
          project: size, files and last activity
          (<code>GET /api/internal/storage</code>).
        </li>
        <li>
          <strong>Scheduler</strong> — lists the jobs (<code>GET /api/internal/scheduler/jobs</code>)
          and runs one now (<code>POST /api/internal/scheduler/jobs/{jobType}/run</code>).
        </li>
      </ul>

      <h2 id="microscope">Microscope and the Hub</h2>
      <p>
        Microscope never connects to a hub. Its one <strong>remote</strong> is the recording volume
        on an Azure file share, declared in its configuration with
        <router-link to="/docs/microscope/configuration/application-properties#remote"><code>jeffrey.microscope.remote.azure-files.*</code></router-link>.
        Microscope lists workspaces, projects, instances and sessions straight off the share, pulls
        recordings on demand, and makes the user's changes there unless the share is declared
        read-only. Retention, cleanup and compression stay with the hub's scheduled jobs on that
        same volume — keep a Jeffrey Hub running on it, or the share only grows. See the
        <router-link to="/docs/microscope/workspaces">Microscope Workspaces</router-link> page
        for the client-side flow.
      </p>

      <h2 id="storage">Storage</h2>
      <p>
        The shared volume is the hub's only state: workspaces, projects, instances and sessions are
        its directories (the only marker is a workspace's <code>.workspace-info.json</code>), and the JFR recordings sit inside them, laid out by
        workspace and project. There is no database, no cache, no object store, and no
        per-profile databases. See the
        <router-link to="/docs/hub/storage">Storage</router-link> page for the directory
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
