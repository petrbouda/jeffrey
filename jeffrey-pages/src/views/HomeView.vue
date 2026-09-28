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
import { ref, computed } from 'vue';

interface HeroBullet {
  icon: string;
  text: string;
}

const microscopeHeroBullets: HeroBullet[] = [
  { icon: 'bi-fire', text: 'Interactive flamegraphs or specialized dashboards' },
  { icon: 'bi-arrows-collapse', text: 'Differential flamegraphs across profiles' },
  { icon: 'bi-droplet-half', text: 'JFR and heap dumps — one analyzer' },
  { icon: 'bi-plug', text: 'Pull from a Hub, or analyze a JFR file standalone' }
];

type AgentLineKind = 'prompt' | 'tool' | 'result';

interface AgentLine {
  kind: AgentLineKind;
  text: string;
  highlight?: string;
  suffix?: string;
}

// A short, illustrative agent session shown in the right hero half.
const agentSession: AgentLine[] = [
  { kind: 'prompt', text: 'Why is checkout slow? perf/checkout.jfr' },
  { kind: 'tool', text: '', highlight: 'recordings_analyzeFile → flamegraph_export' },
  { kind: 'result', text: '38% of CPU in PriceCalculator.applyRules', suffix: ' — Pattern.compile per request' },
  { kind: 'tool', text: '', highlight: 'ide_resolve → PriceCalculator.java:87' }
];

const agentClients: string[] = ['Claude Code', 'Codex', 'Gemini CLI · soon', 'any MCP client'];

