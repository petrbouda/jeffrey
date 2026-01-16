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
import { useDocsNavigation } from '@/composables/useDocsNavigation';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { adjacentPages } = useDocsNavigation();
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
      <nav class="docs-breadcrumb">
        <router-link to="/docs" class="breadcrumb-item">
          <i class="bi bi-book me-1"></i>Docs
        </router-link>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item">Goals</span>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item active">Overview</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-bullseye"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Goals</h1>
        </div>
      </header>

      <div class="docs-content">
        <p>Jeffrey is built with clear objectives that guide every feature and design decision.</p>

        <h2 id="core-objectives">Core Objectives</h2>
        <p>Jeffrey addresses three distinct profiling workflows through dedicated workspace types:</p>

        <div class="objectives-grid">
          <div class="objective-card sandbox">
            <div class="card-header">
              <i class="bi bi-house"></i>
              <h4>Quick Local Analysis</h4>
              <span class="workspace-badge">Sandbox</span>
            </div>
            <div class="card-body">
              <p>Quickly analyze locally available JFR recordings and organize them into projects. Upload a file and start investigating within seconds.</p>
            </div>
          </div>
          <div class="objective-card live">
            <div class="card-header">
              <i class="bi bi-cloud"></i>
              <h4>Recording Collection Infrastructure</h4>
              <span class="workspace-badge">Live</span>
            </div>
            <div class="card-body">
              <p>Conveniently collect JFR recordings and artifacts from running applications, especially in containerized cloud environments like Kubernetes.</p>
            </div>
          </div>
          <div class="objective-card remote">
            <div class="card-header">
              <i class="bi bi-pc-display"></i>
              <h4>Avoid Cloud Computation Costs</h4>
              <span class="workspace-badge">Remote</span>
            </div>
            <div class="card-body">
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

        <div class="design-points">
          <div class="design-point">
            <div class="point-icon"><i class="bi bi-hdd"></i></div>
            <div class="point-content">
              <strong>Filesystem & Blob Storage</strong>
              <p>Use cheaper storage systems instead of expensive managed databases. Recordings are stored on disk or blob storage.</p>
            </div>
          </div>
          <div class="design-point">
            <div class="point-icon"><i class="bi bi-database"></i></div>
            <div class="point-content">
              <strong>In-Process Database (DuckDB)</strong>
              <p>Use file-based, in-process databases to avoid deploying and maintaining separate database servers.</p>
            </div>
          </div>
          <div class="design-point">
            <div class="point-icon"><i class="bi bi-box-seam"></i></div>
            <div class="point-content">
              <strong>Minimal Components</strong>
              <p>No additional services to deploy. Jeffrey runs as a single application with everything included.</p>
            </div>
          </div>
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

        <div class="analysis-cards">
          <div class="analysis-card">
            <div class="card-icon"><i class="bi bi-shield-check"></i></div>
            <div class="card-content">
              <h4>Guardian</h4>
              <p>Automated analysis of flamegraphs. Traverses stacktraces to find suspicious patterns - infinite loops, excessive locking, problematic allocations.</p>
            </div>
          </div>
          <div class="analysis-card">
            <div class="card-icon"><i class="bi bi-graph-up"></i></div>
            <div class="card-content">
              <h4>Auto-Analysis</h4>
              <p>Basic analysis of JFR recording data. Rule-based insights with color-coded results (OK, Warning, Critical) about application behavior.</p>
            </div>
          </div>
        </div>
      </div>

      <nav class="docs-nav-footer">
        <router-link
          v-if="adjacentPages.prev"
          :to="`/docs/${adjacentPages.prev.category}/${adjacentPages.prev.path}`"
          class="nav-link prev"
        >
          <i class="bi bi-arrow-left"></i>
          <div class="nav-text">
            <span class="nav-label">Previous</span>
            <span class="nav-title">{{ adjacentPages.prev.title }}</span>
          </div>
        </router-link>
        <div v-else class="nav-spacer"></div>
        <router-link
          v-if="adjacentPages.next"
          :to="`/docs/${adjacentPages.next.category}/${adjacentPages.next.path}`"
          class="nav-link next"
        >
          <div class="nav-text">
            <span class="nav-label">Next</span>
            <span class="nav-title">{{ adjacentPages.next.title }}</span>
          </div>
          <i class="bi bi-arrow-right"></i>
        </router-link>
      </nav>
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* Objectives Grid */
.objectives-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
  margin: 1.5rem 0;
}

.objective-card {
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
}

.objective-card .card-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
  flex-wrap: wrap;
}

.objective-card .card-header i {
  font-size: 1.1rem;
}

.objective-card .card-header h4 {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 600;
  color: #343a40;
  flex: 1;
}

.workspace-badge {
  font-size: 0.7rem;
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  background: #e2e8f0;
  color: #495057;
}

.objective-card .card-body {
  padding: 1rem;
  background: #fff;
}

.objective-card .card-body p {
  margin: 0;
  font-size: 0.85rem;
  color: #5e6e82;
}

/* Objective card themes */
.objective-card.sandbox .card-header i {
  color: #f59e0b;
}

.objective-card.live .card-header i {
  color: #5e64ff;
}

.objective-card.remote .card-header i {
  color: #10b981;
}

/* Design Points */
.design-points {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin: 1.5rem 0;
}

.design-point {
  display: flex;
  gap: 1rem;
  align-items: flex-start;
  padding: 1rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.point-icon {
  width: 36px;
  height: 36px;
  min-width: 36px;
  border-radius: 8px;
  background: #e2e8f0;
  color: #495057;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1rem;
}

.point-content {
  flex: 1;
}

.point-content strong {
  display: block;
  color: #343a40;
  margin-bottom: 0.25rem;
}

.point-content p {
  margin: 0;
  font-size: 0.85rem;
  color: #5e6e82;
}

/* Analysis Cards */
.analysis-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
  margin: 1.5rem 0;
}

.analysis-card {
  display: flex;
  gap: 1rem;
  padding: 1rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.card-icon {
  width: 40px;
  height: 40px;
  min-width: 40px;
  border-radius: 8px;
  background: linear-gradient(135deg, #5e64ff 0%, #4338ca 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.1rem;
}

.card-content {
  flex: 1;
}

.card-content h4 {
  margin: 0 0 0.25rem 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: #343a40;
}

.card-content p {
  margin: 0;
  font-size: 0.85rem;
  color: #5e6e82;
}

/* Responsive */
@media (max-width: 992px) {
  .objectives-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .analysis-cards {
    grid-template-columns: 1fr;
  }
}
</style>
