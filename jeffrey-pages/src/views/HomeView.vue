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
import { ref, computed, onMounted, onBeforeUnmount } from 'vue';

const MICROSCOPE_JAR_URL = 'https://github.com/petrbouda/jeffrey/releases/latest/download/microscope.jar';
const COPIED_FEEDBACK_MS = 1400;

// ---------- Hero constellation ----------

// Node coordinates live in the cosmos SVG viewBox (0 0 100 92); the core sits at (CORE_X, CORE_Y).
const COSMOS_HEIGHT = 92;
const CORE_X = 50;
const CORE_Y = 46;

// How prominent a product is drawn: the two analysers, then the two ways to start profiling,
// then the companions.
type CosmosTier = 'core' | 'capture' | 'companion';

interface CosmosNode {
  id: string;
  name: string;
  sub: string;
  icon: string;
  tip: string;
  to: string;
  x: number;
  y: number;
  color: string;
  // Seconds; offsets the float animation and the pulse speed so nodes don't move in lockstep.
  delay: number;
  tier: CosmosTier;
  // Bottom nodes open their tooltip upwards.
  tipUp?: boolean;
}

// Listed by tier: on narrow screens the cards stack in this order.
const cosmosNodes: CosmosNode[] = [
  {
    id: 'microscope', name: 'Microscope', sub: 'for you', icon: 'bi-search', tier: 'core',
    tip: 'Flamegraphs, differential views, sub-second timelines, heap dumps and JVM dashboards from your JFR recordings.',
    to: '/docs/microscope', x: 24, y: 17, color: 'var(--ms)', delay: 0
  },
  {
    id: 'mcp', name: 'Microscope MCP', sub: 'for your coding agent', icon: 'bi-stars', tier: 'core',
    tip: '112 tools over MCP for Claude Code, Codex and any MCP 2026-07-28 client. Jeffrey never calls a model itself.',
    to: '/docs/microscope-mcp', x: 76, y: 21, color: 'var(--mcp)', delay: 1.4
  },
  {
    id: 'provisioner', name: 'Provisioner', sub: 'session bootstrap', icon: 'bi-terminal', tier: 'capture',
    tip: 'A native CLI. One HOCON file lays out sessions and writes the JVM argfile that starts your app under the profiler.',
    to: '/docs/provisioner', x: 15, y: 55, color: 'var(--amber)', delay: 3.2
  },
  {
    id: 'jib', name: 'Jeffrey JIB', sub: 'build-time', icon: 'bi-box-seam', tier: 'capture',
    tip: 'A Jib extension that wraps the container entrypoint, so profiling starts before your app does.',
    to: '/docs/jib', x: 85, y: 59, color: 'var(--amber)', delay: 2
  },
  {
    id: 'hub', name: 'Hub', sub: 'shared volume', icon: 'bi-cloud', tier: 'companion', tipUp: true,
    tip: 'Runs in Kubernetes. Retention, cleanup and LZ4 compression of the volume your services record into.',
    to: '/docs/hub', x: 29, y: 84, color: 'var(--ember)', delay: 2.6
  },
  {
    id: 'intellij', name: 'IntelliJ Plugin', sub: 'frame → source', icon: 'bi-window-stack', tier: 'companion', tipUp: true,
    tip: 'Jump from any flamegraph frame to the source line in your open IntelliJ, Java and Kotlin.',
    to: '/docs/intellij-plugin', x: 71, y: 86, color: 'var(--ide)', delay: 0.8
  }
];

const hoveredNode = ref<string | null>(null);

function nodeStyle(node: CosmosNode): Record<string, string> {
  return {
    left: `${node.x}%`,
    top: `${(node.y / COSMOS_HEIGHT) * 100}%`,
    '--node-color': node.color,
    '--delay': `-${node.delay}s`
  };
}

function pulsePath(node: CosmosNode): string {
  return `M${CORE_X},${CORE_Y} L${node.x},${node.y}`;
}

// ---------- Starfield ----------

const STAR_RGB = '120, 86, 40';
const STAR_DENSITY_PX2 = 5200;
const STAR_SEED = 11;
const STAR_RESIZE_DEBOUNCE_MS = 150;
const MAX_DEVICE_PIXEL_RATIO = 2;

const skyCanvas = ref<HTMLCanvasElement | null>(null);
let resizeTimer: number | undefined;

// Deterministic generator so the sky looks the same on every visit.
function seededRandom(seed: number): () => number {
  let state = seed;
  return () => {
    state = (state * 16807) % 2147483647;
    return state / 2147483647;
  };
}

function drawSky(): void {
  const canvas = skyCanvas.value;
  if (!canvas) {
    return;
  }
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    return;
  }
  const rect = canvas.getBoundingClientRect();
  const dpr = Math.min(window.devicePixelRatio || 1, MAX_DEVICE_PIXEL_RATIO);
  canvas.width = rect.width * dpr;
  canvas.height = rect.height * dpr;
  ctx.scale(dpr, dpr);
  const random = seededRandom(STAR_SEED);
  const count = Math.round((rect.width * rect.height) / STAR_DENSITY_PX2);
  for (let i = 0; i < count; i++) {
    const x = random() * rect.width;
    const y = random() * rect.height;
    const size = random() * 1.1 + 0.2;
    ctx.fillStyle = `rgba(${STAR_RGB}, ${0.08 + random() * 0.22})`;
    ctx.beginPath();
    ctx.arc(x, y, size, 0, Math.PI * 2);
    ctx.fill();
  }
}

function onResize(): void {
  window.clearTimeout(resizeTimer);
  resizeTimer = window.setTimeout(drawSky, STAR_RESIZE_DEBOUNCE_MS);
}

