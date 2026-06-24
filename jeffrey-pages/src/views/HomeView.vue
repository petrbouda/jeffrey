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
import { ref, computed } from 'vue';

interface HeroBullet {
  icon: string;
  text: string;
}

const serverHeroBullets: HeroBullet[] = [
  { icon: 'bi-box-seam', text: 'Runs as a container in Kubernetes' },
  { icon: 'bi-hdd-stack', text: 'Collects via shared volume — zero agent overhead' },
  { icon: 'bi-broadcast', text: 'Replay sessions, live streaming, merged downloads' },
  { icon: 'bi-plug', text: 'Connect Microscope or your own custom consumer' }
];

const microscopeHeroBullets: HeroBullet[] = [
  { icon: 'bi-fire', text: 'Interactive flamegraphs or specialized dashboards' },
  { icon: 'bi-arrows-collapse', text: 'Differential flamegraphs across profiles' },
  { icon: 'bi-droplet-half', text: 'Heap dumps, logs, JFR — one analyzer' },
  { icon: 'bi-plug', text: 'Pull from a Hub, or analyze a JFR file standalone' }
];

type Tab = 'server' | 'microscope' | 'plugin';

interface TopologyNode {
  kind: 'apps' | 'volume' | 'server' | 'grpc';
  icon: string;
  label: string;
  code?: string;
  em?: string;
  badge?: string;
}

interface MarketplaceInstall {
  url: string;
  name: string;
  sub: string;
  iconPath: string;
  badges: { label: string; variant?: 'free' | 'compat' | 'default' }[];
}

interface ProductTab {
  id: Tab;
  name: string;
  icon: string;
  tagline: string;
  oneLiner: string;
  features: { icon: string; title: string; desc: string }[];
  deployment: {
    title: string;
    desc: string;
    cmd?: string;
    topology?: TopologyNode[];
    marketplace?: MarketplaceInstall;
  };
  docsRoute: string;
  cta: string;
}

const activeTab = ref<Tab>('microscope');

const productTabs: ProductTab[] = [
  {
    id: 'microscope',
    name: 'Jeffrey Microscope',
    icon: 'bi-search-heart-fill',
    tagline: 'Deep analyzer for JFR, heap dumps and logs.',
    oneLiner: 'Open a JFR file or connect to a Hub. Read flamegraphs that finally render fast.',
    features: [
      { icon: 'bi-fire', title: 'Flamegraphs and Differential Flamegraphs', desc: 'For all JFR events providing the stacktraces.' },
      { icon: 'bi-grid-3x3-gap-fill', title: 'JVM and Tech-specific Dashboards', desc: 'Purpose-built views for GC, threads, JIT, HTTP, JDBC and more.' },
      { icon: 'bi-droplet-half', title: 'Heap dump inspection', desc: 'Dominator trees, leak suspects, OOM root cause.' },
      { icon: 'bi-stars', title: 'AI-assisted OQL', desc: 'Ask in natural language; get focused queries.' },
      { icon: 'bi-plug', title: 'Connect to Hub', desc: 'Pull recordings, artifacts & application\'s lifecycle directly via gRPC.' },
      { icon: 'bi-graph-up', title: 'Sub-second timelines', desc: 'Zoom into the millisecond your service stalled.' }
    ],
    deployment: {
      title: 'Runs as a JAR or container',
      desc: 'No Hub required. Drop it on your laptop, drop in a JFR file, browse.',
      cmd: 'docker run -it --network host petrbouda/microscope-examples'
    },
    docsRoute: '/docs/microscope/overview',
    cta: 'Read Microscope docs'
  },
  {
    id: 'plugin',
    name: 'Microscope IDE Plugin',
    icon: 'bi-window-stack',
    tagline: 'IntelliJ bridge — from a flame-graph frame to the source line.',
    oneLiner: 'Click any frame in Microscope and land on the exact method in your already-open IntelliJ — or pull the source back into Microscope inline.',
    features: [
      { icon: 'bi-arrow-right-circle-fill', title: 'Frame-to-source navigation', desc: 'Open in IDE from any flame-graph frame. PSI-based jump to the exact file, line and column.' },
      { icon: 'bi-file-earmark-code', title: 'Inline source view', desc: 'Pull source live from the IDE and render it next to the profile. Stale-source warnings included.' },
      { icon: 'bi-window-stack', title: 'Multi-IDE awareness', desc: 'Pick which IntelliJ window to target per profile. Microscope remembers and re-resolves on restart.' },
      { icon: 'bi-shuffle', title: 'Java & Kotlin resolution', desc: 'Both JVM languages resolve identically. Graceful fallback from line → method → class.' },
      { icon: 'bi-shield-lock', title: 'Headless & localhost-only', desc: 'Uses IntelliJ\'s built-in server. No port to configure, no token to share, no file written.' }
    ],
    deployment: {
      title: 'Install from the JetBrains Marketplace',
      desc: 'Open Settings → Plugins → Marketplace and search for Jeffrey Microscope, or install directly from the page below.',
      marketplace: {
        url: 'https://plugins.jetbrains.com/plugin/31963-jeffrey-microscope',
        name: 'Jeffrey Microscope',
        sub: 'JetBrains Marketplace · by Petr Bouda',
        iconPath: '/images/release-notes/v0.10.0/plugin-icon.svg',
        badges: [
          { label: 'FREE', variant: 'free' },
          { label: 'Apache 2.0' },
          { label: 'IDEA 2025.1+', variant: 'compat' }
        ]
      }
    },
    docsRoute: '/docs/intellij-plugin',
    cta: 'Read plugin docs'
  },
  {
    id: 'server',
    name: 'Jeffrey Hub',
    icon: 'bi-cloud-fill',
    tagline: 'Collector for application data and lifecycle events.',
    oneLiner: 'Runs in Kubernetes. Captures recordings, artifacts and lifecycle events via shared volume. Streams over gRPC.',
    features: [
      { icon: 'bi-arrow-repeat', title: 'Application Lifecycle Events', desc: 'Tracks workspaces, instances and sessions across your application\'s lifecycle.' },
      { icon: 'bi-cloud-arrow-down', title: 'Collecting Recordings and Artifacts', desc: 'Captures JFR recordings, heap dumps and logs from your running services.' },
      { icon: 'bi-cloud-arrow-up', title: 'Providing Merged Recordings and Artifacts', desc: 'Serves merged recordings and artifacts over gRPC, ready to download or analyze.' },
      { icon: 'bi-broadcast-pin', title: 'Live and Replay Streaming of JFR Events', desc: 'Stream JFR events live, or replay any recorded session over gRPC.' },
      { icon: 'bi-hdd-stack', title: 'Integration based on Shared-volume', desc: 'Straightforward and cheap integration among the components.' },
      { icon: 'bi-puzzle', title: 'Custom consumers', desc: 'Microscope is one client — build your own.' }
    ],
    deployment: {
      title: 'Deploys to Kubernetes',
      desc: 'Hub runs alongside your services with shared-volume integration and gRPC exposure.',
      topology: [
        { kind: 'apps', icon: 'bi-app-indicator', label: 'Your services', em: '+ provisioner' },
        { kind: 'volume', icon: 'bi-hdd-stack', code: '/mnt/jeffrey-home', label: 'shared volume' },
        { kind: 'server', icon: 'bi-cloud-fill', label: 'Jeffrey Hub', badge: 'gRPC API' }
      ]
    },
    docsRoute: '/docs/hub/deployment',
    cta: 'Read deployment guide'
  }
];

