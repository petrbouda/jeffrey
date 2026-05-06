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
  { id: 'what-you-can-drop', text: 'What You Can Drop Here', level: 2 },
  { id: 'workflow', text: 'Workflow', level: 2 },
  { id: 'recording-groups', text: 'Recording Groups', level: 2 },
  { id: 'recordings-vs-projects', text: 'Recordings vs Projects', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Recordings"
      icon="bi bi-record-circle"
    />

    <div class="docs-content">

      <section class="hero">
        <p class="hero-eyebrow"><i class="bi bi-collection-play"></i> Single Inbox</p>
        <h2 class="hero-title">Every JFR file. Every heap dump. One place.</h2>
        <p class="hero-lede">
          The <strong>Recordings</strong> page is where every artifact you investigate lands —
          whether you uploaded it yourself or it was auto-downloaded from a live project session.
          Drop a JFR recording or a heap dump, click <em>Analyze</em>, and you're a profile away
          from flame graphs, timelines, dominator trees, and the AI assistant.
        </p>
      </section>

      <h2 id="what-you-can-drop">What You Can Drop Here</h2>

      <section class="artifact-tiles">
        <div class="artifact-tile tile-jfr">
          <div class="artifact-icon"><i class="bi bi-record-circle"></i></div>
          <h3>JFR Recordings</h3>
          <p>Java Flight Recorder files captured by async-profiler, the JDK, or the Jeffrey CLI.</p>
          <ul class="artifact-features">
            <li><i class="bi bi-fire"></i> Flame graphs &amp; differential views</li>
            <li><i class="bi bi-graph-up"></i> Timeseries &amp; sub-second timelines</li>
            <li><i class="bi bi-clock-history"></i> Thread, GC, JIT, safepoint analysis</li>
            <li><i class="bi bi-shield-check"></i> Guardian automated checks</li>
          </ul>
          <p class="artifact-extension">
            <code>.jfr</code> &nbsp;·&nbsp; <code>.jfr.lz4</code>
          </p>
        </div>

        <div class="artifact-tile tile-heap">
          <div class="artifact-icon"><i class="bi bi-hdd-stack"></i></div>
          <h3>Heap Dumps</h3>
          <p>HPROF heap dumps written by the JVM (<code>jcmd</code>, <code>jmap</code>, OOM auto-dump).</p>
          <ul class="artifact-features">
            <li><i class="bi bi-bar-chart"></i> Class histograms</li>
            <li><i class="bi bi-diagram-3"></i> Dominator tree exploration</li>
            <li><i class="bi bi-bug"></i> Leak suspects</li>
            <li><i class="bi bi-terminal"></i> OQL queries (with AI assistant)</li>
          </ul>
          <p class="artifact-extension">
            <code>.hprof</code> &nbsp;·&nbsp; <code>.hprof.gz</code>
          </p>
        </div>
      </section>

      <DocsCallout type="info">
        <strong>Where do recordings come from?</strong> Manual upload directly on this page <em>or</em>
        the <strong>Download</strong> button on a recording session inside a project's Instances view.
        Both paths persist the file to local storage; the download path additionally tags the artifact
        with <code>server › workspace › project</code> so you can trace it back to its source.
      </DocsCallout>

      <h2 id="workflow">Workflow</h2>
      <p>Two ways to bring an artifact in, then a single path to analysis:</p>

      <div class="workflow-steps">
        <div class="workflow-step">
          <div class="step-number">1a</div>
          <div class="step-content">
            <h4><i class="bi bi-upload"></i> Manual upload</h4>
            <p>Drag a JFR file or heap dump onto the upload panel at the top of the Recordings page.</p>
          </div>
        </div>
        <div class="workflow-step">
          <div class="step-number">1b</div>
          <div class="step-content">
            <h4><i class="bi bi-cloud-arrow-down"></i> Auto-download from a project session</h4>
            <p>From a project's <strong>Instances</strong> view, open a session and click <strong>Download</strong>. The merged recording (plus heap dumps and logs) is streamed to local storage and shows up tagged with its origin.</p>
          </div>
        </div>
        <div class="workflow-step">
          <div class="step-number">2</div>
          <div class="step-content">
            <h4><i class="bi bi-cpu"></i> Analyze</h4>
            <p>Click <strong>Analyze</strong> on a recording or heap dump to create a profile. Jeffrey parses the file and builds a dedicated profile database.</p>
          </div>
        </div>
        <div class="workflow-step">
          <div class="step-number">3</div>
          <div class="step-content">
            <h4><i class="bi bi-search"></i> Investigate</h4>
            <p>Open the profile to access flame graphs, timeseries, thread analysis, dominator trees — and chat with the AI assistant about what it sees.</p>
          </div>
        </div>
      </div>

      <h2 id="recording-groups">Recording Groups</h2>
      <p>Recordings can be organized into named <strong>groups</strong>:</p>
      <ul class="usecase-list">
        <li><i class="bi bi-check2-circle"></i> <strong>Create groups</strong> to categorise artifacts (e.g. by investigation topic, incident, or date).</li>
        <li><i class="bi bi-check2-circle"></i> <strong>Move artifacts</strong> with drag-and-drop or the move action; each artifact belongs to at most one group.</li>
        <li><i class="bi bi-check2-circle"></i> <strong>Delete groups</strong> to remove the group and its artifacts together.</li>
      </ul>
      <p>Groups are flat (one level) and orthogonal to the origin breadcrumb. A "Load tests" group can mix manually-uploaded files and auto-downloaded artifacts from any project — the breadcrumb still tells you where each came from.</p>

      <h2 id="recordings-vs-projects">Recordings vs Projects</h2>
      <p>Projects organize <strong>live applications</strong> — their instances, sessions, profiler settings, and event streams. Recordings is the post-capture artifact view: once a session has produced something worth keeping, you bring it here for analysis.</p>

      <table>
        <thead>
          <tr>
            <th>You're looking for…</th>
            <th>Go to…</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>Live event stream from a running JVM</td>
            <td>Project → Instances → session → Live Stream</td>
          </tr>
          <tr>
            <td>List of past recording sessions in a project</td>
            <td>Project → Instances → session</td>
          </tr>
          <tr>
            <td>Analyzing a captured JFR or heap dump</td>
            <td><strong>Recordings</strong> (top nav)</td>
          </tr>
          <tr>
            <td>Profile (post-analysis)</td>
            <td>Click any analyzed artifact on the Recordings page</td>
          </tr>
        </tbody>
      </table>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* ============ HERO ============ */
.hero {
  margin: 0.25rem 0 2rem;
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

/* ============ ARTIFACT TILES ============ */
.artifact-tiles {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
  margin: 0.75rem 0 1.75rem;
}

.artifact-tile {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 1.25rem 1.15rem 1.15rem;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease;
}

.artifact-tile:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.06);
}

