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
import { onMounted } from 'vue';
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsCallout from '@/components/docs/DocsCallout.vue';
import { useDocsNavigation } from '@/composables/useDocsNavigation';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { adjacentPages } = useDocsNavigation();
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'what-is-jeffrey-cli', text: 'What is Jeffrey CLI?', level: 2 },
  { id: 'recording-sessions', text: 'Recording Sessions', level: 2 },
  { id: 'installation', text: 'Installation', level: 2 },
  { id: 'configuration', text: 'Configuration', level: 2 },
  { id: 'features', text: 'Features', level: 2 },
  { id: 'profiler-options', text: 'Profiler Options', level: 2 },
  { id: 'integration', text: 'Integration with Jeffrey', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const minimalConfig = `jeffrey-home = "/opt/jeffrey"
workspace-id = "production"
project-name = "my-service"`;

const fullConfig = `jeffrey-home = "/opt/jeffrey"
profiler-path = "/opt/async-profiler/libasyncProfiler.so"
workspace-id = "production"
project-name = "my-service"
project-label = "My Service"
repository-type = "ASYNC_PROFILER"

perf-counters { enabled = true }
heap-dump { enabled = true; type = "crash" }
jvm-logging { enabled = true }
messaging { enabled = true; max-age = "24h" }`;

const usageExample = `# Initialize profiling session
java -jar jeffrey-cli.jar init /path/to/config.conf > /tmp/jeffrey.env

# Source environment variables
source /tmp/jeffrey.env

# Start application with profiling
java $JEFFREY_PROFILER_CONFIG -jar my-app.jar`;

const dockerEntrypoint = `#!/bin/bash
# Generate profiling environment
java -jar /jeffrey-libs/jeffrey-cli.jar init /app/jeffrey.conf > /tmp/jeffrey.env
source /tmp/jeffrey.env

# Start application with profiling enabled
exec java $JEFFREY_PROFILER_CONFIG -jar /app/my-app.jar`;
</script>

<template>
  <article class="docs-article">
      <nav class="docs-breadcrumb">
        <router-link to="/docs" class="breadcrumb-item">
          <i class="bi bi-book me-1"></i>Docs
        </router-link>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item">CLI</span>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item active">Overview</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-terminal"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Jeffrey CLI</h1>
          <p class="docs-section-badge">CLI</p>
        </div>
      </header>

      <div class="docs-content">
        <p>Jeffrey CLI is a command-line tool that <strong>initializes profiling sessions</strong> for Java applications, especially in containerized environments.</p>

        <h2 id="what-is-jeffrey-cli">What is Jeffrey CLI?</h2>
        <p>Jeffrey CLI (<code>jeffrey-cli.jar</code>) is an initialization tool that sets up everything needed to profile your Java application:</p>
        <ul>
          <li>Creates workspace/project/session directory structure</li>
          <li>Configures async-profiler or JDK Flight Recorder</li>
          <li>Generates environment variables for application startup</li>
          <li>Enables additional features like heap dumps and JVM logging</li>
        </ul>

        <h3>When to Use Jeffrey CLI</h3>
        <p>Use Jeffrey CLI when you want to:</p>
        <ul>
          <li><strong>Containerized environments</strong> - Profile Java apps in Docker/Kubernetes</li>
          <li><strong>CI/CD pipelines</strong> - Automated profiling during builds and tests</li>
          <li><strong>Production monitoring</strong> - Continuous profiling with minimal overhead</li>
          <li><strong>Multi-instance deployments</strong> - Each instance creates its own session</li>
        </ul>

        <h2 id="recording-sessions">Recording Sessions</h2>
        <p>A <strong>recording session</strong> is created each time the CLI runs - typically when your application is deployed or restarted. Each session gets a unique identifier and its own directory.</p>

        <DocsCallout type="info">
          <strong>New deployment = New session:</strong> Every time your container starts or application restarts, Jeffrey CLI creates a fresh session. This keeps recordings organized and makes it easy to correlate profiling data with specific deployments.
        </DocsCallout>

        <h3>Session Contents</h3>
        <p>Each session directory can contain multiple file types:</p>

        <div class="session-contents-grid">
          <div class="session-content-card jfr">
            <div class="card-header">
              <i class="bi bi-file-earmark-binary"></i>
              <h4>JFR Files</h4>
            </div>
            <div class="card-body">
              <code>profile-*.jfr</code>
              <p>Core profiling data with CPU samples, allocations, locks, and JVM events. Multiple chunk files are created during long-running sessions.</p>
            </div>
          </div>
          <div class="session-content-card heap">
            <div class="card-header">
              <i class="bi bi-memory"></i>
              <h4>Heap Dump</h4>
            </div>
            <div class="card-body">
              <code>heap-dump.hprof.gz</code>
              <p>Memory snapshot captured on OutOfMemoryError or JVM crash. Compressed with gzip for efficient storage.</p>
            </div>
          </div>
          <div class="session-content-card logs">
            <div class="card-header">
              <i class="bi bi-file-text"></i>
              <h4>JVM Logs</h4>
            </div>
            <div class="card-body">
              <code>jfr-jvm.log</code>
              <p>Structured JVM diagnostic logs including GC events, JIT compilation activity, and JFR-related messages.</p>
            </div>
          </div>
          <div class="session-content-card perf">
            <div class="card-header">
              <i class="bi bi-speedometer2"></i>
              <h4>Perf Counters</h4>
            </div>
            <div class="card-body">
              <code>perf-counters.hsperfdata</code>
              <p>JVM performance data captured via <code>-XX:+UsePerfData</code>. Contains low-level metrics about JVM internals.</p>
            </div>
          </div>
          <div class="session-content-card metadata">
            <div class="card-header">
              <i class="bi bi-info-circle"></i>
              <h4>Session Metadata</h4>
            </div>
            <div class="card-body">
              <code>.session-info.json</code>
              <p>Session ID, timestamps, project/workspace identifiers, profiler settings, and custom attributes (cluster, namespace).</p>
            </div>
          </div>
        </div>

        <h2 id="installation">Installation</h2>
        <p>Jeffrey CLI is included in the Jeffrey Docker image. For standalone use:</p>

        <ol>
          <li>Download <code>jeffrey-cli.jar</code> from <a href="https://github.com/petrbouda/jeffrey/releases" target="_blank">GitHub Releases</a></li>
          <li>Place it in your application's deployment package</li>
          <li>For async-profiler support, also include <code>libasyncProfiler.so</code></li>
        </ol>

        <h3>Basic Usage</h3>
        <DocsCodeBlock
          language="bash"
          :code="usageExample"
        />

        <h3>Docker Entrypoint Example</h3>
        <DocsCodeBlock
          language="bash"
          :code="dockerEntrypoint"
        />

        <h2 id="configuration">Configuration</h2>
        <p>Jeffrey CLI uses <strong>HOCON</strong> (Human-Optimized Config Object Notation) for configuration. HOCON is a superset of JSON with more readable syntax.</p>

        <h3>Minimal Configuration</h3>
        <DocsCodeBlock
          language="hocon"
          :code="minimalConfig"
        />

        <h3>Full Configuration</h3>
        <DocsCodeBlock
          language="hocon"
          :code="fullConfig"
        />

        <h3>Configuration Options</h3>
        <table>
          <thead>
            <tr>
              <th>Option</th>
              <th>Required</th>
              <th>Description</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><code>jeffrey-home</code></td>
              <td>Yes</td>
              <td>Base directory for Jeffrey data</td>
            </tr>
            <tr>
              <td><code>workspace-id</code></td>
              <td>Yes</td>
              <td>Workspace identifier (e.g., "production", "staging")</td>
            </tr>
            <tr>
              <td><code>project-name</code></td>
              <td>Yes</td>
              <td>Project name for organizing recordings</td>
            </tr>
            <tr>
              <td><code>profiler-path</code></td>
              <td>No</td>
              <td>Path to libasyncProfiler.so</td>
            </tr>
            <tr>
              <td><code>project-label</code></td>
              <td>No</td>
              <td>Human-readable project label</td>
            </tr>
            <tr>
              <td><code>repository-type</code></td>
              <td>No</td>
              <td>ASYNC_PROFILER (default) or JDK</td>
            </tr>
          </tbody>
        </table>

        <DocsCallout type="tip">
          <strong>Environment variables:</strong> HOCON supports environment variable substitution. Use <code>${VAR}</code> for required variables or <code>${?VAR}</code> for optional ones.
        </DocsCallout>

        <h2 id="features">Features</h2>
        <p>Jeffrey CLI can enable additional features that capture more diagnostic data alongside JFR recordings:</p>

        <div class="features-grid">
          <div class="feature-card perf">
            <div class="feature-icon"><i class="bi bi-speedometer2"></i></div>
            <h4>Perf Counters</h4>
            <p>Captures JVM performance data via <code>-XX:+UsePerfData</code>. Provides low-level metrics about JVM internals.</p>
            <code>perf-counters { enabled = true }</code>
          </div>
          <div class="feature-card heap">
            <div class="feature-icon"><i class="bi bi-memory"></i></div>
            <h4>Heap Dump</h4>
            <p>Automatic heap dumps on OutOfMemoryError or JVM crash. Compressed with gzip for efficient storage.</p>
            <code>heap-dump { enabled = true; type = "crash" }</code>
          </div>
          <div class="feature-card logging">
            <div class="feature-icon"><i class="bi bi-file-text"></i></div>
            <h4>JVM Logging</h4>
            <p>Structured JVM diagnostic logging including GC events, JIT compilation, and JFR activity.</p>
            <code>jvm-logging { enabled = true }</code>
          </div>
          <div class="feature-card messaging">
            <div class="feature-icon"><i class="bi bi-broadcast"></i></div>
            <h4>Messaging</h4>
            <p>Real-time JFR event streaming for live monitoring. Events are stored in a streaming repository.</p>
            <code>messaging { enabled = true; max-age = "24h" }</code>
          </div>
        </div>

        <h2 id="profiler-options">Profiler Options</h2>
        <p>Jeffrey CLI supports two profiler backends:</p>

        <div class="profiler-comparison">
          <div class="profiler-card async">
            <div class="profiler-header">
              <h4>async-profiler</h4>
              <span class="badge default">Default</span>
            </div>
            <div class="profiler-body">
              <p>Low-overhead sampling profiler with excellent accuracy:</p>
              <ul>
                <li>CPU profiling with hardware counters</li>
                <li>Allocation profiling</li>
                <li>Lock contention analysis</li>
                <li>Wall-clock profiling</li>
              </ul>
              <p><strong>Requires:</strong> <code>libasyncProfiler.so</code> native library</p>
            </div>
          </div>
          <div class="profiler-card jdk">
            <div class="profiler-header">
              <h4>JDK Flight Recorder</h4>
              <span class="badge">Built-in</span>
            </div>
            <div class="profiler-body">
              <p>JVM's built-in profiler with comprehensive event coverage:</p>
              <ul>
                <li>No native library required</li>
                <li>Broader event types (GC, I/O, exceptions)</li>
                <li>Always available in JDK 11+</li>
                <li>Lower CPU profiling accuracy</li>
              </ul>
              <p><strong>Use with:</strong> <code>repository-type = "JDK"</code></p>
            </div>
          </div>
        </div>

        <h2 id="integration">Integration with Jeffrey</h2>
        <p>Sessions created by Jeffrey CLI appear in Jeffrey's <strong>Repository</strong> feature within Live and Remote workspaces.</p>

        <h3>Workflow</h3>
        <div class="workflow-steps">
          <div class="workflow-step">
            <div class="step-number">1</div>
            <div class="step-content">
              <strong>CLI Initializes Session</strong>
              <p>Application starts with Jeffrey CLI, creating a new session directory</p>
            </div>
          </div>
          <div class="workflow-step">
            <div class="step-number">2</div>
            <div class="step-content">
              <strong>Profiler Records Data</strong>
              <p>JFR files and artifacts are written to the session directory</p>
            </div>
          </div>
          <div class="workflow-step">
            <div class="step-number">3</div>
            <div class="step-content">
              <strong>Repository Detects Session</strong>
              <p>Jeffrey's Repository page shows the session in Live/Remote workspace</p>
            </div>
          </div>
          <div class="workflow-step">
            <div class="step-number">4</div>
            <div class="step-content">
              <strong>Merge and Copy</strong>
              <p>Select specific JFR files from the session to merge into a Recording (+ download artifacts)</p>
            </div>
          </div>
          <div class="workflow-step">
            <div class="step-number">5</div>
            <div class="step-content">
              <strong>Analyze Profile</strong>
              <p>Create a Profile from the Recording and start analysis</p>
            </div>
          </div>
        </div>

        <DocsCallout type="info">
          <strong>Selective file merging:</strong> Since JFR files are created in chunks (e.g., every 15 minutes), you can select specific files to analyze particular time periods:
          <ul style="margin: 0.5rem 0 0 0; padding-left: 1.25rem;">
            <li><strong>Startup analysis</strong> - Select first few files to analyze application startup</li>
            <li><strong>Peak hours</strong> - Select files from high-traffic periods</li>
            <li><strong>Comparison</strong> - Create separate recordings from different time periods to compare profiles</li>
          </ul>
        </DocsCallout>

        <DocsCallout type="tip">
          <strong>Remote analysis recommended:</strong> For servers with limited resources, use a Remote workspace to download recordings and analyze them on your local machine. This offloads CPU-intensive profile initialization from the server.
        </DocsCallout>
      </div>

      <nav class="docs-nav-footer">
        <router-link
          v-if="adjacentPages.prev"
          :to="`/docs/${adjacentPages.prev.category}/${adjacentPages.prev.path}`"
          class="nav-link prev"
        >
          <i class="bi bi-arrow-left"></i>
          <div class="nav-text">
            <span class="nav-label">Previous</span>
            <span class="nav-title">{{ adjacentPages.prev.title }}</span>
          </div>
        </router-link>
        <div v-else class="nav-spacer"></div>
        <router-link
          v-if="adjacentPages.next"
          :to="`/docs/${adjacentPages.next.category}/${adjacentPages.next.path}`"
          class="nav-link next"
        >
          <div class="nav-text">
            <span class="nav-label">Next</span>
            <span class="nav-title">{{ adjacentPages.next.title }}</span>
          </div>
          <i class="bi bi-arrow-right"></i>
        </router-link>
      </nav>
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* Features Grid */
.features-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
  margin: 1.5rem 0;
}