const active = computed(() => productTabs.find(p => p.id === activeTab.value)!);

function copyCmd(): void {
  if (active.value.deployment.cmd) {
    navigator.clipboard.writeText(active.value.deployment.cmd);
  }
}
</script>

<template>
  <!-- Dual Hero -->
  <section class="dual-hero">
    <div class="hero-half hero-microscope">
      <div class="hero-half-bg">
        <div class="bg-grid"></div>
        <div class="bg-glow bg-glow--microscope"></div>
      </div>
      <div class="hero-half-content">
        <div class="product-eyebrow eyebrow--microscope">
          <span class="eyebrow-icon"><i class="bi bi-search-heart-fill"></i></span>
          <span class="eyebrow-text">
            <strong class="eyebrow-name">Jeffrey Microscope</strong>
            <span class="eyebrow-tag">at the desk</span>
          </span>
        </div>
        <h1 class="product-title">
          Analyze recordings<br/>
          <span class="title-accent title-accent--microscope">on your desk.</span>
        </h1>
        <p class="product-subtitle">
          A deep analyzer for JFR recordings, heap dumps and logs. Upload a file, or connect
          to a Jeffrey Hub and pull artifacts directly.
        </p>
        <ul class="bullet-list">
          <li v-for="(b, i) in microscopeHeroBullets" :key="i">
            <i class="bi" :class="b.icon"></i>
            <span>{{ b.text }}</span>
          </li>
        </ul>
        <div class="cta-row">
          <router-link to="/docs/microscope/overview" class="cta cta--primary cta--microscope">
            <span>Explore Microscope</span>
            <i class="bi bi-arrow-right"></i>
          </router-link>
          <a href="https://github.com/petrbouda/jeffrey/releases/latest/download/microscope.jar" class="cta cta--ghost">
            <i class="bi bi-download"></i>
            <span>Download JAR</span>
          </a>
        </div>
      </div>
    </div>

    <div class="hero-half hero-server">
      <div class="hero-half-bg">
        <div class="bg-grid"></div>
        <div class="bg-glow bg-glow--server"></div>
      </div>
      <div class="hero-half-content">
        <div class="product-eyebrow eyebrow--server">
          <span class="eyebrow-icon"><i class="bi bi-cloud-fill"></i></span>
          <span class="eyebrow-text">
            <strong class="eyebrow-name">Jeffrey Hub</strong>
            <span class="eyebrow-tag">in production</span>
          </span>
        </div>
        <h1 class="product-title">
          Collect runtime data<br/>
          <span class="title-accent title-accent--server">from running services.</span>
        </h1>
        <p class="product-subtitle">
          A containerised collector for Kubernetes. Captures JFR recordings, artifacts and
          session data from your running services — and serves it over gRPC to Microscope,
          your dashboards, or anything else.
        </p>
        <ul class="bullet-list">
          <li v-for="(b, i) in serverHeroBullets" :key="i">
            <i class="bi" :class="b.icon"></i>
            <span>{{ b.text }}</span>
          </li>
        </ul>
        <div class="cta-row">
          <router-link to="/docs/hub/overview" class="cta cta--primary cta--server">
            <span>Explore Jeffrey Hub</span>
            <i class="bi bi-arrow-right"></i>
          </router-link>
          <a href="https://github.com/petrbouda/jeffrey" class="cta cta--ghost" target="_blank">
            <i class="bi bi-github"></i>
            <span>GitHub</span>
          </a>
        </div>
      </div>
    </div>

    <div class="hero-seam">
      <div class="seam-link">
        <span class="seam-cable seam-cable--microscope">
          <span class="seam-dot seam-dot--microscope"></span>
        </span>
        <div class="seam-pill">
          <i class="bi bi-arrow-left-right seam-arrow"></i>
        </div>
        <span class="seam-cable seam-cable--server">
          <span class="seam-dot seam-dot--server"></span>
        </span>
      </div>
    </div>
  </section>

  <!-- Incubating: Performance Analyst -->
  <section class="incubating">
    <div class="container-wide">
      <div class="incubating-panel">
        <div class="inc-glow"></div>
        <div class="inc-head">
          <span class="inc-badge"><i class="bi bi-stars"></i> Incubating in Jeffrey</span>
          <h2>Performance Analyst is growing inside Jeffrey</h2>
          <p>
            An AI-assisted analyst that turns the JFR recordings your Jeffrey Hub collects into
            source-code-level performance recommendations — with severity grades and ready-to-apply patches.
          </p>
        </div>
        <div class="inc-flow">
          <div class="inc-node inc-node--jeffrey">
            <i class="bi bi-cloud-fill"></i>
            <span>Jeffrey Hub</span>
            <span class="inc-node-tag">collects JFR</span>
          </div>
          <div class="inc-cable"><span class="inc-runner"></span></div>
          <div class="inc-node inc-node--pa">
            <i class="bi bi-robot"></i>
            <span>Performance Analyst</span>
            <span class="inc-node-tag">Coming soon</span>
          </div>
        </div>
        <div class="inc-actions">
          <router-link to="/docs/perf-analyst" class="inc-cta">
            <i class="bi bi-book"></i>
            <span>Read the documentation</span>
            <i class="bi bi-arrow-right"></i>
          </router-link>
        </div>
      </div>
    </div>
  </section>

  <!-- Tabbed showcase -->
  <section class="tab-showcase" :data-active="activeTab">
    <div class="container-wide">
      <div class="tab-bar">
        <button
          v-for="t in productTabs"
          :key="t.id"
          class="tab-btn"
          :class="[`tab-btn--${t.id}`, { active: activeTab === t.id }]"
          @click="activeTab = t.id"
        >
          <i class="bi" :class="t.icon"></i>
          <span class="tab-name">{{ t.name }}</span>
        </button>
      </div>

      <div class="tab-panel" :key="activeTab">
        <div class="tab-panel-header">
          <h2>{{ active.tagline }}</h2>
          <p>{{ active.oneLiner }}</p>
        </div>

        <div class="tab-panel-body">
          <div class="tab-feature-grid">
            <article class="tab-feature" v-for="f in active.features" :key="f.title">
              <div class="tab-feature-icon" :class="`tab-feature-icon--${activeTab}`">
                <i class="bi" :class="f.icon"></i>
              </div>
              <h4>{{ f.title }}</h4>
              <p>{{ f.desc }}</p>
            </article>
          </div>

          <aside class="tab-deployment" :class="`tab-deployment--${activeTab}`">
            <span class="dep-eyebrow">Deployment</span>
            <h4>{{ active.deployment.title }}</h4>
            <p>{{ active.deployment.desc }}</p>
            <div class="dep-topology" v-if="active.deployment.topology">
              <template v-for="(node, i) in active.deployment.topology" :key="i">
                <div class="topo-row" :class="`topo-row--${node.kind}`">
                  <i class="bi" :class="node.icon"></i>
                  <span>
                    <code v-if="node.code">{{ node.code }}</code>
                    {{ node.label }}<em v-if="node.em"> {{ node.em }}</em>
                    <span v-if="node.badge" class="topo-badge">{{ node.badge }}</span>
                  </span>
                </div>
                <div class="topo-arrow" v-if="i < active.deployment.topology.length - 1">
                  <i class="bi bi-arrow-down"></i>
                </div>
              </template>
            </div>
            <a
              v-else-if="active.deployment.marketplace"
              :href="active.deployment.marketplace.url"
              target="_blank"
              rel="noopener noreferrer"
              class="dep-marketplace"
            >
              <span class="dep-marketplace-corner">New in 0.10.0</span>
              <div class="dep-marketplace-head">
                <div class="dep-marketplace-icon">
                  <img :src="active.deployment.marketplace.iconPath" :alt="active.deployment.marketplace.name + ' icon'">
                </div>
                <div class="dep-marketplace-titlewrap">
                  <div class="dep-marketplace-title">{{ active.deployment.marketplace.name }}</div>
                  <div class="dep-marketplace-sub">{{ active.deployment.marketplace.sub }}</div>
                </div>
              </div>
              <div class="dep-marketplace-badges">
                <span
                  v-for="b in active.deployment.marketplace.badges"
                  :key="b.label"
                  class="dep-marketplace-badge"
                  :class="b.variant ? `dep-marketplace-badge--${b.variant}` : ''"
                >{{ b.label }}</span>
              </div>
              <div class="dep-marketplace-cta">
                <span>Install from JetBrains Marketplace</span>
                <i class="bi bi-arrow-right"></i>
              </div>
            </a>
            <div class="dep-cmd" v-else-if="active.deployment.cmd">
              <code>{{ active.deployment.cmd }}</code>
              <button class="dep-copy" @click="copyCmd" title="Copy">
                <i class="bi bi-clipboard"></i>
              </button>
            </div>
            <router-link :to="active.docsRoute" class="dep-cta" :class="`dep-cta--${activeTab}`">
              {{ active.cta }} <i class="bi bi-arrow-right"></i>
            </router-link>
          </aside>
        </div>
      </div>
    </div>
  </section>

  <!-- How they connect -->
  <section class="connect">
    <div class="container-wide">
      <div class="connect-inner">
        <span class="connect-eyebrow"><i class="bi bi-link-45deg"></i> How they connect</span>
        <h2>One JFR pipeline. Two halves that work alone.</h2>
        <p>
          Microscope can pull recordings, replays, and live streams from a Hub over gRPC — or work
          with a JFR file you drop in. Hub can serve Microscope, your own gRPC client, or both at once.
        </p>
        <div class="connect-flow">
          <div class="cf-node cf-node-apps">
            <i class="bi bi-app-indicator"></i>
            <span>Your services</span>
          </div>
          <div class="cf-arrow">
            <span>collect</span>
          </div>
          <div class="cf-node cf-node-server">
            <i class="bi bi-cloud-fill"></i>
            <span>Jeffrey Hub</span>
          </div>
          <div class="cf-arrow cf-arrow-grpc">
            <span>gRPC</span>
          </div>
          <div class="cf-node cf-node-microscope">
            <i class="bi bi-search-heart-fill"></i>
            <span>Microscope</span>
          </div>
        </div>
      </div>
    </div>
  </section>

