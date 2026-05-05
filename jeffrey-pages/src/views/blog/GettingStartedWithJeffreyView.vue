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
import { ref } from 'vue';

interface SelectedImage {
  src: string;
  alt: string;
}

const selectedImage = ref<SelectedImage | null>(null);
const showModal = ref(false);

const openImageModal = (imageSrc: string, imageAlt: string): void => {
  selectedImage.value = { src: imageSrc, alt: imageAlt };
  showModal.value = true;
};

const closeImageModal = (): void => {
  showModal.value = false;
  selectedImage.value = null;
};
</script>

<template>
  <div class="container-wide py-5">
    <div class="row">
      <div class="col-12">
        <div class="d-flex align-items-center justify-content-between mb-4">
          <div class="d-flex align-items-center">
            <div class="page-header-icon me-3 bg-getting-started-gradient">
              <i class="bi bi-rocket-takeoff"></i>
            </div>
            <div>
              <h1 class="page-title mb-0">Stop Guessing, Start Profiling</h1>
              <p class="text-muted mb-0">Published on April 15, 2026</p>
            </div>
          </div>
          <router-link to="/blog" class="btn btn-outline-secondary">
            <i class="bi bi-arrow-left me-2"></i>Back to Blog
          </router-link>
        </div>

        <div class="page-content bg-white rounded-3 shadow-sm p-5">
          <div class="blog-content">
            <div class="lead mb-4">
              Getting Started with JFR Analysis Using Jeffrey
            </div>

            <p>
              You optimized that database query. You added caching. You switched to a faster serialization library.
              But did any of it actually help? Without profiling, you're guessing — and guessing is expensive.
              You might spend days optimizing code that accounts for 2% of your application's CPU time while the
              real bottleneck hides in plain sight.
            </p>

            <p>
              Java gives you one of the best profiling mechanisms available in any runtime:
              <strong>JDK Flight Recorder (JFR)</strong>. Combined with <strong>Async Profiler</strong> for
              recording and <strong>Jeffrey</strong> for analysis, you can go from "I think it's slow" to
              "I know exactly where the time goes" in minutes.
            </p>

            <p>
              This article walks you through the entire workflow: recording a JFR file with Async Profiler,
              launching Jeffrey, and uncovering real performance insights from your application.
            </p>

            <h2>Why Async Profiler + JFR?</h2>

            <p>
              JDK Flight Recorder is a profiling and event collection framework built directly into the JVM.
              It captures a wide range of runtime events — garbage collection pauses, thread activity, class loading,
              memory allocation, I/O operations — with extremely low overhead (typically under 2%). JFR events are
              written in a compact binary format designed for production use.
            </p>

            <p>
              <strong>Async Profiler</strong> is an open-source sampling profiler for Java that goes beyond what
              JFR can do on its own. It uses <code>perf_events</code> on Linux to collect CPU samples with accurate
              stack traces (no safepoint bias), and it adds allocation and lock contention profiling on top. The key
              flag that ties everything together is <code>jfrsync=default</code> — this tells Async Profiler to write
              its samples into a JFR file alongside the standard JDK events. You get the best of both worlds in a
              single recording:
            </p>

            <ul class="feature-list">
              <li>
                <strong>CPU profiling</strong> (<code>cpu</code>) — Where is your application spending CPU cycles?
                Async Profiler samples the call stack at regular intervals to build a statistical picture of CPU usage.
              </li>
              <li>
                <strong>Allocation profiling</strong> (<code>alloc</code>) — Where is memory being allocated?
                Every TLAB (Thread Local Allocation Buffer) allocation event is captured, showing you which methods
                are creating objects and how much memory they consume.
              </li>
              <li>
                <strong>Lock contention profiling</strong> (<code>lock</code>) — Where are threads waiting?
                Lock events reveal contention points where threads block on synchronized sections, ReentrantLocks,
                or other concurrency primitives.
              </li>
              <li>
                <strong>JFR synchronization</strong> (<code>jfrsync=default</code>) — Async Profiler merges its
                data with JFR's standard event stream. This means your recording also contains GC events, thread
                statistics, JIT compilation data, and dozens of other JVM metrics — all correlated in the same timeline.
              </li>
            </ul>

            <h2>Recording Your Application</h2>

            <p>There are two common ways to capture a recording with Async Profiler.</p>

            <h3>Option 1: Attach to a Running Process</h3>

            <p>
              If your application is already running, use the <code>asprof</code> command-line tool:
            </p>

            <div class="code-block">
              <pre><code>asprof -e cpu,alloc,lock --jfrsync default -d 60 -f recording.jfr &lt;pid&gt;</code></pre>
            </div>

            <p>
              This attaches to the process with the given PID, profiles for 60 seconds, and writes the output to
              <code>recording.jfr</code>. Adjust <code>-d</code> for longer or shorter recording windows — 30 to
              120 seconds is usually enough to capture representative behavior.
            </p>

            <h3>Option 2: Start with the Java Agent</h3>

            <p>
              To profile from the very beginning (useful for capturing startup behavior), launch your application
              with Async Profiler as a Java agent:
            </p>

            <div class="code-block">
              <pre><code>java -agentpath:/path/to/libasyncProfiler.so=start,event=cpu,alloc,lock,jfrsync=default,file=recording.jfr \
     -jar myapp.jar</code></pre>
            </div>

            <p>
              This starts profiling immediately when the JVM boots. The recording file is written continuously,
              so you can stop the application when you have enough data or let it run for a defined duration.
            </p>

            <p>
              Either way, you end up with a <code>.jfr</code> file that contains both Async Profiler's sampling
              data and JFR's built-in events. This single file is everything Jeffrey needs.
            </p>

            <h2>Getting Jeffrey Running</h2>

            <p>
              Jeffrey is a self-hosted analysis tool — no cloud services, no account signup, no data leaving your
              machine. The fastest way to start is with Docker:
            </p>

            <div class="code-block">
              <pre><code>docker run -it --network host petrbouda/microscope</code></pre>
            </div>

            <p>
              Open <a href="http://localhost:8080" target="_blank">http://localhost:8080</a> in your browser.
              That's it — Jeffrey is ready to analyze your recordings.
            </p>

            <p>
              If you want to explore Jeffrey with pre-loaded example data first (recommended for your first time),
              use the examples image instead:
            </p>

            <div class="code-block">
              <pre><code>docker run -it --network host petrbouda/microscope-examples</code></pre>
            </div>

            <p>
              This ships with sample JFR recordings and pre-built profiles so you can explore every feature
              without generating your own data first.
            </p>

            <h2>Recordings: Upload and Explore</h2>

            <p>
              Jeffrey's <strong>Recordings</strong> is the fastest path from a JFR file to actionable insights.
              Click "Recordings" on the home page, drag and drop your <code>recording.jfr</code> file,
              and click "Analyze."
            </p>

            <div class="images-section">
              <div class="image-container clickable" @click="openImageModal('/images/release-notes/quick-analysis/01-upload.png', 'Upload your JFR recording — drag and drop, select the file, and click Analyze')">
                <img src="/images/release-notes/quick-analysis/01-upload.png" alt="Upload your JFR recording to Jeffrey" class="img-fluid rounded">
                <div class="image-overlay">
                  <i class="bi bi-zoom-in"></i>
                </div>
                <p class="image-caption">Upload your JFR recording — drag and drop, select the file, and click Analyze.</p>
              </div>
            </div>

            <p>
              Jeffrey parses the recording, stores the data in an embedded DuckDB database (one per profile,
              fully isolated), and presents you with an analysis dashboard. The entire process takes seconds
              for typical recordings.
            </p>

            <p>
              From here, you can navigate to any analysis feature: flamegraphs, GC analysis, thread statistics,
              heap memory trends, and more. Everything is generated from that single JFR file.
            </p>

            <h2>Reading Your First Flamegraph</h2>

            <p>
              Flamegraphs are Jeffrey's primary visualization for profiling data. Navigate to
              <strong>Flamegraphs</strong> and select the type of analysis — CPU samples, allocation samples,
              or lock contention.
            </p>

            <div class="images-section">
              <div class="image-container clickable" @click="openImageModal('/images/release-notes/quick-analysis/03-flamegraph-selection.png', 'Choose between CPU, allocation, and lock contention flamegraphs')">
                <img src="/images/release-notes/quick-analysis/03-flamegraph-selection.png" alt="Select the flamegraph type for analysis" class="img-fluid rounded">
                <div class="image-overlay">
                  <i class="bi bi-zoom-in"></i>
                </div>
                <p class="image-caption">Choose between CPU, allocation, and lock contention flamegraphs.</p>
              </div>
            </div>

            <p>A flamegraph is a visualization of sampled stack traces. Here's how to read one:</p>

            <ul class="feature-list">
              <li>
                <strong>Each box is a method (frame)</strong> on the call stack
              </li>
              <li>
                <strong>Width represents time</strong> — the wider a box, the more samples included that method.
                A method that's 30% of the flamegraph width was on the CPU (or allocating, or contending) roughly
                30% of the sampled time
              </li>
              <li>
                <strong>Depth represents the call stack</strong> — the bottom is the entry point (usually
                <code>main</code> or a thread's <code>run</code> method), and each layer up is a method called
                by the one below it
              </li>
              <li>
                <strong>Color is categorical</strong> — it helps distinguish frames visually but doesn't encode severity
              </li>
            </ul>

            <p>
              The flamegraph is interactive. Click on any frame to zoom in and see its children in detail.
              Use the search bar to highlight specific packages or classes across the entire graph.
            </p>

            <div class="images-section">
              <div class="image-container clickable" @click="openImageModal('/images/release-notes/quick-analysis/04-flamegraph-timeseries.png', 'Flamegraph with a correlated timeseries showing sample distribution over time')">
                <img src="/images/release-notes/quick-analysis/04-flamegraph-timeseries.png" alt="Interactive flamegraph with correlated timeseries" class="img-fluid rounded">
                <div class="image-overlay">
                  <i class="bi bi-zoom-in"></i>
                </div>
                <p class="image-caption">Flamegraph with a correlated timeseries showing sample distribution over time.</p>
              </div>
            </div>

            <p>
              Below the flamegraph, Jeffrey shows a <strong>timeseries</strong> of sample counts over the recording
              duration. This helps you spot patterns — a CPU spike at a specific time, a burst of allocations during
              startup, or periodic lock contention. You can select a time range in the timeseries to regenerate the
              flamegraph for just that interval.
            </p>

            <p>
              <strong>What to look for:</strong> Start with the widest frames near the top of the graph. These are
              the methods consuming the most resources. Ask yourself: is this expected? A wide frame in your
              application code is an optimization candidate. A wide frame in a framework's internals might mean
              you're using it inefficiently. A wide frame in <code>GCTaskThread</code> means garbage collection is
              dominating — jump to the GC analysis.
            </p>

            <h2>Guardian: Automated Issue Detection</h2>

            <p>
              Not sure where to start? Jeffrey's <strong>Guardian</strong> runs a set of automated checks against
              your recording and flags potential issues with color-coded severity levels.
            </p>

            <div class="images-section">
              <div class="image-container clickable" @click="openImageModal('/images/feature-screenshots/profile_guardian.png', 'Guardian scans your recording and surfaces issues organized by category')">
                <img src="/images/feature-screenshots/profile_guardian.png" alt="Guardian automated health checks" class="img-fluid rounded">
                <div class="image-overlay">
                  <i class="bi bi-zoom-in"></i>
                </div>
                <p class="image-caption">Guardian scans your recording and surfaces issues organized by category.</p>
              </div>
            </div>

            <p>
              Guardian traverses the stack traces and evaluates event metrics against known patterns.
              It checks for common problems like:
            </p>

            <ul class="feature-list">
              <li>Excessive GC activity or long GC pauses</li>
              <li>High allocation rates in specific code paths</li>
              <li>Thread contention hotspots</li>
              <li>Compilation and deoptimization issues</li>
              <li>Suspicious patterns in framework usage</li>
            </ul>

            <div class="images-section">
              <div class="image-container clickable" @click="openImageModal('/images/feature-screenshots/profile_guardian_2.png', 'Each issue includes severity, description, and the relevant stack traces')">
                <img src="/images/feature-screenshots/profile_guardian_2.png" alt="Guardian issue breakdown" class="img-fluid rounded">
                <div class="image-overlay">
                  <i class="bi bi-zoom-in"></i>
                </div>
                <p class="image-caption">Each issue includes severity, description, and the relevant stack traces.</p>
              </div>
            </div>

            <p>
              Each finding is categorized (OK, Warning, Critical) and includes enough context to understand the
              issue and where to look next. Think of Guardian as your first triage step — it highlights the areas
              most likely to reward investigation.
            </p>

            <h2>GC at a Glance</h2>

            <p>
              Since you recorded with <code>jfrsync=default</code>, your JFR file contains detailed garbage
              collection events alongside the profiling data. Jeffrey's <strong>GC Analysis</strong> gives you a
              comprehensive overview without any additional setup.
            </p>

            <div class="images-section">
              <div class="image-container clickable" @click="openImageModal('/images/release-notes/quick-analysis/02-gc-analysis.png', 'GC analysis dashboard showing pause statistics, collection frequency, and heap usage trends')">
                <img src="/images/release-notes/quick-analysis/02-gc-analysis.png" alt="GC overview dashboard" class="img-fluid rounded">
                <div class="image-overlay">
                  <i class="bi bi-zoom-in"></i>
                </div>
                <p class="image-caption">GC analysis dashboard showing pause statistics, collection frequency, and heap usage trends.</p>
              </div>
            </div>

            <p>The GC dashboard shows you:</p>

            <ul class="feature-list">
              <li>
                <strong>Pause statistics</strong> — total pause time, average pause, maximum pause, and pause distribution
              </li>
              <li>
                <strong>Collection frequency</strong> — how often young and old generation collections run
              </li>
              <li>
                <strong>Heap usage trends</strong> — committed vs. used heap over time, showing whether your
                application's memory footprint is stable, growing, or sawtoothing
              </li>
              <li>
                <strong>GC algorithm details</strong> — which collector is active and its configuration
              </li>
            </ul>

            <p>
              This is often the first place to check when an application feels sluggish but CPU profiling looks
              normal. Long or frequent GC pauses can dominate latency even when your code is efficient. If you see
              pauses in the hundreds of milliseconds or a steadily growing heap with increasingly frequent
              collections, you've found a lead worth investigating.
            </p>

            <h2>What's Next</h2>

            <p>You've just gone from a running Java application to actionable performance data in minutes:</p>

            <ol class="summary-list">
              <li><strong>Record</strong> with Async Profiler (cpu + alloc + lock + jfrsync=default)</li>
              <li><strong>Analyze</strong> with Jeffrey (upload, explore, investigate)</li>
              <li><strong>Identify</strong> hotspots with flamegraphs, automated checks with Guardian, and GC behavior at a glance</li>
            </ol>

            <p>But this is just the beginning. Jeffrey offers significantly more depth than what we covered here. In the next articles in this series, we'll explore:</p>

            <ul class="feature-list">
              <li>
                <strong>Deep flamegraph techniques</strong> — differential flamegraphs to prove your optimization
                worked, sub-second analysis to zoom into startup or specific spikes, frame collapsing to cut through
                framework noise
              </li>
              <li>
                <strong>Memory investigation</strong> — from GC trends to heap dump analysis, dominator trees,
                leak suspects, and reference chain tracing
              </li>
              <li>
                <strong>Application-level monitoring</strong> — HTTP traffic, database statements, connection pools,
                and gRPC analysis using custom JFR events
              </li>
              <li>
                <strong>AI-powered analysis</strong> — asking questions about your recording in natural language
                and getting answers backed by real data
              </li>
            </ul>

            <div class="download-section mt-5">
              <h3>Try Jeffrey Now</h3>
              <p>Ready to profile your application? Start with the examples image:</p>
              <div class="code-block mb-4">
                <pre><code>docker run -it --network host petrbouda/microscope-examples</code></pre>
              </div>
              <p>
                Open <a href="http://localhost:8080" target="_blank">http://localhost:8080</a>, explore the
                pre-loaded examples, and see what your own recordings reveal.
              </p>
              <div class="download-buttons">
                <a href="https://github.com/petrbouda/jeffrey"
                   class="btn btn-primary btn-lg me-3" target="_blank">
                  <i class="bi bi-github me-2"></i>View on GitHub
                </a>
                <a href="https://jeffrey-analyst.cafe"
                   class="btn btn-outline-primary btn-lg" target="_blank">
                  <i class="bi bi-book me-2"></i>Documentation
                </a>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- Image Modal -->
  <Teleport to="body">
    <div v-if="showModal" class="image-modal" @click="closeImageModal">
      <div class="modal-backdrop" @click="closeImageModal"></div>
      <div class="modal-content-image" @click.stop>
        <button class="modal-close" @click="closeImageModal">
          <i class="bi bi-x-lg"></i>
        </button>
        <img v-if="selectedImage" :src="selectedImage.src" :alt="selectedImage.alt" class="modal-image">
        <p v-if="selectedImage" class="modal-caption">{{ selectedImage.alt }}</p>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.page-header-icon {
  font-size: 1.5rem;
  width: 50px;
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  color: white;
  flex-shrink: 0;
}

.bg-getting-started-gradient {
  background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%);
}