.feature-card {
  padding: 1.25rem;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background: #fff;
}

.feature-card .feature-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.2rem;
  color: #fff;
  margin-bottom: 0.75rem;
}

.feature-card h4 {
  margin: 0 0 0.5rem 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: #343a40;
}

.feature-card p {
  margin: 0 0 0.75rem 0;
  font-size: 0.85rem;
  color: #5e6e82;
}

.feature-card code {
  display: block;
  font-size: 0.75rem;
  background: #f8fafc;
  padding: 0.5rem;
  border-radius: 4px;
  color: #6c757d;
}

/* Feature card themes */
.feature-card.perf .feature-icon {
  background: linear-gradient(135deg, #5e64ff 0%, #4338ca 100%);
}

.feature-card.heap .feature-icon {
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
}

.feature-card.logging .feature-icon {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
}

.feature-card.messaging .feature-icon {
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
}

/* Profiler Comparison */
.profiler-comparison {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
  margin: 1.5rem 0;
}

.profiler-card {
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
}

.profiler-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1rem;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.profiler-header h4 {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: #343a40;
}

.profiler-header .badge {
  font-size: 0.7rem;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  background: #e2e8f0;
  color: #495057;
}

.profiler-header .badge.default {
  background: linear-gradient(135deg, #5e64ff 0%, #4338ca 100%);
  color: #fff;
}

.profiler-body {
  padding: 1rem;
}

.profiler-body p {
  margin: 0 0 0.75rem 0;
  font-size: 0.85rem;
  color: #495057;
}

.profiler-body ul {
  margin: 0 0 0.75rem 0;
  padding-left: 1.25rem;
  font-size: 0.85rem;
}

.profiler-body li {
  margin-bottom: 0.25rem;
  color: #5e6e82;
}

/* Workflow Steps */
.workflow-steps {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin: 1.5rem 0;
}

.workflow-step {
  display: flex;
  gap: 1rem;
  align-items: flex-start;
}

.step-number {
  width: 32px;
  height: 32px;
  min-width: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #5e64ff 0%, #4338ca 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 0.9rem;
}

.step-content {
  flex: 1;
  padding-top: 0.25rem;
}

.step-content strong {
  display: block;
  color: #343a40;
  margin-bottom: 0.25rem;
}

.step-content p {
  margin: 0;
  font-size: 0.9rem;
  color: #5e6e82;
}

/* Session Contents Grid */
.session-contents-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
  margin: 1.5rem 0;
}

.session-content-card {
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
}

.session-content-card .card-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.6rem 0.75rem;
  color: #fff;
}