onMounted(() => {
  drawSky();
  window.addEventListener('resize', onResize);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize);
  window.clearTimeout(resizeTimer);
});

// ---------- Pipeline route ----------

// Positions are percentages of the route box (viewBox 0 0 1000 300).
interface RouteStation {
  name: string;
  sub: string;
  icon: string;
  color: string;
  x: number;
  y: number;
}

interface NoteSegment {
  text: string;
  strong?: boolean;
}

// A dashed side note hanging under a station: who helps it, or what else it accepts.
interface RouteNote {
  icon: string;
  color: string;
  parts: NoteSegment[];
  x: number;
  y: number;
}

interface RouteHop {
  label: string;
  color: string;
  x: number;
  y: number;
}

const ROUTE_PATH = 'M10 180 L110 180 C 210 180, 270 60, 370 60 S 530 180, 630 180 S 790 60, 890 60 L990 60';
const ROUTE_PULSE_BEGINS = ['0s', '-2s', '-4s'];
const ROUTE_DASHES = ['M110 208 L110 248', 'M370 88 L370 138', 'M630 208 L630 248', 'M890 88 L890 138'];

const routeStations: RouteStation[] = [
  { name: 'Your services', sub: 'JFR · async-profiler', icon: 'bi-boxes', color: 'var(--ember)', x: 11, y: 60 },
  { name: 'Shared volume', sub: '/mnt/jeffrey-home', icon: 'bi-hdd-stack', color: 'var(--amber)', x: 37, y: 20 },
  { name: 'Microscope', sub: 'flamegraphs · dashboards', icon: 'bi-search', color: 'var(--ms)', x: 63, y: 60 },
  { name: 'Coding agent', sub: 'Claude Code · Codex', icon: 'bi-stars', color: 'var(--mcp)', x: 89, y: 20 }
];

const routeHops: RouteHop[] = [
  { label: 'record', color: 'var(--ember)', x: 24, y: 40 },
  { label: 'Connector', color: 'var(--ms)', x: 50, y: 40 },
  { label: 'MCP', color: 'var(--mcp)', x: 76, y: 40 }
];

const routeNotes: RouteNote[] = [
  {
    icon: 'bi-terminal', color: 'var(--ember)', x: 11, y: 86.7,
    parts: [{ text: 'Provisioner', strong: true }, { text: ' or ' }, { text: 'JIB', strong: true }, { text: ' starts them' }]
  },
  {
    icon: 'bi-cloud', color: 'var(--ember)', x: 37, y: 50,
    parts: [{ text: 'Jeffrey Hub', strong: true }, { text: ' keeps it tidy' }]
  },
  {
    icon: 'bi-file-earmark-arrow-up', color: 'var(--ms)', x: 63, y: 86.7,
    parts: [{ text: 'or drop in a ' }, { text: '.jfr file', strong: true }]
  },
  {
    icon: 'bi-code-slash', color: 'var(--mcp)', x: 89, y: 50,
    parts: [{ text: 'Investigates', strong: true }, { text: ' and ' }, { text: 'changes the code', strong: true }]
  }
];

// On narrow screens the route becomes a vertical timeline: each station, then its note, then the hop to the next.
const routeSteps = routeStations.map((station, i) => ({
  station,
  note: routeNotes[i],
  hop: routeHops[i]
}));

function positionStyle(item: { x: number; y: number; color?: string }): Record<string, string> {
  const style: Record<string, string> = { '--x': `${item.x}%`, '--y': `${item.y}%` };
  if (item.color) {
    style['--c'] = item.color;
  }
  return style;
}

// ---------- Product showcase ----------

type Tab = 'microscope' | 'mcp' | 'plugin' | 'hub';

