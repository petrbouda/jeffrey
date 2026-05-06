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
  { id: 'role', text: 'Role of the Server', level: 2 },
  { id: 'components', text: 'Components', level: 2 },
  { id: 'grpc-surface', text: 'gRPC Service Surface', level: 2 },
  { id: 'microscope-connection', text: 'How Microscope Connects', level: 2 },
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
        Jeffrey Server is a headless, multi-workspace recording-collection backend. It catalogs
        what's running (workspaces, projects, instances, recording sessions) in an embedded
        DuckDB and stores the JFR recordings on a shared filesystem. Analysis happens in
        Microscope; Server's job is to collect, organize, and serve.
      </p>

      <h2 id="role">Role of the Server</h2>
      <p>
        Server runs as a long-lived process next to your Java fleet. Async-Profiler agents
        running inside your applications stream JFR chunks to it on every recording loop. The
        agent gets its configuration (event toggles, sampling intervals, output paths) from
        Server's profiler-settings hub, so a single change at the workspace or project scope
        propagates to every agent in that scope.
      </p>

      <DocsCallout type="info">
        <strong>No analysis on Server.</strong> Flame graphs, Guardian checks, heap dump
        forensics, and the AI assistant all live in Microscope. Server only collects and serves
        — that keeps expensive profile processing off your production infrastructure and on
        developer machines.
      </DocsCallout>

      <h2 id="components">Components</h2>
      <p>
        Server's domain breaks into four layers: domain entities, collection features, gRPC
        services, and storage.
      </p>

      <div class="arch-diagram">
        <div class="arch-apps server-only">
          <div class="arch-app server">
            <div class="arch-app-header server-header">
              <i class="bi bi-cloud"></i>
              <span>Jeffrey Server</span>
              <small>Kubernetes / Server</small>
            </div>
            <div class="arch-app-body">
              <div class="arch-section-label">Domain</div>
              <div class="arch-layer">
                <div class="arch-chip server-feat"><i class="bi bi-folder2-open"></i> Workspaces</div>
                <div class="arch-chip server-feat"><i class="bi bi-kanban"></i> Projects</div>
              </div>
              <div class="arch-layer">
                <div class="arch-chip server-feat"><i class="bi bi-server"></i> Instances</div>
                <div class="arch-chip server-feat"><i class="bi bi-record-circle"></i> Sessions</div>
              </div>
              <div class="arch-section-label">Features</div>
              <div class="arch-layer">
                <div class="arch-chip streaming"><i class="bi bi-broadcast"></i> JFR Streaming</div>
                <div class="arch-chip scheduler"><i class="bi bi-clock"></i> Scheduler &amp; Jobs</div>
              </div>
              <div class="arch-layer">
                <div class="arch-chip server-feat"><i class="bi bi-sliders"></i> Profiler Settings</div>
                <div class="arch-chip server-feat"><i class="bi bi-lock"></i> Blocking &amp; Lifecycle</div>
              </div>
              <div class="arch-section-label">Storage</div>
              <div class="arch-storage-row">
                <div class="arch-storage"><i class="bi bi-database"></i> DuckDB</div>
                <div class="arch-storage"><i class="bi bi-folder"></i> Filesystem</div>
              </div>
            </div>
          </div>
        </div>

        <div class="arch-bottom-server-col">
          <div class="arch-reads-arrow">
            <small>reads</small>
            <i class="bi bi-arrow-down"></i>
          </div>
          <div class="arch-shared-fs">
            <i class="bi bi-hdd-stack"></i>
            <span>Shared Filesystem</span>
            <small>NFS / PVC</small>
          </div>
          <div class="arch-write-labels-row">
            <div class="arch-write-label cli-write">
              <i class="bi bi-arrow-up"></i>
              <small>instances &amp; sessions</small>
            </div>
            <div class="arch-labels-spacer"></div>
            <div class="arch-write-label jfr-write">
              <i class="bi bi-arrow-up"></i>
              <small>JFR recordings</small>
            </div>
          </div>
          <div class="arch-containers-stack">
            <div class="arch-cli-app-container shadow-copy"></div>
            <div class="arch-cli-app-container primary">
              <div class="arch-container-note">Single Container</div>
              <div class="arch-cli-app-row">
                <div class="arch-cli">
                  <i class="bi bi-terminal"></i>
                  <span>Jeffrey CLI</span>
                </div>
                <div class="arch-starts-arrow">
                  <span>runs before</span>
                  <i class="bi bi-arrow-right"></i>
                </div>
                <div class="arch-java-app">
                  <i class="bi bi-cpu"></i>
                  <span>Java Application</span>
                  <div class="arch-agent-badge">
                    <i class="bi bi-heart-pulse"></i>
                    <span>Jeffrey Agent</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <p>
        Around the Server, the <router-link to="/docs/cli/overview">Jeffrey CLI</router-link>
        prepares the Async-Profiler agent's JVM arguments before the application starts, and the
        <router-link to="/docs/agent/overview">Jeffrey Agent</router-link> writes JFR recordings
        to the shared filesystem the Server reads from. The
        <router-link to="/docs/jib/overview">Jeffrey JIB</router-link> module packages the CLI
        and Agent alongside your application image.
      </p>

      <h2 id="grpc-surface">gRPC Service Surface</h2>
      <p>
        Microscope clients and other server-side tools talk to Server over gRPC. Eight services
        cover the surface:
      </p>

      <div class="service-chips">
        <div class="service-chip"><i class="bi bi-collection"></i> Workspaces</div>
        <div class="service-chip"><i class="bi bi-folder"></i> Projects</div>
        <div class="service-chip"><i class="bi bi-hdd-network"></i> Instances</div>
        <div class="service-chip"><i class="bi bi-cloud-download"></i> Recording Download</div>
        <div class="service-chip"><i class="bi bi-archive"></i> Repository</div>
        <div class="service-chip"><i class="bi bi-broadcast"></i> Event Streaming</div>
        <div class="service-chip"><i class="bi bi-list-ul"></i> Workspace Events</div>
        <div class="service-chip"><i class="bi bi-sliders"></i> Profiler Settings</div>
      </div>

      <p>
        Recordings stream on demand from the Server when a client opens them, while live JFR
        events flow over a separate streaming channel for real-time dashboards and incident
        investigations. See the
        <router-link to="/docs/server/grpc-api">gRPC API reference</router-link> for the full
        service surface.
      </p>

      <h2 id="microscope-connection">How Microscope Connects</h2>
      <p>
        Microscope adds a Server with a single gRPC endpoint and an optional auth token, and
        Microscope handles the rest. From the user's point of view, the connected Server looks
        identical to a local one — same workspaces, projects, instances, sessions — except
        recordings are pulled on demand from the Server when the user opens them. See the
        <router-link to="/docs/microscope/workspaces">Microscope Workspaces</router-link> page
        for the client-side flow.
      </p>

      <h2 id="storage">Storage</h2>
      <p>
        Server stores its catalog (workspaces, projects, instances, sessions, scheduler jobs)
        in a single embedded DuckDB file, and stores the JFR recordings themselves on a shared
        filesystem laid out by workspace and project. There is no managed database, no object
        store, and no per-profile databases. See the
        <router-link to="/docs/server/storage">Storage</router-link> page for the directory
        layout and design philosophy.
      </p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* ===== gRPC service chips ===== */
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

