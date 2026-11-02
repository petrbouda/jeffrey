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
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import DocsFeatureCard from '@/components/docs/DocsFeatureCard.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'key-features', text: 'Key Features', level: 2 },
  { id: 'when-to-use-jeffrey-microscope', text: 'When to Use', level: 2 }
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
 
      <section class="hero">
        <p class="hero-eyebrow"><i class="bi bi-stars"></i> Standalone Performance Analyst</p>
        <h2 class="hero-title">Open a recording. See everything.</h2>
        <p class="hero-lede">
          Microscope turns JFR recordings and heap dumps into rich, interactive analysis —
          flame graphs, timeseries, sub-second timelines, automated checks,
          and an MCP server so your coding agent can read the same data. Everything renders
          locally; no backend required.
        </p>
        <div class="hero-actions">
          <router-link class="hero-cta hero-cta-primary" to="/docs/microscope/quick-start">
            <i class="bi bi-rocket-takeoff"></i>
            <span>Quick Start</span>
          </router-link>
        </div>
      </section>

      <section class="value-tiles">
        <div class="value-tile tile-flame">
          <div class="value-icon"><i class="bi bi-fire"></i></div>
          <h3>Profile Analysis</h3>
          <p>Flame graphs, differential views, timeseries, sub-second timelines, thread &amp; GC analysis.</p>
        </div>
        <div class="value-tile tile-heap">
          <div class="value-icon"><i class="bi bi-hdd-stack"></i></div>
          <h3>Heap Dumps</h3>
          <p>Class histograms, dominator trees, leak suspects, and OQL queries — straight from a single dump file.</p>
        </div>
        <div class="value-tile tile-mcp">
          <div class="value-icon"><i class="bi bi-plug"></i></div>
          <h3>Microscope MCP</h3>
          <p>Analyse every profile from a coding agent in your own repository that speaks MCP 2026-07-28 — Claude Code on its v2 MCP runtime, or Codex v0.147.0+ with its <code>mcp_2026_07_28</code> flag on. The agent reads the profile and the source side by side.</p>
        </div>
      </section>

      <h2 id="key-features">Key Features</h2>

      <div class="docs-grid docs-grid-2">
        <DocsFeatureCard
          color="purple"
          icon="bi bi-lightning"
          title="Drag-and-Drop Recordings"
          description="Upload a JFR file or heap dump and start exploring instantly — no project setup, no migrations."
        />
        <DocsFeatureCard
          color="orange"
          icon="bi bi-speedometer2"
          title="Deep Profile Analysis"
          description="Flame graphs, timeseries, sub-second views, thread states, GC, JIT — every JFR event visualized."
        />
        <DocsFeatureCard
          color="red"
          icon="bi bi-memory"
          title="Heap Dump Forensics"
          description="Browse class histograms, walk dominator trees, hunt leak suspects, run OQL queries."
        />
        <DocsFeatureCard
          color="blue"
          icon="bi bi-cloud-arrow-down"
          title="Connector Workspaces"
          description="Declare the connector (the Azure file share holding the recording volume) in configuration, browse production sessions, and pull only the recordings you want to investigate."
        />
        <DocsFeatureCard
          color="neutral"
          icon="bi bi-grid-1x2"
          title="Technology Dashboards"
          description="Pre-built HTTP, JDBC, and gRPC dashboards when paired with Jeffrey Events — observability without the agent zoo."
        />
      </div>

      <h2 id="when-to-use-jeffrey-microscope">When to Use</h2>
      <ul class="usecase-list">
        <li><i class="bi bi-check2-circle"></i> Analyzing JFR recordings from any source — local files, CI artifacts, or fetched from production.</li>
        <li><i class="bi bi-check2-circle"></i> Quick ad-hoc profiling investigations directly on your laptop.</li>
        <li><i class="bi bi-check2-circle"></i> Deep, side-by-side comparisons with differential flame graphs and timeseries.</li>
        <li><i class="bi bi-check2-circle"></i> Hunting memory leaks and oversized retention with heap-dump tooling.</li>
        <li><i class="bi bi-check2-circle"></i> Declaring the Azure file share that holds the recording volume as the connector, for centralised, production-grade recording collection (kept tidy by a Jeffrey Hub).</li>
      </ul>

    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
@import '@/views/docs/docs-hero.css';

.hero {
  --hero-padding: 2.25rem 2rem 2rem;
  --hero-padding-mobile: 1.75rem 1.25rem;
  --hero-title-size-mobile: 1.55rem;
  --hero-lede-margin: 0 0 1.5rem;
  --hero-title-size: 2rem;
}

/* ============ VALUE TILES ============ */
.value-tiles {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.85rem;
  margin: 0 0 2rem;
}

.value-tile {
  padding: 1.1rem 1rem 1rem;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  position: relative;
  overflow: hidden;
  transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease;
}

.value-tile:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.06);
}

.value-tile::after {
  content: "";
  position: absolute;
  inset: 0;
  border-radius: 12px;
  pointer-events: none;
  opacity: 0;
  transition: opacity 140ms ease;
}

.value-tile:hover::after { opacity: 1; }

.value-tile h3 {
  margin: 0.6rem 0 0.35rem;
  font-size: 1rem;
  font-weight: 650;
  color: #0f172a;
  letter-spacing: -0.01em;
}

.value-tile p {
  margin: 0;
  font-size: 0.85rem;
  line-height: 1.5;
  color: #475569;
}

.value-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 10px;
  font-size: 1.05rem;
}

.tile-flame .value-icon  { background: #ffedd5; color: #c2410c; }
.tile-flame:hover        { border-color: #fdba74; }

.tile-heap .value-icon   { background: #fee2e2; color: #b91c1c; }
.tile-heap:hover         { border-color: #fca5a5; }

.tile-mcp .value-icon     { background: #cffafe; color: #0e7490; }
.tile-mcp:hover           { border-color: #67e8f9; }

/* ============ RESPONSIVE ============ */
@media (max-width: 768px) {
  .value-tiles { grid-template-columns: 1fr; }
}
</style>
