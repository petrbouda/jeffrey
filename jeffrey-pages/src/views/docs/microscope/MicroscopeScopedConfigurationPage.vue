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

const mergeOrder = `built-in defaults
  < image base config          (baked in by jeffrey-jib)
  < GLOBAL                     ── published by the Hub onto the shared volume
  < WORKSPACE                  ──
  < PROJECT                    ──
  < container override config  (a mounted file)
  < JEFFREY_* environment variables`;

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'scopes', text: 'Three Scopes', level: 2 },
  { id: 'hierarchy', text: 'Merge Order', level: 2 },
  { id: 'types', text: 'What Can Be Configured', level: 2 },
  { id: 'where-to-edit', text: 'Where to Edit', level: 2 },
  { id: 'settings-builder', text: 'Settings Builder', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Configuration"
      icon="bi bi-sliders"
    />

    <div class="docs-content">
      <h2 id="overview">Overview</h2>
      <p>
        Jeffrey Hub holds configuration for the JVMs it serves and publishes it onto the shared
        volume, where the <router-link to="/docs/provisioner">Provisioner</router-link> reads it as
        it starts a JVM. Today that means one setting: the
        <a href="https://github.com/async-profiler/async-profiler" target="_blank" rel="noopener">Async-Profiler</a>
        command a session records with. You edit it in Microscope, and it reaches an application
        without anyone touching its JVM arguments.
      </p>

      <p>
        The Hub stores typed values, not files. It renders them into one
        <code>.config/jeffrey.conf</code> per scope, and the Provisioner merges whichever of those
        files exist as ordinary configuration layers. Nothing in the UI writes HOCON, and nothing
        the Hub publishes can set a key outside the list below.
      </p>

      <h2 id="scopes">Three Scopes</h2>
      <p>
        A scope is a folder on the shared volume, which is also what gives it its rank: a file in a
        project's folder overrides its workspace's, which overrides the one in the workspaces root.
        There is no resolver anywhere; the directory tree <em>is</em> the precedence.
      </p>

      <table>
        <thead>
          <tr>
            <th>Scope</th>
            <th>Published to</th>
            <th>Affects</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><strong>Global</strong></td>
            <td><code>&lt;workspaces&gt;/.config/jeffrey.conf</code></td>
            <td>Every JVM under the Hub's workspaces root.</td>
          </tr>
          <tr>
            <td><strong>Workspace</strong></td>
            <td><code>&lt;workspace&gt;/.config/jeffrey.conf</code></td>
            <td>Every project of one workspace; overrides Global.</td>
          </tr>
          <tr>
            <td><strong>Project</strong></td>
            <td><code>&lt;workspace&gt;/&lt;project&gt;/.config/jeffrey.conf</code></td>
            <td>One project; overrides Workspace and Global.</td>
          </tr>
        </tbody>
      </table>

      <h2 id="hierarchy">Merge Order</h2>
      <p>
        The published scopes are three layers among several. The container's own configuration sits
        above them, so whoever deploys an application always has the last word and a mistake in the
        Hub can never take over a pod that set the value itself:
      </p>

      <DocsCodeBlock :code="mergeOrder" language="text" />

      <DocsCallout type="tip">
        <strong>Applies on the next JVM start.</strong> A change publishes immediately, so the file
        on the volume is current within moments of saving. It reaches an application when that
        application next starts; nothing reaches into a running process. The instance timeline shows
        which configuration each session actually merged.
      </DocsCallout>

      <h2 id="types">What Can Be Configured</h2>
      <p>
        One setting, deliberately. A published value becomes a JVM option inside an application's
        own argument file, so the list of things that can be said is a security boundary rather than
        a convenience, and it grows one reviewed entry at a time.
      </p>

      <table>
        <thead>
          <tr>
            <th>Type</th>
            <th>Key in the published file</th>
            <th>What it sets</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>ASPROF_SETTINGS</code></td>
            <td><code>asprof-settings</code></td>
            <td>The Async-Profiler command a session starts with.</td>
          </tr>
        </tbody>
      </table>

      <p>
        Anything else in a published file is dropped by the Provisioner with a warning, including a
        file edited by hand on the volume. That matters because keys such as
        <code>additional-jvm-options</code> would otherwise let whoever reaches the Hub run
        arbitrary flags, and HOCON <code>include</code> directives would let it read files off the
        application's own filesystem. Neither is accepted.
      </p>

      <DocsCallout type="warning">
        <strong>The network is the trust boundary.</strong> The Hub's gRPC port has no
        authentication, so anyone who can reach it configures every JVM it serves. Keep it on a
        trusted network. The closed type list bounds what that access can do; it is not a substitute
        for keeping the port private.
      </DocsCallout>

      <h2 id="where-to-edit">Where to Edit</h2>
      <ul>
        <li>
          <strong>Global</strong> and <strong>Workspace</strong> — open the workspace and switch to
          its <em>Configuration</em> tab.
        </li>
        <li>
          <strong>Project</strong> — open the project and switch to its <em>Configuration</em> tab,
          which also shows what the project inherits.
        </li>
        <li>
          <strong>In the container instead</strong> — set <code>asprof-settings</code> in the
          Provisioner's own configuration file, or as <code>JEFFREY_ASPROF_SETTINGS</code>. That
          overrides every published scope.
        </li>
      </ul>

      <p>
        Nothing is published until you set it. With no configuration at any scope, a JVM starts on
        the Provisioner's built-in command.
      </p>

      <h2 id="settings-builder">Settings Builder</h2>
      <p>
        Each scope's editor has two tabs. <strong>Manual</strong> takes the command as text, and
        <strong>Visual Builder</strong> composes one from the fields below and hands it to the
        manual tab so you can read it before saving. Above them, the current value is shown with
        when it was last saved and a short digest of the file it renders to.
      </p>

      <p>
        Jeffrey uses <a href="https://github.com/async-profiler/async-profiler" target="_blank" rel="noopener">Async-Profiler</a>
        as its profiling agent and includes a visual <strong>Profiler Settings Builder</strong> to configure it without
        memorizing command-line arguments. The builder generates the correct profiler parameters based on your selections.
      </p>

      <DocsCallout type="info">
        <strong>Only Supported Profiler:</strong> Currently, Async-Profiler is the only supported profiling agent in
        Jeffrey. It provides excellent integration with JFR format and low overhead profiling capabilities.
      </DocsCallout>

      <div class="screenshot-container">
        <img src="/images/docs/profiler-settings-builder.png" alt="Profiler Settings Builder overview" class="doc-screenshot" />
        <p class="screenshot-caption">Profiler Settings Builder with mandatory options, event toggles, and generated parameters</p>
      </div>

      <h3>Mandatory Options</h3>
      <p>Every profiler configuration requires these essential settings:</p>

      <div class="options-list">
        <div class="option-item">
          <div class="option-header">
            <i class="bi bi-folder2-open"></i>
            <strong>Agent Path</strong>
          </div>
          <p>Path to the Async-Profiler native library (libasyncProfiler.so)</p>
        </div>
        <div class="option-item">
          <div class="option-header">
            <i class="bi bi-file-earmark-code"></i>
            <strong>Output File Pattern</strong>
          </div>
          <p>Template for output file names, supporting placeholders for timestamps and process IDs</p>
        </div>
        <div class="option-item">
          <div class="option-header">
            <i class="bi bi-arrow-repeat"></i>
            <strong>Loop Duration</strong>
          </div>
          <p>Duration of each recording cycle before starting a new output file</p>
        </div>
      </div>

      <h3>Event Options</h3>
      <p>
        Select which profiling events to capture. Each event type can be expanded to configure additional options
        like sampling intervals, thresholds, and profiling modes.
      </p>

      <div class="profiler-features">
        <div class="feature-card">
          <div class="feature-icon"><i class="bi bi-cpu"></i></div>
          <div class="feature-content">
            <h4>CPU Profiling</h4>
            <p>Sample thread stack traces at regular intervals to find CPU-intensive methods and performance bottlenecks. <code>ctimer</code> and <code>itimer</code> attribute every sample to the carrier thread, so spans that ran on a virtual thread get no flamegraph — drop <code>event=</code> to let the JVM's own sampler attribute to virtual threads instead</p>
          </div>
        </div>
        <div class="feature-card">
          <div class="feature-icon"><i class="bi bi-memory"></i></div>
          <div class="feature-content">
            <h4>Allocation Profiling</h4>
            <p>Track object allocations above threshold to identify memory pressure sources and allocation hotspots</p>
          </div>
        </div>
        <div class="feature-card">
          <div class="feature-icon"><i class="bi bi-lock"></i></div>
          <div class="feature-content">
            <h4>Lock Profiling</h4>
            <p>Monitor contended locks and synchronization events to debug threading and concurrency issues</p>
          </div>
        </div>
        <div class="feature-card">
          <div class="feature-icon"><i class="bi bi-clock-history"></i></div>
          <div class="feature-content">
            <h4>Wall-Clock Profiling</h4>
            <p>Sample all threads regardless of state to find I/O bottlenecks and blocking operations</p>
          </div>
        </div>
        <div class="feature-card">
          <div class="feature-icon"><i class="bi bi-signpost-split"></i></div>
          <div class="feature-content">
            <h4>Method Tracing</h4>
            <p>Trace specific method invocations for detailed timing analysis of critical code paths</p>
          </div>
        </div>
        <div class="feature-card">
          <div class="feature-icon"><i class="bi bi-hdd-stack"></i></div>
          <div class="feature-content">
            <h4>Native Memory Profiling</h4>
            <p>Track native (off-heap) memory allocations to debug memory usage outside the Java heap</p>
          </div>
        </div>
      </div>

      <div class="screenshot-container">
        <img src="/images/docs/profiler-settings-builder-2.png" alt="Profiler Settings Builder with expanded options" class="doc-screenshot" />
        <p class="screenshot-caption">Expanded CPU and Allocation profiling options with sampling interval and threshold settings</p>
      </div>

      <h3>Advanced Options</h3>
      <p>Fine-tune JFR output format and enable additional features:</p>

      <div class="profiler-features">
        <div class="feature-card">
          <div class="feature-icon"><i class="bi bi-arrow-down-up"></i></div>
          <div class="feature-content">
            <h4>JFR Synchronization</h4>
            <p>Merge Async-Profiler events with JDK's JFR recording for richer profiling data. Choose from predefined JFC modes (default, profile) or use custom configuration.</p>
          </div>
        </div>
        <div class="feature-card">
          <div class="feature-icon"><i class="bi bi-file-binary"></i></div>
          <div class="feature-content">
            <h4>Chunk Size</h4>
            <p>Maximum size of each JFR chunk file. Smaller chunks enable parallel processing of JFR data. Default is 100 MB.</p>
          </div>
        </div>
        <div class="feature-card">
          <div class="feature-icon"><i class="bi bi-hourglass-split"></i></div>
          <div class="feature-content">
            <h4>Chunk Time</h4>
            <p>Maximum duration of each JFR chunk. A new chunk starts after the specified time. Default is 1 hour.</p>
          </div>
        </div>
      </div>

      <h3>Active Parameters</h3>
      <p>
        The Settings Builder displays the generated Async-Profiler command arguments in real-time. This helps you
        understand exactly what configuration will be applied and allows you to copy or modify the parameters if needed.
      </p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

.hierarchy-diagram {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  margin: 1.5rem 0;
  padding: 1.5rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.hierarchy-level {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0.75rem 2rem;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #e2e8f0;
  min-width: 280px;
}

.hierarchy-level .level-label {
  font-weight: 600;
  font-size: 0.9rem;
  color: #343a40;
}

.hierarchy-level .level-desc {
  font-size: 0.75rem;
  color: #6c757d;
  margin-top: 0.25rem;
  text-align: center;
}

.hierarchy-level.global {
  border-color: rgba(107, 114, 128, 0.3);
}

.hierarchy-level.global .level-label {
  color: #6b7280;
}

.hierarchy-level.workspace {
  border-color: rgba(94, 100, 255, 0.3);
}

.hierarchy-level.workspace .level-label {
  color: #5e64ff;
}

.hierarchy-level.project {
  border-color: rgba(16, 185, 129, 0.3);
}

.hierarchy-level.project .level-label {
  color: #10b981;
}

.hierarchy-arrow {
  color: #94a3b8;
  font-size: 1.25rem;
}

/* Profiler Features Grid */
.profiler-features {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
  margin: 1.5rem 0;
}

.feature-card {
  display: flex;
  gap: 0.75rem;
  padding: 1rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.feature-icon {
  width: 40px;
  height: 40px;
  min-width: 40px;
  border-radius: 8px;
  background: #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.feature-icon i {
  font-size: 1.1rem;
  color: #5e64ff;
}

.feature-content {
  flex: 1;
}

.feature-content h4 {
  margin: 0 0 0.25rem 0;
  font-size: 0.9rem;
  font-weight: 600;
  color: #343a40;
}

.feature-content p {
  margin: 0;
  font-size: 0.8rem;
  color: #5e6e82;
}

/* Options List */
.options-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin: 1.5rem 0;
}

.option-item {
  padding: 1rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.option-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.option-header i {
  font-size: 1rem;
  color: #5e64ff;
}

.option-header strong {
  font-size: 0.9rem;
  color: #343a40;
}

.option-item p {
  margin: 0;
  font-size: 0.85rem;
  color: #5e6e82;
  padding-left: 1.5rem;
}

/* Screenshot Styling */
.screenshot-container {
  margin: 1.5rem 0;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}

.doc-screenshot {
  width: 100%;
  display: block;
}

.screenshot-caption {
  margin: 0;
  padding: 0.75rem 1rem;
  font-size: 0.8rem;
  color: #5e6e82;
  background: #f8fafc;
  border-top: 1px solid #e2e8f0;
  text-align: center;
}

@media (max-width: 992px) {
  .profiler-features {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 576px) {
  .profiler-features {
    grid-template-columns: 1fr;
  }
}
</style>
