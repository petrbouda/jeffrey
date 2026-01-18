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
import DocsFeatureCard from '@/components/docs/DocsFeatureCard.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'core-objectives', text: 'Core Objectives', level: 2 },
  { id: 'target-audience', text: 'Target Audience', level: 2 },
  { id: 'design-decisions', text: 'Design Decisions', level: 2 },
  { id: 'auto-analysis', text: 'Auto Analysis', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Goals"
        icon="bi bi-bullseye"
      />

      <div class="docs-content">
        <p>Jeffrey is built with clear objectives that guide every feature and design decision.</p>

        <h2 id="core-objectives">Core Objectives</h2>
        <p>Jeffrey addresses three distinct profiling workflows through dedicated workspace types:</p>

        <div class="docs-grid docs-grid-stack">
          <div class="docs-card docs-card-horizontal">
            <div class="docs-icon docs-icon-md docs-icon-orange">
              <i class="bi bi-house"></i>
            </div>
            <div class="docs-card-content">
              <h4 class="docs-title-with-badge">Quick Local Analysis <span class="docs-badge docs-badge-default">Sandbox</span></h4>
              <p>Quickly analyze locally available JFR recordings and organize them into projects. Upload a file and start investigating within seconds.</p>
            </div>
          </div>
          <div class="docs-card docs-card-horizontal">
            <div class="docs-icon docs-icon-md docs-icon-purple">
              <i class="bi bi-cloud"></i>
            </div>
            <div class="docs-card-content">
              <h4 class="docs-title-with-badge">Recording Collection Infrastructure <span class="docs-badge docs-badge-default">Live</span></h4>
              <p>Conveniently collect JFR recordings and artifacts from running applications, especially in containerized cloud environments like Kubernetes.</p>
            </div>
          </div>
          <div class="docs-card docs-card-horizontal">
            <div class="docs-icon docs-icon-md docs-icon-green">
              <i class="bi bi-pc-display"></i>
            </div>
            <div class="docs-card-content">
              <h4 class="docs-title-with-badge">Avoid Cloud Computation Costs <span class="docs-badge docs-badge-default">Remote</span></h4>
              <p>Offload expensive profile processing from cloud infrastructure to local machines. Download recordings remotely and analyze them locally.</p>
            </div>
          </div>
        </div>

        <h2 id="target-audience">Target Audience</h2>
        <p>Jeffrey is designed for <strong>small and medium-sized projects and companies</strong> that need:</p>
        <ul>
          <li>A solution that can be <strong>quickly deployed</strong> and ready to use immediately</li>
          <li>No complex infrastructure requirements or lengthy setup processes</li>
          <li>Self-hosted profiling without dependency on external services</li>
        </ul>

        <h2 id="design-decisions">Design Decisions</h2>

        <h3>Operational Cost Optimization</h3>
        <p>Jeffrey prioritizes keeping operational costs low while maintaining useful features:</p>

        <div class="docs-grid docs-grid-stack">
          <DocsFeatureCard
            icon="bi bi-hdd"
            title="Filesystem & Blob Storage"
            description="Use cheaper storage systems instead of expensive managed databases. Recordings are stored on disk or blob storage."
            color="neutral"
          />
          <DocsFeatureCard
            icon="bi bi-database"
            title="In-Process Database (DuckDB)"
            description="Use file-based, in-process databases to avoid deploying and maintaining separate database servers."
            color="neutral"
          />
          <DocsFeatureCard
            icon="bi bi-box-seam"
            title="Minimal Components"
            description="No additional services to deploy. Jeffrey runs as a single application with everything included."
            color="neutral"
          />
        </div>

        <h3>Accessible Visualizations</h3>
        <p>Jeffrey provides visualizations that are clear for <strong>all developer levels</strong> - from junior to senior:</p>
        <ul>
          <li>Minimal JVM knowledge required to start analyzing</li>
          <li>Explanations of JVM internals provided where needed to reason about results</li>
          <li>Progressive disclosure - start with overviews, drill down into details as needed</li>
        </ul>

        <h2 id="auto-analysis">Auto Analysis</h2>
        <p>Tools for quick insights <strong>before</strong> deep and time-consuming investigation:</p>

        <div class="docs-grid docs-grid-2">
          <DocsFeatureCard
            icon="bi bi-shield-check"
            title="Guardian"
            description="Automated analysis of flamegraphs. Traverses stacktraces to find suspicious patterns - infinite loops, excessive locking, problematic allocations."
          />
          <DocsFeatureCard
            icon="bi bi-graph-up"
            title="Auto-Analysis"
            description="Basic analysis of JFR recording data. Rule-based insights with color-coded results (OK, Warning, Critical) about application behavior."
          />
        </div>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