interface TopologyNode {
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
  badges: string[];
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

const productTabs: ProductTab[] = [
  {
    id: 'microscope',
    name: 'Microscope',
    icon: 'bi-search-heart-fill',
    tagline: 'Deep analyzer for JFR recordings and heap dumps.',
    oneLiner: 'Open a JFR file or pull one off the recording volume. Read flamegraphs that finally render fast.',
    features: [
      { icon: 'bi-fire', title: 'Flamegraphs and Differential Flamegraphs', desc: 'For all JFR events providing the stacktraces.' },
      { icon: 'bi-grid-3x3-gap-fill', title: 'JVM and Tech-specific Dashboards', desc: 'Purpose-built views for GC, threads, JIT, HTTP, JDBC and more.' },
      { icon: 'bi-droplet-half', title: 'Heap dump inspection', desc: 'Dominator trees, leak suspects, OOM root cause.' },
      { icon: 'bi-graph-up', title: 'Sub-second timelines', desc: 'Zoom into the millisecond your service stalled.' },
      { icon: 'bi-diagram-3', title: 'Traces', desc: 'Span waterfalls correlated with I/O, locks and GC pauses — from the same JFR file.' },
      { icon: 'bi-plug', title: 'Browse the Connector', desc: 'Pull recordings, artifacts & application\'s lifecycle straight off the Azure file share that holds the recording volume.' }
    ],
    deployment: {
      eyebrow: 'Deployment',
      title: 'Runs as a JAR or container',
      desc: 'No Hub required. Drop it on your laptop, drop in a JFR file, browse.',
      cmd: 'docker run -it --network host petrbouda/microscope-examples'
    },
    docsRoute: '/docs/microscope',
    cta: 'Read Microscope docs'
  },
  {
    id: 'mcp',
    name: 'Microscope MCP',
    icon: 'bi-stars',
    tagline: 'The profiler your coding agent can call.',
    oneLiner: 'The same analysis you see in the UI, served over MCP 2026-07-28 to Claude Code, Codex and any other 2026-07-28 client — next to your source code.',
    features: [
      { icon: 'bi-plug-fill', title: '112 tools, 101 read-only', desc: 'Profiles, flamegraphs, JVM dashboards, heap dumps, traces and DuckDB SQL. Every tool declares whether it writes.' },
      { icon: 'bi-magic', title: 'Skills that know the method', desc: 'profile-run, analyze-jfr, analyze-heap, advise-jfr, regression-check and seven more.' },
      { icon: 'bi-people', title: 'Sub-agents', desc: 'profile-analyst and heap-triage read the exports in parallel, and the conversation gets back findings rather than raw documents.' },
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
        badges: ['FREE', 'Apache 2.0', 'IDEA 2025.1+']
      }
    },
    docsRoute: '/docs/intellij-plugin',
    cta: 'Read plugin docs'
  },
  {
    id: 'hub',
    name: 'Jeffrey Hub',
    icon: 'bi-cloud-fill',
    tagline: 'Maintenance service for the shared recording volume.',
    oneLiner: 'Runs in Kubernetes next to the shared volume your services record into. Keeps it within its limits with scheduled jobs.',
    features: [
      { icon: 'bi-trash3', title: 'Retention', desc: 'Drops sessions by age and per-project cap, trims each instance’s newest session, enforces a storage quota and cleans up stale instances.' },
      { icon: 'bi-file-zip', title: 'Compression', desc: 'LZ4-compresses finished JFR chunks in place.' },
      { icon: 'bi-speedometer2', title: 'Storage Dashboard', desc: 'Size, files and last activity per workspace and project.' },
      { icon: 'bi-calendar-check', title: 'Scheduler', desc: 'Lists the jobs and runs one now, without waiting for its next tick.' },
      { icon: 'bi-hdd-stack', title: 'Integration based on Shared-volume', desc: 'No API and no database — the volume is the only state; Microscope reads it from the Azure file share.' }
    ],
    deployment: {
      eyebrow: 'Deployment',
      title: 'Deploys to Kubernetes',
      desc: 'Hub runs alongside your services on the shared volume they record into.',
      topology: [
        { icon: 'bi-app-indicator', label: 'Your services', em: '+ provisioner' },
        { icon: 'bi-hdd-stack', code: '/mnt/jeffrey-home', label: 'shared volume' },
        { icon: 'bi-cloud-fill', label: 'Jeffrey Hub', badge: 'Scheduler jobs' }
      ]
    },
    docsRoute: '/docs/hub/deployment',
    cta: 'Read deployment guide'
  }
];

const activeTab = ref<Tab>('microscope');
const copied = ref(false);

const active = computed(() => productTabs.find(p => p.id === activeTab.value)!);

function selectTab(id: Tab): void {
  activeTab.value = id;
}

function onTabKeydown(event: KeyboardEvent, index: number): void {
  const moves: Record<string, number> = { ArrowRight: 1, ArrowLeft: -1 };
  const move = moves[event.key];
  if (move === undefined) {
    return;
  }
  event.preventDefault();
  const next = productTabs[(index + move + productTabs.length) % productTabs.length];
  selectTab(next.id);
  document.getElementById(`home-tab-${next.id}`)?.focus();
}

async function copyCmd(): Promise<void> {
  const cmd = active.value.deployment.cmd;
  if (!cmd) {
    return;
  }
  try {
    await navigator.clipboard.writeText(cmd);
    copied.value = true;
    window.setTimeout(() => {
      copied.value = false;
    }, COPIED_FEEDBACK_MS);
  } catch {
    copied.value = false;
  }
}
</script>

