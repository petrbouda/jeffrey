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
import DocsFeatureCard from '@/components/docs/DocsFeatureCard.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'how-it-works', text: 'How It Works', level: 2 },
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
      title="Performance Analyst"
      icon="bi bi-robot"
    />

    <div class="docs-content">

      <DocsCallout type="info" title="Incubating">
        Performance Analyst is a young project being incubated inside Jeffrey. The concepts below are
        stable, but APIs, configuration keys, and screens may still change between releases.
      </DocsCallout>

      <section class="hero">
        <p class="hero-eyebrow"><i class="bi bi-stars"></i> AI Performance Optimization</p>
        <h2 class="hero-title">From profiles to pull requests.</h2>
        <p class="hero-lede">
          Performance Analyst pulls the JFR recordings your <router-link to="/docs/hub">Jeffrey Hub</router-link>
          collects, turns each profile into a focused prompt, and asks an AI model — with read-only access
          to your source repository — to explain what's slow and how to fix it. Every recommendation comes
          with a severity grade and, where possible, a ready-to-apply patch.
        </p>
        <div class="hero-actions">
          <router-link class="hero-cta hero-cta-primary" to="/docs/perf-analyst/quick-start">
            <i class="bi bi-rocket-takeoff"></i>
            <span>Quick Start</span>
          </router-link>
          <router-link class="hero-cta hero-cta-ghost" to="/docs/perf-analyst/architecture">
            <i class="bi bi-diagram-3"></i>
            <span>Architecture</span>
          </router-link>
        </div>
      </section>

      <section class="value-tiles">
        <div class="value-tile tile-hub">
          <div class="value-icon"><i class="bi bi-cloud-download"></i></div>
          <h3>Hub-Connected</h3>
          <p>Browse workspaces, projects and instances on your Jeffrey Hubs over gRPC, and download recordings into local projects.</p>
        </div>
        <div class="value-tile tile-repo">
          <div class="value-icon"><i class="bi bi-git"></i></div>
          <h3>Repository-Aware</h3>
          <p>The AI clones your GitHub/GitLab repo and reads the actual source — so hotspots map to real files, methods and lines.</p>
        </div>
        <div class="value-tile tile-severity">
          <div class="value-icon"><i class="bi bi-bar-chart-steps"></i></div>
          <h3>Severity-Ranked</h3>
          <p>Each finding is graded CRITICAL / HIGH / MEDIUM / LOW and ranked in a single Overview across all your projects.</p>
        </div>
      </section>

      <h2 id="how-it-works">How It Works</h2>
      <p>
        Performance Analyst sits between a Jeffrey Hub (the source of recordings) and your source
        repository (the source of truth for the code). For a chosen recording and event type it:
      </p>
      <ol class="step-list">
        <li><span class="step-n">1</span> Downloads the JFR recording from the Hub and stores it in a local project.</li>
        <li><span class="step-n">2</span> Builds a deterministic <strong>flamegraph prompt</strong> — a markdown summary of the hottest stacks — and caches it.</li>
        <li><span class="step-n">3</span> Asks the AI to grade severity, write recommendations, and propose a unified-diff patch.</li>
        <li><span class="step-n">4</span> Stores the result and surfaces it, ranked by severity, in the Overview.</li>
      </ol>

      <h2 id="key-features">Key Features</h2>

      <div class="docs-grid docs-grid-2">
        <DocsFeatureCard
          color="green"
          icon="bi bi-cloud-download"
          title="Hub Browsing & Download"
          description="Register one or more Jeffrey Hubs and browse their workspaces, projects and instances. Download recordings on demand into local projects."
        />
        <DocsFeatureCard
          color="blue"
          icon="bi bi-fire"
          title="Flamegraph AI Prompts"
          description="Each recording is reduced to a deterministic markdown summary of its hottest stacks per event type, cached so it is computed only once."
        />
        <DocsFeatureCard
          color="purple"
          icon="bi bi-git"
          title="Repository-Aware Recommendations"
          description="The model clones your GitHub/GitLab repo and reads it with read-only tools — listFiles, glob, readFile and grep — to ground every finding in real code."
        />
        <DocsFeatureCard
          color="red"
          icon="bi bi-file-earmark-diff"
          title="Severity & Patches"
          description="Findings are graded CRITICAL / HIGH / MEDIUM / LOW, with markdown explanations and an optional ready-to-apply unified-diff patch."
        />
        <DocsFeatureCard
          color="orange"
          icon="bi bi-bar-chart-steps"
          title="Severity Overview"
          description="A single dashboard ranks the highest-impact recommendations across every project, so you always know what to fix first."
        />
        <DocsFeatureCard
          color="neutral"
          icon="bi bi-cpu"
          title="Pluggable AI Providers"
          description="Run against Claude, OpenAI (ChatGPT), a self-hosted Ollama model, or the Claude Code CLI in headless mode with an MCP tool server."
        />
      </div>

      <h2 id="when-to-use">When to Use</h2>
      <ul class="usecase-list">
        <li><i class="bi bi-check2-circle"></i> Turning JFR recordings collected by a Hub into concrete, code-level optimization advice.</li>
        <li><i class="bi bi-check2-circle"></i> Triaging many services at once — letting severity ranking point you at the worst offenders first.</li>
        <li><i class="bi bi-check2-circle"></i> Getting a starting patch for a hotspot instead of only a flame graph to stare at.</li>
        <li><i class="bi bi-check2-circle"></i> Running AI analysis headlessly (CI, a server, your own subscription) rather than clicking through Microscope.</li>
      </ul>

      <p class="footnote">
        Looking for deep, interactive analysis of a single recording — flame graphs, Guardian, heap dumps,
        the OQL assistant? That's <router-link to="/docs/microscope">Jeffrey Microscope</router-link>.
        Performance Analyst is the automated, recommendation-first companion.
      </p>

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
    radial-gradient(120% 100% at 100% 0%, rgba(16, 185, 129, 0.12) 0%, rgba(16, 185, 129, 0) 60%),
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
  background: radial-gradient(circle, rgba(16, 185, 129, 0.16) 0%, rgba(16, 185, 129, 0) 70%);
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
  color: #047857;
  background: #ecfdf5;
  border-radius: 999px;
}

