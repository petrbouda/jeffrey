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
import { onMounted } from 'vue';
import DocsCallout from '@/components/docs/DocsCallout.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

type FeatureGroup = 'editor' | 'recordings' | 'agents';

interface Feature {
  group: FeatureGroup;
  icon: string;
  title: string;
  description: string;
}

const FEATURE_GROUPS: { id: FeatureGroup; label: string; icon: string }[] = [
  { id: 'editor', label: 'In the editor', icon: 'bi bi-code-slash' },
  { id: 'recordings', label: 'Recordings', icon: 'bi bi-fire' },
  { id: 'agents', label: 'AI agents', icon: 'bi bi-robot' }
];

const FEATURES: Feature[] = [
  { group: 'editor', icon: 'bi bi-box-arrow-up-right', title: 'Open in IDE', description: 'Jump from any flame-graph frame straight to its source line.' },
  { group: 'editor', icon: 'bi bi-file-earmark-code', title: 'View Source', description: 'Read the class from the IDE inline, with the frame line highlighted.' },
  { group: 'editor', icon: 'bi bi-translate', title: 'Java & Kotlin', description: 'Both languages resolve to the right file and line.' },
  { group: 'editor', icon: 'bi bi-git', title: 'Multi-Window Aware', description: 'Targets the right open window; each reports its branch and HEAD.' },
  { group: 'recordings', icon: 'bi bi-send', title: 'Send to Microscope', description: 'Right-click a JFR, heap dump or pprof/OTLP file to analyse it.' },
  { group: 'recordings', icon: 'bi bi-fire', title: 'Marked Recordings', description: 'Profile files carry the Jeffrey flame icon in the project view.' },
  { group: 'recordings', icon: 'bi bi-layout-text-window', title: 'Recording Panel', description: 'Status, key figures, findings and view tiles right in the editor tab.' },
  { group: 'recordings', icon: 'bi bi-arrow-left-right', title: 'Compare Recordings', description: 'Before vs. after, with a verdict on whether the pair is comparable.' },
  { group: 'agents', icon: 'bi bi-terminal', title: 'Hand It to an Agent', description: 'Launch Claude Code, Codex or Gemini CLI wired to Microscope\'s MCP.' },
  { group: 'agents', icon: 'bi bi-crosshair', title: 'Frame Lookup', description: 'ide_ MCP tools ground an agent\'s findings in real files and lines.' }
];

function featuresOf(group: FeatureGroup): Feature[] {
  return FEATURES.filter(feature => feature.group === group);
}

