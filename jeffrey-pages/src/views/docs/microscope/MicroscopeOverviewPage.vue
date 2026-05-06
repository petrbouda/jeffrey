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
        <p class="hero-eyebrow"><i class="bi bi-stars"></i> Standalone JFR Analyst</p>
        <h2 class="hero-title">Open a recording. See everything.</h2>
        <p class="hero-lede">
          Microscope turns JFR recordings and heap dumps into rich, interactive analysis —
          flame graphs, timeseries, sub-second timelines, the Guardian's automated checks,
          and an AI assistant that already knows your data. Everything renders locally; no
          backend required.
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
        <div class="value-tile tile-ai">
          <div class="value-icon"><i class="bi bi-robot"></i></div>
          <h3>AI Assistant</h3>
          <p>Chat with Claude or OpenAI over the active profile or heap dump — the model has direct access to your data.</p>
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
          color="green"
          icon="bi bi-shield-check"
          title="Guardian Auto-Checks"
          description="Dozens of automated checks flag the usual culprits: lock contention, GC pressure, safepoint outliers, hashmap collisions, and more."
        />
        <DocsFeatureCard
          color="blue"
          icon="bi bi-cloud-arrow-down"
          title="Remote Workspaces"
          description="Connect to Jeffrey Server, browse production sessions, and pull only the recordings you want to investigate."
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
        <li><i class="bi bi-check2-circle"></i> Connecting to Jeffrey Server for centralised, production-grade recording collection.</li>
      </ul>

    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* ============ HERO ============ */
.hero {
  margin: 0.25rem 0 2rem;
  padding: 2.25rem 2rem 2rem;
  background:
    radial-gradient(120% 100% at 100% 0%, rgba(94, 100, 255, 0.10) 0%, rgba(94, 100, 255, 0) 60%),
    linear-gradient(180deg, #f8fafc 0%, #ffffff 100%);
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  position: relative;
  overflow: hidden;
}

.hero::before {
  content: "";
  position: absolute;
  top: -40%;
  right: -10%;
  width: 320px;
  height: 320px;
  background: radial-gradient(circle, rgba(124, 58, 237, 0.12) 0%, rgba(124, 58, 237, 0) 70%);
  pointer-events: none;
}

.hero-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  margin: 0 0 0.75rem;
  padding: 0.3rem 0.65rem;
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #5e64ff;
  background: #ede9fe;
  border-radius: 999px;
}

.hero-eyebrow i { font-size: 0.8rem; }

.hero-title {
  margin: 0 0 0.6rem;
  font-size: 2rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: #0f172a;
  line-height: 1.15;
}

.hero-lede {
  margin: 0 0 1.5rem;
  font-size: 1rem;
  line-height: 1.55;
  color: #475569;
  max-width: 60ch;
}

.hero-actions {
  display: flex;
  gap: 0.6rem;
  flex-wrap: wrap;
}

.hero .hero-cta {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.55rem 1rem;
  font-size: 0.85rem;
  font-weight: 600;
  border-radius: 8px;
  text-decoration: none;
  transition: transform 120ms ease, box-shadow 120ms ease, background-color 120ms ease;
  white-space: nowrap;
}

.hero .hero-cta:hover { transform: translateY(-1px); }

.hero .hero-cta-primary {
  color: #fff;
  background: linear-gradient(135deg, #5e64ff 0%, #4338ca 100%);
  box-shadow: 0 4px 14px rgba(94, 100, 255, 0.35);
}

.hero .hero-cta-primary:hover {
  box-shadow: 0 6px 18px rgba(94, 100, 255, 0.45);
  color: #fff;
}

.hero .hero-cta-ghost {
  color: #4338ca;
  background: #fff;
  border: 1px solid #c7d2fe;
}

.hero .hero-cta-ghost:hover {
  background: #f5f3ff;
  color: #4338ca;
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

.tile-ai .value-icon     { background: #cffafe; color: #0e7490; }
.tile-ai:hover           { border-color: #67e8f9; }

/* ============ USECASE LIST ============ */
.usecase-list {
  list-style: none;
  padding: 0;
  margin: 0.75rem 0 0.5rem;
  display: grid;
  grid-template-columns: 1fr;
  gap: 0.4rem;
}

.usecase-list li {
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

.usecase-list li i {
  color: #10b981;
  font-size: 1rem;
  margin-top: 0.15rem;
  flex-shrink: 0;
}

/* ============ RESPONSIVE ============ */
@media (max-width: 768px) {
  .hero { padding: 1.75rem 1.25rem; }
  .hero-title { font-size: 1.55rem; }
  .hero-lede { font-size: 0.95rem; }
  .value-tiles { grid-template-columns: 1fr; }
}
</style>
