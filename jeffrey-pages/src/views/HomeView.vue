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
import { useProductVersions, jibVersionLinks } from '@/composables/useProductVersions';

const MICROSCOPE_JAR_URL = 'https://github.com/petrbouda/jeffrey/releases/latest/download/microscope.jar';
const COPIED_FEEDBACK_MS = 1400;

// ---------- Pipeline satellites ----------
// Products that branch off their parent station in the hero pipeline.

interface PipelineSatellite {
  id: string;
  name: string;
  sub: string;
  icon: string;
  color: string;
  to: string;
  x: number;
  y: number;
  delay: number;
}

const pipelineSatellites: PipelineSatellite[] = [
  { id: 'provisioner', name: 'Provisioner', sub: 'session bootstrap', icon: 'bi-terminal', color: 'var(--provisioner)', to: '/docs/provisioner', x: 6, y: 84.5, delay: 1.5 },
  { id: 'jib', name: 'Jeffrey JIB', sub: 'build-time', icon: 'bi-box-seam', color: 'var(--jib)', to: '/docs/jib', x: 18.6, y: 84.5, delay: 3.8 },
  { id: 'volume-scheduler', name: 'Volume Scheduler', sub: 'maintenance', icon: 'bi-clock-history', color: 'var(--volume-scheduler)', to: '/docs/volume-scheduler', x: 37, y: 2, delay: 0.6 },
  { id: 'intellij', name: 'IntelliJ Plugin', sub: 'frame → source', icon: 'bi-window-stack', color: 'var(--ide)', to: '/docs/intellij-plugin', x: 65, y: 84.5, delay: 4.2 },
  { id: 'mcp', name: 'Microscope MCP', sub: '116 tools', icon: 'bi-stars', color: 'var(--mcp)', to: '/docs/microscope-mcp', x: 91, y: 2, delay: 2.8 }
];

// SVG-coordinate branch lines from stations to their satellites (viewBox 0 0 1000 420).
const BRANCH_LINES = [
  { x1: 100, y1: 284, x2: 60, y2: 344, color: 'var(--provisioner)' },
  { x1: 120, y1: 284, x2: 186, y2: 344, color: 'var(--jib)' },
  { x1: 370, y1: 56, x2: 370, y2: 10, color: 'var(--volume-scheduler)' },
  { x1: 650, y1: 284, x2: 650, y2: 344, color: 'var(--ide)' },
  { x1: 910, y1: 56, x2: 910, y2: 10, color: 'var(--mcp)' }
];

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

// The S-curve is the hero's centrepiece, viewBox 0 0 1000 420.
const PIPELINE_PATH = 'M10 260 L100 260 C 200 260, 260 80, 370 80 S 540 260, 650 260 S 810 80, 910 80 L990 80';
const PIPELINE_PULSE_BEGINS = ['0s', '-2.3s', '-4.6s'];

interface RouteStation {
  name: string;
  sub: string;
  icon: string;
  color: string;
  x: number;
  y: number;
}

interface RouteHop {
  label: string;
  color: string;
  x: number;
  y: number;
}

const routeStations: RouteStation[] = [
  { name: 'Your services', sub: 'JFR · async-profiler', icon: 'bi-boxes', color: 'var(--ember)', x: 10, y: 61.9 },
  { name: 'Shared volume', sub: '/mnt/jeffrey', icon: 'bi-hdd-stack', color: 'var(--amber)', x: 37, y: 19 },
  { name: 'Microscope', sub: 'flamegraphs · dashboards', icon: 'bi-search', color: 'var(--ms)', x: 65, y: 61.9 },
  { name: 'Coding agent', sub: 'Claude Code · Codex', icon: 'bi-stars', color: 'var(--mcp)', x: 91, y: 19 }
];

const routeHops: RouteHop[] = [
  { label: 'record', color: 'var(--ember)', x: 23.5, y: 42 },
  { label: 'Connector', color: 'var(--ms)', x: 51, y: 42 },
  { label: 'MCP', color: 'var(--mcp)', x: 78, y: 42 }
];