<template>
  <div class="home">
    <!-- Hero: the Jeffrey mark at the centre of its product constellation -->
    <section class="hero">
      <canvas ref="skyCanvas" class="sky" aria-hidden="true"></canvas>
      <div class="wrap hero-grid">
        <div class="hero-copy">
          <span class="kicker">JVM performance analyst · self-hosted</span>
          <h1 class="hero-title"><span class="hero-title-dim">Profile your JVM</span><br />with runtime data.</h1>
          <p class="lede">
            Jeffrey turns JFR recordings and heap dumps into flamegraphs, timeseries and JVM dashboards.
            You read them in Microscope, and your coding agent reads the same profile over MCP.
          </p>
          <div class="ctas">
            <router-link to="/docs/microscope/quick-start" class="hero-cta hero-cta--primary">
              Get started <i class="bi bi-arrow-right"></i>
            </router-link>
            <router-link to="/docs/microscope-mcp/claude-code" class="hero-cta hero-cta--agent">
              <i class="bi bi-stars"></i> Connect your agent
            </router-link>
          </div>
          <div class="formats">
            <span>JFR</span><span>HPROF</span><span>pprof</span><span>OTLP</span><span>Apache-2.0</span>
            <a :href="MICROSCOPE_JAR_URL">microscope.jar <i class="bi bi-download"></i></a>
          </div>
        </div>

        <div class="cosmos" aria-label="The Jeffrey product family">
          <svg class="cosmos-links" viewBox="0 0 100 92" preserveAspectRatio="xMidYMid meet" aria-hidden="true">
            <ellipse class="ring" :cx="CORE_X" :cy="CORE_Y" rx="34" ry="31" />
            <ellipse class="ring ring--outer" :cx="CORE_X" :cy="CORE_Y" rx="47" ry="43" />
            <line
              v-for="node in cosmosNodes"
              :key="`edge-${node.id}`"
              class="edge"
              :class="{ 'edge--on': hoveredNode === node.id }"
              :x1="CORE_X" :y1="CORE_Y" :x2="node.x" :y2="node.y"
              :style="{ stroke: node.color }"
            />
            <circle
              v-for="node in cosmosNodes"
              :key="`pulse-${node.id}`"
              class="pulse"
              r=".7"
              :style="{ fill: node.color }"
            >
              <animateMotion :dur="`${3 + node.delay}s`" repeatCount="indefinite" :path="pulsePath(node)" />
            </circle>
          </svg>
          <div class="core"><img src="/images/jeffrey-icon.svg" alt="" /></div>
          <router-link
            v-for="node in cosmosNodes"
            :key="node.id"
            :to="node.to"
            class="node"
            :class="[`node--${node.id}`, `node--${node.tier}`, { 'node--tip-up': node.tipUp }]"
            :style="nodeStyle(node)"
            @mouseenter="hoveredNode = node.id"
            @mouseleave="hoveredNode = null"
            @focus="hoveredNode = node.id"
            @blur="hoveredNode = null"
          >
            <span class="node-card">
              <span class="node-ic"><i class="bi" :class="node.icon"></i></span>
              <span><b>{{ node.name }}</b><small>{{ node.sub }}</small></span>
            </span>
            <span class="tip">
              {{ node.tip }}
              <strong>Open docs <i class="bi bi-arrow-up-right"></i></strong>
            </span>
          </router-link>
        </div>
      </div>
    </section>

    <div class="wrap"><div class="divider" aria-hidden="true"><img src="/images/jeffrey-icon.svg" alt="" /></div></div>

    <!-- How the parts connect: one curved route from your services to your coding agent -->
    <section class="pipeline" aria-labelledby="pipeline-title">
      <div class="wrap">
        <div class="section-head">
          <h2 id="pipeline-title">One JFR pipeline. Every part works alone.</h2>
          <p>Your services record to a shared volume. Microscope reads it, or any JFR file you drop in, and your coding agent turns the findings into a code change.</p>
        </div>
        <div class="route">
          <svg class="route-svg" viewBox="0 0 1000 300" aria-hidden="true">
            <defs>
              <linearGradient id="home-route-gradient" gradientUnits="userSpaceOnUse" x1="10" y1="0" x2="990" y2="0">
                <stop offset="0" style="stop-color: var(--ember); stop-opacity: 0" />
                <stop offset="0.1" style="stop-color: var(--ember)" />
                <stop offset="0.37" style="stop-color: var(--amber)" />
                <stop offset="0.63" style="stop-color: var(--ms)" />
                <stop offset="0.9" style="stop-color: var(--mcp)" />
                <stop offset="1" style="stop-color: var(--mcp); stop-opacity: 0" />
              </linearGradient>
            </defs>
            <path v-for="dash in ROUTE_DASHES" :key="dash" class="route-dash" :d="dash" />
            <path class="route-glow" :d="ROUTE_PATH" />
            <path id="home-route-path" class="route-line" :d="ROUTE_PATH" />
            <circle v-for="begin in ROUTE_PULSE_BEGINS" :key="begin" class="pulse route-pulse" r="4">
              <animateMotion dur="6s" repeatCount="indefinite" :begin="begin">
                <mpath href="#home-route-path" />
              </animateMotion>
            </circle>
          </svg>
          <template v-for="step in routeSteps" :key="step.station.name">
            <div class="station" :style="positionStyle(step.station)">
              <span class="station-ic"><i class="bi" :class="step.station.icon"></i></span>
              <span><b>{{ step.station.name }}</b><small>{{ step.station.sub }}</small></span>
            </div>
            <span class="route-note" :style="positionStyle(step.note)">
              <i class="bi" :class="step.note.icon"></i>
              <span>
                <template v-for="(part, i) in step.note.parts" :key="i">
                  <b v-if="part.strong">{{ part.text }}</b><template v-else>{{ part.text }}</template>
                </template>
              </span>
            </span>
            <span v-if="step.hop" class="hop" :style="positionStyle(step.hop)">{{ step.hop.label }}</span>
          </template>
        </div>
      </div>
    </section>

    <div class="wrap"><div class="divider" aria-hidden="true"><img src="/images/jeffrey-icon.svg" alt="" /></div></div>

    <!-- Every product, in detail -->
    <section id="products" class="showcase" :data-active="activeTab">
      <div class="wrap">
        <div class="section-head">
          <h2 class="section-title-lg">Every product, in detail.</h2>
        </div>
        <div class="tabbar" role="tablist" aria-label="Products">
          <button
            v-for="(t, i) in productTabs"
            :id="`home-tab-${t.id}`"
            :key="t.id"
            type="button"
            role="tab"
            class="tab"
            :aria-selected="activeTab === t.id"
            :tabindex="activeTab === t.id ? 0 : -1"
            aria-controls="home-tab-panel"
            @click="selectTab(t.id)"
            @keydown="onTabKeydown($event, i)"
          >
            <i class="bi" :class="t.icon"></i>
            <span>{{ t.name }}</span>
          </button>
        </div>

        <div id="home-tab-panel" :key="activeTab" class="panel" role="tabpanel" :aria-labelledby="`home-tab-${activeTab}`">
          <div class="panel-head">
            <h3>{{ active.tagline }}</h3>
            <p>{{ active.oneLiner }}</p>
          </div>
          <div class="panel-body">
            <div class="features">
              <article v-for="f in active.features" :key="f.title" class="feature">
                <span class="feature-ic"><i class="bi" :class="f.icon"></i></span>
                <h4>{{ f.title }}</h4>
                <p>{{ f.desc }}</p>
              </article>
            </div>

            <aside class="dep">
              <span class="dep-eyebrow">{{ active.deployment.eyebrow }}</span>
              <h4>{{ active.deployment.title }}</h4>
              <p>{{ active.deployment.desc }}</p>

              <ol v-if="active.deployment.topology" class="topo">
                <li v-for="(node, i) in active.deployment.topology" :key="i">
                  <i class="bi" :class="node.icon"></i>
                  <span>
                    <code v-if="node.code">{{ node.code }}</code>
                    <b v-else>{{ node.label }}</b>
                    <template v-if="node.code">{{ ` ${node.label}` }}</template>
                    <em v-if="node.em">{{ ` ${node.em}` }}</em>
                    <span v-if="node.badge" class="topo-badge">{{ node.badge }}</span>
                  </span>
                </li>
              </ol>

              <a
                v-else-if="active.deployment.marketplace"
                :href="active.deployment.marketplace.url"
                target="_blank"
                rel="noopener noreferrer"
                class="market"
              >
                <img :src="active.deployment.marketplace.iconPath" :alt="`${active.deployment.marketplace.name} icon`" />
                <span>
                  <b>{{ active.deployment.marketplace.name }}</b>
                  <small>{{ active.deployment.marketplace.sub }}</small>
                </span>
                <span class="market-badges">
                  <em v-for="badge in active.deployment.marketplace.badges" :key="badge">{{ badge }}</em>
                </span>
              </a>

              <div v-else-if="active.deployment.cmd" class="cmd">
                <pre><code>{{ active.deployment.cmd }}</code></pre>
                <button
                  type="button"
                  class="copy"
                  :class="{ 'copy--done': copied }"
                  :aria-label="copied ? 'Copied' : 'Copy command'"
                  @click="copyCmd"
                >
                  <i class="bi" :class="copied ? 'bi-check2' : 'bi-clipboard'"></i>
                </button>
              </div>

              <router-link :to="active.docsRoute" class="dep-cta">
                {{ active.cta }} <i class="bi bi-arrow-right"></i>
              </router-link>
            </aside>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* Constellation homepage: warm-paper sky, the Jeffrey mark at the centre of its products,
   then the pipeline as one curved route, then a tabbed product deep-dive. */
