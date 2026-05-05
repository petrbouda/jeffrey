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
import DocsLinkCard from '@/components/docs/DocsLinkCard.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'introduction', text: 'Introduction', level: 2 },
  { id: 'architecture', text: 'Architecture', level: 2 },
  { id: 'components', text: 'Components', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Jeffrey Server"
      icon="bi bi-cloud"
    />

    <div class="docs-content">
      <h2 id="introduction">Introduction</h2>
      <p><strong>Jeffrey Server</strong> is the headless, multi-workspace counterpart of Jeffrey Microscope. It runs continuously next to your Java fleet — typically on Kubernetes or a dedicated host — captures JFR recordings from running applications, organizes them by workspace and project, and exposes them over a gRPC API.</p>
      <p>Use Jeffrey Server when you want <strong>always-on profiling of production or staging environments</strong>: schedule recording sessions, retain recordings on shared storage, and let Microscope clients connect on demand to analyze them.</p>

      <h2 id="architecture">Architecture</h2>
      <p>Jeffrey Server keeps its domain (workspaces, projects, instances, sessions) in DuckDB and stores JFR recordings on a shared filesystem. The Jeffrey CLI and Jeffrey Agent run alongside your Java application — the CLI prepares the configuration before the JVM starts, the Agent emits recordings the server picks up.</p>

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
                <div class="arch-chip server-feat"><i class="bi bi-bell"></i> Alerts &amp; Messages</div>
              </div>
              <div class="arch-layer">
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

      <h2 id="components">Components</h2>
      <p>Jeffrey Server is the runtime; the CLI, Agent, and JIB module support deployment around it.</p>

      <DocsLinkCard
        title="Jeffrey Server"
        description="The collection server itself: workspaces, projects, recording sessions, scheduler, gRPC public API, deployment."
        to="/docs/server/overview"
        icon="bi bi-cloud"
      />
      <DocsLinkCard
        title="Jeffrey CLI"
        description="Command-line tool that prepares profiler-agent configuration for containerized Java applications before the JVM starts."
        to="/docs/cli/overview"
        icon="bi bi-terminal"
      />
      <DocsLinkCard
        title="Jeffrey Agent"
        description="JVM agent that runs inside your Java application and produces the JFR recordings Jeffrey Server collects."
        to="/docs/agent/overview"
        icon="bi bi-heart-pulse"
      />
      <DocsLinkCard
        title="Jeffrey JIB"
        description="Container image conventions and JIB-based packaging for shipping Jeffrey CLI alongside your application."
        to="/docs/jib/overview"
        icon="bi bi-box-seam"
      />
    </div>
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

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

/* Bottom row centered under server box */
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