</template>

<style scoped>
/* ============ DUAL HERO ============ */
.dual-hero {
  position: relative;
  display: grid;
  grid-template-columns: 1fr 1fr;
  min-height: 640px;
  overflow: hidden;
}

.hero-half {
  position: relative;
  display: flex;
  align-items: stretch;
  padding: 5rem 3.5rem 7.5rem;
  overflow: hidden;
}

.hero-server {
  background: linear-gradient(135deg, #1a0b2e 0%, #2d1657 50%, #4c1d95 100%);
  color: #fff;
}

.hero-microscope {
  background: linear-gradient(135deg, #061528 0%, #0e2a4d 50%, #1e3a8a 100%);
  color: #fff;
}

.hero-half-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size: 60px 60px;
  mask-image: radial-gradient(ellipse at center, black 30%, transparent 80%);
}

.bg-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(70px);
  opacity: 0.55;
}

.bg-glow--server {
  width: 460px;
  height: 460px;
  background: radial-gradient(circle, #a855f7 0%, transparent 70%);
  top: 10%;
  right: -120px;
}

.bg-glow--microscope {
  width: 460px;
  height: 460px;
  background: radial-gradient(circle, #38bdf8 0%, transparent 70%);
  bottom: 10%;
  left: -120px;
}

.hero-half-content {
  position: relative;
  z-index: 2;
  max-width: 540px;
  margin-left: auto;       /* default: right-aligned (used by the LEFT half so content sits toward the seam) */
  display: flex;
  flex-direction: column;
}

.hero-server .hero-half-content {
  margin-left: 0;
  margin-right: auto;      /* RIGHT half: left-aligned so content sits toward the seam */
}

.product-eyebrow {
  display: inline-flex;
  align-self: flex-start;
  align-items: center;
  gap: 1rem;
  padding: 0.7rem 1.5rem 0.7rem 0.7rem;
  border-radius: 16px;
  margin-bottom: 2rem;
  backdrop-filter: blur(8px);
}

.eyebrow-icon {
  width: 50px;
  height: 50px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 1.4rem;
  color: #fff;
  flex-shrink: 0;
}

.eyebrow-text {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.35rem;
  line-height: 1.05;
}

.eyebrow-name {
  font-size: 1.3rem;
  font-weight: 700;
  color: #fff;
  letter-spacing: -0.005em;
}

.eyebrow-tag {
  font-style: normal;
  font-weight: 600;
  font-size: 0.78rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.eyebrow--server {
  background: rgba(168, 85, 247, 0.12);
  border: 1px solid rgba(168, 85, 247, 0.45);
  box-shadow: 0 6px 24px rgba(168, 85, 247, 0.22);
}

.eyebrow--server .eyebrow-icon {
  background: linear-gradient(135deg, #a855f7, #7c3aed);
  box-shadow: 0 4px 14px rgba(168, 85, 247, 0.5);
}

.eyebrow--server .eyebrow-tag {
  color: #d8b4fe;
}

.eyebrow--microscope {
  background: rgba(56, 189, 248, 0.12);
  border: 1px solid rgba(56, 189, 248, 0.45);
  box-shadow: 0 6px 24px rgba(56, 189, 248, 0.22);
}

.eyebrow--microscope .eyebrow-icon {
  background: linear-gradient(135deg, #38bdf8, #2563eb);
  box-shadow: 0 4px 14px rgba(56, 189, 248, 0.5);
}

.eyebrow--microscope .eyebrow-tag {
  color: #7dd3fc;
}

.product-title {
  font-size: 3.1rem;
  font-weight: 800;
  line-height: 1.05;
  letter-spacing: -0.02em;
  margin-bottom: 1.4rem;
}

.title-accent {
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.title-accent--server {
  background-image: linear-gradient(135deg, #c084fc 0%, #f0abfc 50%, #fb7185 100%);
}

.title-accent--microscope {
  background-image: linear-gradient(135deg, #7dd3fc 0%, #38bdf8 50%, #6366f1 100%);
}

.product-subtitle {
  font-size: 1.05rem;
  line-height: 1.65;
  color: rgba(255, 255, 255, 0.78);
  margin-bottom: 1.75rem;
  max-width: 480px;
}

.bullet-list {
  list-style: none;
  padding: 0;
  margin: auto 0 1.85rem;
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}

.bullet-list li {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  font-size: 0.97rem;
  color: rgba(255, 255, 255, 0.88);
  line-height: 1.45;
}

.bullet-list i {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
  font-size: 0.95rem;
}

.hero-server .bullet-list i { background: rgba(168, 85, 247, 0.2); color: #d8b4fe; }
.hero-microscope .bullet-list i { background: rgba(56, 189, 248, 0.2); color: #7dd3fc; }

.cta-row {
  display: flex;
  gap: 0.85rem;
  flex-wrap: wrap;
}

.cta {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.85rem 1.5rem;
  border-radius: 10px;
  font-weight: 600;
  font-size: 0.95rem;
  text-decoration: none;
  transition: transform 0.2s, box-shadow 0.2s, background 0.2s;
}

.cta--primary {
  color: #fff;
}

.cta--server {
  background: linear-gradient(135deg, #a855f7 0%, #7c3aed 100%);
  box-shadow: 0 6px 24px rgba(168, 85, 247, 0.4);
}

.cta--server:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 30px rgba(168, 85, 247, 0.5);
  color: #fff;
}

.cta--microscope {
  background: linear-gradient(135deg, #38bdf8 0%, #2563eb 100%);
  box-shadow: 0 6px 24px rgba(56, 189, 248, 0.4);
}

.cta--microscope:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 30px rgba(56, 189, 248, 0.5);
  color: #fff;
}

.cta--ghost {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: #fff;
}

.cta--ghost:hover {
  background: rgba(255, 255, 255, 0.13);
  border-color: rgba(255, 255, 255, 0.35);
  color: #fff;
}

/* Seam between halves */
.hero-seam {
  position: absolute;
  bottom: 2.5rem;
  left: 50%;
  transform: translateX(-50%);
  z-index: 5;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.7rem;
  width: min(620px, 90%);
  pointer-events: none;
}

.seam-link {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 0;
}

.seam-cable {
  position: relative;
  flex: 1;
  height: 2px;
  border-radius: 2px;
}

/* After the dual-hero swap, Microscope sits on the LEFT and Server on the RIGHT —
   the seam cables flip so each gradient still leans into its adjacent half. */
.seam-cable--microscope {
  background: linear-gradient(90deg, rgba(125, 211, 252, 0) 0%, rgba(125, 211, 252, 0.55) 100%);
  box-shadow: 0 0 12px rgba(56, 189, 248, 0.25);
}

.seam-cable--server {
  background: linear-gradient(90deg, rgba(216, 180, 254, 0.55) 0%, rgba(216, 180, 254, 0) 100%);
  box-shadow: 0 0 12px rgba(168, 85, 247, 0.25);
}

.seam-dot {
  position: absolute;
  top: 50%;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  transform: translateY(-50%);
  animation: seamDotPulse 2.6s ease-in-out infinite;
}

.seam-dot--server {
  left: 0;
  background: #d8b4fe;
  box-shadow: 0 0 0 2px rgba(216, 180, 254, 0.18), 0 0 14px rgba(168, 85, 247, 0.7);
}

.seam-dot--microscope {
  right: 0;
  background: #7dd3fc;
  box-shadow: 0 0 0 2px rgba(125, 211, 252, 0.18), 0 0 14px rgba(56, 189, 248, 0.7);
  animation-delay: 1.3s;
}

@keyframes seamDotPulse {
  0%, 100% { opacity: 0.85; transform: translateY(-50%) scale(1); }
  50% { opacity: 1; transform: translateY(-50%) scale(1.25); }
}

.seam-pill {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  margin: 0 0.85rem;
  background: rgba(15, 15, 26, 0.92);
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 50%;
  backdrop-filter: blur(8px);
  color: #fff;
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.45), 0 0 0 4px rgba(99, 102, 241, 0.08);
}

.seam-arrow {
  font-size: 1rem;
  background: linear-gradient(90deg, #d8b4fe 0%, #7dd3fc 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

/* ============ INCUBATING: PERFORMANCE ANALYST ============ */
.incubating {
  background: linear-gradient(180deg, #ffffff 0%, #f0fdf4 100%);
  padding: 4rem 0 0;
}

.incubating-panel {
  position: relative;
  overflow: hidden;
  border-radius: 18px;
  border: 1px solid #bbf7d0;
  background: linear-gradient(180deg, #f0fdf4 0%, #ffffff 100%);
  padding: 2.6rem 2.4rem;
}

.inc-glow {
  position: absolute;
  width: 320px;
  height: 320px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(16, 185, 129, 0.16) 0%, transparent 70%);
  filter: blur(40px);
  top: -120px;
  right: -40px;
  pointer-events: none;
}

.inc-head {
  position: relative;
  z-index: 2;
  text-align: center;
  margin-bottom: 1.8rem;
}

.inc-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  margin-bottom: 0.9rem;
  padding: 0.3rem 0.85rem;
  border-radius: 999px;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  color: #047857;
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.incubating h2 {
  font-size: 1.9rem;
  font-weight: 800;
  letter-spacing: -0.02em;
  margin: 0 0 0.6rem;
  color: #064e3b;
}

.incubating p {
  font-size: 1rem;
  line-height: 1.6;
  color: #475569;
  margin: 0 auto;
  max-width: 700px;
}

.inc-flow {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 1.6rem;
}

.inc-node {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  min-width: 160px;
  padding: 1rem 1.4rem;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  font-size: 0.92rem;
  font-weight: 700;
  color: #0f172a;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.06);
}

.inc-node i {
  font-size: 1.5rem;
}

.inc-node--jeffrey i {
  color: #a855f7;
}

.inc-node--pa {
  border-color: #a7f3d0;
  background: linear-gradient(180deg, #ecfdf5 0%, #fff 100%);
}

.inc-node--pa i {
  color: #059669;
}

.inc-node-tag {
  font-size: 0.66rem;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #94a3b8;
}

.inc-node--pa .inc-node-tag {
  color: #059669;
}

.inc-cable {
  position: relative;
  flex: 0 0 120px;
  height: 3px;
  background: repeating-linear-gradient(90deg, #34d399 0 8px, transparent 8px 16px);
}

.inc-runner {
  position: absolute;
  top: 50%;
  left: 0;
  width: 11px;
  height: 11px;
  border-radius: 50%;
  background: #10b981;
  transform: translateY(-50%);
  box-shadow: 0 0 12px rgba(16, 185, 129, 0.8);
  animation: incRun 2.4s linear infinite;
}

@keyframes incRun {
  0% { left: 0; opacity: 0; }
  12% { opacity: 1; }
  88% { opacity: 1; }
  100% { left: 100%; opacity: 0; }
}

@keyframes incRunVertical {
  0% { top: 0; opacity: 0; }
  12% { opacity: 1; }
  88% { opacity: 1; }
  100% { top: 100%; opacity: 0; }
}

.inc-actions {
  position: relative;
  z-index: 2;
  display: flex;
  justify-content: center;
  margin-top: 1.8rem;
}

.inc-cta {
  display: inline-flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.7rem 1.3rem;
  border-radius: 10px;
  font-weight: 600;
  font-size: 0.92rem;
  text-decoration: none;
  color: #fff;
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  box-shadow: 0 6px 20px rgba(16, 185, 129, 0.35);
  transition: transform 0.2s, box-shadow 0.2s;
}

.inc-cta:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 26px rgba(16, 185, 129, 0.45);
  color: #fff;
}

/* ============ TAB SHOWCASE ============ */
.tab-showcase {
  padding: 4rem 0 5rem;
  background: linear-gradient(180deg, #f8fafc 0%, #eef2ff 100%);
  position: relative;
}

.tab-bar {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 0.5rem;
  background: #fff;
  border-radius: 14px;
  padding: 0.5rem;
  max-width: 780px;
  margin: 0 auto 3rem;
  box-shadow: 0 8px 28px rgba(15, 23, 42, 0.08);
  border: 1px solid #e2e8f0;
}

.tab-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.55rem;
  padding: 0.85rem 1.25rem;
  border: none;
  background: transparent;
  border-radius: 10px;
  font-weight: 600;
  font-size: 0.95rem;
  cursor: pointer;
  color: #64748b;
  transition: all 0.25s;
  font-family: inherit;
}

.tab-btn i { font-size: 1.05rem; }

.tab-btn:hover { color: #1e293b; }

.tab-btn--server.active {
  background: linear-gradient(135deg, #a855f7 0%, #7c3aed 100%);
  color: #fff;
  box-shadow: 0 6px 18px rgba(168, 85, 247, 0.35);
}

.tab-btn--microscope.active {
  background: linear-gradient(135deg, #38bdf8 0%, #2563eb 100%);
  color: #fff;
  box-shadow: 0 6px 18px rgba(56, 189, 248, 0.35);
}

.tab-btn--plugin.active {
  background: linear-gradient(135deg, #fb923c 0%, #ea580c 100%);
  color: #fff;
  box-shadow: 0 6px 18px rgba(249, 115, 22, 0.35);
}

.tab-panel {
  animation: tabFade 0.35s ease-out;
}

@keyframes tabFade {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.tab-panel-header {
  text-align: center;
  margin-bottom: 2.5rem;
}

.tab-panel-header h2 {
  font-size: 2.2rem;
  font-weight: 800;
  letter-spacing: -0.02em;
  margin: 0 0 0.5rem;
  color: #0f172a;
}

.tab-panel-header p {
  font-size: 1.05rem;
  color: #475569;
  margin: 0 auto;
  max-width: 1080px;
}

.tab-panel-body {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 2rem;
  align-items: start;
}

.tab-feature-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.tab-feature {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 1.4rem;
  transition: all 0.2s;
}

.tab-feature:hover {
  border-color: #c7d2fe;
  transform: translateY(-2px);
  box-shadow: 0 12px 26px rgba(15, 23, 42, 0.07);
}

.tab-feature-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.15rem;
  color: #fff;
  margin-bottom: 0.85rem;
}

.tab-feature-icon--server { background: linear-gradient(135deg, #a855f7, #7c3aed); }
.tab-feature-icon--microscope { background: linear-gradient(135deg, #38bdf8, #2563eb); }
.tab-feature-icon--plugin { background: linear-gradient(135deg, #fb923c, #ea580c); }

.tab-feature h4 {
  font-size: 1rem;
  font-weight: 700;
  margin: 0 0 0.35rem;
  color: #0f172a;
}

.tab-feature p {
  font-size: 0.88rem;
  color: #475569;
  margin: 0;
  line-height: 1.5;
}

.tab-deployment {
  border-radius: 16px;
  padding: 1.75rem;
}

.dep-topology {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 0.35rem;
  margin-bottom: 1.4rem;
}

.topo-row {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.55rem 0.85rem;
  background: #fff;
  border: 1px solid #e9d5ff;
  border-radius: 10px;
  font-size: 0.88rem;
  color: #0f172a;
}

.topo-row i {
  font-size: 1.05rem;
  color: #7c3aed;
}

.topo-row em {
  font-style: normal;
  color: #64748b;
  font-weight: 400;
  font-size: 0.8rem;
}

.topo-badge {
  display: inline-flex;
  align-items: center;
  margin-left: 0.55rem;
  padding: 0.18rem 0.55rem;
  background: rgba(14, 165, 233, 0.12);
  color: #0284c7;
  border-radius: 999px;
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.topo-row code {
  background: rgba(124, 58, 237, 0.08);
  padding: 0.05rem 0.35rem;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 0.82rem;
  color: #6d28d9;
}

.topo-row--volume i { color: #f59e0b; }
.topo-row--server i { color: #7c3aed; }
.topo-row--grpc i { color: #0ea5e9; }

.topo-arrow {
  display: flex;
  justify-content: center;
  color: rgba(124, 58, 237, 0.5);
  font-size: 0.85rem;
  line-height: 1;
}

.tab-deployment--server {
  background: linear-gradient(180deg, #faf5ff 0%, #fff 100%);
  border: 1px solid #e9d5ff;
}

.tab-deployment--microscope {
  background: linear-gradient(180deg, #f0f9ff 0%, #fff 100%);
  border: 1px solid #bae6fd;
}

.tab-deployment--plugin {
  background: linear-gradient(180deg, #fff7ed 0%, #fff 100%);
  border: 1px solid #fed7aa;
}

.dep-eyebrow {
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.16em;
  display: block;
  margin-bottom: 0.4rem;
}

.tab-deployment--server .dep-eyebrow { color: #7c3aed; }
.tab-deployment--microscope .dep-eyebrow { color: #0284c7; }
.tab-deployment--plugin .dep-eyebrow { color: #c2410c; }

.tab-deployment h4 {
  font-size: 1.1rem;
  font-weight: 700;
  margin: 0 0 0.5rem;
  color: #0f172a;
}

.tab-deployment p {
  font-size: 0.92rem;
  color: #475569;
  line-height: 1.55;
  margin: 0 0 1.25rem;
}

.dep-cmd {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: #0f172a;
  color: #e0e7ff;
  border-radius: 10px;
  padding: 0.7rem 0.9rem;
  margin-bottom: 1.25rem;
  font-family: 'Courier New', monospace;
  font-size: 0.78rem;
}

.dep-cmd code {
  flex: 1;
  background: transparent;
  color: inherit;
  user-select: all;
  overflow: auto;
}

.dep-copy {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  border-radius: 6px;
  border: none;
  background: rgba(255, 255, 255, 0.1);
  color: #e0e7ff;
  cursor: pointer;
  transition: background 0.2s;
}

.dep-copy:hover { background: rgba(255, 255, 255, 0.2); }

/* Marketplace install card (plugin deployment variant) */
.dep-marketplace {
  position: relative;
  display: block;
  text-decoration: none;
  color: inherit;
  background: linear-gradient(180deg, #0f172a 0%, #1f1409 100%);
  border-radius: 14px;
  padding: 1.2rem 1.1rem 1.1rem;
  margin-bottom: 1.25rem;
  border: 1px solid rgba(249, 115, 22, 0.4);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.25), 0 0 0 1px rgba(249, 115, 22, 0.12);
  transition: transform 0.2s, box-shadow 0.2s;
}

.dep-marketplace:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 36px rgba(0, 0, 0, 0.3), 0 0 0 1px rgba(249, 115, 22, 0.25);
}

.dep-marketplace-corner {
  position: absolute;
  top: 0.7rem;
  right: 0.7rem;
  padding: 0.2rem 0.55rem;
  border-radius: 999px;
  background: rgba(249, 115, 22, 0.22);
  border: 1px solid rgba(249, 115, 22, 0.55);
  color: #fdba74;
  font-size: 0.62rem;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.dep-marketplace-head {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.9rem;
}

.dep-marketplace-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: linear-gradient(135deg, #fff7ed, #fed7aa);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 6px 18px rgba(249, 115, 22, 0.3);
}

.dep-marketplace-icon img { width: 30px; height: 30px; }

.dep-marketplace-titlewrap { display: flex; flex-direction: column; gap: 0.15rem; min-width: 0; }

.dep-marketplace-title {
  font-size: 1rem;
  font-weight: 800;
  color: #fff;
  letter-spacing: -0.01em;
}

.dep-marketplace-sub { color: rgba(255, 255, 255, 0.6); font-size: 0.78rem; }

.dep-marketplace-badges { display: flex; gap: 0.4rem; flex-wrap: wrap; margin-bottom: 1rem; }

.dep-marketplace-badge {
  padding: 0.2rem 0.55rem;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.16);
  color: #fff;
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.dep-marketplace-badge--free {
  background: rgba(34, 197, 94, 0.18);
  border-color: rgba(34, 197, 94, 0.5);
  color: #86efac;
}

.dep-marketplace-badge--compat {
  background: rgba(56, 189, 248, 0.18);
  border-color: rgba(56, 189, 248, 0.5);
  color: #7dd3fc;
}

.dep-marketplace-cta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.7rem 0.95rem;
  border-radius: 10px;
  background: linear-gradient(135deg, #fb923c 0%, #ea580c 100%);
  color: #fff;
  font-weight: 700;
  font-size: 0.88rem;
  box-shadow: 0 6px 20px rgba(249, 115, 22, 0.35);
}

.dep-cta {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.7rem 1.1rem;
  border-radius: 10px;
  font-weight: 600;
  font-size: 0.9rem;
  text-decoration: none;
  transition: all 0.2s;
  color: #fff;
}

.dep-cta--server { background: linear-gradient(135deg, #a855f7 0%, #7c3aed 100%); }
.dep-cta--microscope { background: linear-gradient(135deg, #38bdf8 0%, #2563eb 100%); }
.dep-cta--plugin { background: linear-gradient(135deg, #fb923c 0%, #ea580c 100%); }

.dep-cta:hover { transform: translateY(-1px); color: #fff; }

/* ============ CONNECT ============ */
.connect {
  background: #fff;
  padding: 5rem 0;
}

.connect-inner {
  text-align: center;
  max-width: 880px;
  margin: 0 auto;
}

.connect-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.14em;
  color: #6366f1;
  padding: 0.3rem 0.85rem;
  background: rgba(99, 102, 241, 0.1);
  border-radius: 999px;
  margin-bottom: 1rem;
}

.connect h2 {
  font-size: 2.1rem;
  font-weight: 800;
  letter-spacing: -0.02em;
  margin: 0 0 0.85rem;
  color: #0f172a;
}

.connect p {
  font-size: 1.05rem;
  color: #475569;
  line-height: 1.7;
  margin: 0 0 2.5rem;
}

.connect-flow {
  display: grid;
  grid-template-columns: 1fr auto 1fr auto 1fr;
  align-items: center;
  gap: 2rem;
  max-width: 920px;
  margin: 0 auto;
}

.cf-node {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 1.1rem 0.5rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.9rem;
  font-weight: 700;
  color: #0f172a;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.06);
}

.cf-node i {
  font-size: 1.4rem;
}

.cf-node-apps i { color: #475569; }
.cf-node-server i { color: #a855f7; }
.cf-node-microscope i { color: #38bdf8; }

.cf-node-server {
  background: linear-gradient(180deg, #faf5ff 0%, #fff 100%);
  border-color: #e9d5ff;
}

.cf-node-microscope {
  background: linear-gradient(180deg, #f0f9ff 0%, #fff 100%);
  border-color: #bae6fd;
}

.cf-arrow {
  position: relative;
  height: 2px;
  background: linear-gradient(90deg, #cbd5e1, #94a3b8);
  min-width: 140px;
}

.cf-arrow::after {
  content: '';
  position: absolute;
  right: -2px;
  top: 50%;
  transform: translateY(-50%);
  border-left: 6px solid #94a3b8;
  border-top: 4px solid transparent;
  border-bottom: 4px solid transparent;
}

.cf-arrow span {
  position: absolute;
  left: 50%;
  top: -1.4rem;
  transform: translateX(-50%);
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: #6366f1;
  background: #eef2ff;
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
}

.cf-arrow-grpc span {
  color: #0284c7;
  background: #f0f9ff;
}

/* ============ RESPONSIVE ============ */
@media (max-width: 1100px) {
  .product-title { font-size: 2.5rem; }
  .hero-half { padding: 4rem 2.5rem 6.5rem; }
  .tab-panel-body { grid-template-columns: 1fr; }
  .tab-deployment { position: static; }
  .tab-feature-grid { grid-template-columns: 1fr; }
  .connect-flow { grid-template-columns: 1fr; gap: 1rem; }
  .cf-arrow { display: none; }
}

@media (max-width: 880px) {
  .dual-hero { grid-template-columns: 1fr; min-height: auto; }
  .hero-half { padding: 4rem 2rem; }
  .hero-half-content { max-width: none; }
  .hero-microscope .hero-half-content { margin: 0; }
  .hero-server .hero-half-content { margin: 0; }
  .hero-seam {
    bottom: auto;
    top: 50%;
    transform: translate(-50%, -50%);
  }
  .product-title { font-size: 2.1rem; }
  .tab-panel-header h2 { font-size: 1.7rem; }
  .incubating h2 { font-size: 1.55rem; }
  .inc-flow { flex-direction: column; }
  .inc-node { width: 100%; max-width: 280px; }
  .inc-cable {
    flex-basis: 40px;
    width: 3px;
    height: 40px;
    background: repeating-linear-gradient(180deg, #34d399 0 8px, transparent 8px 16px);
  }
  .inc-runner {
    left: 50%;
    top: 0;
    transform: translateX(-50%);
    animation-name: incRunVertical;
  }
}

@media (max-width: 760px) {
  .tab-bar { grid-template-columns: 1fr; }
}

@media (max-width: 480px) {
  .hero-half { padding: 3rem 1.25rem; }
  .product-title { font-size: 1.8rem; }
  .cta { padding: 0.75rem 1.1rem; font-size: 0.9rem; }
}
</style>
