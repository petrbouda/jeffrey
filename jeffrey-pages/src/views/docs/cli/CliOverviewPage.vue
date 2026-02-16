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
import DocsLinkCard from '@/components/docs/DocsLinkCard.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

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
      <DocsPageHeader
        title="Jeffrey CLI"
        icon="bi bi-terminal"
      />

      <div class="docs-content">
        <p>Jeffrey CLI is a command-line tool that <strong>configures JVM processes</strong> for profiling, especially in containerized environments.</p>

        <h2 id="what-is-jeffrey-cli">What is Jeffrey CLI?</h2>
        <p>Jeffrey CLI (<code>jeffrey-cli.jar</code>) reads a HOCON configuration file and generates environment variables containing JVM flags. These environment variables are then used by your target JVM process.</p>

        <div class="how-it-works">
          <div class="flow-step">
            <div class="docs-icon docs-icon-md docs-icon-purple">
              <i class="bi bi-file-earmark-code"></i>
            </div>
            <div class="flow-content">
              <strong>HOCON Config</strong>
              <p>Define features and settings</p>
            </div>
          </div>
          <div class="flow-arrow"><i class="bi bi-arrow-right"></i></div>
          <div class="flow-step">
            <div class="docs-icon docs-icon-md docs-icon-purple">
              <i class="bi bi-terminal"></i>
            </div>
            <div class="flow-content">
              <strong>Jeffrey CLI</strong>
              <p>Generates JVM flags</p>
            </div>
          </div>
          <div class="flow-arrow"><i class="bi bi-arrow-right"></i></div>
          <div class="flow-step">
            <div class="docs-icon docs-icon-md docs-icon-purple">
              <i class="bi bi-filetype-java"></i>
            </div>
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

        <p>Each application <strong>instance</strong> (identified by hostname or a custom <code>project.instance-id</code>) creates its own directory within the project. Sessions are nested inside instances — each restart creates a new session within the same instance directory.</p>

        <DocsCallout type="info">
          <strong>New deployment = New session:</strong> Every time your container starts or application restarts, Jeffrey CLI creates a fresh session within the instance directory. The same instance can accumulate multiple sessions over time, making it easy to track the history of a specific application instance.
        </DocsCallout>

        <h3>Session Contents</h3>
        <p>Each session directory can contain multiple file types:</p>

        <div class="docs-grid docs-grid-3">
          <div class="docs-card session-card jfr">
            <div class="docs-card-header">
              <i class="bi bi-file-earmark-binary"></i>
              <h4>JFR Files</h4>
            </div>
            <div class="docs-card-body">
              <code>profile-*.jfr</code>
              <p>Core profiling data with CPU samples, allocations, locks, and JVM events. Multiple chunk files are created during long-running sessions.</p>
            </div>
          </div>
          <div class="docs-card session-card heap">
            <div class="docs-card-header">
              <i class="bi bi-memory"></i>
              <h4>Heap Dump</h4>
            </div>
            <div class="docs-card-body">
              <code>heap-dump.hprof.gz</code>
              <p>Memory snapshot captured on OutOfMemoryError or JVM crash. Compressed with gzip for efficient storage.</p>
            </div>
          </div>
          <div class="docs-card session-card logs">
            <div class="docs-card-header">
              <i class="bi bi-file-text"></i>
              <h4>JVM Logs</h4>
            </div>
            <div class="docs-card-body">
              <code>jfr-jvm.log</code>
              <p>Structured JVM diagnostic logs including GC events, JIT compilation activity, and JFR-related messages.</p>
            </div>
          </div>
          <div class="docs-card session-card perf">
            <div class="docs-card-header">
              <i class="bi bi-speedometer2"></i>
              <h4>Perf Counters</h4>
            </div>
            <div class="docs-card-body">
              <code>perf-counters.hsperfdata</code>
              <p>JVM performance data captured via <code>-XX:+UsePerfData</code>. Contains low-level metrics about JVM internals. Also serves as a finisher file.</p>
            </div>
          </div>
          <div class="docs-card session-card hotspot-err">
            <div class="docs-card-header">
              <i class="bi bi-exclamation-triangle"></i>
              <h4>HotSpot Error Log</h4>
            </div>
            <div class="docs-card-body">
              <code>hs-jvm-err.log</code>
              <p>HotSpot JVM error log captured on JVM crash. Also serves as a finisher file for automatic session completion detection.</p>
            </div>
          </div>
          <div class="docs-card session-card metadata">
            <div class="docs-card-header">
              <i class="bi bi-info-circle"></i>
              <h4>Session Metadata</h4>
            </div>
            <div class="docs-card-body">
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

        <DocsLinkCard
          to="/docs/cli/configuration"
          icon="bi bi-gear"
          title="Configuration"
          description="Learn about HOCON configuration files, available options, features (heap dumps, JVM logging, messaging), and profiler backends."
        />

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

      <DocsNavFooter />
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

/* Session Cards */
.session-card .docs-card-body code {
  display: block;
  font-size: 0.75rem;
  background: #f8fafc;
  padding: 0.35rem 0.5rem;
  border-radius: 4px;
  color: #495057;
  margin-bottom: 0.5rem;
}

.session-card .docs-card-body p {
  margin: 0;
  font-size: 0.8rem;
  color: #5e6e82;
  line-height: 1.4;
}

.session-card.jfr .docs-card-header i { color: #5e64ff; }
.session-card.heap .docs-card-header i { color: #ef4444; }
.session-card.logs .docs-card-header i { color: #10b981; }
.session-card.perf .docs-card-header i { color: #f59e0b; }
.session-card.hotspot-err .docs-card-header i { color: #dc2626; }
.session-card.metadata .docs-card-header i { color: #6366f1; }

/* Responsive */
@media (max-width: 768px) {
  .how-it-works {
    flex-direction: column;
  }

  .flow-arrow {
    transform: rotate(90deg);
  }
}
</style>
