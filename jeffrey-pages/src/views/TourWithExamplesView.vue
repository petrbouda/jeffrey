<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
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
</script>

<template>
  <div class="container-wide py-5">
    <div class="row">
      <div class="col-12">
        <div class="d-flex align-items-center justify-content-between mb-4">
          <div class="d-flex align-items-center">
            <div class="page-header-icon me-3 bg-primary-gradient">
              <i class="bi bi-collection"></i>
            </div>
            <h1 class="page-title mb-0">Tour with Examples</h1>
          </div>
          <router-link to="/launch-it" class="btn btn-outline-secondary">
            <i class="bi bi-arrow-left me-2"></i>Back to Launch It
          </router-link>
        </div>

        <div class="page-content bg-white rounded-3 p-0">
          <div class="content-section">

            <div class="intro-text">
              <p>The <code>jeffrey-examples</code> Docker image contains pre-generated recordings that demonstrate Jeffrey's capabilities. These examples help you understand how Jeffrey works without needing to set up your own recordings.</p>
            </div>

            <div class="step-card">
              <div class="step-number">1</div>
              <div class="step-content">
                <h3>Quick Start</h3>
                <p>Launch the Docker container with the following command:</p>
                <pre><code>docker run -it --network host petrbouda/jeffrey-examples</code></pre>
                <p>After starting the container, open <a href="http://localhost:8080" target="_blank">http://localhost:8080</a> in your browser to access the Jeffrey UI.</p>
              </div>
            </div>

            <div class="concepts-grid">
              <div class="concept-card">
                <div class="concept-icon">
                  <i class="bi bi-github"></i>
                </div>
                <h4>Test Application</h4>
                <p>A multi-threaded web application storing person information with intentional inefficiencies for interesting profiling data.</p>
                <ul>
                  <li><a href="https://github.com/petrbouda/jeffrey-testapp">View on GitHub</a></li>
                  <li><code>direct</code> - HTTP/JSON serialized directly to/from Java Objects, contain additional caching</li>
                  <li><code>dom</code> - HTTP/JSON through DOM (JsonNode) then to Java Objects, creates garbage intentionally</li>
                </ul>
              </div>

              <div class="concept-card">
                <div class="concept-icon">
                  <i class="bi bi-diagram-3"></i>
                </div>
                <h4>Profile Concepts</h4>
                <ul>
                  <li><strong>Recording:</strong> Profiling data, in most case a single JFR recording file, but can contain some additional files (Logs, PerfData Heap Dumps, ...)</li>
                  <li><strong>Profile:</strong> Parsed and initialized data from the recordings to a single unit for investigation</li>
                  <li><strong>Primary:</strong> Main profile</li>
                  <li><strong>Secondary:</strong> Profile to be used for comparison</li>
                </ul>
              </div>
            </div>

            <div class="step-card">
              <div class="step-number">2</div>
              <div class="step-content">
                <h3>Create Your First Profile</h3>
                <p>Select the <code>Examples</code> project and navigate to the <code>Recordings</code> section. Choose any recording from the folder <code>Persons</code> and start the initialization of a new profile.
                  Switch to the <code>Profiles</code> section and wait for the initialization to be finished.</p>

                <div class="info-note">
                  <i class="bi bi-info-circle"></i>
                  <span>It can take a while based on the size of the selected recording. Currently, SQLite database is used under the hood, therefore, there are limited possibilities to enhance parallelism while inserting data. It's going to be improved in the future.</span>
                </div>
              </div>
            </div>

          </div>
        </div>

        <div class="categories-section">
          <div class="categories-grid">
            <div class="category-card">
              <div class="category-icon cpu">
                <i class="bi bi-cpu"></i>
              </div>
              <h4>CPU Examples</h4>
              <p>
                These recordings were recorded using Async-Profiler and contain full-featured CPU samples with stacktraces that include Java, JVM and Kernel frames
                (available only on Linux with perf_events).
                After opening the generated profile, select the <code>Flamegraphs</code> section and choose of the predefined flamegraph types.
              </p>
              <p>
                You can also use the <code>Differential Flamegraphs</code> section to compare two profiles, <code>direct</code> vs. <code>dom</code> serde
                (both profiles needs to be generated from recordings). The <code>Secondary Profile</code> needs to be selected to enable the button in sidebar menu.
              </p>
              <div class="category-examples">
                <div class="example-item">jeffrey-persons-direct-serde-cpu.jfr</div>
                <div class="example-item">jeffrey-persons-dom-serde-cpu.jfr</div>
              </div>
            </div>

            <div class="category-card">
              <div class="category-icon timer">
                <i class="bi bi-stopwatch"></i>
              </div>
              <h4>CTimer Examples</h4>
              <p>
                ctimer is a special mode while recording using Async-Profiler: <a href="https://github.com/async-profiler/async-profiler/blob/master/docs/ProfilingInContainer.md">Profiling in Container</a>.
                This is the default mode when perf_events are not available on Linux, especially in containers.
              </p>
              <p>
                These recordings contain CPU samples with stacktraces, but without Kernel frames. You can check it out in the <code>Flamegraphs</code>
                and <code>Differential Flamegraphs</code> section.
              </p>
              <div class="category-examples">
                <div class="example-item">jeffrey-persons-direct-serde-ctimer.jfr</div>
                <div class="example-item">jeffrey-persons-dom-serde-ctimer.jfr</div>
              </div>
            </div>

            <div class="category-card">
              <div class="category-icon jdk">
                <i class="bi bi-gear"></i>
              </div>
              <h4>JDK Examples</h4>
              <p>
                These recordings were recorded purely using JDK Flight Recorder (JFR). The advantage of this mode is that it works on all platforms, not only on Linux.
              </p>
              <p>
                However, it does not contain real CPU samples and the result might not be correct in some cases.
                It samples application threads that are in a runnable state.
              </p>
              <div class="category-examples">
                <div class="example-item">jeffrey-persons-direct-serde-jdk.jfr</div>
                <div class="example-item">jeffrey-persons-dom-serde-jdk.jfr</div>
              </div>
            </div>

            <div class="category-card">
              <div class="category-icon custom">
                <i class="bi bi-lightning"></i>
              </div>
              <h4>Custom Events</h4>
              <p>
                Application-specific metrics and business logic events. Concretely, this recording contain emitted events from <a href="https://central.sonatype.com/artifact/cafe.jeffrey-analyst/jeffrey-events">Jeffrey Events</a>:
              </p>
              <ul>
                <li>HTTP Server</li>
                <li>JDBC Statements</li>
                <li>JDBC Pool</li>
              </ul>
              <p>Switch to the <code>custom mode</code> at the top of the sidebar menu and look at the dashboards related to custom events.</p>
              <p>
                Don't forget to check <code>Guardian Analysis</code> section with warnings related to:
                <code>JIT Compilation</code>, <code>Logging</code> and <code>Regular Expressions</code>
              </p>
              <div class="category-examples">
                <div class="example-item">jeffrey-persons-custom-events.jfr</div>
              </div>
            </div>

            <div class="category-card">
              <div class="category-icon tracing">
                <i class="bi bi-diagram-2"></i>
              </div>
              <h4>Method Tracing</h4>
              <p>
                This is the another special mode from the Async-Profiler that intercept Java or Native methods and records every invocation of the given methods.
                Look here for more details: <a href="https://github.com/async-profiler/async-profiler/blob/master/docs/ProfilingModes.md#java-method-profiling">Method Profiling</a>
              </p>
              <p>
                Samples are recorded in <code>jdk.ExecutionSample</code> event-type and available in predefined <code>Flamegraphs</code> section.
              </p>
              <div class="category-examples">
                <div class="example-item">jeffrey-persons-dom-serde-method-tracing.jfr</div>
              </div>
            </div>

            <div class="category-card">
              <div class="category-icon memory">
                <i class="bi bi-memory"></i>
              </div>
              <h4>Native Allocation Samples</h4>
              <p>
                If you need to figure out native memory leaks in your application, then record native memory using Async-Profiler
                (<a href="https://github.com/async-profiler/async-profiler/blob/master/docs/ProfilingModes.md#native-memory-leaks">Native Memory Profiling</a>) and let visualize it in Jeffrey.
              </p>
              <p>
                Look at the <code>Flamegraphs</code> section and select Native Allocation flamegraphs related to <code>Leaks</code> or <code>Malloc</code> samples.
              </p>
              <div class="category-examples">
                <div class="example-item">jeffrey-persons-native-allocation-samples.jfr</div>
              </div>
            </div>
          </div>
          <div class="closing-section">
            <h3>Start Exploring</h3>
            <p>These examples are designed to help you get familiar with Jeffrey's features and learn how to use it effectively for performance analysis.</p>
            <p>Feel free to explore the recordings and profiles. Each example showcases different aspects of Java application profiling.
            Don't forget to check other dashboards containing interesting data belonging application's behavior.</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-header-icon {
  font-size: 1.5rem;
  width: 50px;
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgba(78, 115, 223, 0.8);
  border-radius: 12px;
  color: white;
}

