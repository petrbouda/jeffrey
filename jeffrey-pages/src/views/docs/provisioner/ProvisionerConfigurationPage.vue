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
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'env-only', text: 'Environment-Only Configuration', level: 2 },
  { id: 'config-file', text: 'Configuration File', level: 2 },
  { id: 'configuration-options', text: 'Configuration Options', level: 2 },
  { id: 'features', text: 'Features', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const envOnlySetup = `# The complete environment-only setup - no config file needed:
JEFFREY_HOME=/mnt/jeffrey            # shared volume root (baked by jeffrey-jib)
JEFFREY_PROJECT_NAME=my-service      # baked by jeffrey-jib from the artifactId
JEFFREY_WORKSPACE_REF_ID=production         # optional (default: the hub's default workspace)

# Optional extras:
JEFFREY_PROJECT_LABEL="My Service"
JEFFREY_INSTANCE_NAME=instance-1     # default: HOSTNAME (= pod name), then UUID
JEFFREY_ATTRIBUTES="cluster=blue,namespace=production"
JEFFREY_HEAP_DUMP=crash              # exit | crash | off
JEFFREY_PERF_COUNTERS=true
JEFFREY_JVM_LOGGING="jfr*=trace:file=<<JEFFREY_CURRENT_SESSION>>/jfr-jvm.log"
JEFFREY_ADDITIONAL_JVM_OPTIONS="-Xmx2g"`;

const minimalConfig = `jeffrey-home = "/opt/jeffrey"
project {
    workspace-ref-id = "production"
    name = "my-service"
}`;

const fullConfig = `jeffrey-home = "/opt/jeffrey"
profiler-path = "/opt/async-profiler/libasyncProfiler.so"
agent-path = "/opt/jeffrey/libs/current/jeffrey-agent.jar"
arg-file = "/tmp/jvm.args"
project {
    workspace-ref-id = "production"
    name = "my-service"
    label = "My Service"
    instance-name = "my-service-pod-1"
}
attributes { cluster = "blue", namespace = "production" }

debug-non-safepoints { enabled = true }
perf-counters { enabled = true }
heap-dump { enabled = true, type = "crash" }
jvm-logging {
  enabled = true
  command = "jfr*=trace:file=<<JEFFREY_CURRENT_SESSION>>/jfr-jvm.log::filecount=3,filesize=5m"
}
jdk-java-options { enabled = true }
additional-jvm-options = "-Xmx2g -Xms2g -Djeffrey.logging.trace-file.path=<<JEFFREY_CURRENT_SESSION>>/jeffrey-app.log"`;
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Configuration"
        icon="bi bi-gear"
      />

      <div class="docs-content">
        <p>Jeffrey Provisioner can be configured two ways: entirely through <strong><code>JEFFREY_*</code> environment variables</strong> (the zero-file path — recommended for containers) or through a <strong>HOCON</strong> configuration file for advanced setups. Resolution order is always <strong>HOCON value &rarr; environment variable &rarr; built-in default</strong>, so a config file wins wherever it sets a value.</p>

        <h2 id="env-only">Environment-Only Configuration</h2>
        <p>When <code>provisioner init</code> runs without <code>--base-config</code> (or the jeffrey-jib entrypoint finds no config file), it configures itself from environment variables. Images built with the jeffrey-jib extension already bake <code>JEFFREY_HOME</code> and <code>JEFFREY_PROJECT_NAME</code> (derived from the Maven artifactId / Gradle project name), so the common case needs <em>no per-application configuration at all</em> beyond mounting the shared volume:</p>
        <DocsCodeBlock
          language="bash"
          :code="envOnlySetup"
        />

        <DocsCallout type="tip">
          <strong>Fail-open:</strong> any misconfiguration (missing binaries, missing project name, broken config) starts the application <em>without profiling</em> instead of preventing it from starting — look for the single <code>Jeffrey profiling ENABLED: …</code> / <code>profiling DISABLED: &lt;reason&gt;</code> line in the container log to see the outcome.
        </DocsCallout>

        <h2 id="config-file">Configuration File</h2>

        <h3>Minimal Configuration</h3>
        <p>The minimum required settings to start profiling:</p>
        <DocsCodeBlock
          language="hocon"
          :code="minimalConfig"
        />

        <h3>Full Configuration</h3>
        <p>A complete configuration with all features enabled:</p>
        <DocsCodeBlock
          language="hocon"
          :code="fullConfig"
        />

        <DocsCallout type="tip">
          <strong>Environment variables:</strong> HOCON supports environment variable substitution. Use <code>${VAR}</code> for required variables or <code>${?VAR}</code> for optional ones.
        </DocsCallout>

        <h2 id="configuration-options">Configuration Options</h2>

        <p>Several path-style settings accept an environment-variable override when the HOCON entry is left blank — useful when the value is baked into a container image (see <router-link to="/docs/jib">Jeffrey JIB</router-link>) or supplied by the orchestrator at runtime. Resolution order is always <strong>HOCON value &rarr; environment variable &rarr; built-in default</strong>.</p>

        <table>
          <thead>
            <tr>
              <th>Option</th>
              <th>Required</th>
              <th>Env override</th>
              <th>Description</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><code>jeffrey-home</code></td>
              <td>One of</td>
              <td><code>JEFFREY_HOME</code></td>
              <td>Base directory for Jeffrey data; <code>workspaces/</code> is created under it. Exactly one of <code>jeffrey-home</code> / <code>workspaces-dir</code> must be set. Falls back to the <code>JEFFREY_HOME</code> env var when neither is set in HOCON.</td>
            </tr>
            <tr>
              <td><code>workspaces-dir</code></td>
              <td>One of</td>
              <td>—</td>
              <td>Points directly at an existing workspaces directory instead of deriving it from <code>jeffrey-home</code>. Mutually exclusive with <code>jeffrey-home</code>.</td>
            </tr>
            <tr>
              <td><code>project.workspace-ref-id</code></td>
              <td>No</td>
              <td><code>JEFFREY_WORKSPACE_REF_ID</code></td>
              <td>
                Reference ID of the workspace on the target Jeffrey Hub. Optional — when
                omitted (or blank), events route to the server's default workspace
                (<code>$default</code> unless reconfigured via
                <code>jeffrey.hub.default-workspace.reference-id</code> on the server).
                If set, the value must match an existing workspace's reference ID — unknown
                IDs are dropped server-side with a warning, unless the hub enables
                <code>jeffrey.hub.workspaces.auto-create=true</code>, in which case the
                workspace is created on the first incoming event.
              </td>
            </tr>
            <tr>
              <td><code>project.name</code></td>
              <td>Yes</td>
              <td><code>JEFFREY_PROJECT_NAME</code></td>
              <td>Project name for organizing recordings. Images built with jeffrey-jib bake this env var from the Maven artifactId / Gradle project name.</td>
            </tr>
            <tr>
              <td><code>profiler-path</code></td>
              <td>No</td>
              <td><code>JEFFREY_PROFILER_PATH</code></td>
              <td>Path to <code>libasyncProfiler.so</code>. When unset, auto-resolved from <code>libs/current/libasyncProfiler-&#123;arch&#125;.so</code> under <code>jeffrey-home</code> — the <code>&#123;arch&#125;</code> suffix is detected from the JVM's <code>os.arch</code> (<code>amd64</code> or <code>arm64</code>).</td>
            </tr>
            <tr>
              <td><code>project.instance-name</code></td>
              <td>No</td>
              <td><code>JEFFREY_INSTANCE_NAME</code></td>
              <td>Instance name (defaults to <code>HOSTNAME</code> environment variable or generated UUID)</td>
            </tr>
            <tr>
              <td><code>project.label</code></td>
              <td>No</td>
              <td><code>JEFFREY_PROJECT_LABEL</code></td>
              <td>Human-readable project label</td>
            </tr>
            <tr>
              <td><code>agent-path</code></td>
              <td>No</td>
              <td><code>JEFFREY_AGENT_PATH</code></td>
              <td>Path to <code>jeffrey-agent.jar</code>. Auto-resolved from <code>libs/current/jeffrey-agent.jar</code> when <code>jeffrey-home</code> is set.</td>
            </tr>
            <tr>
              <td><code>env-file</code></td>
              <td>No</td>
              <td>—</td>
              <td>Path to write the <code>.env</code> file with shell export statements</td>
            </tr>
            <tr>
              <td><code>arg-file</code></td>
              <td>No</td>
              <td><code>JEFFREY_ARG_FILE</code></td>
              <td>Path to write the JVM arguments file (Java @argfile format, one arg per line). Defaults to <code>/tmp/jvm.args</code> — override this when running on a read-only root filesystem.</td>
            </tr>
            <tr>
              <td><code>print-env</code></td>
              <td>No</td>
              <td>—</td>
              <td>Print the <code>.env</code> file content to stdout (default: <code>false</code>)</td>
            </tr>
            <tr>
              <td><code>profiler-config</code></td>
              <td>No</td>
              <td>—</td>
              <td>Explicit async-profiler command string. Overrides both hub-pushed workspace settings and the built-in default. Supports the <code>&lt;&lt;JEFFREY_PROFILER_PATH&gt;&gt;</code> and <code>&lt;&lt;JEFFREY_CURRENT_SESSION&gt;&gt;</code> placeholders.</td>
            </tr>
            <tr>
              <td><code>repository-type</code></td>
              <td>No</td>
              <td>—</td>
              <td>Recording repository type: <code>ASYNC_PROFILER</code> (default) or <code>JDK</code>.</td>
            </tr>
            <tr>
              <td><code>additional-jvm-options</code></td>
              <td>No</td>
              <td><code>JEFFREY_ADDITIONAL_JVM_OPTIONS</code></td>
              <td>Extra JVM flags appended to the generated arguments. Supports the <code>&lt;&lt;JEFFREY_CURRENT_SESSION&gt;&gt;</code> placeholder.</td>
            </tr>
            <tr>
              <td><code>provisioner-verbose</code></td>
              <td>No</td>
              <td><code>JEFFREY_PROVISIONER_VERBOSE</code></td>
              <td>Enables DEBUG logging of the provisioner itself (accepts <code>1</code>/<code>true</code>/<code>yes</code>/<code>on</code>).</td>
            </tr>
            <tr>
              <td><code>attributes</code></td>
              <td>No</td>
              <td><code>JEFFREY_ATTRIBUTES</code></td>
              <td>Custom key-value metadata (e.g., cluster, namespace). Env form: <code>key=value,key=value</code></td>
            </tr>
            <tr>
              <td><code>perf-counters.enabled</code></td>
              <td>No</td>
              <td><code>JEFFREY_PERF_COUNTERS</code></td>
              <td>Save JVM performance counters into the session directory</td>
            </tr>
            <tr>
              <td><code>heap-dump</code></td>
              <td>No</td>
              <td><code>JEFFREY_HEAP_DUMP</code></td>
              <td>Heap dump on OutOfMemoryError. Env form: <code>exit</code> | <code>crash</code> | <code>off</code></td>
            </tr>
            <tr>
              <td><code>jvm-logging.command</code></td>
              <td>No</td>
              <td><code>JEFFREY_JVM_LOGGING</code></td>
              <td>JVM unified logging command (<code>-Xlog:&lt;command&gt;</code>); a non-blank env value enables the feature</td>
            </tr>
          </tbody>
        </table>

        <DocsCallout type="info">
          <strong>Architecture detection:</strong> the auto-resolved <code>profiler-path</code> reads <code>os.arch</code> at startup and looks for <code>libasyncProfiler-amd64.so</code> on x86_64 or <code>libasyncProfiler-arm64.so</code> on aarch64. On any other architecture (e.g. <code>ppc64le</code>, <code>s390x</code>) the provisioner logs a warning and skips profiler setup — <strong>your application still starts</strong>, just without async-profiler attached. Set <code>profiler-path</code> explicitly if you have a custom build.
        </DocsCallout>

        <h2 id="features">Features</h2>
        <p>Jeffrey Provisioner can enable additional features that capture more diagnostic data alongside JFR recordings:</p>

        <div class="features-grid">
          <div class="feature-card debug-safepoints">
            <div class="feature-icon"><i class="bi bi-bullseye"></i></div>
            <h4>Debug Non-Safepoints</h4>
            <p>Enables precise profiling by recording method information at non-safepoint locations. Provides more accurate stack traces for CPU profiling. Enabled by default.</p>
            <code>debug-non-safepoints { enabled = true }</code>
          </div>
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
            <p>Structured JVM diagnostic logging including GC events, JIT compilation, and JFR activity. Files with <code>-jvm.log</code> suffix are automatically recognized as JVM log artifacts. Use <code>&lt;&lt;JEFFREY_CURRENT_SESSION&gt;&gt;</code> placeholder in the command for the session directory path.</p>
            <code>jvm-logging { enabled = true }</code>
          </div>
          <div class="feature-card heartbeat">
            <div class="feature-icon"><i class="bi bi-heart-pulse"></i></div>
            <h4>Heartbeat &amp; Clean-Exit Marker</h4>
            <p>Automatic whenever <code>agent-path</code> resolves — not a configuration block. The Jeffrey Agent writes <code>.heartbeat/heartbeat</code> (epoch millis) into the session directory every 5 seconds, and a <code>.heartbeat/finished</code> marker on clean JVM shutdown, so the hub detects finished sessions immediately and falls back to heartbeat staleness only after crashes.</p>
            <code>agent-path = ".../jeffrey-agent.jar"</code>
          </div>
          <div class="feature-card streaming">
            <div class="feature-icon"><i class="bi bi-arrow-repeat"></i></div>
            <h4>JFR Streaming Repository</h4>
            <p>Automatic whenever <code>agent-path</code> resolves. The JVM is started with <code>-XX:FlightRecorderOptions=repository=&lt;session&gt;/streaming-repo</code> so the hub can stream live JFR events from the session directory.</p>
            <code>-XX:FlightRecorderOptions=repository=...</code>
          </div>
          <div class="feature-card jdk-options">
            <div class="feature-icon"><i class="bi bi-gear-wide-connected"></i></div>
            <h4>JDK Java Options</h4>
            <p>Exports <code>JDK_JAVA_OPTIONS</code> environment variable. The JVM picks this up automatically.</p>
            <code>jdk-java-options { enabled = true }</code>
          </div>
          <div class="feature-card jdk-options">
            <div class="feature-icon"><i class="bi bi-plus-circle"></i></div>
            <h4>Additional JVM Options</h4>
            <p>Extra JVM flags added to the argfile and profiler settings, independent of <code>JDK_JAVA_OPTIONS</code> export.</p>
            <code>additional-jvm-options = "-Xmx2g -Xms2g"</code>
          </div>
        </div>

        <DocsCallout type="info">
          <strong>Path placeholders:</strong> Use <code>&lt;&lt;JEFFREY_CURRENT_SESSION&gt;&gt;</code> in configuration values to reference the session directory path. This is replaced at runtime with the actual path.
        </DocsCallout>
      </div>

      <DocsNavFooter />
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

.feature-card.debug-safepoints .feature-icon {
  background: linear-gradient(135deg, #06b6d4 0%, #0891b2 100%);
}

.feature-card.streaming .feature-icon {
  background: linear-gradient(135deg, #14b8a6 0%, #0d9488 100%);
}

.feature-card.heartbeat .feature-icon {
  background: linear-gradient(135deg, #ec4899 0%, #db2777 100%);
}

.feature-card.jdk-options .feature-icon {
  background: linear-gradient(135deg, #8b5cf6 0%, #6d28d9 100%);
}

/* Responsive */
@media (max-width: 768px) {
  .features-grid {
    grid-template-columns: 1fr;
  }
}
</style>