.home {
  --night: #faf7f2;
  --surface: #ffffff;
  --surface-2: #f6f1ea;
  --line: rgba(48, 32, 12, 0.09);
  --line-2: rgba(48, 32, 12, 0.16);
  --text: #17130e;
  --muted: #5f584e;
  --faint: #91887b;
  --flame: #ff6d00;
  --amber: #f59e0b;
  --ember: #e4570b;
  --flame-ink: #1b0c00;
  --on-accent: #ffffff;
  --ms: #2f6bff;
  --ms-deep: #1d3fd8;
  --mcp: #0fa572;
  --mcp-deep: #0b7a55;
  --ide: #8b5cf6;
  --core-top: #ffffff;
  --core-bottom: #fff1e4;
  --shadow-card: 0 1px 2px rgba(48, 32, 12, 0.06), 0 16px 36px -18px rgba(48, 32, 12, 0.28);
  --font-mono: 'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, monospace;
  --r-sm: 8px;
  --r-md: 14px;
  --r-lg: 22px;
  background: var(--night);
  color: var(--text);
  font-size: 16px;
  line-height: 1.55;
  -webkit-font-smoothing: antialiased;
  overflow-x: hidden;
  padding-bottom: 96px;
}

/* Neutralise Bootstrap's reboot inside the page. :where() keeps these one class strong:
   enough to beat Bootstrap's element rules, weak enough for every component rule below to win. */
.home :where(a) {
  color: inherit;
  text-decoration: none;
}

.home :where(h1, h2, h3, h4, p) {
  margin: 0;
}

.home h1, .home h2, .home h3 {
  text-wrap: balance;
}

.home :focus-visible {
  outline: 2px solid var(--amber);
  outline-offset: 3px;
  border-radius: 6px;
}

.wrap {
  width: 100%;
  max-width: 1360px;
  margin: 0 auto;
  padding-left: 40px;
  padding-right: 40px;
}

.kicker {
  font: 600 12px/1.4 var(--font-mono);
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--ember);
}

/* ============ Hero ============ */
.hero {
  position: relative;
  overflow: hidden;
}

.hero::before {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(40% 55% at 74% 50%, rgba(255, 109, 0, 0.13), transparent 70%);
  pointer-events: none;
}

.sky {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.hero-grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 40px;
  align-items: center;
  padding-top: 48px;
  padding-bottom: 40px;
}

.hero-copy {
  display: grid;
  gap: 24px;
  align-content: center;
}

.hero-title {
  font-size: clamp(30px, 3.4vw, 50px);
  font-weight: 700;
  line-height: 1.05;
  letter-spacing: -0.02em;
}