.page-title {
  font-weight: 700;
  font-size: 2rem;
  color: #343a40;
  line-height: 1.2;
}

.page-content {
  min-height: 60vh;
}

.blog-content {
  line-height: 1.7;
  font-size: 1.1rem;
}

.blog-content h2 {
  color: #2c3e50;
  font-weight: 700;
  margin-top: 3rem;
  margin-bottom: 1.5rem;
  padding-bottom: 0.5rem;
  border-bottom: 3px solid #11998e;
}

.blog-content h3 {
  color: #34495e;
  font-weight: 600;
  margin-top: 2.5rem;
  margin-bottom: 1.5rem;
  padding-bottom: 0.3rem;
  border-bottom: 2px solid #38ef7d;
}

.blog-content h4 {
  color: #34495e;
  font-weight: 600;
  margin-top: 2rem;
  margin-bottom: 1rem;
}

.lead {
  font-size: 1.2rem;
  color: #495057;
  font-weight: 400;
  font-style: italic;
}

.code-block {
  background-color: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 6px;
  padding: 1rem;
  margin: 1rem 0;
  border-left: 4px solid #11998e;
}

.code-block pre {
  margin: 0;
  background: none;
  border: none;
  padding: 0;
}

.code-block code {
  background: none;
  color: #2c3e50;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 0.9rem;
}