.session-content-card .card-header i {
  font-size: 1rem;
}

.session-content-card .card-header h4 {
  margin: 0;
  font-size: 0.85rem;
  font-weight: 600;
}

.session-content-card .card-body {
  padding: 0.75rem;
  background: #fff;
}

.session-content-card .card-body code {
  display: block;
  font-size: 0.75rem;
  background: #f8fafc;
  padding: 0.35rem 0.5rem;
  border-radius: 4px;
  color: #495057;
  margin-bottom: 0.5rem;
}

.session-content-card .card-body p {
  margin: 0;
  font-size: 0.8rem;
  color: #5e6e82;
  line-height: 1.4;
}

/* Session content card themes - subtle colors */
.session-content-card .card-header {
  background: #f8fafc;
  color: #495057;
  border-bottom: 1px solid #e2e8f0;
}

.session-content-card.jfr .card-header i {
  color: #5e64ff;
}

.session-content-card.heap .card-header i {
  color: #ef4444;
}

.session-content-card.logs .card-header i {
  color: #10b981;
}

.session-content-card.perf .card-header i {
  color: #f59e0b;
}

.session-content-card.metadata .card-header i {
  color: #6366f1;
}

/* Responsive */
@media (max-width: 992px) {
  .session-contents-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .features-grid,
  .profiler-comparison {
    grid-template-columns: 1fr;
  }

  .session-contents-grid {
    grid-template-columns: 1fr;
  }
}
</style>