.hero-eyebrow i { font-size: 0.8rem; }

.hero .hero-title {
  margin: 0 0 0.6rem;
  padding-bottom: 0;
  border-bottom: none;
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
  max-width: 62ch;
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
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  box-shadow: 0 4px 14px rgba(16, 185, 129, 0.35);
}

.hero .hero-cta-primary:hover {
  box-shadow: 0 6px 18px rgba(16, 185, 129, 0.45);
  color: #fff;
}

.hero .hero-cta-ghost {
  color: #047857;
  background: #fff;
  border: 1px solid #a7f3d0;
}

.hero .hero-cta-ghost:hover {
  background: #f0fdf4;
  color: #047857;
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

.tile-hub .value-icon      { background: #ecfdf5; color: #059669; }
.tile-hub:hover            { border-color: #a7f3d0; }

.tile-repo .value-icon     { background: #ede9fe; color: #6d28d9; }
.tile-repo:hover           { border-color: #c4b5fd; }

.tile-severity .value-icon { background: #ffedd5; color: #c2410c; }
.tile-severity:hover       { border-color: #fdba74; }

/* ============ STEP LIST ============ */
.step-list {
  list-style: none;
  padding: 0;
  margin: 0.75rem 0 1.5rem;
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

/* ============ USECASE LIST ============ */
.usecase-list {
  list-style: none;
  padding: 0;
  margin: 0.75rem 0 1.25rem;
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

.footnote {
  margin-top: 1.5rem;
  padding: 0.9rem 1.1rem;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: 10px;
  font-size: 0.9rem;
  color: #334155;
  line-height: 1.55;
}

/* ============ RESPONSIVE ============ */
@media (max-width: 768px) {
  .hero { padding: 1.75rem 1.25rem; }
  .hero .hero-title { font-size: 1.55rem; }
  .hero-lede { font-size: 0.95rem; }
  .value-tiles { grid-template-columns: 1fr; }
}
</style>