.hero-title-dim {
  background: linear-gradient(180deg, var(--text), color-mix(in srgb, var(--text) 55%, var(--night)));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.lede {
  font-size: 18px;
  color: var(--muted);
  max-width: 54ch;
}

.ctas {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.hero-cta {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 12px 18px;
  border-radius: var(--r-sm);
  border: 1px solid transparent;
  font-size: 15px;
  font-weight: 500;
  line-height: 1.5;
  letter-spacing: 0.005em;
  white-space: nowrap;
  transition: background 0.15s, border-color 0.15s, color 0.15s, box-shadow 0.15s;
}

.hero-cta i {
  font-size: 16px;
  transition: transform 0.15s;
}

.hero-cta--primary {
  background: rgba(255, 109, 0, 0.22);
  border-color: rgba(255, 109, 0, 0.65);
  color: var(--ember);
  font-weight: 600;
}

.hero-cta--primary:hover {
  background: var(--flame);
  border-color: var(--flame);
  color: var(--flame-ink);
  box-shadow: 0 8px 22px -12px var(--flame);
}

.hero-cta--primary:hover i {
  transform: translateX(3px);
}

.hero-cta--agent {
  background: rgba(255, 255, 255, 0.75);
  border-color: var(--line-2);
  color: var(--text);
}

.hero-cta--agent i {
  color: var(--mcp);
}

.hero-cta--agent:hover {
  border-color: color-mix(in srgb, var(--mcp) 55%, transparent);
  background: color-mix(in srgb, var(--mcp) 10%, transparent);
}

.formats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  font: 13px var(--font-mono);
  color: var(--faint);
}

.formats a {
  color: var(--muted);
  border-bottom: 1px dashed var(--line-2);
}

.formats a:hover {
  color: var(--text);
}

/* ============ Constellation ============ */
.cosmos {
  position: relative;
  width: 100%;
  max-width: 720px;
  justify-self: center;
  aspect-ratio: 100 / 92;
}

.cosmos-links {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: visible;
}

.ring {
  fill: none;
  stroke: var(--line-2);
  stroke-dasharray: 0.6 1.4;
  stroke-width: 0.18;
}

.ring--outer {
  stroke: var(--line);
}

.edge {
  stroke-width: 0.3;
  opacity: 0.6;
  transition: opacity 0.2s, stroke-width 0.2s;
}

.edge--on {
  opacity: 1;
  stroke-width: 0.5;
}

.pulse {
  opacity: 0.9;
}

.core {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 25%;
  aspect-ratio: 1;
  transform: translate(-50%, -50%);
  border-radius: 26%;
  display: grid;
  place-items: center;
  background: linear-gradient(160deg, var(--core-top), var(--core-bottom));
  border: 1px solid rgba(255, 109, 0, 0.3);
  box-shadow: 0 0 0 12px rgba(255, 109, 0, 0.05), 0 0 0 28px rgba(255, 109, 0, 0.03),
    0 0 110px rgba(255, 109, 0, 0.32), var(--shadow-card);
  animation: breathe 5s ease-in-out infinite;
}

.core img {
  width: 56%;
  height: 56%;
}

@keyframes breathe {
  50% {
    box-shadow: 0 0 0 16px rgba(255, 109, 0, 0.06), 0 0 0 36px rgba(255, 109, 0, 0.03),
      0 0 150px rgba(255, 109, 0, 0.42), var(--shadow-card);
  }
}

.node {
  position: absolute;
  transform: translate(-50%, -50%);
  z-index: 2;
}

.node-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 16px 11px 11px;
  border-radius: var(--r-md);
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid var(--line-2);
  box-shadow: var(--shadow-card);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  white-space: nowrap;
  animation: float 7s ease-in-out infinite;
  animation-delay: var(--delay, 0s);
  transition: border-color 0.2s, background 0.2s;
}

@keyframes float {
  50% {
    transform: translateY(-6px);
  }
}

.node-ic {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  font-size: 17px;
  background: var(--surface-2);
  color: var(--ember);
}

.node b {
  display: block;
  font-size: 14.5px;
  font-weight: 500;
}

.node small {
  display: block;
  font-size: 12px;
  color: var(--faint);
}

.node--core .node-card {
  gap: 14px;
  padding: 16px 22px 16px 16px;
  border-color: color-mix(in srgb, var(--node-color) 30%, var(--line-2));
}

.node--core .node-ic {
  width: 48px;
  height: 48px;
  font-size: 22px;
  color: var(--on-accent);
}

.node--core b {
  font-size: 17px;
}

.node--core small {
  font-size: 12.5px;
}

/* Capture: the ways profiling starts. A step below the core: amber-tinted and a little larger. */
.node--capture .node-card {
  padding: 12px 18px 12px 12px;
  border-color: color-mix(in srgb, var(--amber) 40%, var(--line-2));
  box-shadow: 0 0 32px color-mix(in srgb, var(--amber) 18%, transparent), var(--shadow-card);
}

.node--capture .node-ic {
  width: 40px;
  height: 40px;
  font-size: 18px;
  color: var(--ember);
  background: color-mix(in srgb, var(--amber) 18%, var(--surface));
}

.node--capture b {
  font-size: 15px;
}

/* Companion: optional extras. Compact and quiet until hovered. */
.node--companion .node-card {
  gap: 10px;
  padding: 8px 14px 8px 8px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 8px 22px -16px rgba(48, 32, 12, 0.28);
}

.node--companion .node-ic {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  font-size: 14px;
  color: var(--faint);
}

.node--companion b {
  font-size: 13.5px;
  font-weight: 400;
  color: var(--muted);
}

.node--companion small {
  font-size: 11px;
}

.node--companion:hover .node-ic,
.node--companion:focus-visible .node-ic {
  color: var(--node-color);
}

.node--microscope .node-ic {
  background: linear-gradient(135deg, var(--ms), var(--ms-deep));
}

.node--mcp .node-ic {
  background: linear-gradient(135deg, var(--mcp), var(--mcp-deep));
}

.node--microscope .node-card {
  box-shadow: 0 0 48px color-mix(in srgb, var(--ms) 28%, transparent), var(--shadow-card);
}

.node--mcp .node-card {
  box-shadow: 0 0 48px color-mix(in srgb, var(--mcp) 28%, transparent), var(--shadow-card);
}

.node:hover .node-card,
.node:focus-visible .node-card {
  border-color: var(--node-color, var(--ember));
  background: var(--surface);
}

.node:focus-visible {
  outline: none;
}

.tip {
  position: absolute;
  left: 50%;
  top: calc(100% + 10px);
  width: 250px;
  transform: translate(-50%, 4px);
  padding: 12px 14px;
  border-radius: var(--r-sm);
  background: var(--surface);
  border: 1px solid var(--line-2);
  box-shadow: var(--shadow-card);
  font-size: 13px;
  line-height: 1.5;
  color: var(--muted);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.18s, transform 0.18s;
  z-index: 5;
}

.tip strong {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  font-weight: 500;
  color: var(--node-color, var(--ember));
}

.node--tip-up .tip {
  top: auto;
  bottom: calc(100% + 10px);
}

