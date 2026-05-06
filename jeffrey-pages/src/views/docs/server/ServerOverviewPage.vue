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
  { id: 'when-to-use-jeffrey-server', text: 'When to Use', level: 2 }
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

      <section class="hero">
        <p class="hero-eyebrow"><i class="bi bi-stars"></i> Production Recording Backend</p>
        <h2 class="hero-title">Collect once, analyze anywhere.</h2>
        <p class="hero-lede">
          Jeffrey Server runs alongside your Java fleet — typically on Kubernetes or a dedicated
          host — and continuously collects JFR recordings. It catalogs them by workspace, project,
          and instance, schedules recording jobs, and serves the results over gRPC. No analysis
          on the server; that happens in Microscope, on demand.
        </p>
        <div class="hero-actions">
          <router-link class="hero-cta hero-cta-primary" to="/docs/server/deployment">
            <i class="bi bi-cloud-upload"></i>
            <span>Deployment</span>
          </router-link>
          <router-link class="hero-cta hero-cta-ghost" to="/docs/server/architecture">
            <i class="bi bi-diagram-3"></i>
            <span>Architecture</span>
          </router-link>
        </div>
      </section>

      <section class="value-tiles">
        <div class="value-tile tile-broadcast">
          <div class="value-icon"><i class="bi bi-broadcast-pin"></i></div>
          <h3>Continuous Recording</h3>
          <p>Async-Profiler agents stream JFR chunks into Server on every loop — no manual capture, no missed windows.</p>
        </div>
        <div class="value-tile tile-collection">
          <div class="value-icon"><i class="bi bi-collection"></i></div>
          <h3>Recording Sessions</h3>
          <p>Recordings are grouped into sessions per JVM lifecycle, with automatic discovery, retention, and lifecycle states.</p>
        </div>
        <div class="value-tile tile-grpc">
          <div class="value-icon"><i class="bi bi-hdd-network"></i></div>
          <h3>gRPC to Microscope</h3>
          <p>Microscope clients connect as remote workspaces, browse sessions, and pull only the recordings they want to investigate.</p>
        </div>
      </section>

      <h2 id="key-features">Key Features</h2>

      <div class="docs-grid docs-grid-2">
        <DocsFeatureCard
          color="purple"
          icon="bi bi-broadcast-pin"
          title="Continuous Recording"
          description="Always-on JFR collection from running applications via Async-Profiler agents — no operator in the loop."
        />
        <DocsFeatureCard
          color="orange"
          icon="bi bi-collection"
          title="Recording Sessions"
          description="JFR chunks are grouped into sessions per JVM run, indexed by workspace, project, and instance."
        />
        <DocsFeatureCard
          color="red"
          icon="bi bi-archive"
          title="All Artifacts in One Place"
          description="Heap dumps, JVM logs, application logs, and hotspot crash dumps — collected next to JFR recordings so you have everything in one place when something breaks."
        />
        <DocsFeatureCard
          color="green"
          icon="bi bi-hdd-network"
          title="gRPC API"
          description="Discovery, project, instance, recording-download, repository, profiler-settings, and messages services — defined by proto."
        />
        <DocsFeatureCard
          color="neutral"
          icon="bi bi-broadcast"
          title="Live Event Streaming"
          description="Stream JFR events to connected Microscope clients in real time for live dashboards and incident investigations."
        />
        <DocsFeatureCard
          color="blue"
          icon="bi bi-folder2-open"
          title="Multi-Tenant Workspaces"
          description="Many teams, many projects, one server. Each workspace owns its profiler settings and storage scope."
        />
      </div>

      <h2 id="when-to-use-jeffrey-server">When to Use</h2>
      <ul class="usecase-list">
        <li><i class="bi bi-check2-circle"></i> Always-on production / staging recording collection across many JVMs.</li>
        <li><i class="bi bi-check2-circle"></i> Centralised, retention-governed storage of JFR recordings, heap dumps, JVM &amp; application logs, and crash dumps on a shared filesystem.</li>
        <li><i class="bi bi-check2-circle"></i> Letting many developers analyze the same recordings with Microscope without each pulling files manually.</li>
        <li><i class="bi bi-check2-circle"></i> Driving Async-Profiler agents from a single source of truth for profiler settings (per workspace or project).</li>
        <li><i class="bi bi-check2-circle"></i> CI / automated profiling pipelines that need a server-side queue and durable session lifecycle.</li>
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
    radial-gradient(120% 100% at 100% 0%, rgba(124, 58, 237, 0.10) 0%, rgba(124, 58, 237, 0) 60%),
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
  background: radial-gradient(circle, rgba(124, 58, 237, 0.14) 0%, rgba(124, 58, 237, 0) 70%);
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
  color: #7c3aed;
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
  background: linear-gradient(135deg, #8b5cf6 0%, #6d28d9 100%);
  box-shadow: 0 4px 14px rgba(124, 58, 237, 0.35);
}

.hero .hero-cta-primary:hover {
  box-shadow: 0 6px 18px rgba(124, 58, 237, 0.45);
  color: #fff;
}

.hero .hero-cta-ghost {
  color: #6d28d9;
  background: #fff;
  border: 1px solid #c4b5fd;
}

.hero .hero-cta-ghost:hover {
  background: #f5f3ff;
  color: #6d28d9;
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

.tile-broadcast .value-icon  { background: #ede9fe; color: #6d28d9; }
.tile-broadcast:hover        { border-color: #c4b5fd; }

.tile-collection .value-icon { background: #ffedd5; color: #c2410c; }
.tile-collection:hover       { border-color: #fdba74; }

.tile-grpc .value-icon       { background: #cffafe; color: #0e7490; }
.tile-grpc:hover             { border-color: #67e8f9; }

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
  align-items: center;
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
