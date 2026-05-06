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
  { id: 'whats-deployed', text: "What's Deployed", level: 2 },
  { id: 'topology', text: 'Topology', level: 2 },
  { id: 'lifecycle', text: 'Build → Deploy → Run', level: 2 },
  { id: 'prerequisites', text: 'Prerequisites', level: 2 },
  { id: 'next', text: "Where to Go Next", level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Deployment"
      icon="bi bi-cloud-upload"
    />

    <div class="docs-content">

      <section class="hero">
        <p class="hero-eyebrow"><i class="bi bi-github"></i> Worked example: jeffrey-testapp</p>
        <h2 class="hero-title">A real Helm-based deployment, end to end.</h2>
        <p class="hero-lede">
          The pages in this section walk through the
          <a href="https://github.com/petrbouda/jeffrey-testapp" target="_blank" rel="noopener">jeffrey-testapp</a>
          repository — a two-module Spring Boot 4 setup deployed via three Helm charts. It
          shows the full collection pipeline: the Jeffrey JIB extension at build time, the
          shared-volume <code>copy-libs</code> pattern at deploy time, and the CLI-driven init
          flow at container start. Every YAML, <code>pom.xml</code>, and <code>jeffrey-base.conf</code>
          snippet on the following pages is taken verbatim from that repo.
        </p>
        <div class="hero-actions">
          <a class="hero-cta hero-cta-primary" href="https://github.com/petrbouda/jeffrey-testapp" target="_blank" rel="noopener">
            <i class="bi bi-github"></i>
            <span>jeffrey-testapp on GitHub</span>
          </a>
          <router-link class="hero-cta hero-cta-ghost" to="/docs/server/deployment/helm-chart">
            <i class="bi bi-file-earmark-code"></i>
            <span>Jump to Helm Chart</span>
          </router-link>
        </div>
      </section>

      <h2 id="whats-deployed">What's Deployed</h2>
      <p>
        Four Helm releases land in a single namespace (default <code>jeffrey-testapp</code>),
        all sharing one <code>jeffrey-pvc</code> volume:
      </p>

      <div class="release-tiles">
        <div class="release-tile tile-server">
          <div class="release-icon"><i class="bi bi-cloud"></i></div>
          <div class="release-body">
            <h3>jeffrey-server</h3>
            <p>The Jeffrey Server itself. Owns <code>jeffrey-pvc</code>; <code>copy-libs</code> publishes the CLI bundle, agent JAR, and async-profiler library into the shared volume for the testapp pods to consume. Exposes HTTP <code>8080</code> + gRPC <code>9090</code>.</p>
          </div>
        </div>
        <div class="release-tile tile-direct">
          <div class="release-icon"><i class="bi bi-lightning-charge"></i></div>
          <div class="release-body">
            <h3>direct (jeffrey-testapp-server, mode=direct)</h3>
            <p>SQLite-backed REST app running the efficient <code>PersonService</code> (<code>efficient.mode=true</code>).</p>
          </div>
        </div>
        <div class="release-tile tile-dom">
          <div class="release-icon"><i class="bi bi-bricks"></i></div>
          <div class="release-body">
            <h3>dom (jeffrey-testapp-server, mode=dom)</h3>
            <p>Same app running the inefficient <code>PersonService</code> (<code>efficient.mode=false</code>). Deployed alongside <code>direct</code> so a single workload generates two distinct profiles to compare.</p>
          </div>
        </div>
        <div class="release-tile tile-client">
          <div class="release-icon"><i class="bi bi-arrow-repeat"></i></div>
          <div class="release-body">
            <h3>jeffrey-testapp-client</h3>
            <p>A single load generator that drives both <code>direct</code> and <code>dom</code> servers concurrently — each base URL gets its own scheduler.</p>
          </div>
        </div>
      </div>

      <DocsCallout type="tip">
        <strong>Side-by-side comparison.</strong> Installing <code>direct</code> and <code>dom</code>
        from the same chart with different <code>--set mode=…</code> values produces two distinct
        projects in Jeffrey Server (<code>direct-jeffrey-testapp-server</code> and
        <code>dom-jeffrey-testapp-server</code>). Open both in Microscope for a one-click
        differential profile.
      </DocsCallout>

      <h2 id="topology">Topology</h2>
      <p>
        The diagram below shows the runtime topology — one PVC, four pods, one direction of
        data flow per arrow. <code>jeffrey-server</code> publishes the CLI bundle into
        <code>/mnt/jeffrey/libs/current/</code>; the three monitored pods read it back when
        their JIB-wrapped entrypoints run <code>jeffrey-cli init</code>; profile data flows
        back to <code>jeffrey-server</code> over gRPC <code>9090</code>.
      </p>

      <div class="cluster-diagram">
        <div class="cluster-label">Kubernetes namespace: <code>jeffrey-testapp</code></div>

        <div class="diagram-row top-row">
          <div class="cluster-pod pod-server">
            <div class="pod-header"><i class="bi bi-cloud"></i><span>jeffrey-server</span></div>
            <div class="pod-meta">HTTP 8080 · gRPC 9090</div>
            <div class="pod-tag">copy-libs writer</div>
          </div>
        </div>

        <div class="diagram-arrow-block">
          <div class="diagram-arrow up"><i class="bi bi-arrow-up"></i><small>gRPC ingestion</small></div>
          <div class="diagram-arrow down"><i class="bi bi-arrow-down"></i><small>copy-libs writes</small></div>
        </div>

        <div class="cluster-pvc">
          <i class="bi bi-hdd-stack"></i>
          <span>jeffrey-pvc</span>
          <small>RWX · /mnt/jeffrey · libs/current/{jeffrey-cli, agent, libasyncProfiler}</small>
        </div>

        <div class="diagram-arrow-block">
          <div class="diagram-arrow up"><i class="bi bi-arrow-up"></i><small>read at startup</small></div>
        </div>

        <div class="diagram-row bottom-row">
          <div class="cluster-pod pod-direct">
            <div class="pod-header"><i class="bi bi-lightning-charge"></i><span>direct</span></div>
            <div class="pod-meta">testapp-server · mode=direct</div>
            <div class="pod-tag">JIB entrypoint</div>
          </div>
          <div class="cluster-pod pod-dom">
            <div class="pod-header"><i class="bi bi-bricks"></i><span>dom</span></div>
            <div class="pod-meta">testapp-server · mode=dom</div>
            <div class="pod-tag">JIB entrypoint</div>
          </div>
          <div class="cluster-pod pod-client">
            <div class="pod-header"><i class="bi bi-arrow-repeat"></i><span>testapp-client</span></div>
            <div class="pod-meta">load generator</div>
            <div class="pod-tag">JIB entrypoint</div>
          </div>
        </div>

        <div class="cluster-ingress">
          <i class="bi bi-globe"></i>
          <span>Ingress / Microscope clients</span>
          <small>HTTP into jeffrey-server · gRPC for remote-workspace</small>
        </div>
      </div>

      <h2 id="lifecycle">Build → Deploy → Run</h2>
      <p>The end-to-end flow that this section's pages walk you through, in order:</p>

      <div class="lifecycle-steps">
        <div class="lifecycle-step">
          <div class="step-number">1</div>
          <div class="step-content">
            <h4><i class="bi bi-box-seam"></i> Build the image</h4>
            <p>The application's <code>pom.xml</code> wires the <strong>Jeffrey JIB extension</strong> into the <code>jib-maven-plugin</code>. <code>mvn jib:dockerBuild</code> produces an image whose entrypoint is wrapped to invoke <code>jeffrey-cli init</code> at container start. No agent or profiler binaries get baked in. <router-link to="/docs/server/deployment/jeffrey-jib">→ Jeffrey JIB Extension</router-link></p>
          </div>
        </div>
        <div class="lifecycle-step">
          <div class="step-number">2</div>
          <div class="step-content">
            <h4><i class="bi bi-hdd-stack"></i> Provision the shared volume</h4>
            <p><code>jeffrey-server</code>'s Helm chart creates a <code>ReadWriteMany</code> PVC; <code>copy-libs</code> populates <code>libs/current/</code> with the per-arch CLI binary, agent JAR, and async-profiler library. <router-link to="/docs/server/deployment/shared-volume">→ Shared Volume</router-link></p>
          </div>
        </div>
        <div class="lifecycle-step">
          <div class="step-number">3</div>
          <div class="step-content">
            <h4><i class="bi bi-terminal"></i> Configure the CLI</h4>
            <p>Each monitored pod mounts a small <code>jeffrey-base.conf</code> via ConfigMap. The wrapped entrypoint runs <code>jeffrey-cli init</code> against that config to generate the JVM-arg response file before the JVM boots. <router-link to="/docs/server/deployment/jeffrey-cli">→ Jeffrey CLI</router-link></p>
          </div>
        </div>
        <div class="lifecycle-step">
          <div class="step-number">4</div>
          <div class="step-content">
            <h4><i class="bi bi-file-earmark-code"></i> Deploy with Helm</h4>
            <p>Three Helm charts (<code>jeffrey-server</code> + two flavours of the testapp) installed with vanilla <code>helm upgrade --install</code>. An init container on the testapp pods polls <code>jeffrey-server</code>'s readiness probe so <code>copy-libs</code> always finishes before the JIB entrypoint looks for the CLI. <router-link to="/docs/server/deployment/helm-chart">→ Helm Chart</router-link></p>
          </div>
        </div>
      </div>

      <h2 id="prerequisites">Prerequisites</h2>
      <ul class="usecase-list">
        <li><i class="bi bi-check2-circle"></i> A Kubernetes cluster with <code>kubectl</code> configured. OrbStack / minikube / kind work for the dev path; production needs an RWX-capable <code>StorageClass</code> (NFS, EFS, Azure Files, …).</li>
        <li><i class="bi bi-check2-circle"></i> Helm v3 on your <code>PATH</code>.</li>
        <li><i class="bi bi-check2-circle"></i> Java 25 (the testapp builds on Java 25; the JIB base image is <code>eclipse-temurin:25-jre</code>).</li>
        <li><i class="bi bi-check2-circle"></i> Maven (3.9+) for the <code>jib:dockerBuild</code> / <code>jib:build</code> commands.</li>
        <li><i class="bi bi-check2-circle"></i> A container registry to push to — only needed if you're not running with <code>jib:dockerBuild</code> against a local Docker daemon.</li>
      </ul>

      <h2 id="next">Where to Go Next</h2>
      <p>The four sub-pages cover the testapp deployment from build time to running cluster:</p>

      <div class="next-grid">
        <router-link class="next-card" to="/docs/server/deployment/jeffrey-jib">
          <div class="next-icon"><i class="bi bi-box-seam"></i></div>
          <h4>Jeffrey JIB Extension</h4>
          <p>How the entrypoint wrapper gets baked into your image at build time.</p>
        </router-link>
        <router-link class="next-card" to="/docs/server/deployment/shared-volume">
          <div class="next-icon"><i class="bi bi-hdd-stack"></i></div>
          <h4>Shared Volume</h4>
          <p>The <code>copy-libs</code> pattern, PVC contract, and OrbStack/minikube fallback.</p>
        </router-link>
        <router-link class="next-card" to="/docs/server/deployment/jeffrey-cli">
          <div class="next-icon"><i class="bi bi-terminal"></i></div>
          <h4>Jeffrey CLI</h4>
          <p>Anatomy of <code>jeffrey-base.conf</code> and the runtime env-var contract.</p>
        </router-link>
        <router-link class="next-card" to="/docs/server/deployment/helm-chart">
          <div class="next-icon"><i class="bi bi-file-earmark-code"></i></div>
          <h4>Helm Chart</h4>
          <p>Full Kubernetes walkthrough: three charts, install scripts, init containers.</p>
        </router-link>
      </div>
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
  font-size: 1.85rem;
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

/* ============ RELEASE TILES ============ */
.release-tiles {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0.85rem;
  margin: 1rem 0 1.5rem;
}

.release-tile {
  display: flex;
  gap: 0.85rem;
  padding: 1rem 1.1rem;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease;
}

.release-tile:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.06);
}