// Which satellites belong to which station, by index.
const STATION_SATELLITE_IDS: string[][] = [
  ['provisioner', 'jib'],
  ['volume-scheduler'],
  ['intellij'],
  ['mcp']
];

// Interleaved steps for the narrow-screen vertical flow.
const pipelineSteps = routeStations.map((station, i) => ({
  station,
  satellites: pipelineSatellites.filter(s => STATION_SATELLITE_IDS[i].includes(s.id)),
  hop: routeHops[i]
}));

function positionStyle(item: { x: number; y: number; color?: string }): Record<string, string> {
  const style: Record<string, string> = { '--x': `${item.x}%`, '--y': `${item.y}%` };
  if (item.color) {
    style['--c'] = item.color;
  }
  return style;
}

function satelliteStyle(sat: PipelineSatellite): Record<string, string> {
  return {
    '--x': `${sat.x}%`,
    '--y': `${sat.y}%`,
    '--c': sat.color,
    '--delay': `-${sat.delay}s`
  };
}

// ---------- Product showcase ----------

type Tab = 'microscope' | 'mcp' | 'provisioner' | 'jib' | 'plugin' | 'volume-scheduler';

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
      title: 'Runs as a plain JAR',
      desc: 'No Volume Scheduler required. Java 25+ on your laptop, drop in a JFR file, browse.',
      cmd: 'java -jar microscope.jar'
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
      { icon: 'bi-speedometer2', title: 'Runtime, not compile-time', desc: 'Real CPU, allocation and lock profiles from your running JVM — not lint rules or static-analysis guesses. Every recommendation is backed by what actually happened under load.' },
      { icon: 'bi-fire', title: 'Flamegraphs, traces, heap dumps', desc: 'Differential flamegraphs, wall-clock traces, GC analysis, thread states, dominator trees, OQL — 116 tools your agent navigates programmatically.' },
      { icon: 'bi-code-slash', title: 'Charged to your source code', desc: 'Every hotspot is mapped to the method in your checkout. The agent sees the cost per frame, opens the file and reads the line — runtime names matched to your source spelling.' },
      { icon: 'bi-stars', title: 'Skills that know the method', desc: 'analyze-jfr, optimize-code, regression-check, profile-run — 13 skills and 2 sub-agents that return typed findings, not raw data.' },
      { icon: 'bi-check2-square', title: 'Findings carry evidence', desc: 'Each finding is typed with its evidence and a next-tool pointer. The agent follows the chain: hotspot, call sites, source and recommendation.' },
      { icon: 'bi-git', title: 'From profile to pull request', desc: 'optimize-code maps the hottest frames to source files, proposes minimal edits, applies them and re-profiles to verify the fix landed.' }
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
    id: 'provisioner',
    name: 'Provisioner',
    icon: 'bi-terminal-fill',
    tagline: 'One command before java — and your JVM starts under the profiler.',
    oneLiner: 'A native CLI that runs right before your application: it opens a recording session on the shared volume and writes the argfile that starts the JVM under async-profiler and JFR.',
    features: [
      { icon: 'bi-file-earmark-code', title: 'Writes a JVM @argfile', desc: 'The async-profiler agent, JFR, heap dump on OOM and unified logging, one option per line for java @/tmp/jvm.args. Or a .env file to source.' },
      { icon: 'bi-folder2-open', title: 'Lays out the session', desc: 'workspace / project / instance / session on the volume, a UUIDv7 per start. The folder names are the state; no metadata file is written.' },
      { icon: 'bi-sliders', title: 'One HOCON file, or none', desc: 'Every key has a JEFFREY_* variable that wins over the file, so containers can run it from the environment alone.' },
      { icon: 'bi-braces', title: 'Placeholders', desc: '<<ENV:NAME>> and <<JEFFREY:CURRENT_SESSION>> put this run\'s session directory into your own flags, e.g. app logs beside the recordings.' },
      { icon: 'bi-lightning-charge', title: 'Native or jar', desc: 'A GraalVM binary for amd64 and arm64 that starts in milliseconds, or one ~4 MB jar that runs on your app\'s own JVM.' },
      { icon: 'bi-shield-check', title: 'Fail-open', desc: 'A missing library or a broken config starts the app unprofiled, never not at all, with one profiling DISABLED: line in the log.' }
    ],
    deployment: {
      eyebrow: 'Usage',
      title: 'Download, init, exec',
      desc: 'Grab provisioner.jar or the native binary from GitHub Releases and run it in your entrypoint. With JEFFREY_HOME and JEFFREY_PROJECT_NAME set, it needs no config file.',
      cmd: 'provisioner init\nexec java @/tmp/jvm.args -jar app.jar'
    },
    docsRoute: '/docs/provisioner',
    cta: 'Read Provisioner docs'
  },
  {
    id: 'jib',
    name: 'JIB Extension',
    icon: 'bi-box-seam-fill',
    tagline: 'Profiling baked into the image, before your app starts.',
    oneLiner: 'A Jib extension for Maven and Gradle that wraps the container entrypoint. The provisioner and async-profiler ship inside the image, so the pod needs no command: override and nothing is downloaded at start.',
    features: [
      { icon: 'bi-box-arrow-in-right', title: 'Wraps the entrypoint', desc: 'jeffrey-entrypoint becomes ENTRYPOINT and Jib\'s java -cp … <MainClass> moves to CMD. Main class, classpath and jvmFlags are kept.' },
      { icon: 'bi-box-seam', title: 'Self-contained image', desc: 'Provisioner and async-profiler land under /opt/jeffrey in their own layer, only for the architectures you target.' },
      { icon: 'bi-ui-checks-grid', title: 'Four flavours, no properties', desc: 'maven-native, maven-jar, gradle-native or gradle-jar. The dependency you declare is the whole configuration.' },
      { icon: 'bi-person-badge', title: 'Self-identifying', desc: 'JEFFREY_PROJECT_NAME is baked from the artifactId or project name. The pod\'s only YAML is the volume mount.' },
      { icon: 'bi-toggle-off', title: 'Runtime kill switch', desc: 'JEFFREY_ENABLED=false on the pod runs the plain Jib command, no rebuild. Any misconfiguration fails open.' },
      { icon: 'bi-cpu', title: 'amd64 and arm64', desc: 'Payloads for both Linux architectures. An unsupported platform fails the build rather than shipping an image that cannot profile itself.' }
    ],
    deployment: {
      eyebrow: 'Build setup',
      title: 'One plugin dependency',
      desc: 'Add a flavour to the jib plugin and register the extension. At container start the wrapper provisions the session, then hands over to java.',
      topology: [
        { icon: 'bi-hammer', code: 'jib:build', label: 'with jeffrey-jib-maven-native' },
        { icon: 'bi-box-seam', code: '/opt/jeffrey', label: 'provisioner + async-profiler' },
        { icon: 'bi-play-circle', code: 'jeffrey-entrypoint', label: 'at start', badge: 'Fail-open' }
      ]
    },
    docsRoute: '/docs/jib',
    cta: 'Read JIB docs'
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
    id: 'volume-scheduler',
    name: 'Volume Scheduler',
    icon: 'bi-clock-history',
    tagline: 'A small maintenance service for the shared recording volume.',
    oneLiner: 'Runs beside Microscope, next to the shared volume your services record into, and keeps that volume within its limits with two scheduled jobs.',
    features: [
      { icon: 'bi-trash3', title: 'Retention', desc: 'Drops sessions by age and by a cap per instance, trims each instance’s newest session, keeps every project within its disk budget and cleans up stale instances.' },
      { icon: 'bi-file-zip', title: 'Compression', desc: 'LZ4-compresses finished JFR chunks in place.' },
      { icon: 'bi-speedometer2', title: 'Storage API', desc: 'Size, files and last activity per workspace and project, as JSON.' },
      { icon: 'bi-calendar-check', title: 'Jobs API', desc: 'Lists both jobs over the REST API and runs either one now, without waiting for its next tick.' },
      { icon: 'bi-hdd-stack', title: 'Integration based on Shared-volume', desc: 'No API to Microscope and no database — the volume is the only state; Microscope reads it through its connector.' }
    ],
    deployment: {
      eyebrow: 'Deployment',
      title: 'One native container',
      desc: 'The volume scheduler runs beside your services and mounts the shared volume they record into.',
      topology: [
        { icon: 'bi-app-indicator', label: 'Your services', em: '+ provisioner' },
        { icon: 'bi-hdd-stack', code: '/mnt/jeffrey', label: 'shared volume' },
        { icon: 'bi-clock-history', label: 'volume-scheduler', badge: 'Scheduled jobs' }
      ]
    },
    docsRoute: '/docs/volume-scheduler',
    cta: 'Read Volume Scheduler docs'
  }
];