.artifact-tile h3 {
  margin: 0.4rem 0 0.15rem;
  font-size: 1.05rem;
  font-weight: 650;
  color: #0f172a;
  letter-spacing: -0.01em;
}

.artifact-tile > p {
  margin: 0 0 0.5rem;
  font-size: 0.88rem;
  line-height: 1.5;
  color: #475569;
}

.artifact-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  font-size: 1.15rem;
}

.tile-jfr .artifact-icon { background: #ffedd5; color: #c2410c; }
.tile-jfr:hover         { border-color: #fdba74; }

.tile-heap .artifact-icon { background: #fee2e2; color: #b91c1c; }
.tile-heap:hover         { border-color: #fca5a5; }

.artifact-features {
  list-style: none;
  padding: 0;
  margin: 0.25rem 0 0.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.artifact-features li {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  font-size: 0.85rem;
  color: #334155;
  line-height: 1.4;
}

.artifact-features li i {
  font-size: 0.95rem;
  color: #5e64ff;
  flex-shrink: 0;
}

.artifact-extension {
  margin: 0;
  padding-top: 0.55rem;
  border-top: 1px dashed #e2e8f0;
  font-size: 0.78rem;
  color: #64748b;
}

.artifact-extension code {
  background: #f1f5f9;
  padding: 0.1rem 0.4rem;
  border-radius: 4px;
}

/* ============ WORKFLOW STEPS ============ */
.workflow-steps {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin: 1.25rem 0 1.5rem;
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

/* ============ USECASE LIST (reused in Recording Groups) ============ */
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
  .hero { padding: 1.6rem 1.2rem; }
  .hero-title { font-size: 1.5rem; }
  .hero-lede { font-size: 0.95rem; }
  .artifact-tiles { grid-template-columns: 1fr; }
}
</style>
