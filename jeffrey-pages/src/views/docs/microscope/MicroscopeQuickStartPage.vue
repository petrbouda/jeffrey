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
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'install', text: 'Install', level: 2 },
  { id: 'open-ui', text: 'Open the UI', level: 2 },
  { id: 'upload-recording', text: 'Upload a Recording', level: 2 },
  { id: 'analyze-profile', text: 'Analyze Your Profile', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Quick Start"
      icon="bi bi-rocket-takeoff"
    />

    <div class="docs-content">

      <section class="hero">
        <p class="hero-eyebrow"><i class="bi bi-stopwatch"></i> Up and running in minutes</p>
        <h2 class="hero-title">Install, open, analyze.</h2>
        <p class="hero-lede">
          Pick Docker or a plain Java run, point a browser at <code>localhost:8080</code>, drop a JFR
          recording or heap dump, and you're inside the Microscope. The whole loop is four steps.
        </p>

        <router-link to="/tour-with-examples" class="hero-promo">
          <span class="hero-promo-icon"><i class="bi bi-box-seam"></i></span>
          <span class="hero-promo-body">
            <strong>No JFR yet?</strong> The
            <strong>Tour with Examples</strong> image ships with pre-loaded sample recordings —
            poke at flame graphs, the Guardian, and the AI assistant before instrumenting your own
            application.
          </span>
          <span class="hero-promo-arrow"><i class="bi bi-arrow-right"></i></span>
        </router-link>
      </section>

      <h2 id="install">Install</h2>
      <p>Two ways to run Microscope. Pick whichever fits your environment.</p>

      <section class="install-tiles">
        <div class="install-tile tile-java">
          <div class="install-icon"><i class="bi bi-cup-hot"></i></div>
          <h3>Standalone JAR <span class="tile-badge">Recommended</span></h3>
          <p>Run Microscope directly on your machine — best for day-to-day desktop use.</p>
          <ol class="install-steps">
            <li>
              Download <code>microscope.jar</code> from
              <a href="https://github.com/petrbouda/jeffrey/releases" target="_blank" rel="noopener">GitHub Releases</a>.
            </li>
            <li>Make sure <strong>Java 25 or newer</strong> is on your <code>PATH</code>.</li>
            <li>Start it:</li>
          </ol>
          <DocsCodeBlock
            language="bash"
            code="java -jar microscope.jar"
          />
        </div>

        <div class="install-tile tile-docker">
          <div class="install-icon"><i class="bi bi-box-seam"></i></div>
          <h3>Docker</h3>
          <p>Zero local dependencies — just Docker. Convenient for trying out the examples image.</p>
          <DocsCodeBlock
            language="bash"
            code="docker run -it --network host petrbouda/microscope"
          />
          <p class="install-note">
            Or grab the version with pre-loaded examples:
          </p>
          <DocsCodeBlock
            language="bash"
            code="docker run -it --network host petrbouda/microscope-examples"
          />
        </div>
      </section>

      <h2 id="open-ui">Open the UI</h2>
      <p>Microscope listens on port <strong>8080</strong> by default. Open the URL in any modern browser:</p>

      <DocsCodeBlock
        language="text"
        code="http://localhost:8080"
      />

      <DocsCallout type="info">
        You should land on the Microscope home page. With the <code>microscope-examples</code>
        image, the example recordings are pre-loaded on the Recordings page — skip the next step.
      </DocsCallout>

      <h2 id="upload-recording">Upload a Recording</h2>
      <p>Open the <strong>Recordings</strong> page from the top navigation and bring an artifact in:</p>

      <div class="workflow-steps">
        <div class="workflow-step">
          <div class="step-number">1</div>
          <div class="step-content">
            <h4><i class="bi bi-upload"></i> Drop a file</h4>
            <p>Drag a <code>.jfr</code> recording or a <code>.hprof</code> heap dump onto the upload panel.</p>
          </div>
        </div>
        <div class="workflow-step">
          <div class="step-number">2</div>
          <div class="step-content">
            <h4><i class="bi bi-cpu"></i> Click <em>Analyze</em></h4>
            <p>Microscope parses the artifact into a dedicated profile database. Larger recordings take longer.</p>
          </div>
        </div>
      </div>

      <h2 id="analyze-profile">Analyze Your Profile</h2>
      <p>Once initialization finishes, the profile opens with the full toolset:</p>

      <ul class="usecase-list">
        <li><i class="bi bi-check2-circle"></i> <strong>Flame graphs</strong> — total, allocation, lock, wall-clock; differential views for two-profile comparisons.</li>
        <li><i class="bi bi-check2-circle"></i> <strong>Timeseries &amp; sub-second timelines</strong> — see hot spots over time, zoom into millisecond windows.</li>
        <li><i class="bi bi-check2-circle"></i> <strong>Threads, GC, JIT, safepoints</strong> — every JFR event surfaced as a usable view.</li>
        <li><i class="bi bi-check2-circle"></i> <strong>Guardian</strong> — automated checks flag the usual culprits (lock contention, GC pressure, hashmap collisions, …).</li>
        <li><i class="bi bi-check2-circle"></i> <strong>AI assistant</strong> — chat with Claude or OpenAI over the active profile or heap dump.</li>
      </ul>

      <DocsCallout type="tip">
        Use the sidebar to jump between analysis modes. The
        <router-link to="/docs/microscope/profiles">Profiles</router-link> page covers the full
        analysis surface.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* ============ HERO ============ */
.hero {
  margin: 0.25rem 0 1.5rem;
  padding: 2rem 2rem 1.85rem;
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
  font-size: 1.85rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: #0f172a;
  line-height: 1.15;
}

.hero-lede {
  margin: 0;
  font-size: 1rem;
  line-height: 1.55;
  color: #475569;
  max-width: 60ch;
}

/* ============ HERO PROMO (embedded "Tour with Examples" CTA) ============ */
.hero .hero-promo {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  margin: 1.25rem 0 0;
  padding: 0.85rem 1rem;
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid #c4b5fd;
  border-left: 4px solid #7c3aed;
  border-radius: 10px;
  text-decoration: none;
  color: #334155;
  box-shadow: 0 4px 14px rgba(124, 58, 237, 0.10);
  transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease;
  position: relative;
  z-index: 1;
}

.hero .hero-promo:hover {
  transform: translateY(-1px);
  border-color: #a78bfa;
  border-left-color: #6d28d9;
  box-shadow: 0 8px 22px rgba(124, 58, 237, 0.18);
  text-decoration: none;
  color: #334155;
}

.hero-promo-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  min-width: 36px;
  border-radius: 8px;
  background: linear-gradient(135deg, #8b5cf6 0%, #6d28d9 100%);
  color: #fff;
  font-size: 1rem;
  box-shadow: 0 2px 6px rgba(124, 58, 237, 0.25);
}

.hero-promo-body {
  flex: 1;
  font-size: 0.88rem;
  line-height: 1.5;
  color: #334155;
}

.hero-promo-body strong { color: #1f2937; }

.hero-promo-arrow {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  min-width: 28px;
  border-radius: 50%;
  background: #ede9fe;
  color: #6d28d9;
  font-size: 0.85rem;
  transition: transform 160ms ease, background-color 160ms ease;
}

.hero .hero-promo:hover .hero-promo-arrow {
  background: #ddd6fe;
  transform: translateX(2px);
}

/* ============ INSTALL TILES ============ */
.install-tiles {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
  margin: 0.75rem 0 1.75rem;
}

.install-tile {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  padding: 1.25rem 1.15rem 1.15rem;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease;
}

.install-tile:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.06);
}