const activeTab = ref<Tab>('microscope');
const copied = ref(false);
const { versionFor, volumeSchedulerDockerHubUrl } = useProductVersions();

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
    <!-- Hero: centred copy over the full-width pipeline -->
    <section class="hero">
      <canvas ref="skyCanvas" class="sky" aria-hidden="true"></canvas>
      <div class="wrap">
        <div class="hero-stack">
          <div class="hero-copy-center">
            <span class="kicker">JVM performance analyst · self-hosted</span>
            <h1 class="hero-title"><span class="hero-title-dim">Profile your JVM</span> with runtime data.</h1>
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

          <!-- Pipeline: S-curve with stations and satellite products -->
          <div class="pipeline" aria-label="JFR data pipeline with Jeffrey products">
            <svg class="pipeline-svg" viewBox="0 0 1000 420" aria-hidden="true">
              <defs>
                <linearGradient id="home-pipeline-gradient" gradientUnits="userSpaceOnUse" x1="10" y1="0" x2="990" y2="0">
                  <stop offset="0" style="stop-color: var(--ember); stop-opacity: 0" />
                  <stop offset="0.08" style="stop-color: var(--ember)" />
                  <stop offset="0.35" style="stop-color: var(--amber)" />
                  <stop offset="0.65" style="stop-color: var(--ms)" />
                  <stop offset="0.92" style="stop-color: var(--mcp)" />
                  <stop offset="1" style="stop-color: var(--mcp); stop-opacity: 0" />
                </linearGradient>
              </defs>

              <!-- Branch lines from stations to satellite products -->
              <line
                v-for="(branch, i) in BRANCH_LINES"
                :key="`branch-${i}`"
                class="pipeline-branch"
                :x1="branch.x1" :y1="branch.y1" :x2="branch.x2" :y2="branch.y2"
                :style="{ stroke: branch.color }"
              />

              <!-- S-curve glow and line -->
              <path class="pipeline-glow" :d="PIPELINE_PATH" />
              <path id="home-pipeline-path" class="pipeline-line" :d="PIPELINE_PATH" />

              <!-- Jeffrey hub at the inflection point -->
              <circle class="pipeline-hub-bg" cx="500" cy="170" r="22" />
              <rect x="490" y="159" width="20" height="6" rx="1.5" class="pipeline-hub-bar pipeline-hub-bar--amber" />
              <rect x="492" y="168" width="16" height="6" rx="1.5" class="pipeline-hub-bar pipeline-hub-bar--ember" />
              <rect x="494" y="177" width="12" height="6" rx="1.5" class="pipeline-hub-bar pipeline-hub-bar--ms" />

              <!-- Animated pulses -->
              <circle v-for="begin in PIPELINE_PULSE_BEGINS" :key="begin" class="pipeline-pulse" r="4">
                <animateMotion dur="7s" repeatCount="indefinite" :begin="begin">
                  <mpath href="#home-pipeline-path" />
                </animateMotion>
              </circle>
            </svg>

            <!-- Stations and satellite products, interleaved for the narrow-screen vertical flow -->
            <template v-for="step in pipelineSteps" :key="step.station.name">
              <div class="station" :style="positionStyle(step.station)">
                <span class="station-ic"><i class="bi" :class="step.station.icon"></i></span>
                <span><b>{{ step.station.name }}</b><small>{{ step.station.sub }}</small></span>
              </div>
              <router-link
                v-for="sat in step.satellites"
                :key="sat.id"
                :to="sat.to"
                class="satellite"
                :style="satelliteStyle(sat)"
              >
                <span class="satellite-ic"><i class="bi" :class="sat.icon"></i></span>
                <span><b>{{ sat.name }}</b><small>{{ sat.sub }}</small></span>
              </router-link>
              <span v-if="step.hop" class="hop" :style="positionStyle(step.hop)">{{ step.hop.label }}</span>
            </template>
          </div>
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
            <div class="features-col">
              <div v-if="versionFor(active.id)" class="version-banner">
                <span class="version-icon"><i class="bi bi-bookmark-star-fill"></i></span>
                <div class="version-info">
                  <span class="version-label">Latest release</span>
                  <code class="version-number">{{ versionFor(active.id)!.version }}</code>
                </div>
                <template v-if="active.id === 'jib' && versionFor('jib')">
                  <div class="version-links">
                    <a :href="jibVersionLinks(versionFor('jib')!.version).maven" target="_blank" rel="noopener noreferrer" class="version-link">
                      Maven <i class="bi bi-box-arrow-up-right"></i>
                    </a>
                    <a :href="jibVersionLinks(versionFor('jib')!.version).gradle" target="_blank" rel="noopener noreferrer" class="version-link">
                      Gradle <i class="bi bi-box-arrow-up-right"></i>
                    </a>
                  </div>
                </template>
                <template v-else-if="active.id === 'volume-scheduler' && versionFor('volume-scheduler')">
                  <div class="version-links">
                    <a :href="versionFor('volume-scheduler')!.url" target="_blank" rel="noopener noreferrer" class="version-link">
                      GitHub Releases <i class="bi bi-box-arrow-up-right"></i>
                    </a>
                    <a :href="volumeSchedulerDockerHubUrl" target="_blank" rel="noopener noreferrer" class="version-link">
                      Docker Hub <i class="bi bi-box-arrow-up-right"></i>
                    </a>
                  </div>
                </template>
                <a v-else :href="versionFor(active.id)!.url" target="_blank" rel="noopener noreferrer" class="version-link">
                  {{ versionFor(active.id)!.source }} <i class="bi bi-box-arrow-up-right"></i>
                </a>
              </div>
              <div class="features">
                <article v-for="f in active.features" :key="f.title" class="feature">
                <span class="feature-ic"><i class="bi" :class="f.icon"></i></span>
                <h4>{{ f.title }}</h4>
                <p>{{ f.desc }}</p>
              </article>
              </div>
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
/* Elevated pipeline homepage: warm-paper sky, the centred hero copy over a full-width S-curve
   pipeline with satellite product cards, then a tabbed product deep-dive. */
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
  --ms: #0284c7;
  --ms-deep: #075985;
  --mcp: #059669;
  --mcp-deep: #065f46;
  --provisioner: #e11d48;
  --jib: #4f46e5;
  --ide: #ea580c;
  --volume-scheduler: #7c3aed;
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
  background: radial-gradient(50% 65% at 50% 60%, rgba(255, 109, 0, 0.11), transparent 70%);
  pointer-events: none;
}