.page-title {
  font-weight: 700;
  font-size: 2rem;
  color: #343a40;
}

.page-content {
  min-height: 60vh;
  overflow: hidden;
}

.content-section {
  padding: 1rem;
}

.info-note {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  background: #f3f8ff;
  border: 1px solid #e1ecf7;
  border-radius: 8px;
  padding: 1rem;
  margin-top: 1rem;
  font-size: 0.85rem;
  color: #1565c0;
}

.info-note i {
  color: #1976d2;
  font-size: 1rem;
  margin-top: 0.1rem;
  flex-shrink: 0;
}

.info-note span {
  line-height: 1.5;
}

.intro-text {
  margin-bottom: 2rem;
  color: #6c757d;
  line-height: 1.6;
}

.intro-text p {
  margin-bottom: 1rem;
}

.intro-text code {
  background: #f8f9fa;
  padding: 0.2rem 0.4rem;
  border-radius: 4px;
  font-size: 0.9rem;
}

.step-card {
  display: flex;
  align-items: flex-start;
  gap: 1.5rem;
  background: white;
  border: 2px solid #e9ecef;
  border-radius: 12px;
  padding: 1.5rem;
  transition: all 0.3s ease;
}

.step-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.08);
  border-color: #667eea;
}

.step-number {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.2rem;
  font-weight: 700;
  flex-shrink: 0;
}