.install-tile h3 {
  margin: 0.4rem 0 0.15rem;
  font-size: 1.05rem;
  font-weight: 650;
  color: #0f172a;
  letter-spacing: -0.01em;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.install-tile > p {
  margin: 0 0 0.3rem;
  font-size: 0.88rem;
  color: #475569;
}

.install-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  font-size: 1.15rem;
}

.tile-docker .install-icon { background: #dbeafe; color: #1d4ed8; }
.tile-docker:hover         { border-color: #93c5fd; }

.tile-java .install-icon   { background: #fef3c7; color: #b45309; }
.tile-java:hover           { border-color: #fcd34d; }

.tile-badge {
  display: inline-block;
  padding: 0.15rem 0.5rem;
  font-size: 0.65rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #0e7490;
  background: #cffafe;
  border-radius: 999px;
}

.install-steps {
  margin: 0.4rem 0 0.6rem;
  padding-left: 1.25rem;
  font-size: 0.88rem;
  color: #334155;
  line-height: 1.5;
}

.install-steps li { margin-bottom: 0.25rem; }

.install-note {
  margin: 0.6rem 0 0.4rem;
  font-size: 0.85rem;
  color: #64748b;
}

/* ============ WORKFLOW STEPS ============ */
.workflow-steps {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin: 1rem 0 1.5rem;
}

.workflow-step {
  display: flex;
  gap: 1rem;
  padding: 1rem 1.1rem;
  background: #f8fafc;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  transition: transform 140ms ease, box-shadow 140ms ease;
}

.workflow-step:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.05);
}

.step-number {
  width: 36px;
  height: 36px;
  min-width: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, #5e64ff 0%, #4338ca 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 0.85rem;
  box-shadow: 0 4px 10px rgba(94, 100, 255, 0.25);
}

.step-content { flex: 1; }

.step-content h4 {
  margin: 0 0 0.2rem;
  font-size: 0.95rem;
  font-weight: 650;
  color: #1f2937;
  display: flex;
  align-items: center;
  gap: 0.45rem;
}

.step-content h4 i {
  font-size: 0.95rem;
  color: #5e64ff;
}

.step-content p {
  margin: 0;
  font-size: 0.86rem;
  color: #475569;
  line-height: 1.5;
}

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
  .hero { padding: 1.6rem 1.2rem; }
  .hero-title { font-size: 1.5rem; }
  .hero-lede { font-size: 0.95rem; }
  .hero .hero-promo { flex-wrap: wrap; }
  .install-tiles { grid-template-columns: 1fr; }
}
</style>