.sky {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.hero-stack {
  position: relative;
  z-index: 1;
  padding-top: 60px;
  padding-bottom: 48px;
}

.hero-copy-center {
  display: grid;
  gap: 24px;
  justify-items: center;
  text-align: center;
  margin-bottom: 44px;
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
  max-width: 60ch;
}

.ctas {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: center;
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
  justify-content: center;
}

.formats a {
  color: var(--muted);
  border-bottom: 1px dashed var(--line-2);
}

.formats a:hover {
  color: var(--text);
}

/* ============ Pipeline ============ */
.pipeline {
  position: relative;
  max-width: 1180px;
  margin: 0 auto;
  aspect-ratio: 1000 / 420;
}

.pipeline-svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: visible;
}

.pipeline-glow {
  fill: none;
  stroke: url(#home-pipeline-gradient);
  stroke-width: 14;
  stroke-linecap: round;
  opacity: 0.14;
}

.pipeline-line {
  fill: none;
  stroke: url(#home-pipeline-gradient);
  stroke-width: 2.2;
  stroke-linecap: round;
}

.pipeline-branch {
  stroke-width: 1.3;
  stroke-dasharray: 4 4;
  opacity: 0.3;
}

.pipeline-hub-bg {
  fill: var(--night);
  stroke: var(--line-2);
  stroke-width: 1;
}

.pipeline-hub-bar--amber {
  fill: var(--amber);
  opacity: 0.75;
}

.pipeline-hub-bar--ember {
  fill: var(--ember);
  opacity: 0.65;
}

.pipeline-hub-bar--ms {
  fill: var(--ms);
  opacity: 0.55;
}

.pipeline-pulse {
  fill: var(--amber);
  opacity: 0.9;
}

.station,
.satellite,
.hop {
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

/* Satellite product cards: smaller, with float animation and product-coloured icon. */
.satellite {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px 8px 8px;
  border-radius: var(--r-md);
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid var(--line-2);
  box-shadow: 0 0 32px -12px color-mix(in srgb, var(--c) 35%, transparent), 0 8px 22px -16px rgba(48, 32, 12, 0.28);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  animation: sat-float 7s ease-in-out infinite;
  animation-delay: var(--delay, 0s);
  transition: border-color 0.2s, background 0.2s;
}

.satellite:hover,
.satellite:focus-visible {
  border-color: var(--c);
  background: var(--surface);
}

.satellite:focus-visible {
  outline: none;
}

.satellite-ic {
  flex: none;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  font-size: 14px;
  color: var(--on-accent);
  background: var(--c);
}

.satellite b {
  display: block;
  font-size: 13.5px;
  font-weight: 500;
}

.satellite small {
  display: block;
  font-size: 11px;
  color: var(--faint);
}

@keyframes sat-float {
  0%, 100% {
    transform: translate(-50%, -50%) translateY(0);
  }
  50% {
    transform: translate(-50%, -50%) translateY(-6px);
  }
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

.showcase[data-active='provisioner'] {
  --tab-accent: var(--provisioner);
}

.showcase[data-active='jib'] {
  --tab-accent: var(--jib);
}

.showcase[data-active='plugin'] {
  --tab-accent: var(--ide);
}

.showcase[data-active='volume-scheduler'] {
  --tab-accent: var(--volume-scheduler);
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

.features-col {
  min-width: 0;
}

.features {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}

.version-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  border-radius: var(--r-md);
  background: color-mix(in srgb, var(--tab-accent) 10%, var(--surface));
  border: 1px solid color-mix(in srgb, var(--tab-accent) 30%, transparent);
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.version-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  font-size: 15px;
  color: var(--on-accent);
  background: var(--tab-accent);
  flex-shrink: 0;
}

.version-info {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
  flex-wrap: wrap;
}

.version-label {
  font-size: 13.5px;
  font-weight: 600;
}

.version-number {
  font: 600 12.5px/1 var(--font-mono);
  padding: 5px 10px;
  border-radius: 6px;
  background: color-mix(in srgb, var(--tab-accent) 18%, transparent);
  color: var(--tab-accent);
}

.version-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: var(--tab-accent);
  flex-shrink: 0;
  transition: opacity 0.15s;
}

.version-link:hover {
  opacity: 0.75;
}

.version-links {
  margin-left: auto;
  display: flex;
  gap: 14px;
  flex-shrink: 0;
  flex-wrap: wrap;
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
  .pipeline {
    aspect-ratio: auto;
    width: fit-content;
    max-width: 100%;
    margin: 0 auto;
    padding-left: 30px;
    display: grid;
    gap: 10px;
    justify-items: start;
  }

  .pipeline::before {
    content: '';
    position: absolute;
    left: 10px;
    top: 24px;
    bottom: 24px;
    width: 2px;
    border-radius: 2px;
    background: linear-gradient(var(--ember), var(--amber), var(--ms), var(--mcp));
  }

  .pipeline-svg {
    display: none;
  }

  .station,
  .satellite,
  .hop {
    position: relative;
    left: auto;
    top: auto;
    transform: none;
  }

  .station {
    max-width: 100%;
    white-space: normal;
  }

  .satellite {
    margin-left: 16px;
    white-space: normal;
    animation: none;
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

  .hero-stack {
    padding-top: 40px;
    padding-bottom: 32px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .satellite,
  .panel {
    animation: none;
  }

  .pipeline-pulse {
    display: none;
  }
}
</style>