const headings = [
  { id: 'what-it-does', text: 'What It Does', level: 2 },
  { id: 'how-pairing-works', text: 'How Pairing Works', level: 2 },
  { id: 'endpoints', text: 'What the Plugin Serves', level: 2 },
  { id: 'features', text: 'Features in Microscope', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="IntelliJ Plugin"
      icon="bi bi-window-stack"
    />

    <div class="docs-content">

      <section class="hero">
        <p class="hero-eyebrow"><i class="bi bi-link-45deg"></i> IDE Bridge</p>
        <h2 class="hero-title">From a flame-graph frame to the source line in one click.</h2>
        <p class="hero-lede">
          The <strong>Jeffrey IntelliJ plugin</strong> is a small companion to Microscope.
          It exposes a tiny HTTP API over IntelliJ's built-in server so Microscope can jump
          from a JFR flame-graph frame straight to the right method in the right open IDE
          window — and fetch source text to show inline.
        </p>
      </section>

      <p>
        Ready to wire it up? See <router-link to="/docs/intellij-plugin/setup">Setup</router-link>
        to install the plugin and <router-link to="/docs/intellij-plugin/configuration">Configuration</router-link>
        for the port range and trusted-project rules.
      </p>

      <h2 id="what-it-does">What It Does</h2>
      <p>
        Once installed and running, the plugin lets Microscope ask the IDE four things:
        &ldquo;which classes do you know about?&rdquo;, &ldquo;<em>where</em> is this method,
        without touching my editor?&rdquo;, &ldquo;open <em>this</em> method in the editor&rdquo;,
        and &ldquo;send me the source of <em>this</em> class.&rdquo; The second is what an AI agent
        uses when it needs a file and a line for a finding but has no business moving your cursor.
      </p>
      <p>
        It also exposes an action that sends a recording or heap dump in your project straight to
        Microscope, without going through the Recordings upload flow.
      </p>

      <h2 id="how-pairing-works">How Pairing Works</h2>
      <p>
        There is no port to configure, no token to share, and no file written to disk. The plugin
        registers an HTTP handler on IntelliJ's built-in server, and Microscope's
        <code>JeffreyPluginBridge</code> discovers running IDEs by scanning localhost over a small
        port range and calling <code>/api/jeffrey/instance</code> on each. A response carries the
        IDE's instance id, version, and the list of open (trusted) projects — enough for Microscope
        to render the per-profile target picker.
      </p>

      <div class="arch-flow">
        <div class="flow-node"><i class="bi bi-window-stack"></i><span>IntelliJ window<br><small>handler on built-in server</small></span></div>
        <div class="flow-arrow"><i class="bi bi-arrow-right"></i></div>
        <div class="flow-node"><i class="bi bi-search"></i><span>Port scan<br><small>localhost 63342&ndash;63362</small></span></div>
        <div class="flow-arrow"><i class="bi bi-arrow-right"></i></div>
        <div class="flow-node"><i class="bi bi-pc-display"></i><span>Microscope<br><small>calls /api/jeffrey/instance</small></span></div>
      </div>

      <p>
        Scanning is lazy: once a window is linked to a profile, the chosen port is cached and
        reused for every jump. Only when that port stops responding (IDE restart, port reassigned)
        does Microscope re-scan to find the same instance again — so you don't pay scan cost on
        every profile view.
      </p>

      <p>
        The link itself outlives both processes. Microscope stores which project, in which IDE, at
        which path — not the port or the process id, which describe one run and are wrong by the time
        they are read back. So restarting IntelliJ, or Microscope, or both, leaves the profile linked;
        the first jump afterwards re-scans once to find the window again and carries on. Disconnecting
        a window is remembered too: a link you removed does not return at the next start.
      </p>

      <DocsCallout type="info">
        <strong>Custom port range &amp; trusted projects.</strong> The default scan range is
        63342&ndash;63362 (IntelliJ's built-in server defaults), and only trusted projects are
        exposed. Both are covered in
        <router-link to="/docs/intellij-plugin/configuration">Configuration</router-link>.
      </DocsCallout>

      <h2 id="endpoints">What the Plugin Serves</h2>
      <p>
        Six endpoints on IntelliJ&rsquo;s built-in server, under <code>/api/jeffrey/</code>. You will
        not call them by hand &mdash; Microscope does &mdash; but they are what the integration is,
        and <code>ping</code> is the quickest way to tell whether the plugin is alive:
      </p>
      <table>
        <thead>
          <tr>
            <th>Endpoint</th>
            <th>Answers</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>GET ping</code></td>
            <td>Liveness, and the protocol version this plugin speaks</td>
          </tr>
          <tr>
            <td><code>GET instance</code></td>
            <td>This IDE, and every open trusted project &mdash; name, path, focus, branch and HEAD commit</td>
          </tr>
          <tr>
            <td><code>POST navigate</code></td>
            <td>Resolve a frame, open the file, bring the window to the front</td>
          </tr>
          <tr>
            <td><code>POST resolve</code></td>
            <td>The same answer with <strong>no editor movement</strong> &mdash; protocol 2 and up</td>
          </tr>
          <tr>
            <td><code>GET has</code></td>
            <td>Whether a project contains a class, or a class and method</td>
          </tr>
          <tr>
            <td><code>GET source</code></td>
            <td>A class&rsquo;s source text, attached sources preferred over decompiled</td>
          </tr>
        </tbody>
      </table>

      <p>
        <code>resolve</code> exists separately from <code>navigate</code> because they are separate
        acts, and only one is safe to repeat while somebody is working. It is what the
        <router-link to="/docs/microscope-mcp/tools#ide"><code>ide_</code> MCP tools</router-link>
        call, so an agent can check a hundred frames without your editor jumping. A plugin older than
        protocol 2 does not serve it, and Microscope says so rather than reporting the IDE as offline.
      </p>

      <p>
        Traffic goes the other way too, and over exactly one endpoint: the recording tab asks
        Microscope <code>GET /api/internal/recordings/by-path</code> what it holds for the file you
        opened, and gets back the whole panel &mdash; whether the file has been analysed, and if so
        the four figures and the auto-analysis findings. Matching is by <strong>file name plus byte
        size</strong>, because importing copies the file and records no origin path; the lookup never
        imports anything as a side effect. One call rather than four means a figure on that tab can
        only be wrong in one place.
      </p>

      <h2 id="features">Features in Microscope</h2>
      <div class="feature-groups">
        <section
          v-for="group in FEATURE_GROUPS"
          :key="group.id"
          class="feature-group"
          :class="`feature-group--${group.id}`"
        >
          <h3 class="feature-group-label"><i :class="group.icon"></i>{{ group.label }}</h3>
          <div class="feature-tiles">
            <div v-for="feature in featuresOf(group.id)" :key="feature.title" class="feature-tile">
              <span class="feature-icon"><i :class="feature.icon"></i></span>
              <div>
                <div class="feature-title">{{ feature.title }}</div>
                <p class="feature-desc">{{ feature.description }}</p>
              </div>
            </div>
          </div>
        </section>
      </div>

      <DocsCallout type="info" title="The analysis stays in Microscope">
        The plugin does not draw flame graphs, dashboards or charts inside the IDE, and it will not
        grow a tool window that does. Everything that shows you a profile is a link into Microscope,
        opened in a browser. One renderer, in one place, so what the IDE points at and what you read
        cannot drift apart &mdash; and the plugin stays a settings panel and a pair of menu items.
      </DocsCallout>

      <p>
        Prefer to point Microscope at a different IDE plugin instead? See
        <router-link to="/docs/intellij-plugin/jfr-profiler">Java JFR Profiler Plugin</router-link>.
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

/* ============ ARCH FLOW STRIP ============ */
.arch-flow {
  display: flex;
  flex-wrap: wrap;
  align-items: stretch;
  gap: 0.5rem;
  margin: 1rem 0 1.75rem;
  padding: 1rem;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}

.flow-node {
  flex: 1 1 160px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  padding: 0.75rem 0.5rem;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  text-align: center;
  font-size: 0.78rem;
  font-weight: 500;
  color: #334155;
}

.flow-node i {
  font-size: 1.25rem;
  color: #4338ca;
}

.flow-node small {
  display: block;
  font-size: 0.7rem;
  font-weight: 400;
  color: #64748b;
  margin-top: 0.15rem;
}

.flow-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  font-size: 1.1rem;
}

@media (max-width: 768px) {
  .hero { padding: 1.6rem 1.2rem; }
  .hero-title { font-size: 1.5rem; }
  .hero-lede { font-size: 0.95rem; }
  .arch-flow { flex-direction: column; }
  .flow-arrow { transform: rotate(90deg); }
}

/* ============ FEATURE TILES ============ */
.feature-groups {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  margin: 1rem 0 1.75rem;
}

.feature-group {
  --accent: #5e64ff;
  --accent-soft: #eef0ff;
  --accent-border: #c7cbff;
}

.feature-group--recordings {
  --accent: #ea580c;
  --accent-soft: #fff4ec;
  --accent-border: #fed7aa;
}

.feature-group--agents {
  --accent: #059669;
  --accent-soft: #ecfdf5;
  --accent-border: #a7f3d0;
}

.docs-content .feature-group-label {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  margin: 0 0 0.7rem;
  padding: 0.3rem 0.75rem;
  border: 0;
  font-size: 0.72rem;
  line-height: 1.2;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: 999px;
}

.feature-tiles {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.75rem;
}

.feature-tile {
  display: flex;
  align-items: flex-start;
  gap: 0.8rem;
  padding: 0.85rem 1rem;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-top: 3px solid var(--accent);
  border-radius: 10px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.feature-tile:hover {
  border-color: var(--accent-border);
  border-top-color: var(--accent);
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.07);
}

.feature-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 2.25rem;
  height: 2.25rem;
  font-size: 1.05rem;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: 9px;
}

.feature-title {
  font-weight: 600;
  font-size: 0.95rem;
  color: #0f172a;
  margin-bottom: 0.2rem;
}

.docs-content .feature-desc {
  margin: 0;
  font-size: 0.85rem;
  line-height: 1.45;
  color: #64748b;
}

@media (max-width: 768px) {
  .feature-tiles { grid-template-columns: 1fr; }
}
</style>