.feature-list {
  list-style: none;
  padding-left: 0;
}

.feature-list li {
  padding: 0.5rem 0;
  padding-left: 2rem;
  position: relative;
}

.feature-list li::before {
  content: "\25B6";
  color: #11998e;
  font-weight: bold;
  position: absolute;
  left: 0;
  font-size: 0.7rem;
  top: 0.75rem;
}

.summary-list {
  padding-left: 1.5rem;
}

.summary-list li {
  padding: 0.4rem 0;
}

.images-section {
  margin: 2rem 0;
}

.image-container {
  text-align: center;
  position: relative;
}

.image-container.clickable {
  cursor: pointer;
  transition: transform 0.3s ease;
}

.image-container.clickable:hover {
  transform: scale(1.02);
}

.image-container img {
  max-width: 100%;
  height: auto;
  box-shadow: 0 8px 25px rgba(0,0,0,0.15);
  border: 1px solid #e9ecef;
  transition: opacity 0.3s ease;
}

.image-container.clickable:hover img {
  opacity: 0.9;
}

.image-overlay {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  background-color: rgba(0, 0, 0, 0.7);
  color: white;
  border-radius: 50%;
  width: 50px;
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.5rem;
  opacity: 0;
  transition: opacity 0.3s ease;
  pointer-events: none;
}

