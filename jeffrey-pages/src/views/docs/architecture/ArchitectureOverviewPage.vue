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
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'high-level-architecture', text: 'High-Level Architecture', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Architecture Overview"
        icon="bi bi-diagram-3"
      />

      <div class="docs-content">
        <p>Jeffrey consists of two applications: <strong>Jeffrey Local</strong> (an analysis tool for visualizing and exploring JFR profiles) and <strong>Jeffrey Server</strong> (a recording collection service that manages workspaces, sessions, and live recordings). Jeffrey Local connects to Jeffrey Server via gRPC to access remote workspaces, and also offers <strong>Recordings</strong> for analyzing JFR files directly without a server connection.</p>

        <h2 id="high-level-architecture">High-Level Architecture</h2>
        <p>Jeffrey runs as two separate applications. Jeffrey Server collects and stores recordings from Java applications, while Jeffrey Local provides the analysis UI and connects to the server via gRPC:</p>

        <div class="arch-diagram">
          <!-- Two apps side by side -->
          <div class="arch-apps">
            <!-- Jeffrey Local -->
            <div class="arch-app local">
              <div class="arch-app-header local-header">
                <i class="bi bi-pc-display"></i>
                <span>Jeffrey Local</span>
                <small>Developer Machine</small>
              </div>
              <div class="arch-app-body">
                <div class="arch-section-label">Domain</div>
                <div class="arch-layer">
                  <div class="arch-chip server-feat"><i class="bi bi-file-earmark-binary"></i> Recordings</div>
                  <div class="arch-chip server-feat"><i class="bi bi-speedometer2"></i> Profiles</div>
                </div>
                <div class="arch-section-label">Features</div>
                <div class="arch-layer">
                  <div class="arch-chip analysis"><i class="bi bi-fire"></i> Flamegraph</div>
                  <div class="arch-chip analysis"><i class="bi bi-graph-up"></i> Timeseries</div>
                  <div class="arch-chip analysis"><i class="bi bi-stopwatch"></i> Sub-Second</div>
                </div>
                <div class="arch-layer">
                  <div class="arch-chip analysis"><i class="bi bi-shield-check"></i> Guardian</div>
                  <div class="arch-chip analysis"><i class="bi bi-clock-history"></i> Threads</div>
                  <div class="arch-chip analysis"><i class="bi bi-database"></i> Heap Dump</div>
                </div>
                <div class="arch-layer">
                  <div class="arch-chip analysis"><i class="bi bi-globe"></i> HTTP & JDBC</div>
                  <div class="arch-chip ai"><i class="bi bi-robot"></i> AI Analysis</div>
                  <div class="arch-chip entry"><i class="bi bi-record-circle"></i> Recordings</div>
                </div>
                <div class="arch-section-label">Storage</div>
                <div class="arch-storage-row">
                  <div class="arch-storage"><i class="bi bi-database"></i> DuckDB</div>
                  <div class="arch-storage"><i class="bi bi-folder"></i> Filesystem</div>
                </div>
              </div>
            </div>

            <!-- gRPC connection -->
            <div class="arch-grpc-link">
              <div class="grpc-line"></div>
              <div class="grpc-label">
                <i class="bi bi-arrow-left-right"></i>
                <span>gRPC</span>
              </div>
              <div class="grpc-line"></div>
            </div>

            <!-- Jeffrey Server -->
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
                  <div class="arch-chip scheduler"><i class="bi bi-clock"></i> Scheduler & Jobs</div>
                </div>
                <div class="arch-layer">
                  <div class="arch-chip server-feat"><i class="bi bi-sliders"></i> Profiler Settings</div>
                  <div class="arch-chip server-feat"><i class="bi bi-bell"></i> Alerts & Messages</div>
                </div>
                <div class="arch-layer">
                  <div class="arch-chip server-feat"><i class="bi bi-lock"></i> Blocking & Lifecycle</div>
                </div>
                <div class="arch-section-label">Storage</div>
                <div class="arch-storage-row">
                  <div class="arch-storage"><i class="bi bi-database"></i> DuckDB</div>
                  <div class="arch-storage"><i class="bi bi-folder"></i> Filesystem</div>
                </div>
              </div>
            </div>
          </div>

          <!-- Bottom: Shared Filesystem + Java Apps aligned under Server -->
          <div class="arch-bottom-row">
            <!-- Empty left spacer to align with Jeffrey Local -->
            <div class="arch-bottom-spacer"></div>
            <!-- Connector spacer for gRPC column -->
            <div class="arch-bottom-connector-spacer"></div>
            <!-- Right side: aligned under Jeffrey Server -->
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
                  <small>instances & sessions</small>
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
        </div>

      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* ===== HIGH-LEVEL ARCHITECTURE DIAGRAM ===== */
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

