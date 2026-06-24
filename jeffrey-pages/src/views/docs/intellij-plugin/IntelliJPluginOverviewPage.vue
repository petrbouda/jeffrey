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
  { id: 'what-it-does', text: 'What It Does', level: 2 },
  { id: 'how-pairing-works', text: 'How Pairing Works', level: 2 },
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
        Once installed and running, the plugin lets Microscope ask the IDE three things:
        &ldquo;which classes do you know about?&rdquo;, &ldquo;open <em>this</em> method
        in the editor&rdquo;, and &ldquo;send me the source of <em>this</em> class.&rdquo;
        It also exposes an action that opens any <code>.jfr</code> file in your project directly
        in Microscope, without going through the Recordings upload flow.
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

      <DocsCallout type="info">
        <strong>Custom port range &amp; trusted projects.</strong> The default scan range is
        63342&ndash;63362 (IntelliJ's built-in server defaults), and only trusted projects are
        exposed. Both are covered in
        <router-link to="/docs/intellij-plugin/configuration">Configuration</router-link>.
      </DocsCallout>

      <h2 id="features">Features in Microscope</h2>
      <ul class="usecase-list">
        <li><i class="bi bi-check2-circle"></i> <strong>Open in IDE</strong> from any flame-graph frame — navigates to the method's source line and focuses the IDE window.</li>
        <li><i class="bi bi-check2-circle"></i> <strong>Inline source preview</strong> in tooltips and detail panels — fetches the class body straight from the IDE so it always matches your local checkout.</li>
        <li><i class="bi bi-check2-circle"></i> <strong>Per-profile window selection</strong> — when multiple IntelliJ windows are open, choose which one to target for a given profile; the choice is cached.</li>
        <li><i class="bi bi-check2-circle"></i> <strong>Java &amp; Kotlin resolution</strong> — both <code>JavaResolver</code> and <code>KotlinResolver</code> ship with the plugin, so JVM languages resolve symbol locations identically.</li>
      </ul>

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
</style>