type Tab = 'microscope' | 'mcp' | 'plugin' | 'server';

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
    eyebrow: string;
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
    name: 'Microscope',
    icon: 'bi-search-heart-fill',
    tagline: 'Deep analyzer for JFR recordings and heap dumps.',
    oneLiner: 'Open a JFR file or connect to a Hub. Read flamegraphs that finally render fast.',
    features: [
      { icon: 'bi-fire', title: 'Flamegraphs and Differential Flamegraphs', desc: 'For all JFR events providing the stacktraces.' },
      { icon: 'bi-grid-3x3-gap-fill', title: 'JVM and Tech-specific Dashboards', desc: 'Purpose-built views for GC, threads, JIT, HTTP, JDBC and more.' },
      { icon: 'bi-droplet-half', title: 'Heap dump inspection', desc: 'Dominator trees, leak suspects, OOM root cause.' },
      { icon: 'bi-graph-up', title: 'Sub-second timelines', desc: 'Zoom into the millisecond your service stalled.' },
      { icon: 'bi-diagram-3', title: 'Traces', desc: 'Span waterfalls correlated with I/O, locks and GC pauses — from the same JFR file.' },
      { icon: 'bi-plug', title: 'Connect to Hub', desc: 'Pull recordings, artifacts & application\'s lifecycle directly via gRPC.' }
    ],
    deployment: {
      eyebrow: 'Deployment',
      title: 'Runs as a JAR or container',
      desc: 'No Hub required. Drop it on your laptop, drop in a JFR file, browse.',
      cmd: 'docker run -it --network host petrbouda/microscope-examples'
    },
    docsRoute: '/docs/microscope/overview',
    cta: 'Read Microscope docs'
  },
  {
    id: 'mcp',
    name: 'Microscope MCP',
    icon: 'bi-stars',
    tagline: 'The profiler your coding agent can call.',
    oneLiner: 'The same analysis you see in the UI, served over MCP 2026-07-28 to Claude Code, Codex and any MCP client — next to your source code.',
    features: [
      { icon: 'bi-plug-fill', title: '111 tools, 100 read-only', desc: 'Profiles, flamegraphs, JVM dashboards, heap dumps, traces and DuckDB SQL. Every tool declares whether it writes.' },
      { icon: 'bi-magic', title: 'Skills that know the method', desc: 'profile-run, analyze-jfr, analyze-heap, advise-jfr, regression-check and five more.' },
      { icon: 'bi-people', title: 'Sub-agents', desc: 'profile-lead dispatches profile-analyst and heap-triage, and gets back findings rather than raw exports.' },
      { icon: 'bi-shield-lock', title: 'No model inside', desc: 'Jeffrey never calls a model provider. Your agent calls in; your profiles stay local.' }
    ],
    deployment: {
      eyebrow: 'Install',
      title: 'On by default at /api/mcp',
      desc: 'A running Microscope already serves MCP. Add the plugin to Claude Code for the skills and agents.',
      cmd: '/plugin marketplace add petrbouda/jeffrey\n/plugin install microscope@jeffrey'
    },
    docsRoute: '/docs/microscope-mcp',
    cta: 'Read MCP docs'
  },
  {
    id: 'plugin',
    name: 'IDE Plugin',
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
      eyebrow: 'Deployment',
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
      { icon: 'bi-cloud-arrow-up', title: 'Serving Recordings and Artifacts', desc: 'Serves recording chunks and artifacts over gRPC — a whole session, or the hour that matters.' },
      { icon: 'bi-hdd-stack', title: 'Integration based on Shared-volume', desc: 'Straightforward and cheap integration among the components.' },
      { icon: 'bi-puzzle', title: 'Custom consumers', desc: 'Microscope is one client — build your own.' }
    ],
    deployment: {
      eyebrow: 'Deployment',
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

interface EcoProduct {
  id: string;
  name: string;
  role: string;
  desc: string;
  icon: string;
  chips: string[];
  to: string;
  flag?: string;
}

// The rest of the Jeffrey ecosystem — companion products shown under the
// Microscope + Microscope MCP hero. The Hub leads: it is where production recordings come from.
const ecosystem: EcoProduct[] = [
  {
    id: 'hub',
    name: 'Jeffrey Hub',
    role: 'Kubernetes · Collector',
    icon: 'bi-cloud-fill',
    desc: 'Collects JFR recordings, heap dumps and logs from running services via a shared volume, and serves them over gRPC — to Microscope, to your agent\'s analyze-hub skill, or to your own consumer.',
    chips: ['Kubernetes', 'Shared volume', 'gRPC', 'Lifecycle events'],
    to: '/docs/hub/overview',
    flag: 'Production'
  },
  {
    id: 'plugin',
    name: 'IntelliJ Plugin',
    role: 'IDE · Companion',
    icon: 'bi-window-stack',
    desc: 'Jump from any Microscope flame-graph frame straight to the source line in your open IntelliJ — Java and Kotlin — or pull inline source back into the profile.',
    chips: ['Open in IDE', 'Inline source', 'Multi-IDE', 'Marketplace'],
    to: '/docs/intellij-plugin'
  },
  {
    id: 'provisioner',
    name: 'Provisioner',
    role: 'Standalone · Session bootstrap',
    icon: 'bi-terminal',
    desc: 'One HOCON file lays out your workspace, project and session tree, registers sessions with the Hub, and generates the JVM argfile that starts your app under the profiler.',
    chips: ['HOCON config', 'JVM argfile', 'Session layout', 'Native binary'],
    to: '/docs/provisioner'
  },
  {
    id: 'jib',
    name: 'Jeffrey JIB',
    role: 'Standalone · Build-time',
    icon: 'bi-box-seam',
    desc: 'A Jib (Gradle/Maven) extension that wraps your container entrypoint so Jeffrey profiling starts before your app does — no command override, and the provisioner and async-profiler ride along in their own image layer.',
    chips: ['Gradle/Maven', 'Entrypoint wrapper', 'Baked payloads', 'Kill switch'],
    to: '/docs/jib'
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
  <!-- Dual Hero: Microscope for you, Microscope MCP for your coding agent -->
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
            <span class="eyebrow-tag">for you</span>
          </span>
        </div>
        <h1 class="product-title">
          Analyze recordings<br/>
          <span class="title-accent title-accent--microscope">on your desk.</span>
        </h1>
        <p class="product-subtitle">
          A deep analyzer for JFR recordings and heap dumps. Upload a file, or connect
          to a Jeffrey Hub and pull recordings directly.
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

    <div class="hero-half hero-ai">
      <div class="hero-half-bg">
        <div class="bg-grid"></div>
        <div class="bg-glow bg-glow--ai"></div>
      </div>
      <div class="hero-half-content">
        <div class="product-eyebrow eyebrow--ai">
          <span class="eyebrow-icon"><i class="bi bi-stars"></i></span>
          <span class="eyebrow-text">
            <strong class="eyebrow-name">Microscope MCP</strong>
            <span class="eyebrow-tag">for your coding agent</span>
          </span>
        </div>
        <h1 class="product-title">
          Let your agent<br/>
          <span class="title-accent title-accent--ai">read the profile.</span>
        </h1>
        <p class="product-subtitle">
          The same analysis, served over MCP to Claude Code, Codex and any MCP client —
          so hot frames land next to your source code.
        </p>
        <div class="agent-term" aria-label="Example coding-agent session">
          <div class="agent-term-bar">
            <span class="agent-term-dot"></span>
            <span class="agent-term-dot"></span>
            <span class="agent-term-dot"></span>
            <span class="agent-term-title">claude</span>
            <span class="agent-term-endpoint">● /api/mcp</span>
          </div>
          <div class="agent-term-body">
            <div v-for="(line, i) in agentSession" :key="i" class="agent-line" :class="`agent-line--${line.kind}`">
              <template v-if="line.kind === 'tool'">⏺ <b>{{ line.highlight }}</b></template>
              <template v-else-if="line.kind === 'result'"><b>{{ line.text }}</b>{{ line.suffix }}</template>
              <template v-else>{{ line.text }}</template>
            </div>
          </div>
        </div>
        <div class="agent-clients">
          <span v-for="c in agentClients" :key="c" class="agent-client">{{ c }}</span>
        </div>
        <div class="cta-row">
          <router-link to="/docs/microscope-mcp/claude-code" class="cta cta--primary cta--ai">
            <span>Connect your agent</span>
            <i class="bi bi-arrow-right"></i>
          </router-link>
          <router-link to="/docs/microscope-mcp" class="cta cta--ghost">
            <i class="bi bi-book"></i>
            <span>MCP docs</span>
          </router-link>
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
        <span class="seam-cable seam-cable--ai">
          <span class="seam-dot seam-dot--ai"></span>
        </span>
      </div>
    </div>
  </section>

  <!-- The rest of the Jeffrey ecosystem -->
  <section class="ecosystem">
    <div class="container-wide">
      <div class="eco-head">
        <span class="eco-rule"></span>
        <span class="eco-eyebrow"><i class="bi bi-grid-1x2-fill"></i> The Jeffrey ecosystem</span>
        <span class="eco-rule eco-rule--r"></span>
      </div>
      <div class="eco-grid">
        <router-link
          v-for="p in ecosystem"
          :key="p.id"
          :to="p.to"
          class="eco-card"
          :class="`eco-card--${p.id}`"
        >
          <div class="eco-ic"><i class="bi" :class="p.icon"></i></div>
          <div class="eco-body">
            <div class="eco-title">
              <span>{{ p.name }}</span>
              <span v-if="p.flag" class="eco-flag">{{ p.flag }}</span>
            </div>
            <div class="eco-role">{{ p.role }}</div>
            <p class="eco-desc">{{ p.desc }}</p>
            <div class="eco-chips">
              <span v-for="c in p.chips" :key="c" class="eco-chip">{{ c }}</span>
            </div>
          </div>
        </router-link>
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
            <span class="dep-eyebrow">{{ active.deployment.eyebrow }}</span>
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
        <h2>One JFR pipeline. Every half works alone.</h2>
        <p>
          Microscope can pull recordings from a Hub over gRPC — or work with a JFR file you
          drop in. You read it in the browser; your coding agent reads it over MCP.
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
          <div class="cf-arrow cf-arrow-mcp">
            <span>MCP</span>
          </div>
          <div class="cf-node cf-node-ai">
            <i class="bi bi-stars"></i>
            <span>Coding agent</span>
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

.hero-ai {
  background: linear-gradient(135deg, #03140f 0%, #072e25 50%, #065f46 100%);
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

.bg-glow--ai {
  width: 460px;
  height: 460px;
  background: radial-gradient(circle, #34d399 0%, transparent 70%);
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

.hero-ai .hero-half-content {
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

.eyebrow--ai {
  background: rgba(52, 211, 153, 0.12);
  border: 1px solid rgba(52, 211, 153, 0.45);
  box-shadow: 0 6px 24px rgba(52, 211, 153, 0.22);
}

.eyebrow--ai .eyebrow-icon {
  background: linear-gradient(135deg, #34d399, #059669);
  box-shadow: 0 4px 14px rgba(52, 211, 153, 0.5);
}

.eyebrow--ai .eyebrow-tag {
  color: #6ee7b7;
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

.title-accent--ai {
  background-image: linear-gradient(135deg, #a7f3d0 0%, #34d399 50%, #22d3ee 100%);
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

.cta--ai {
  background: linear-gradient(135deg, #34d399 0%, #059669 100%);
  box-shadow: 0 6px 24px rgba(52, 211, 153, 0.4);
}

.cta--ai:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 30px rgba(52, 211, 153, 0.5);
  color: #fff;
}

/* Example coding-agent session in the right hero half */
.agent-term {
  background: rgba(2, 6, 23, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 14px;
  box-shadow: 0 24px 60px -24px rgba(0, 0, 0, 0.6);
  overflow: hidden;
  margin-bottom: 1.1rem;
}

.agent-term-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0.55rem 0.85rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  font-family: ui-monospace, Menlo, Consolas, monospace;
  font-size: 0.72rem;
  color: #94a3b8;
}

.agent-term-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.18);
}

.agent-term-title {
  margin-left: 0.4rem;
}

.agent-term-endpoint {
  margin-left: auto;
  color: #34d399;
}

.agent-term-body {
  padding: 0.75rem 0.95rem 0.85rem;
  font-family: ui-monospace, Menlo, Consolas, monospace;
  font-size: 0.76rem;
  line-height: 1.7;
  color: #cbd5e1;
}

.agent-line--prompt { color: #fff; }
.agent-line--prompt::before { content: '› '; color: #34d399; }
.agent-line--tool { color: #7dd3fc; }
.agent-line--tool b { color: #fcd34d; font-weight: 500; }
.agent-line--result {
  color: #94a3b8;
  padding-left: 0.7rem;
  border-left: 2px solid rgba(255, 255, 255, 0.12);
  margin: 0.1rem 0 0.3rem 0.2rem;
}
.agent-line--result b { color: #fff; font-weight: 500; }

.agent-clients {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-bottom: 1.6rem;
}

.agent-client {
  font-size: 0.76rem;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.86);
  background: rgba(255, 255, 255, 0.07);
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 7px;
  padding: 0.2rem 0.6rem;
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

/* Microscope sits on the LEFT and Microscope MCP on the RIGHT —
   the seam cables flip so each gradient still leans into its adjacent half. */
.seam-cable--microscope {
  background: linear-gradient(90deg, rgba(125, 211, 252, 0) 0%, rgba(125, 211, 252, 0.55) 100%);
  box-shadow: 0 0 12px rgba(56, 189, 248, 0.25);
}

.seam-cable--ai {
  background: linear-gradient(90deg, rgba(110, 231, 183, 0.55) 0%, rgba(110, 231, 183, 0) 100%);
  box-shadow: 0 0 12px rgba(52, 211, 153, 0.25);
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

.seam-dot--ai {
  left: 0;
  background: #6ee7b7;
  box-shadow: 0 0 0 2px rgba(110, 231, 183, 0.18), 0 0 14px rgba(52, 211, 153, 0.7);
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
  background: linear-gradient(90deg, #7dd3fc 0%, #6ee7b7 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

/* ============ ECOSYSTEM ============ */
.ecosystem {
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  padding: 4.5rem 0 1rem;
}

.eco-head {
  display: flex;
  align-items: center;
  gap: 1.1rem;
  margin-bottom: 2.2rem;
}

.eco-rule {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, transparent, #cbd5e1);
}

.eco-rule--r {
  background: linear-gradient(90deg, #cbd5e1, transparent);
}

.eco-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.78rem;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.16em;
  color: #6366f1;
  background: rgba(99, 102, 241, 0.1);
  border: 1px solid rgba(99, 102, 241, 0.25);
  border-radius: 999px;
  padding: 0.45rem 1.1rem;
  white-space: nowrap;
}

.eco-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.1rem;
}

.eco-card {
  display: flex;
  gap: 1.1rem;
  padding: 1.4rem;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  text-decoration: none;
  color: inherit;
  transition: transform 0.2s, box-shadow 0.2s, border-color 0.2s;
}

.eco-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.09);
  border-color: var(--eco-a);
}

.eco-card--provisioner { --eco-a: #f43f5e; --eco-b: #e11d48; }
.eco-card--plugin { --eco-a: #fb923c; --eco-b: #ea580c; }
.eco-card--jib { --eco-a: #6366f1; --eco-b: #4f46e5; }
.eco-card--hub {
  --eco-a: #a855f7;
  --eco-b: #7c3aed;
  background: linear-gradient(135deg, #faf5ff 0%, #fff 70%);
  border-color: rgba(168, 85, 247, 0.35);
}

.eco-flag {
  font-size: 0.6rem;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #fff;
  background: linear-gradient(135deg, var(--eco-a), var(--eco-b));
  padding: 0.12rem 0.45rem;
  border-radius: 5px;
}

.eco-ic {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  border-radius: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.35rem;
  color: #fff;
  background: linear-gradient(135deg, var(--eco-a), var(--eco-b));
}

.eco-body { min-width: 0; }

.eco-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.45rem;
  font-size: 1.05rem;
  font-weight: 800;
  color: #0f172a;
  margin: 0 0 0.15rem;
}

.eco-role {
  font-size: 0.66rem;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--eco-a);
  margin: 0 0 0.55rem;
}

.eco-desc {
  font-size: 0.88rem;
  color: #475569;
  line-height: 1.55;
  margin: 0 0 0.85rem;
}

.eco-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.eco-chip {
  font-size: 0.7rem;
  font-weight: 600;
  color: #64748b;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 0.13rem 0.5rem;
}

/* ============ TAB SHOWCASE ============ */
.tab-showcase {
  padding: 4rem 0 5rem;
  background: linear-gradient(180deg, #f8fafc 0%, #eef2ff 100%);
  position: relative;
}

.tab-bar {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.5rem;
  background: #fff;
  border-radius: 14px;
  padding: 0.5rem;
  max-width: 900px;
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

.tab-btn--mcp.active {
  background: linear-gradient(135deg, #34d399 0%, #059669 100%);
  color: #fff;
  box-shadow: 0 6px 18px rgba(52, 211, 153, 0.35);
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
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
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
.tab-feature-icon--mcp { background: linear-gradient(135deg, #34d399, #059669); }

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

.tab-deployment--mcp {
  background: linear-gradient(180deg, #ecfdf5 0%, #fff 100%);
  border: 1px solid #a7f3d0;
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
.tab-deployment--mcp .dep-eyebrow { color: #047857; }

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
  min-width: 0;
  user-select: all;
  overflow: auto;
  white-space: pre;
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
.dep-cta--mcp { background: linear-gradient(135deg, #34d399 0%, #059669 100%); }

.dep-cta:hover { transform: translateY(-1px); color: #fff; }

/* ============ CONNECT ============ */
.connect {
  background: #fff;
  padding: 5rem 0;
}

.connect-inner {
  text-align: center;
  max-width: 1080px;
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
  grid-template-columns: 1fr auto 1fr auto 1fr auto 1fr;
  align-items: center;
  gap: 1.5rem;
  max-width: 1080px;
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
.cf-node-ai i { color: #059669; }

.cf-node-ai {
  background: linear-gradient(180deg, #ecfdf5 0%, #fff 100%);
  border-color: #a7f3d0;
}

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
  min-width: 90px;
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

.cf-arrow-mcp span {
  color: #047857;
  background: #ecfdf5;
}

/* ============ RESPONSIVE ============ */
@media (max-width: 1100px) {
  .product-title { font-size: 2.5rem; }
  .hero-half { padding: 4rem 2.5rem 6.5rem; }
  .tab-panel-body { grid-template-columns: minmax(0, 1fr); }
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
  .hero-ai .hero-half-content { margin: 0; }
  .hero-seam {
    bottom: auto;
    top: 50%;
    transform: translate(-50%, -50%);
  }
  .product-title { font-size: 2.1rem; }
  .tab-panel-header h2 { font-size: 1.7rem; }
  .eco-grid { grid-template-columns: 1fr; }
}

@media (max-width: 960px) {
  .tab-bar { grid-template-columns: 1fr 1fr; }
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