.arch-top {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
}

.arch-client {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1.25rem;
  background: #fff;
  border: 2px solid #10b981;
  border-radius: 24px;
  font-size: 0.85rem;
  font-weight: 600;
  color: #343a40;
}

.arch-client i { color: #10b981; font-size: 1.1rem; }

.arch-arrow-down, .arch-arrow-up {
  color: #94a3b8;
  font-size: 1.1rem;
}

/* Apps row */
.arch-apps {
  display: flex;
  align-items: stretch;
  gap: 0;
  width: 100%;
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

.local-header { background: linear-gradient(135deg, #5e64ff 0%, #4338ca 100%); }
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

.arch-chip.vue { background: #d1fae5; color: #065f46; }
.arch-chip.rest { background: #dbeafe; color: #1e40af; }
.arch-chip.grpc-c { background: #ede9fe; color: #6d28d9; }
.arch-chip.grpc-s { background: #ede9fe; color: #6d28d9; }
.arch-chip.analysis { background: #fef3c7; color: #92400e; }
.arch-chip.ai { background: #cffafe; color: #155e75; }
.arch-chip.streaming { background: #fce7f3; color: #9d174d; }
.arch-chip.scheduler { background: #e0e7ff; color: #3730a3; }
.arch-chip.server-feat { background: #f3f4f6; color: #374151; }
.arch-chip.entry { background: #ecfdf5; color: #065f46; }

.arch-section-label {
  font-size: 0.55rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: #94a3b8;
  margin-top: 0.15rem;
}

/* Pipeline inside app box */
.arch-pipeline {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.4rem 0.5rem;
  background: #f8fafc;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
  flex-wrap: wrap;
}

.pipe-item {
  padding: 0.2rem 0.45rem;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 0.6rem;
  font-weight: 500;
  color: #374151;
}

.pipe-item.pipe-highlight {
  background: linear-gradient(135deg, #5e64ff 0%, #4338ca 100%);
  color: #fff;
  border-color: transparent;
}

.pipe-arrow {
  font-size: 0.5rem;
  color: #9ca3af;
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

/* gRPC link between apps */
.arch-grpc-link {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0 0.5rem;
  min-width: 80px;
}

.grpc-line {
  width: 2px;
  height: 24px;
  background: linear-gradient(180deg, #8b5cf6 0%, #a78bfa 100%);
}

.grpc-label {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.15rem;
  padding: 0.3rem 0.6rem;
  background: #ede9fe;
  border-radius: 6px;
  border: 1px solid #c4b5fd;
}

.grpc-label i { color: #7c3aed; font-size: 0.9rem; }
.grpc-label span { font-size: 0.6rem; color: #6d28d9; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; }

/* Bottom: Shared Filesystem + Java Apps aligned under Server */
.arch-bottom-row {
  display: flex;
  width: 100%;
  gap: 0;
}

.arch-bottom-spacer {
  flex: 1;
}

.arch-bottom-connector-spacer {
  min-width: 80px;
  padding: 0 0.5rem;
}

.arch-bottom-server-col {
  flex: 1;
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

.arch-arrow-vertical {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.1rem;
  color: #94a3b8;
}

.arch-arrow-vertical small {
  font-size: 0.6rem;
  color: #94a3b8;
  font-style: italic;
}

.arch-write-labels-row {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 0.5rem;
  margin-top: 0.25rem;
  /* match container inner padding + shadow offset */
  padding: 0 1rem;
  padding-right: calc(1rem + 8px);
}

.arch-labels-spacer {
  /* matches the runs-before arrow width */
  min-width: 70px;
}

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

.arch-cli-app-container.primary {
  position: relative;
}

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

.arch-write-label.cli-write i { color: #059669; }
.arch-write-label.cli-write small { color: #059669; }
.arch-write-label.jfr-write i { color: #ef4444; }
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

.arch-starts-arrow span {
  font-size: 0.6rem;
  font-style: italic;
}

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

/* Responsive */
@media (max-width: 768px) {
  .arch-apps {
    flex-direction: column;
    gap: 0.75rem;
  }

  .arch-grpc-link {
    flex-direction: row;
    padding: 0.5rem 0;
    min-width: auto;
  }

  .grpc-line {
    width: 24px;
    height: 2px;
  }

  .arch-bottom-row {
    flex-direction: column;
  }

  .arch-bottom-spacer,
  .arch-bottom-connector-spacer {
    display: none;
  }
}
</style>