.node:hover .tip,
.node:focus-visible .tip {
  opacity: 1;
  transform: translate(-50%, 0);
}

.node:hover,
.node:focus-visible {
  z-index: 6;
}

/* ============ Divider ============ */
.divider {
  display: flex;
  align-items: center;
  gap: 16px;
  width: min(440px, 100%);
  margin: 44px auto;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--line-2));
}

.divider::after {
  background: linear-gradient(90deg, var(--line-2), transparent);
}

.divider img {
  width: 16px;
  height: 16px;
  filter: drop-shadow(0 0 8px rgba(255, 109, 0, 0.55));
}

/* ============ Section heads ============ */
.section-head {
  display: grid;
  gap: 12px;
  justify-items: center;
  text-align: center;
  max-width: 760px;
  margin: 0 auto;
}

.section-head h2 {
  font-size: clamp(26px, 2.8vw, 38px);
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -0.02em;
}

.section-head .section-title-lg {
  font-size: clamp(34px, 4.4vw, 58px);
  line-height: 1;
}

.section-head p {
  color: var(--muted);
  max-width: 62ch;
}

/* ============ Pipeline route ============ */
.route {
  position: relative;
  max-width: 1180px;
  margin: 36px auto 0;
  aspect-ratio: 1000 / 300;
}

.route-svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: visible;
}

