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
  { id: 'integration', text: 'Integration with Jeffrey', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const usageExample = `# Initialize profiling session
java -jar jeffrey-cli.jar init /path/to/config.conf > /tmp/jeffrey.env

# Source environment variables
source /tmp/jeffrey.env

# Start application with profiling (using JEFFREY_PROFILER_CONFIG)
java $JEFFREY_PROFILER_CONFIG -jar my-app.jar`;

const usageWithJdkOptions = `# With jdk-java-options enabled in config, JDK_JAVA_OPTIONS is set automatically
java -jar jeffrey-cli.jar init /path/to/config.conf > /tmp/jeffrey.env
source /tmp/jeffrey.env

# JDK_JAVA_OPTIONS is picked up automatically by the JVM
java -jar my-app.jar`;

const dockerEntrypoint = `#!/bin/bash
# Generate profiling environment
java -jar /jeffrey-libs/jeffrey-cli.jar init /app/jeffrey.conf > /tmp/jeffrey.env
source /tmp/jeffrey.env

# Start application (JDK_JAVA_OPTIONS picked up automatically)
exec java -jar /app/my-app.jar`;
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
        </div>
      </header>

      <div class="docs-content">
        <p>Jeffrey CLI is a command-line tool that <strong>configures JVM processes</strong> for profiling, especially in containerized environments.</p>

        <h2 id="what-is-jeffrey-cli">What is Jeffrey CLI?</h2>
        <p>Jeffrey CLI (<code>jeffrey-cli.jar</code>) reads a HOCON configuration file and generates environment variables containing JVM flags. These environment variables are then used by your target JVM process.</p>

        <div class="how-it-works">
          <div class="flow-step">
            <div class="flow-icon"><i class="bi bi-file-earmark-code"></i></div>
            <div class="flow-content">
              <strong>HOCON Config</strong>
              <p>Define features and settings</p>
            </div>
          </div>
          <div class="flow-arrow"><i class="bi bi-arrow-right"></i></div>
          <div class="flow-step">
            <div class="flow-icon"><i class="bi bi-terminal"></i></div>
            <div class="flow-content">
              <strong>Jeffrey CLI</strong>
              <p>Generates JVM flags</p>
            </div>
          </div>
          <div class="flow-arrow"><i class="bi bi-arrow-right"></i></div>
          <div class="flow-step">
            <div class="flow-icon"><i class="bi bi-filetype-java"></i></div>
            <div class="flow-content">
              <strong>Environment Variable</strong>
              <p>Used by target JVM</p>
            </div>
          </div>
        </div>

        <p>The CLI performs these tasks:</p>
        <ul>
          <li>Creates workspace/project/session directory structure</li>
          <li>Generates JVM flags for async-profiler and enabled features</li>
          <li>Outputs environment variables (<code>JEFFREY_PROFILER_CONFIG</code> or <code>JDK_JAVA_OPTIONS</code>)</li>
          <li>Stores session metadata for Jeffrey to detect</li>
        </ul>

        <h3>When to Use Jeffrey CLI</h3>
        <p>Use Jeffrey CLI when you want to:</p>
        <ul>
          <li><strong>Containerized environments</strong> - Configure JVM profiling in Docker/Kubernetes</li>
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
        <p>Using <code>JEFFREY_PROFILER_CONFIG</code> environment variable:</p>
        <DocsCodeBlock
          language="bash"
          :code="usageExample"
        />

        <h3>Using JDK_JAVA_OPTIONS</h3>
        <p>When <code>jdk-java-options</code> is enabled in your config, the JVM flags are set via <code>JDK_JAVA_OPTIONS</code> which is automatically picked up by the JVM:</p>
        <DocsCodeBlock
          language="bash"
          :code="usageWithJdkOptions"
        />

        <DocsCallout type="tip">
          <strong>JDK_JAVA_OPTIONS recommended:</strong> Using <code>jdk-java-options { enabled = true }</code> is the simplest approach for containers - the JVM automatically reads this environment variable without any command-line changes.
        </DocsCallout>

        <h3>Docker Entrypoint Example</h3>
        <DocsCodeBlock
          language="bash"
          :code="dockerEntrypoint"
        />

        <router-link to="/docs/cli/configuration" class="config-link-card">
          <div class="config-link-icon">
            <i class="bi bi-gear"></i>
          </div>
          <div class="config-link-content">
            <h4>Configuration</h4>
            <p>Learn about HOCON configuration files, available options, features (heap dumps, JVM logging, messaging), and profiler backends.</p>
          </div>
          <i class="bi bi-arrow-right config-link-arrow"></i>
        </router-link>

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

/* How It Works Flow */
.how-it-works {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  margin: 1.5rem 0;
  padding: 1.25rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.flow-step {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.flow-icon {
  width: 40px;
  height: 40px;
  min-width: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #5e64ff 0%, #7c3aed 100%);
  border-radius: 8px;
}

.flow-icon i {
  font-size: 1.1rem;
  color: #fff;
}

.flow-content strong {
  display: block;
  font-size: 0.85rem;
  color: #343a40;
  margin-bottom: 0.15rem;
}

.flow-content p {
  margin: 0;
  font-size: 0.75rem;
  color: #6b7280;
}

.flow-arrow {
  color: #9ca3af;
  font-size: 1.25rem;
}

@media (max-width: 768px) {
  .how-it-works {
    flex-direction: column;
  }

  .flow-arrow {
    transform: rotate(90deg);
  }
}

/* Config Link Card */
.config-link-card {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem 1.25rem;
  margin: 1.5rem 0;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
  border-left: 4px solid #5e64ff;
  border-radius: 8px;
  text-decoration: none !important;
  color: inherit;
  transition: all 0.2s ease;
}

.config-link-card:hover {
  background: linear-gradient(135deg, rgba(94,100,255,0.08) 0%, rgba(124,58,237,0.08) 100%);
  border-color: #5e64ff;
  transform: translateX(4px);
  text-decoration: none !important;
}

.config-link-icon {
  width: 48px;
  height: 48px;
  min-width: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #5e64ff 0%, #7c3aed 100%);
  border-radius: 10px;
}

.config-link-icon i {
  font-size: 1.25rem;
  color: #fff;
}

.config-link-content {
  flex: 1;
}

.config-link-content h4 {
  margin: 0 0 0.25rem 0;
  font-size: 1rem;
  font-weight: 600;
  color: #343a40;
  text-decoration: none !important;
}

.config-link-content p {
  margin: 0;
  font-size: 0.85rem;
  color: #5e6e82;
  line-height: 1.4;
  text-decoration: none !important;
}

.config-link-arrow {
  font-size: 1.25rem;
  color: #5e64ff;
  transition: transform 0.2s ease;
}

.config-link-card:hover .config-link-arrow {
  transform: translateX(4px);
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
  .session-contents-grid {
    grid-template-columns: 1fr;
  }
}
</style>
