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
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'architecture', text: 'Architecture', level: 2 },
  { id: 'server-connection', text: 'Connection to Jeffrey Server', level: 3 },
  { id: 'recording-pipeline', text: 'Recording → Profile Pipeline', level: 3 },
  { id: 'ai-integration', text: 'AI Integration', level: 3 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Jeffrey Microscope"
      icon="bi bi-pc-display"
    />

    <div class="docs-content">
      <h2 id="architecture">Architecture</h2>
      <p>Microscope analyzes recordings in two ways: it can load a JFR file directly, or connect to a Jeffrey Server over gRPC to read recordings from remote workspaces. From Microscope's perspective, the server is an external dependency — its internals are documented separately.</p>

      <div class="arch-diagram">
        <div class="arch-apps">
          <div class="arch-app microscope">
            <div class="arch-app-header microscope-header">
              <i class="bi bi-pc-display"></i>
              <span>Jeffrey Microscope</span>
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
                <div class="arch-chip analysis"><i class="bi bi-globe"></i> HTTP &amp; JDBC</div>
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

          <div class="arch-grpc-link">
            <div class="grpc-line"></div>
            <div class="grpc-label">
              <i class="bi bi-arrow-left-right"></i>
              <span>gRPC</span>
            </div>
            <div class="grpc-line"></div>
          </div>

          <div class="arch-app server blackbox">
            <div class="arch-app-header server-header">
              <i class="bi bi-cloud"></i>
              <span>Jeffrey Server</span>
              <small>Remote</small>
            </div>
            <div class="arch-blackbox-body">
              <i class="bi bi-box"></i>
              <p>External service.</p>
              <router-link to="/docs/server" class="arch-blackbox-link">
                See Jeffrey Server <i class="bi bi-arrow-right"></i>
              </router-link>
            </div>
          </div>
        </div>
      </div>

      <h3 id="server-connection">Connection to Jeffrey Server</h3>
      <p>When a workspace is bound to a remote, Microscope opens one cached gRPC client per service, all bundled into a <code>RemoteClients</code> record. Recording downloads stream as 64&nbsp;KB chunks, and live JFR events flow over a separate event-streaming channel. With no remote bound, Microscope is fully offline and operates only on local files and heap dumps.</p>

      <div class="service-chips">
        <div class="service-chip"><i class="bi bi-compass"></i> Discovery</div>
        <div class="service-chip"><i class="bi bi-collection"></i> Workspaces</div>
        <div class="service-chip"><i class="bi bi-folder"></i> Projects</div>
        <div class="service-chip"><i class="bi bi-hdd-network"></i> Instances</div>
        <div class="service-chip"><i class="bi bi-cloud-download"></i> Recording Download</div>
        <div class="service-chip"><i class="bi bi-broadcast"></i> Event Streaming</div>
        <div class="service-chip"><i class="bi bi-archive"></i> Repository</div>
        <div class="service-chip"><i class="bi bi-sliders"></i> Profiler Settings</div>
        <div class="service-chip"><i class="bi bi-list-ul"></i> Workspace Events</div>
      </div>

      <h3 id="recording-pipeline">Recording → Profile Pipeline</h3>
      <p>A recording is just a JFR file on disk. Initializing a profile runs the parser, which writes events into a fresh <strong>per-profile DuckDB</strong>. Analysis features (Flamegraph, Timeseries, Sub-Second, Guardian, Threads, Heap Dump) read from that database on demand. Each profile owns its DB, so dropping a profile is a single file delete and parses run in parallel without contention. See <router-link to="/docs/microscope/storage">Storage</router-link> for tier-by-tier details.</p>

      <div class="arch-flow">
        <div class="flow-node"><i class="bi bi-file-earmark-binary"></i><span>Recording (JFR)</span></div>
        <div class="flow-arrow"><i class="bi bi-arrow-right"></i></div>
        <div class="flow-node"><i class="bi bi-cpu"></i><span>Parser</span></div>
        <div class="flow-arrow"><i class="bi bi-arrow-right"></i></div>
        <div class="flow-node"><i class="bi bi-database"></i><span>Per-profile DuckDB</span></div>
        <div class="flow-arrow"><i class="bi bi-arrow-right"></i></div>
        <div class="flow-node"><i class="bi bi-graph-up"></i><span>Analysis</span></div>
      </div>

      <h3 id="ai-integration">AI Integration</h3>
      <p>The assistant is wired through Spring AI with pluggable Claude and OpenAI providers, selected by <code>jeffrey.ai.provider</code> and <code>jeffrey.ai.model</code>. Two MCP servers expose the data: <code>duckdb-ai-mcp</code> answers JFR questions by running OQL against the active profile's DuckDB, and <code>heap-dump-ai-mcp</code> handles heap-dump queries. Configuration and capability detail live on the <router-link to="/docs/ai/overview">AI Overview</router-link> page.</p>

      <div class="arch-flow">
        <div class="flow-node"><i class="bi bi-chat-dots"></i><span>Assistant</span></div>
        <div class="flow-arrow"><i class="bi bi-arrow-right"></i></div>
        <div class="flow-node"><i class="bi bi-robot"></i><span>Spring AI<br><small>Claude / OpenAI</small></span></div>
        <div class="flow-arrow"><i class="bi bi-arrow-right"></i></div>
        <div class="flow-node"><i class="bi bi-plug"></i><span>MCP Server</span></div>
        <div class="flow-arrow"><i class="bi bi-arrow-right"></i></div>
        <div class="flow-node"><i class="bi bi-database"></i><span>Profile DB / Heap Dump</span></div>
      </div>

    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* ===== ARCHITECTURE DIAGRAM (microscope variant, server as black box) ===== */
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

.microscope-header { background: linear-gradient(135deg, #5e64ff 0%, #4338ca 100%); }
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

.arch-chip.analysis { background: #fef3c7; color: #92400e; }
.arch-chip.ai { background: #cffafe; color: #155e75; }
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
.grpc-label span {
  font-size: 0.6rem;
  color: #6d28d9;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

/* Black-box server panel */
.arch-app.blackbox {
  flex: 0 1 220px;
  background:
    repeating-linear-gradient(45deg, #f8fafc 0 8px, #f1f5f9 8px 16px);
  border-style: dashed;
}

.arch-blackbox-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 1.25rem 0.75rem;
  text-align: center;
  height: calc(100% - 36px);
}

.arch-blackbox-body > i {
  font-size: 1.75rem;
  color: #8b5cf6;
}

.arch-blackbox-body p {
  margin: 0;
  font-size: 0.7rem;
  color: #6c757d;
  font-style: italic;
}

.arch-blackbox-link {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.3rem 0.6rem;
  font-size: 0.65rem;
  font-weight: 600;
  color: #6d28d9;
  background: #ede9fe;
  border: 1px solid #c4b5fd;
  border-radius: 5px;
  text-decoration: none;
}

.arch-blackbox-link:hover {
  background: #ddd6fe;
}

/* ===== gRPC SERVICE CHIPS ===== */
.service-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin: 1rem 0 1.5rem;
}

.service-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.4rem 0.75rem;
  background: #ede9fe;
  color: #5b21b6;
  border: 1px solid #c4b5fd;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 500;
}

.service-chip i {
  font-size: 0.85rem;
  color: #7c3aed;
}

/* ===== ARCH FLOW STRIP (pipeline / AI wiring) ===== */
.arch-flow {
  display: flex;
  flex-wrap: wrap;
  align-items: stretch;
  gap: 0.5rem;
  margin: 1rem 0 1.75rem;
  padding: 1rem;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}

.flow-node {
  flex: 1 1 140px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  padding: 0.75rem 0.5rem;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  text-align: center;
  font-size: 0.75rem;
  font-weight: 500;
  color: #334155;
}

.flow-node i {
  font-size: 1.25rem;
  color: #4338ca;
}

.flow-node small {
  display: block;
  font-size: 0.65rem;
  font-weight: 400;
  color: #64748b;
  margin-top: 0.15rem;
}

.flow-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  font-size: 1.1rem;
}

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

  .arch-flow {
    flex-direction: column;
  }

  .flow-arrow {
    transform: rotate(90deg);
  }
}
</style>
