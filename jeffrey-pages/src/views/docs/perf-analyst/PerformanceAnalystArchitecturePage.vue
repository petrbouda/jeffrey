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
  { id: 'role', text: 'Role', level: 2 },
  { id: 'components', text: 'Components', level: 2 },
  { id: 'recommendation-workflow', text: 'Recommendation Workflow', level: 2 },
  { id: 'ai-providers', text: 'AI Providers', level: 2 }
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
        Performance Analyst is a standalone Spring Boot application with an embedded Vue 3 SPA. It keeps
        a small amount of state in a local SQLite database, talks to one or more Jeffrey Hubs over gRPC to
        fetch recordings, and drives an AI model — with read-only access to a cloned source repository — to
        produce recommendations.
      </p>

      <h2 id="role">Role</h2>
      <p>
        Where <router-link to="/docs/microscope">Microscope</router-link> is for deep, interactive analysis
        of a single recording, Performance Analyst is the automated, recommendation-first companion. It never
        builds a full per-profile database; it reduces each recording to a flamegraph prompt and lets the AI
        do the reasoning against your real code.
      </p>

      <DocsCallout type="info">
        <strong>Lightweight by design.</strong> A single SQLite file holds projects, registered hubs,
        downloaded-recording metadata, cached prompts, generated recommendations and version-control
        configuration. There are no per-profile databases and no analysis pipeline to operate.
      </DocsCallout>

      <h2 id="components">Components</h2>
      <p>
        The backend breaks into four concerns: <strong>Hub clients</strong> that fetch recordings,
        <strong>flamegraph prompts</strong> that summarise them, the <strong>recommendation engine</strong>
        that drives the AI plus its repository tools, and <strong>storage</strong>.
      </p>

      <div class="arch-diagram">
        <div class="arch-apps server-only">
          <div class="arch-app">
            <div class="arch-app-header pa-header">
              <i class="bi bi-robot"></i>
              <span>Performance Analyst</span>
              <small>Standalone JAR / Container</small>
            </div>
            <div class="arch-app-body">
              <div class="arch-section-label">Hub Connectivity</div>
              <div class="arch-layer">
                <div class="arch-chip hub-feat"><i class="bi bi-cloud"></i> Hubs Manager</div>
                <div class="arch-chip hub-feat"><i class="bi bi-hdd-network"></i> gRPC Hub Clients</div>
              </div>
              <div class="arch-section-label">Profile Summarisation</div>
              <div class="arch-layer">
                <div class="arch-chip prompt"><i class="bi bi-fire"></i> Flamegraph Prompt Manager</div>
              </div>
              <div class="arch-section-label">Recommendation Engine</div>
              <div class="arch-layer">
                <div class="arch-chip engine"><i class="bi bi-cpu"></i> Recommendation Manager</div>
                <div class="arch-chip engine"><i class="bi bi-git"></i> Repository Cloner</div>
              </div>
              <div class="arch-layer">
                <div class="arch-chip tools"><i class="bi bi-tools"></i> Repo Tools (read-only)</div>
                <div class="arch-chip tools"><i class="bi bi-plug"></i> MCP Server (Claude Code)</div>
              </div>
              <div class="arch-section-label">Storage</div>
              <div class="arch-storage-row">
                <div class="arch-storage"><i class="bi bi-database"></i> SQLite</div>
                <div class="arch-storage"><i class="bi bi-folder"></i> Downloaded Recordings</div>
              </div>
            </div>
          </div>
        </div>

        <div class="arch-deps-row">
          <div class="arch-dep">
            <div class="arch-dep-arrow"><small>gRPC</small><i class="bi bi-arrow-down-up"></i></div>
            <router-link to="/docs/hub" class="arch-dep-box hub">
              <i class="bi bi-cloud-fill"></i>
              <span>Jeffrey Hub</span>
              <small>recordings &amp; metadata</small>
            </router-link>
          </div>
          <div class="arch-dep">
            <div class="arch-dep-arrow"><small>clone</small><i class="bi bi-arrow-down-up"></i></div>
            <div class="arch-dep-box repo">
              <i class="bi bi-git"></i>
              <span>GitHub / GitLab</span>
              <small>source repository</small>
            </div>
          </div>
          <div class="arch-dep">
            <div class="arch-dep-arrow"><small>chat + tools</small><i class="bi bi-arrow-down-up"></i></div>
            <div class="arch-dep-box llm">
              <i class="bi bi-stars"></i>
              <span>AI Model</span>
              <small>Claude · ChatGPT · Ollama</small>
            </div>
          </div>
        </div>
      </div>

      <h2 id="recommendation-workflow">Recommendation Workflow</h2>
      <p>
        Generating a recommendation for a recording and event type runs through a fixed pipeline. The
        flamegraph prompt is cached, so re-running against the same recording skips straight to the AI step.
      </p>

      <div class="flow">
        <div class="flow-node"><i class="bi bi-cloud-download"></i><span>Download recording</span><small>from Hub</small></div>
        <div class="flow-arrow"><i class="bi bi-arrow-right"></i></div>
        <div class="flow-node"><i class="bi bi-fire"></i><span>Flamegraph prompt</span><small>cached markdown</small></div>
        <div class="flow-arrow"><i class="bi bi-arrow-right"></i></div>
        <div class="flow-node"><i class="bi bi-git"></i><span>Clone repo</span><small>temp checkout</small></div>
        <div class="flow-arrow"><i class="bi bi-arrow-right"></i></div>
        <div class="flow-node accent"><i class="bi bi-cpu"></i><span>AI + repo tools</span><small>explore &amp; reason</small></div>
        <div class="flow-arrow"><i class="bi bi-arrow-right"></i></div>
        <div class="flow-node accent"><i class="bi bi-file-earmark-diff"></i><span>Severity + patch</span><small>stored &amp; ranked</small></div>
      </div>

      <p>
        The AI is given four read-only tools to explore the cloned checkout — every path is validated against
        the checkout root so it can never escape the repository:
      </p>
      <div class="tool-chips">
        <div class="tool-chip"><i class="bi bi-list-ul"></i> listFiles</div>
        <div class="tool-chip"><i class="bi bi-asterisk"></i> glob</div>
        <div class="tool-chip"><i class="bi bi-file-earmark-text"></i> readFile</div>
        <div class="tool-chip"><i class="bi bi-search"></i> grep</div>
      </div>

      <h2 id="ai-providers">AI Providers</h2>
      <p>
        The recommendation engine talks to a provider-agnostic chat backend, configured with
        <code>jeffrey.performance-analyst.ai.*</code> properties:
      </p>
      <ul class="provider-list">
        <li><i class="bi bi-stars"></i> <strong>Claude</strong> and <strong>ChatGPT</strong> via API key.</li>
        <li><i class="bi bi-hdd"></i> <strong>Ollama</strong> for a self-hosted model (set a base URL).</li>
        <li><i class="bi bi-terminal"></i> <strong>Claude Code</strong> CLI in headless mode — it reaches the repo tools through an MCP Streamable-HTTP server exposed at <code>/api/internal/mcp/claude-code</code>, reusing your local Claude subscription with no API key.</li>
      </ul>

      <p>
        For how recordings reach Performance Analyst in the first place, see
        <router-link to="/docs/perf-analyst/hub-connection">Hub Connection</router-link>.
      </p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* ===== ARCHITECTURE DIAGRAM ===== */