.release-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  min-width: 40px;
  border-radius: 10px;
  font-size: 1.1rem;
}

.tile-server  .release-icon { background: #ede9fe; color: #6d28d9; }
.tile-direct  .release-icon { background: #ecfdf5; color: #047857; }
.tile-dom     .release-icon { background: #ffedd5; color: #c2410c; }
.tile-client  .release-icon { background: #cffafe; color: #0e7490; }

.release-tile:hover.tile-server  { border-color: #c4b5fd; }
.release-tile:hover.tile-direct  { border-color: #6ee7b7; }
.release-tile:hover.tile-dom     { border-color: #fdba74; }
.release-tile:hover.tile-client  { border-color: #67e8f9; }

.release-body { flex: 1; }

.release-body h3 {
  margin: 0 0 0.3rem;
  font-size: 0.95rem;
  font-weight: 650;
  color: #0f172a;
  letter-spacing: -0.01em;
}

.release-body p {
  margin: 0;
  font-size: 0.85rem;
  line-height: 1.5;
  color: #475569;
}

/* ============ CLUSTER DIAGRAM ============ */
.cluster-diagram {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.4rem;
  margin: 1.5rem 0;
  padding: 1.75rem 1.25rem 1.5rem;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px dashed #cbd5e1;
  border-radius: 12px;
  position: relative;
}

.cluster-label {
  position: absolute;
  top: 0.6rem;
  left: 0.85rem;
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: #64748b;
}

.cluster-label code {
  font-size: 0.7rem;
  background: #fff;
  padding: 0.05rem 0.35rem;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  margin-left: 0.25rem;
}

.diagram-row {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
  justify-content: center;
  margin-top: 1rem;
}

.diagram-row.top-row { margin-top: 1.5rem; }

.cluster-pod {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  align-items: center;
  padding: 0.75rem 0.95rem;
  min-width: 170px;
  background: #fff;
  border: 1.5px solid #e2e8f0;
  border-radius: 10px;
  text-align: center;
}

.pod-header {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-weight: 650;
  font-size: 0.85rem;
  color: #0f172a;
}

.pod-header i { font-size: 0.95rem; }

.pod-meta {
  font-size: 0.7rem;
  color: #64748b;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
}

.pod-tag {
  font-size: 0.65rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding: 0.1rem 0.45rem;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
}

.pod-server  { border-color: #c4b5fd; }
.pod-server  .pod-header { color: #6d28d9; }
.pod-server  .pod-tag    { background: #ede9fe; color: #6d28d9; }

.pod-direct  { border-color: #6ee7b7; }
.pod-direct  .pod-header { color: #047857; }
.pod-direct  .pod-tag    { background: #ecfdf5; color: #047857; }

.pod-dom     { border-color: #fdba74; }
.pod-dom     .pod-header { color: #c2410c; }
.pod-dom     .pod-tag    { background: #ffedd5; color: #c2410c; }

.pod-client  { border-color: #67e8f9; }
.pod-client  .pod-header { color: #0e7490; }
.pod-client  .pod-tag    { background: #cffafe; color: #0e7490; }

.diagram-arrow-block {
  display: flex;
  gap: 1.5rem;
  align-items: center;
  justify-content: center;
}

.diagram-arrow {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.1rem;
  color: #94a3b8;
}

.diagram-arrow i { font-size: 1.1rem; }
.diagram-arrow small {
  font-size: 0.6rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: #64748b;
}

.cluster-pvc {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  margin: 0.4rem 0;
  padding: 0.65rem 1.25rem;
  background: #fff;
  border: 2px solid #f59e0b;
  border-radius: 10px;
}

.cluster-pvc i { color: #f59e0b; font-size: 1.15rem; }
.cluster-pvc span { font-weight: 650; font-size: 0.9rem; color: #92400e; font-family: 'JetBrains Mono', 'Fira Code', monospace; }
.cluster-pvc small { font-size: 0.7rem; color: #6c757d; margin-left: 0.4rem; }

.cluster-ingress {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  margin-top: 1rem;
  padding: 0.6rem 1.1rem;
  background: #fff;
  border: 1.5px dashed #cbd5e1;
  border-radius: 10px;
}

.cluster-ingress i { font-size: 1.05rem; color: #475569; }
.cluster-ingress span { font-weight: 600; font-size: 0.85rem; color: #0f172a; }
.cluster-ingress small { font-size: 0.7rem; color: #64748b; margin-left: 0.4rem; }

@media (max-width: 768px) {
  .release-tiles { grid-template-columns: 1fr; }
  .diagram-row.bottom-row { flex-direction: column; align-items: stretch; }
}

/* ============ LIFECYCLE STEPS ============ */
.lifecycle-steps {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin: 1rem 0 1.5rem;
}

.lifecycle-step {
  display: flex;
  gap: 1rem;
  padding: 1rem 1.1rem;
  background: #f8fafc;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  transition: transform 140ms ease, box-shadow 140ms ease;
}

.lifecycle-step:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.05);
}

.step-number {
  width: 36px;
  height: 36px;
  min-width: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, #8b5cf6 0%, #6d28d9 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 0.85rem;
  box-shadow: 0 4px 10px rgba(124, 58, 237, 0.25);
}

.step-content { flex: 1; }

.step-content h4 {
  margin: 0 0 0.25rem;
  font-size: 0.95rem;
  font-weight: 650;
  color: #1f2937;
  display: flex;
  align-items: center;
  gap: 0.45rem;
}

.step-content h4 i { font-size: 0.95rem; color: #7c3aed; }

.step-content p {
  margin: 0;
  font-size: 0.86rem;
  color: #475569;
  line-height: 1.55;
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

/* ============ NEXT GRID ============ */
.next-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0.85rem;
  margin: 1rem 0 0.75rem;
}

.next-card {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: 1rem 1.15rem;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  text-decoration: none;
  transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease;
}

.next-card:hover {
  transform: translateY(-2px);
  border-color: #c4b5fd;
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.06);
  text-decoration: none;
}

.next-icon {
  width: 36px;
  height: 36px;
  border-radius: 9px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #ede9fe;
  color: #6d28d9;
  font-size: 1rem;
}

.next-card h4 {
  margin: 0.4rem 0 0.1rem;
  font-size: 0.95rem;
  font-weight: 650;
  color: #0f172a;
}

.next-card p {
  margin: 0;
  font-size: 0.83rem;
  line-height: 1.45;
  color: #475569;
}

@media (max-width: 768px) {
  .hero { padding: 1.75rem 1.25rem; }
  .hero-title { font-size: 1.55rem; }
  .next-grid { grid-template-columns: 1fr; }
}
</style>