.step-content h3 {
  font-size: 1.4rem;
  font-weight: 600;
  color: #2c3e50;
  margin-bottom: 0.75rem;
}

.step-content p {
  color: #6c757d;
  line-height: 1.6;
  margin-bottom: 1rem;
}

.concepts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 1.5rem;
  margin: 2rem 0;
}

.concept-card {
  background: white;
  border: 2px solid #e9ecef;
  border-radius: 12px;
  padding: 1.5rem;
  transition: all 0.3s ease;
  position: relative;
}

.concept-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.08);
  border-color: #667eea;
}

.concept-icon {
  width: 32px;
  height: 32px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: absolute;
  top: 1.75rem;
  left: 1.75rem;
  color: white;
  font-size: 1.1rem;
  z-index: 2;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.concept-card h4 {
  font-size: 1.1rem;
  font-weight: 700;
  color: #2c3e50;
  margin: 2px 0 1rem 0;
  padding: 0.5rem 1rem 0.5rem 3rem;
  border-radius: 6px;
  text-align: left;
  position: relative;
  z-index: 1;
}

.concept-card p {
  color: #6c757d;
  line-height: 1.6;
  margin-bottom: 1rem;
}

.concept-card ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.concept-card li {
  padding: 0.5rem 0;
  border-bottom: 1px solid #e9ecef;
  color: #495057;
  font-size: 0.9rem;
}

.concept-card li:last-child {
  border-bottom: none;
}

.concept-card a {
  color: #667eea;
  text-decoration: none;
  font-weight: 500;
}

.concept-card a:hover {
  text-decoration: underline;
}

.section-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  margin: 1.5rem -1rem 1rem -1rem;
  padding: 0.75rem 1.5rem;
  border-radius: 8px;
  position: relative;
}