.arch-diagram {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  margin: 1.5rem 0;
  padding: 2rem 1.5rem;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  border-radius: 12px;
  border: 1px solid #e2e8f0;
}

.arch-apps {
  display: flex;
  align-items: stretch;
  width: 100%;
}

.arch-apps.server-only {
  justify-content: center;
}

.arch-apps.server-only .arch-app {
  flex: 0 1 560px;
  max-width: 600px;
}

.arch-app {
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  background: #fff;
  width: 100%;
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

.pa-header { background: linear-gradient(135deg, #10b981 0%, #059669 100%); }

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

.arch-chip.hub-feat { background: #ecfdf5; color: #047857; }
.arch-chip.prompt { background: #fff7ed; color: #c2410c; }
.arch-chip.engine { background: #ede9fe; color: #5b21b6; }
.arch-chip.tools { background: #eff6ff; color: #1d4ed8; }

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

/* ===== external dependencies row ===== */
.arch-deps-row {
  display: flex;
  gap: 0.75rem;
  width: 100%;
  max-width: 600px;
  justify-content: center;
}

.arch-dep {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.arch-dep-arrow {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.1rem;
  color: #64748b;
}

.arch-dep-arrow i { font-size: 0.9rem; }
.arch-dep-arrow small {
  font-size: 0.6rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.arch-dep-box {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.15rem;
  padding: 0.6rem 0.5rem;
  border-radius: 8px;
  text-align: center;
  text-decoration: none;
}

.arch-dep-box span { font-size: 0.74rem; font-weight: 600; }
.arch-dep-box small { font-size: 0.6rem; color: #64748b; }
.arch-dep-box i { font-size: 1.1rem; }

.arch-dep-box.hub { background: #faf5ff; border: 1px solid #d8b4fe; }
.arch-dep-box.hub i, .arch-dep-box.hub span { color: #7c3aed; }
.arch-dep-box.hub:hover { background: #f3e8ff; }

.arch-dep-box.repo { background: #fff; border: 1px solid #e2e8f0; }
.arch-dep-box.repo i, .arch-dep-box.repo span { color: #334155; }

.arch-dep-box.llm { background: #ecfdf5; border: 1px solid #a7f3d0; }
.arch-dep-box.llm i, .arch-dep-box.llm span { color: #047857; }

/* ===== workflow flow ===== */
.flow {
  display: flex;
  flex-wrap: wrap;
  align-items: stretch;
  gap: 0.4rem;
  margin: 1rem 0 1.5rem;
  padding: 1.25rem 1rem;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}

.flow-node {
  flex: 1 1 130px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.25rem;
  padding: 0.75rem 0.5rem;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  text-align: center;
}

.flow-node span { font-size: 0.76rem; font-weight: 600; color: #0f172a; }
.flow-node small { font-size: 0.62rem; color: #64748b; }
.flow-node i { font-size: 1.2rem; color: #64748b; }

.flow-node.accent { background: #ecfdf5; border-color: #a7f3d0; }
.flow-node.accent i { color: #059669; }

.flow-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  font-size: 1rem;
}

/* ===== tool + provider lists ===== */
.tool-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  margin: 0.75rem 0 1.25rem;
}

.tool-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.4rem 0.7rem;
  font-size: 0.78rem;
  font-weight: 600;
  font-family: 'Courier New', monospace;
  color: #1d4ed8;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 6px;
}

.tool-chip i { font-size: 0.85rem; }

.provider-list {
  list-style: none;
  padding: 0;
  margin: 0.75rem 0 1.25rem;
  display: grid;
  gap: 0.4rem;
}

.provider-list li {
  display: flex;
  align-items: flex-start;
  gap: 0.6rem;
  padding: 0.55rem 0.85rem;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 0.9rem;
  color: #334155;
  line-height: 1.5;
}

.provider-list li i {
  color: #059669;
  font-size: 1rem;
  flex-shrink: 0;
  margin-top: 0.1rem;
}

/* ===== responsive ===== */
@media (max-width: 768px) {
  .arch-deps-row { flex-direction: column; }
  .flow { flex-direction: column; }
  .flow-arrow { transform: rotate(90deg); }
}
</style>
