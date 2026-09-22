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
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'builder', text: 'Building a Command', level: 2 },
  { id: 'command', text: 'The Generated Command', level: 3 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Profiler Builder"
      icon="bi bi-cpu"
    />

    <div class="docs-content">
      <h2 id="overview">Overview</h2>
      <p>
        <strong>Profiler Builder</strong> assembles an
        <a href="https://github.com/async-profiler/async-profiler" target="_blank" rel="noopener">Async-Profiler</a>
        agent command: which events to capture, sampling rates, output layout and the JFR-specific options. It is a
        top-level page in Microscope, next to Recordings and Hubs, and it does one thing — you build a command and
        copy it. Nothing is saved.
      </p>
      <p>
        Paste the result into the JVM you want to profile, then bring the recording it produces back into Microscope
        through <router-link to="/docs/microscope/recordings">Recordings</router-link>. In a deployment provisioned by
        <router-link to="/docs/provisioner/configuration">Jeffrey Provisioner</router-link>, give it to the provisioner
        as <code>profiler-command</code> in its configuration file, or as the
        <code>JEFFREY_PROFILER_COMMAND</code> environment variable, which wins over the file.
      </p>

      <h2 id="builder">Building a Command</h2>
      <p>
        The builder is a form over
        <a href="https://github.com/async-profiler/async-profiler" target="_blank" rel="noopener">Async-Profiler</a>'s
        agent options, so a recording can be configured without memorizing command-line arguments. Toggle what you
        want to record on the left; the assembled command appears in the panel on the right and updates as you go.
      </p>

      <DocsCallout type="info">
        <strong>The page stores nothing.</strong> Profiler Builder only assembles a command for you to copy — it does
        not apply it to anything, and needs no hub, workspace or project. The agent path and output file are literal
        values that go straight into the JVM you are about to profile.
      </DocsCallout>

      

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
            <p>Monitor contended locks and synchronization events to debug threading and concurrency issues. The builder starts at <code>lock=10us</code>, async-profiler's own default, so only contentions that waited at least 10 µs are recorded; 0 records every contention, which can flood the recording on a busy service</p>
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
            <p>Record calls to specific Java methods (<code>trace=Class.method</code>). A latency threshold keeps only the slow calls (<code>trace=Class.method:5ms</code>); without one, every call is recorded</p>
          </div>
        </div>
        <div class="feature-card">
          <div class="feature-icon"><i class="bi bi-hdd-stack"></i></div>
          <div class="feature-content">
            <h4>Native Memory Profiling</h4>
            <p>Track native (off-heap) memory allocations to debug memory usage outside the Java heap. The builder samples every 512 KiB (<code>nativemem=512k</code>); clearing the interval records every malloc, the most expensive setting</p>
          </div>
        </div>
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

      <h3 id="command">The Generated Command</h3>
      <p>
        The panel on the right shows the assembled command and, above it, each active option as its own labelled
        parameter, so you can check what a toggle contributed before copying. <strong>Copy command</strong> puts the
        whole <code>-agentpath:</code> argument on the clipboard, ready to paste into your JVM arguments.
      </p>
      <p>
        Leaving <strong>Agent Path</strong> empty falls back to <code>/path/to/libasyncProfiler.so</code> and leaving
        the output empty falls back to <code>/tmp/profile-%t.jfr</code>; both are placeholders meant to be replaced.
      </p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

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
