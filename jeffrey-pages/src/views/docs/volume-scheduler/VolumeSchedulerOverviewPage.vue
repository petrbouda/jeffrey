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
  { id: 'when-to-use', text: 'When to Use', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Volume Scheduler"
      icon="bi bi-clock-history"
    />

    <div class="docs-content">

      <section class="hero">
        <p class="hero-eyebrow"><i class="bi bi-stars"></i> Recording Volume Maintenance</p>
        <h2 class="hero-title">Keep the shared volume tidy.</h2>
        <p class="hero-lede">
          The Volume Scheduler is a small maintenance service that runs beside Microscope, next to the
          shared recording volume your services record into — typically as one native container on
          Kubernetes. The volume is its only state, with no database: the volume scheduler runs two
          scheduled jobs on it, retention and LZ4 compression of finished JFR chunks. It exposes no API to
          Microscope — Microscope reads the same volume itself, through
          <router-link to="/docs/connectors">its connector</router-link>, and
          <router-link to="/docs/microscope-mcp/tools#connector">its <code>connector_</code> tools</router-link>
          pick a session, or the hours of it that matter, and download that.
        </p>
        <div class="hero-actions">
          <router-link class="hero-cta hero-cta-primary" to="/docs/volume-scheduler/configuration">
            <i class="bi bi-gear"></i>
            <span>Configuration</span>
          </router-link>
          <router-link class="hero-cta hero-cta-ghost" to="/docs/volume-scheduler/architecture">
            <i class="bi bi-diagram-3"></i>
            <span>Architecture</span>
          </router-link>
        </div>
      </section>

      <section class="value-tiles">
        <div class="value-tile tile-broadcast">
          <div class="value-icon"><i class="bi bi-trash3"></i></div>
          <h3>Retention</h3>
          <p>Sessions are dropped by age and by a cap per instance, each instance's newest session is trimmed, every project is kept within its disk budget, and stale instances without sessions are cleaned up.</p>
        </div>
        <div class="value-tile tile-collection">
          <div class="value-icon"><i class="bi bi-file-zip"></i></div>
          <h3>Compression</h3>
          <p>Finished JFR chunks are LZ4-compressed in place by the compression job — never on a read path.</p>
        </div>
        <div class="value-tile tile-grpc">
          <div class="value-icon"><i class="bi bi-speedometer2"></i></div>
          <h3>REST API</h3>
          <p>A small JSON API reports what the last retention pass measured per workspace and project, and lists both jobs so you can run either one now.</p>
        </div>
      </section>

      <h2 id="key-features">Key Features</h2>

      <div class="docs-grid docs-grid-2">
        <DocsFeatureCard
          color="purple"
          icon="bi bi-calendar-check"
          title="Two Scheduled Jobs"
          description="Retention (session age, a session cap per instance, newest-session trim, a disk budget per project, stale instance cleanup) and LZ4 compression of JFR chunks run on a fixed delay against the shared volume."
        />
        <DocsFeatureCard
          color="orange"
          icon="bi bi-collection"
          title="Recording Sessions"
          description="The Provisioner lays JFR chunks out on the volume as one session per JVM run, under workspace, project and instance; their UUIDv7 directory names order them, and retention ages them."
        />
        <DocsFeatureCard
          color="red"
          icon="bi bi-archive"
          title="All Artifacts in One Place"
          description="Heap dumps, JVM logs, application logs, and hotspot crash dumps sit next to JFR recordings on the volume, under the same retention."
        />
        <DocsFeatureCard
          color="green"
          icon="bi bi-hdd-stack"
          title="Storage Figures"
          description="GET /api/v1/storage returns size, file count and last activity per workspace and project, as the last retention pass measured them."
        />
        <DocsFeatureCard
          color="neutral"
          icon="bi bi-play-circle"
          title="Run a Job Now"
          description="GET /api/v1/jobs lists both jobs with their schedule; POST /api/v1/jobs/{job}/run runs retention or compression on demand and returns its summary."
        />
        <DocsFeatureCard
          color="blue"
          icon="bi bi-folder2-open"
          title="Multi-Tenant Workspaces"
          description="Many teams, many projects, one volume. Each workspace owns its storage scope."
        />
      </div>

      <h2 id="when-to-use">When to Use</h2>
      <ul class="usecase-list">
        <li><i class="bi bi-check2-circle"></i> Always-on production / staging recording onto a shared volume across many JVMs — the volume scheduler keeps that volume from growing without bound.</li>
        <li><i class="bi bi-check2-circle"></i> Retention-governed storage of JFR recordings, heap dumps, JVM &amp; application logs, and crash dumps on a shared filesystem.</li>
        <li><i class="bi bi-check2-circle"></i> Whenever your services record onto a volume that Microscope reads through its connector — without the volume scheduler running on it, nothing is retained, cleaned up or compressed, and the volume only grows.</li>
      </ul>
      <p>
        You do not need it to analyse a recording on your laptop: Microscope opens a JFR file on its own.
        The <router-link to="/docs/deployment/helm-chart">Helm chart</router-link> in Deployment by
        Example shows it running beside the profiled pods.
      </p>

    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
@import '@/views/docs/docs-hero.css';

.hero {
  --hero-tint: 124, 58, 237;
  --hero-glow-alpha: 0.14;
  --hero-accent: #7c3aed;
  --hero-cta-from: #8b5cf6;
  --hero-cta-to: #6d28d9;
  --hero-cta-shadow: 124, 58, 237;
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

/* ============ RESPONSIVE ============ */
@media (max-width: 768px) {
  .value-tiles { grid-template-columns: 1fr; }
}
</style>