.image-container.clickable:hover .image-overlay {
  opacity: 1;
}

.image-caption {
  margin-top: 0.5rem;
  font-size: 0.9rem;
  color: #6c757d;
  font-style: italic;
}

.download-section {
  background-color: #f0f8f4;
  padding: 2rem;
  border-radius: 8px;
  border-left: 4px solid #11998e;
  text-align: center;
}

.download-section h3 {
  color: #0d7d6c;
  margin-top: 0;
  border-bottom: none;
}

.download-buttons {
  margin-top: 1.5rem;
}

/* Image Modal Styles */
.image-modal {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 1050;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  animation: fadeIn 0.3s ease;
  background-color: rgba(0, 0, 0, 0.8);
  backdrop-filter: blur(5px);
}

.modal-backdrop {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: -1;
}

.modal-content-image {
  position: relative;
  max-width: 90vw;
  max-height: 90vh;
  background: white;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3);
  animation: scaleIn 0.3s ease;
  z-index: 1;
}

.modal-close {
  position: absolute;
  top: 15px;
  right: 15px;
  background: rgba(0, 0, 0, 0.7);
  color: white;
  border: none;
  border-radius: 50%;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 10;
  font-size: 1.2rem;
  transition: background-color 0.3s ease;
}

.modal-close:hover {
  background: rgba(0, 0, 0, 0.9);
}

.modal-image {
  width: 100%;
  height: auto;
  display: block;
  max-height: 80vh;
  object-fit: contain;
}

.modal-caption {
  padding: 1rem;
  margin: 0;
  text-align: center;
  background-color: #f8f9fa;
  color: #495057;
  font-weight: 600;
  border-top: 1px solid #e9ecef;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes scaleIn {
  from {
    transform: scale(0.9);
    opacity: 0;
  }
  to {
    transform: scale(1);
    opacity: 1;
  }
}

@media (max-width: 768px) {
  .image-modal {
    padding: 1rem;
  }

  .modal-content-image {
    max-width: 95vw;
    max-height: 95vh;
  }

  .modal-close {
    top: 10px;
    right: 10px;
    width: 35px;
    height: 35px;
    font-size: 1rem;
  }
}
</style>