.route-glow {
  fill: none;
  stroke: url(#home-route-gradient);
  stroke-width: 12;
  stroke-linecap: round;
  opacity: 0.14;
}

.route-line {
  fill: none;
  stroke: url(#home-route-gradient);
  stroke-width: 2.2;
  stroke-linecap: round;
}

.route-dash {
  fill: none;
  stroke: var(--line-2);
  stroke-width: 1.5;
  stroke-dasharray: 4 6;
}

.route-pulse {
  fill: var(--amber);
}

.station,
.hop,
.route-note {
  position: absolute;
  left: var(--x);
  top: var(--y);
  transform: translate(-50%, -50%);
  z-index: 1;
  white-space: nowrap;
}

.station {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 18px 12px 12px;
  border-radius: var(--r-md);
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid var(--line-2);
  box-shadow: 0 0 40px -10px color-mix(in srgb, var(--c) 55%, transparent), var(--shadow-card);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.station-ic {
  flex: none;
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  font-size: 19px;
  color: var(--on-accent);
  background: linear-gradient(135deg, color-mix(in srgb, var(--c) 70%, var(--surface)), var(--c));
}

.station b {
  display: block;
  font-size: 15px;
  font-weight: 500;
}

.station small {
  display: block;
  font: 12px/1.4 var(--font-mono);
  color: var(--faint);
}

.hop {
  font: 500 10.5px/1 var(--font-mono);
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--c);
  padding: 7px 11px;
  border-radius: 999px;
  background: var(--night);
  border: 1px solid color-mix(in srgb, var(--c) 50%, transparent);
}

.route-note {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 13px;
  border-radius: 999px;
  font-size: 12.5px;
  color: var(--muted);
  background: var(--surface);
  border: 1px dashed var(--line-2);
}

.route-note i {
  color: var(--c);
}

.route-note b {
  color: var(--text);
  font-weight: 500;
}

/* ============ Product showcase ============ */
.showcase {
  --tab-accent: var(--ember);
}

.showcase[data-active='microscope'] {
  --tab-accent: var(--ms);
}

.showcase[data-active='mcp'] {
  --tab-accent: var(--mcp);
}

.showcase[data-active='plugin'] {
  --tab-accent: var(--ide);
}

.showcase[data-active='hub'] {
  --tab-accent: var(--ember);
}

.tabbar {
  display: flex;
  gap: 6px;
  width: fit-content;
  max-width: 100%;
  margin: 32px auto;
  padding: 6px;
  overflow-x: auto;
  border: 1px solid var(--line-2);
  border-radius: 999px;
  background: var(--surface);
}

.tab {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  color: var(--muted);
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  cursor: pointer;
  transition: color 0.15s, background 0.15s, border-color 0.15s;
}

.tab:hover {
  color: var(--text);
}

.tab[aria-selected='true'] {
  color: var(--text);
  background: color-mix(in srgb, var(--tab-accent) 16%, transparent);
  border-color: color-mix(in srgb, var(--tab-accent) 50%, transparent);
}

.tab[aria-selected='true'] i {
  color: var(--tab-accent);
}

.panel {
  animation: rise 0.35s ease both;
}

@keyframes rise {
  from {
    opacity: 0.4;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.panel-head {
  display: grid;
  gap: 10px;
  justify-items: center;
  text-align: center;
  margin-bottom: 32px;
}

.panel-head h3 {
  font-size: clamp(24px, 2.6vw, 34px);
  font-weight: 700;
  line-height: 1.15;
  letter-spacing: -0.02em;
}

.panel-head p {
  color: var(--muted);
  max-width: 64ch;
}

.panel-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: 20px;
  align-items: start;
}

.features {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}

.feature {
  display: grid;
  gap: 6px;
  align-content: start;
  padding: 20px;
  border-radius: var(--r-md);
  background: var(--surface);
  border: 1px solid var(--line);
  box-shadow: 0 1px 2px rgba(48, 32, 12, 0.04);
  transition: border-color 0.2s, background 0.2s;
}

.feature:hover {
  border-color: color-mix(in srgb, var(--tab-accent) 45%, transparent);
  background: color-mix(in srgb, var(--tab-accent) 4%, var(--surface));
}

.feature-ic {
  width: 36px;
  height: 36px;
  margin-bottom: 8px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  font-size: 17px;
  color: var(--tab-accent);
  background: color-mix(in srgb, var(--tab-accent) 13%, transparent);
}

.feature h4 {
  font-size: 15.5px;
  font-weight: 500;
}

.feature p {
  font-size: 14px;
  color: var(--muted);
}

.dep {
  position: sticky;
  top: 90px;
  min-width: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 10px;
  padding: 26px;
  border-radius: var(--r-lg);
  background: linear-gradient(180deg, color-mix(in srgb, var(--tab-accent) 10%, var(--surface)), var(--surface));
  border: 1px solid color-mix(in srgb, var(--tab-accent) 35%, transparent);
  box-shadow: 0 0 60px -24px color-mix(in srgb, var(--tab-accent) 45%, transparent), var(--shadow-card);
}

.dep-eyebrow {
  font: 600 11px/1 var(--font-mono);
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--tab-accent);
}

.dep h4 {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.15;
  letter-spacing: -0.015em;
}

.dep p {
  font-size: 14.5px;
  color: var(--muted);
}

.cmd {
  position: relative;
  margin-top: 8px;
  background: var(--surface-2);
  border: 1px solid var(--line-2);
  border-radius: var(--r-sm);
}

.cmd pre {
  margin: 0;
  padding: 14px 48px 14px 14px;
  overflow-x: auto;
  font: 12.5px/1.7 var(--font-mono);
}

.cmd code {
  color: var(--text);
  font: inherit;
}

.copy {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  border: 1px solid var(--line-2);
  background: transparent;
  color: var(--muted);
  font-size: 15px;
  cursor: pointer;
}

.copy:hover {
  color: var(--text);
  background: var(--surface-2);
}

.copy--done {
  color: var(--mcp);
  border-color: var(--mcp);
}

.topo {
  list-style: none;
  margin: 8px 0 0;
  padding: 0;
  display: grid;
  gap: 22px;
}

.topo li {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: var(--r-sm);
  background: var(--night);
  border: 1px solid var(--line-2);
  font-size: 14px;
  color: var(--muted);
}

.topo li > i {
  font-size: 18px;
  color: var(--tab-accent);
}

.topo li b {
  color: var(--text);
  font-weight: 500;
}

.topo li code {
  color: var(--text);
  font: 12.5px var(--font-mono);
}

.topo li em {
  font-style: normal;
  color: var(--faint);
}

.topo li + li::before {
  content: '';
  position: absolute;
  left: 22px;
  top: -22px;
  height: 22px;
  border-left: 2px dashed color-mix(in srgb, var(--tab-accent) 50%, transparent);
}

.topo-badge {
  margin-left: 6px;
  padding: 3px 7px;
  border-radius: 5px;
  font: 600 10.5px/1 var(--font-mono);
  background: color-mix(in srgb, var(--tab-accent) 13%, transparent);
  color: var(--tab-accent);
}

.market {
  margin-top: 8px;
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 8px 12px;
  align-items: center;
  padding: 14px;
  border-radius: var(--r-sm);
  background: var(--night);
  border: 1px solid var(--line-2);
  transition: border-color 0.15s;
}

.market:hover {
  border-color: var(--tab-accent);
}

.market img {
  width: 36px;
  height: 36px;
}

.market b {
  display: block;
  font-weight: 500;
}

.market small {
  font-size: 12.5px;
  color: var(--muted);
}

.market-badges {
  grid-column: 1 / -1;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.market-badges em {
  padding: 5px 8px;
  border-radius: 5px;
  font: 600 11px/1 var(--font-mono);
  font-style: normal;
  background: var(--surface-2);
  color: var(--muted);
}

.dep-cta {
  margin-top: 8px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
  color: var(--tab-accent);
}

.dep-cta i {
  transition: transform 0.15s;
}

.dep-cta:hover i {
  transform: translateX(3px);
}

/* ============ Responsive ============ */
@media (max-width: 1099px) {
  .route {
    aspect-ratio: auto;
    width: fit-content;
    max-width: 100%;
    margin: 28px auto 0;
    padding-left: 30px;
    display: grid;
    gap: 14px;
    justify-items: start;
  }

  .route::before {
    content: '';
    position: absolute;
    left: 10px;
    top: 24px;
    bottom: 24px;
    width: 2px;
    border-radius: 2px;
    background: linear-gradient(var(--ember), var(--amber), var(--ms), var(--mcp));
  }

  .route-svg {
    display: none;
  }

  .station,
  .hop,
  .route-note {
    position: relative;
    left: auto;
    top: auto;
    transform: none;
  }

  .station {
    max-width: 100%;
    white-space: normal;
  }

  .route-note {
    margin-left: 16px;
    white-space: normal;
  }
}

@media (max-width: 1099px) {
  .hero-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .cosmos {
    margin-top: 12px;
  }
}

@media (max-width: 1023px) {
  .panel-body {
    grid-template-columns: minmax(0, 1fr);
  }

  .dep {
    position: static;
  }
}

@media (max-width: 759px) {
  .wrap {
    padding-left: 16px;
    padding-right: 16px;
  }
}

@media (max-width: 679px) {
  .cosmos {
    aspect-ratio: auto;
    max-width: none;
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;
  }

  .cosmos-links {
    display: none;
  }

  .core {
    position: relative;
    left: auto;
    top: auto;
    transform: none;
    grid-column: 1 / -1;
    justify-self: center;
    width: 120px;
    margin: 24px 0 36px;
  }

  .node {
    position: relative;
    left: auto !important;
    top: auto !important;
    transform: none;
    min-width: 0;
  }

  .node--core {
    grid-column: 1 / -1;
  }

  .node-card {
    height: 100%;
    white-space: normal;
    animation: none;
  }

  .tip {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .core,
  .node-card,
  .panel {
    animation: none;
  }

  .pulse {
    display: none;
  }
}
</style>