/* ===== ARCHITECTURE DIAGRAM (server-only variant) ===== */
.arch-diagram {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
  margin: 1.5rem 0;
  padding: 2rem 1.5rem;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  border-radius: 12px;
  border: 1px solid #e2e8f0;
}

.arch-apps {
  display: flex;
  align-items: stretch;
  gap: 0;
  width: 100%;
}

.arch-apps.server-only {
  justify-content: center;
}

.arch-apps.server-only .arch-app {
  flex: 0 1 520px;
  max-width: 560px;
}

.arch-app {
  flex: 1;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  background: #fff;
}

.arch-app-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.6rem 1rem;
  color: #fff;
  font-weight: 600;
  font-size: 0.85rem;
}

.arch-app-header small {
  margin-left: auto;
  font-weight: 400;
  font-size: 0.7rem;
  opacity: 0.85;
}

.server-header { background: linear-gradient(135deg, #8b5cf6 0%, #6d28d9 100%); }

.arch-app-body {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 0.75rem;
}

.arch-layer {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
}

.arch-chip {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.3rem 0.6rem;
  border-radius: 5px;
  font-size: 0.7rem;
  font-weight: 500;
  flex: 1;
  min-width: 0;
  white-space: nowrap;
}

.arch-chip i { font-size: 0.75rem; }

.arch-chip.streaming { background: #fce7f3; color: #9d174d; }
.arch-chip.scheduler { background: #e0e7ff; color: #3730a3; }
.arch-chip.server-feat { background: #f3f4f6; color: #374151; }

.arch-section-label {
  font-size: 0.55rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: #94a3b8;
  margin-top: 0.15rem;
}

.arch-storage-row {
  display: flex;
  gap: 0.4rem;
}

.arch-storage {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.3rem;
  padding: 0.3rem 0.5rem;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 5px;
  font-size: 0.7rem;
  font-weight: 500;
  color: #92400e;
}

.arch-bottom-server-col {
  width: 100%;
  max-width: 560px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.4rem;
}

.arch-reads-arrow {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.1rem;
  color: #7c3aed;
}

.arch-reads-arrow i { font-size: 0.9rem; }
.arch-reads-arrow small {
  font-size: 0.6rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.arch-shared-fs {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1.25rem;
  background: #fff;
  border: 2px solid #f59e0b;
  border-radius: 8px;
  width: 100%;
  justify-content: center;
}

.arch-shared-fs i { color: #f59e0b; font-size: 1.1rem; }
.arch-shared-fs span { font-size: 0.8rem; font-weight: 600; color: #92400e; }
.arch-shared-fs small { font-size: 0.65rem; color: #6c757d; margin-left: 0.25rem; }

.arch-write-labels-row {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 0.5rem;
  margin-top: 0.25rem;
  padding: 0 1rem;
  padding-right: calc(1rem + 8px);
}

.arch-labels-spacer { min-width: 70px; }

.arch-containers-stack {
  position: relative;
  margin-top: 0.25rem;
  padding-right: 8px;
  padding-bottom: 8px;
}

.arch-cli-app-container {
  padding: 0.5rem 1rem 0.6rem;
  border: 1px dashed #cbd5e1;
  border-radius: 10px;
  background: #fff;
}

.arch-cli-app-container.shadow-copy {
  position: absolute;
  top: 8px;
  left: 8px;
  right: 0;
  bottom: 0;
  padding: 0;
  background: #f8fafc;
}

.arch-cli-app-container.primary { position: relative; }

.arch-container-note {
  font-size: 0.6rem;
  color: #94a3b8;
  text-align: center;
  font-style: italic;
  margin-bottom: 0.35rem;
}

.arch-write-label {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.1rem;
}

.arch-write-label i { font-size: 0.9rem; }

.arch-write-label small {
  font-size: 0.6rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.arch-write-label.cli-write i,
.arch-write-label.cli-write small { color: #059669; }
.arch-write-label.jfr-write i,
.arch-write-label.jfr-write small { color: #ef4444; }

.arch-cli-app-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
}

.arch-cli {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.2rem;
  padding: 0.4rem 0.6rem;
  background: #ecfdf5;
  border: 1.5px solid #059669;
  border-radius: 8px;
}

.arch-cli i { color: #059669; font-size: 1rem; }
.arch-cli span { font-size: 0.65rem; font-weight: 600; color: #065f46; }

.arch-starts-arrow {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  color: #94a3b8;
}

.arch-starts-arrow span { font-size: 0.6rem; font-style: italic; }
.arch-starts-arrow i { font-size: 0.85rem; }

.arch-java-app {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.2rem;
  padding: 0.4rem 0.6rem;
  background: #fef2f2;
  border: 1.5px solid #ef4444;
  border-radius: 8px;
}

.arch-java-app i { color: #ef4444; font-size: 1rem; }
.arch-java-app span { font-size: 0.65rem; font-weight: 600; color: #991b1b; }

.arch-agent-badge {
  display: flex;
  align-items: center;
  gap: 0.2rem;
  margin-top: 0.25rem;
  padding: 0.15rem 0.4rem;
  background: #fef3c7;
  border: 1px solid #fbbf24;
  border-radius: 4px;
}

.arch-agent-badge i { color: #d97706; font-size: 0.65rem; }
.arch-agent-badge span { font-size: 0.55rem; font-weight: 600; color: #92400e; }
</style>
