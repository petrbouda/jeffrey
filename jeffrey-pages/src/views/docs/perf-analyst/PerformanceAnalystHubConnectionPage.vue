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
  { id: 'data-flow', text: 'End-to-End Data Flow', level: 2 },
  { id: 'grpc-clients', text: 'gRPC Clients', level: 2 },
  { id: 'browse-download', text: 'Browse & Download', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Hub Connection"
      icon="bi bi-cloud"
    />

    <div class="docs-content">
      <p>
        Performance Analyst does not collect recordings itself — it consumes them from a
        <router-link to="/docs/hub">Jeffrey Hub</router-link>. You register one or more hubs, browse their
        contents, and download the recordings you want to analyze. All communication happens over the same
        gRPC surface Microscope uses.
      </p>

      <h2 id="data-flow">End-to-End Data Flow</h2>
      <p>
        Recordings originate in your running services, are collected by a Hub, pulled into Performance
        Analyst over gRPC, and then combined with your source repository and an AI model to produce
        recommendations.
      </p>

      <div class="dataflow">
        <div class="df-node services">
          <i class="bi bi-app-indicator"></i>
          <span>Your Services</span>
          <small>+ Jeffrey Agent</small>
        </div>
        <div class="df-arrow"><span>JFR</span><i class="bi bi-arrow-right"></i></div>
        <div class="df-node hub">
          <i class="bi bi-cloud-fill"></i>
          <span>Jeffrey Hub</span>
          <small>collects &amp; serves</small>
        </div>
        <div class="df-arrow grpc"><span>gRPC</span><i class="bi bi-arrow-right"></i></div>
        <div class="df-node pa">
          <i class="bi bi-robot"></i>
          <span>Performance Analyst</span>
          <small>downloads recordings</small>
        </div>
        <div class="df-arrow"><span>clone + chat</span><i class="bi bi-arrow-right"></i></div>
        <div class="df-node out">
          <i class="bi bi-file-earmark-diff"></i>
          <span>Recommendations</span>
          <small>severity + patch</small>
        </div>
      </div>

      <DocsCallout type="info">
        <strong>Same gRPC surface as Microscope.</strong> A Hub treats Performance Analyst like any other
        client. Anything Microscope can browse — workspaces, projects, instances, recordings — Performance
        Analyst can browse too.
      </DocsCallout>

      <h2 id="grpc-clients">gRPC Clients</h2>
      <p>
        For each registered hub, Performance Analyst resolves a bundle of gRPC clients and reuses them
        across requests. The ones it relies on most:
      </p>
      <div class="service-chips">
        <div class="service-chip"><i class="bi bi-compass"></i> Discovery</div>
        <div class="service-chip"><i class="bi bi-collection"></i> Workspaces</div>
        <div class="service-chip"><i class="bi bi-folder"></i> Projects</div>
        <div class="service-chip"><i class="bi bi-hdd-network"></i> Instances</div>
        <div class="service-chip"><i class="bi bi-cloud-download"></i> Recording Download</div>
      </div>
      <p>
        Discovery enumerates workspaces and projects; Instances lists the JVMs that produced recordings; and
        Recording Download streams the JFR bytes into local storage. See the
        <router-link to="/docs/hub/grpc-api">Hub gRPC API reference</router-link> for the full surface.
      </p>

      <h2 id="browse-download">Browse &amp; Download</h2>
      <p>
        After a hub is registered, the typical path is:
      </p>
      <ol class="step-list">
        <li><span class="step-n">1</span> <strong>Register a hub</strong> with its address (host + port, plaintext or TLS).</li>
        <li><span class="step-n">2</span> <strong>Browse</strong> its workspaces, projects and instances through Discovery.</li>
        <li><span class="step-n">3</span> <strong>Download</strong> the recordings you care about into a local project — they're cataloged in SQLite and stored on disk.</li>
        <li><span class="step-n">4</span> <strong>Analyze</strong> a downloaded recording: generate its flamegraph prompt and run a recommendation (see <router-link to="/docs/perf-analyst/quick-start">Quick Start</router-link>).</li>
      </ol>

      <p class="footnote">
        The recommendation step also needs a source repository attached to the project — that part talks to
        GitHub/GitLab, not the Hub. The Hub's only job is to be the source of recordings.
      </p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* ===== data-flow diagram ===== */
.dataflow {
  display: flex;
  flex-wrap: wrap;
  align-items: stretch;
  justify-content: center;
  gap: 0.4rem;
  margin: 1.5rem 0;
  padding: 1.75rem 1.25rem;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}

.df-node {
  flex: 1 1 140px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.25rem;
  padding: 0.85rem 0.6rem;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  text-align: center;
}

.df-node span { font-size: 0.78rem; font-weight: 700; color: #0f172a; }
.df-node small { font-size: 0.62rem; color: #64748b; }
.df-node i { font-size: 1.4rem; }

.df-node.services i { color: #475569; }
.df-node.hub { background: #faf5ff; border-color: #d8b4fe; }
.df-node.hub i, .df-node.hub span { color: #7c3aed; }
.df-node.pa { background: #ecfdf5; border-color: #a7f3d0; }
.df-node.pa i, .df-node.pa span { color: #047857; }
.df-node.out i { color: #c2410c; }

.df-arrow {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.1rem;
  color: #94a3b8;
  min-width: 56px;
}

.df-arrow span {
  font-size: 0.58rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #94a3b8;
}

.df-arrow i { font-size: 1rem; }

.df-arrow.grpc span { color: #059669; }

/* ===== gRPC chips ===== */
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
  color: #047857;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  border-radius: 6px;
}

.service-chip i { font-size: 0.85rem; }

/* ===== step list ===== */
.step-list {
  list-style: none;
  padding: 0;
  margin: 0.75rem 0 1.25rem;
  display: grid;
  gap: 0.5rem;
}

.step-list li {
  display: flex;
  align-items: flex-start;
  gap: 0.7rem;
  padding: 0.65rem 0.9rem;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 0.9rem;
  color: #334155;
  line-height: 1.5;
}

.step-n {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #10b981;
  color: #fff;
  font-size: 0.72rem;
  font-weight: 700;
}

.footnote {
  margin-top: 1rem;
  padding: 0.9rem 1.1rem;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: 10px;
  font-size: 0.9rem;
  color: #334155;
  line-height: 1.55;
}

@media (max-width: 768px) {
  .dataflow { flex-direction: column; }
  .df-arrow { flex-direction: row; gap: 0.4rem; }
  .df-arrow i { transform: rotate(90deg); }
}
</style>