.section-header h3 {
  font-size: 1.3rem;
  font-weight: 600;
  color: white;
  margin: 0;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
}


.example-item {
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 8px;
  padding: 1rem;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 0.85rem;
  color: #495057;
  transition: all 0.3s ease;
}

.example-item:hover {
  border-color: #667eea;
  background: #f8f9ff;
  transform: translateY(-1px);
}

.categories-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1.5rem;
}

.category-card {
  background: white;
  border: 2px solid #e9ecef;
  border-radius: 12px;
  padding: 1rem;
  transition: all 0.3s ease;
  position: relative;
}

.category-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.08);
  border-color: #667eea;
}

.category-icon {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: absolute;
  top: 1.25rem;
  left: 1.25rem;
  color: white;
  font-size: 1.1rem;
  z-index: 2;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.category-icon.cpu { background: linear-gradient(135deg, #ff6b6b, #ee5a24); }
.category-icon.timer { background: linear-gradient(135deg, #4ecdc4, #2c3e50); }
.category-icon.jdk { background: linear-gradient(135deg, #45b7d1, #3742fa); }
.category-icon.custom { background: linear-gradient(135deg, #ffa726, #fb8c00); }
.category-icon.tracing { background: linear-gradient(135deg, #ab47bc, #8e24aa); }
.category-icon.memory { background: linear-gradient(135deg, #26c6da, #00acc1); }

.category-card h4 {
  font-size: 1.1rem;
  font-weight: 700;
  color: #2c3e50;
  margin: 2px 0 1rem 0;
  padding: 0.5rem 1rem 0.5rem 3rem;
  border-radius: 6px;
  text-align: left;
  position: relative;
  z-index: 1;
}

.category-card p {
  color: #6c757d;
  line-height: 1.6;
  font-size: 0.9rem;
}

.category-card ul {
  color: #6c757d;
  line-height: 1.6;
  font-size: 0.9rem;
  padding-left: 1.5rem;
  margin: 1rem 0;
}

.category-card li {
  margin-bottom: 0.5rem;
}

.category-examples {
  margin-top: 1rem;
}

.category-examples .example-item {
  margin-bottom: 0.5rem;
}

.categories-section {
  padding: 0.5rem 0;
}

.categories-section .categories-grid {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 0.5rem;
}

.closing-section {
  margin-top: 3rem;
  padding: 2rem;
  background: #f8f9fa;
  border-radius: 12px;
  text-align: center;
}

.closing-section h3 {
  font-size: 1.6rem;
  font-weight: 700;
  color: #2c3e50;
  margin-bottom: 1rem;
}

.closing-section p {
  color: #6c757d;
  line-height: 1.6;
  margin-bottom: 1rem;
}

pre {
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 8px;
  padding: 0.75rem;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  margin: 0.5rem 0;
  overflow-x: auto;
}

pre code {
  background: none;
  padding: 0;
  font-size: 0.9rem;
  color: #495057;
  border: none;
}

@media (max-width: 768px) {
  .step-card {
    flex-direction: column;
    text-align: center;
  }

  .concepts-grid {
    grid-template-columns: 1fr;
  }

  .categories-grid {
    grid-template-columns: 1fr;
  }


  .content-section {
    padding: 1rem;
  }
}
</style>
