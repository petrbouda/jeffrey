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

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'builder', text: 'Building a Command', level: 2 },
  { id: 'mandatory', text: 'Mandatory Options', level: 3 },
  { id: 'events', text: 'Event Options', level: 3 },
  { id: 'thresholds', text: 'Thresholds', level: 3 },
  { id: 'advanced', text: 'Advanced Options', level: 3 },
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
        copy it. Nothing is saved. Links in the page header lead to the async-profiler documentation on profiling
        modes, profiler options and method tracing.
      </p>
      <p>
        Paste the result into the JVM you want to profile, then bring the recording it produces back into Microscope
        through <router-link to="/docs/microscope/recordings">Recordings</router-link>. In a deployment provisioned by
        <router-link to="/docs/provisioner/configuration">Jeffrey Provisioner</router-link>, give it to the provisioner
        as <code>profiler-command</code> in its configuration file, or as the
        <code>JEFFREY_PROFILER_COMMAND</code> environment variable, which wins over the file. Pick
        <strong>Jeffrey JIB</strong> and the command carries only the options: the provisioner runs them on the
        Async-profiler library its <code>profiler-path</code> names, which jeffrey-jib bakes into the image. Every
        combination of library and command is listed under
        <router-link to="/docs/provisioner/configuration#choosing-the-profiler">Choosing the Profiler</router-link>.
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
        not apply it to anything, and needs no hub, workspace or project. The profiler path and output file are
        literal values that go straight into the command you copy.
      </DocsCallout>

      <h3 id="mandatory">Mandatory Options</h3>
      <p>Every profiler configuration requires these essential settings:</p>

      <div class="options-list">
        <div class="option-item">
          <div class="option-header">
            <i class="bi bi-folder2-open"></i>
            <strong>Async-profiler library</strong>
          </div>
          <p>
            Which library the command runs on, picked from two tiles. <strong>Jeffrey JIB</strong> (the default,
            marked Recommended) needs nothing else: the
            command carries only the options, and Jeffrey Provisioner runs them on the Async-profiler baked into the
            image. <strong>Custom profiler</strong> asks for the path to your own <code>libasyncProfiler.so</code>,
            which goes into the command as <code>-agentpath:&lt;path&gt;=</code>.
          </p>
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

      <h3 id="events">Event Options</h3>
      <p>
        Select which profiling events to capture. Each event type can be expanded to configure additional options
        like sampling intervals, thresholds, and profiling modes.
      </p>

      <div class="profiler-features">
        <div class="feature-card">
          <div class="feature-icon"><i class="bi bi-cpu"></i></div>
          <div class="feature-content">
            <h4>CPU Profiling</h4>
            <p>Sample thread stack traces at regular intervals to find CPU-intensive methods and performance bottlenecks. The builder offers <code>ctimer</code> (no kernel stacks, works where perf_events are unavailable, e.g. in containers) and <code>cpu</code> (perf_events with kernel stacks). <code>ctimer</code> attributes every sample to the carrier thread, so spans that ran on a virtual thread get no flamegraph — drop <code>event=</code> to let the JVM's own sampler attribute to virtual threads instead</p>
          </div>
        </div>
        <div class="feature-card">
          <div class="feature-icon"><i class="bi bi-memory"></i></div>
          <div class="feature-content">
            <h4>Allocation Profiling</h4>
            <p>Sample object allocations to identify memory pressure sources and allocation hotspots. An interval takes one sample per N bytes allocated (<code>alloc=512k</code>); without one the option stays a bare <code>alloc</code> and async-profiler uses its own default</p>
          </div>
        </div>
        <div class="feature-card">
          <div class="feature-icon"><i class="bi bi-lock"></i></div>
          <div class="feature-content">
            <h4>Lock Profiling</h4>
            <p>Monitor contended locks and synchronization events to debug threading and concurrency issues. The builder starts at <code>lock=10us</code>, async-profiler's own default, so only contentions that waited at least 10 µs are recorded; clearing the field is written as <code>lock=0</code> and records every contention, which can flood the recording on a busy service</p>
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
            <p>Record calls to specific Java methods. Add each method as its own row; every row gets its own latency threshold and unit and becomes one <code>trace=</code> option (<code>trace=com.example.OrderService.place:5ms</code>). A new row records every call until you set a threshold on it</p>
          </div>
        </div>
        <div class="feature-card">
          <div class="feature-icon"><i class="bi bi-hdd-stack"></i></div>
          <div class="feature-content">
            <h4>Native Memory Profiling</h4>
            <p>Track native (off-heap) memory allocations to debug memory usage outside the Java heap. The builder samples every 512 KiB (<code>nativemem=512k</code>); clearing the interval records every malloc, the most expensive setting. <strong>Omit free() events</strong> records only allocations and skips <code>free()</code> calls, which is cheaper</p>
          </div>
        </div>
      </div>

      <DocsCallout type="info">
        <strong>Only the <code>Class.method</code> form can be traced.</strong> Wildcards and JVM signatures work
        (<code>java.lang.Thread.*</code>, <code>*.&lt;init&gt;</code>,
        <code>java.lang.String.indexOf(Ljava/lang/String;)I</code>), but the builder rejects a pattern with a colon
        (the threshold belongs on the row), spaces, JVM symbols such as <code>G1CollectedHeap::allocate</code> and
        native methods such as <code>Java_java_lang_Thread_start</code>. async-profiler refuses to start on a single
        bad target, so the builder catches it before the command is copied.
      </DocsCallout>

      <h3 id="thresholds">Thresholds</h3>
      <p>
        Every threshold field carries a badge that says what the command will actually do. A threshold reads as
        <strong>≥ 5 ms</strong>, <strong>waits ≥ 10 µs</strong> or <strong>one sample per 512 KiB</strong>; an empty
        field reads as <strong>every call</strong>, <strong>every contention</strong> or <strong>every malloc</strong>,
        so the most expensive setting is visible rather than implied.
      </p>
      <p>
        async-profiler rejects fractions, so amounts are always written as whole numbers in the largest unit that
        holds them exactly: <code>0.5ms</code> becomes <code>500us</code>, <code>1024k</code> becomes <code>1m</code>.
        A value of zero, or one smaller than the base unit, counts as no threshold.
      </p>

      <h3 id="advanced">Advanced Options</h3>
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
        parameter, so you can check what a toggle contributed before copying. Click the command to copy it; a switch
        above it picks the form it is copied in:
      </p>
      <ul>
        <li><strong>ENV var</strong> &mdash; <code>JEFFREY_PROFILER_COMMAND='…'</code>, to set on the pod. It wins
          over the configuration file. The value is single-quoted so a shell does not read
          <code>&lt;&lt;JEFFREY:…&gt;&gt;</code> or <code>%t</code>.</li>
        <li><strong>HOCON</strong> &mdash; <code>profiler-command = "…"</code>, for the provisioner's configuration
          file.</li>
        <li><strong>Options</strong> (Jeffrey JIB) or <strong>JVM argument</strong> (Custom profiler) &mdash; the bare
          command. With a custom profiler that is the whole <code>-agentpath:</code> argument, ready to paste into the
          JVM arguments of an application you start yourself.</li>
      </ul>
      <p>
        With Jeffrey JIB the output defaults to <code>&lt;&lt;JEFFREY:CURRENT_SESSION&gt;&gt;/profile-%t.jfr</code>,
        which the provisioner points at the session directory. With a custom profiler it defaults to
        <code>/tmp/profile-%t.jfr</code>, and an empty profiler path falls back to
        <code>/path/to/libasyncProfiler.so</code>; both are placeholders meant to be replaced. Switching between the two
        keeps an output path you typed yourself.
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
