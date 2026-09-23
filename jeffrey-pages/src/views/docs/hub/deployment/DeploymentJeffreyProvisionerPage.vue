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
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'init-flow', text: 'Init Flow at Container Start', level: 2 },
  { id: 'env-vars', text: 'Required Environment Variables', level: 2 },
  { id: 'base-conf', text: 'Anatomy of jeffrey-base.conf', level: 2 },
  { id: 'project-block', text: 'Project Block', level: 2 },
  { id: 'capture-blocks', text: 'Capture Blocks', level: 2 },
  { id: 'jvm-options', text: 'Additional JVM Options', level: 2 },
  { id: 'omitted-paths', text: "Why Paths Are Omitted", level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const envBlock = `# helm/jeffrey-testapp-server/templates/deployment.yaml
env:
  - name: SPRING_CONFIG_ADDITIONAL_LOCATION
    value: /app/config/application.properties
  - name: JEFFREY_HOME
    value: {{ .Values.sharedVolume.mountPath | quote }}     # /mnt/jeffrey
  - name: JEFFREY_BASE_CONFIG
    value: {{ .Values.jeffrey.baseConfigPath | quote }}     # /jeffrey/jeffrey-base.conf
  - name: JEFFREY_ENABLED
    value: {{ .Values.jeffrey.enabled | quote }}            # "true"
  - name: JEFFREY_TESTAPP_MODE
    value: {{ .Values.mode | quote }}                       # "direct" or "dom"`;

const loggingEnv = `# helm/jeffrey-hub/templates/deployment.yaml
# Jeffrey Hub itself, running under the provisioner. Everything after -XX:+AlwaysPreTouch is
# application configuration forwarded as-is: jeffrey.hub.* are the Hub's, logging.* are Spring Boot's.
env:
  - name: JEFFREY_ENABLED
    value: "true"
  - name: JEFFREY_ADDITIONAL_JVM_OPTIONS
    value: >-
      -Xmx300m -Xms300m -XX:+UseG1GC -XX:+AlwaysPreTouch
      -Djeffrey.hub.home.dir=<<ENV:JEFFREY_HOME>>
      -Dlogging.level.cafe.jeffrey=TRACE
      -Dlogging.threshold.console=INFO
      -Dlogging.file.name=<<JEFFREY:CURRENT_SESSION>>/jeffrey-app.log
      -Dlogging.logback.rollingpolicy.max-file-size=10MB
      -Dlogging.logback.rollingpolicy.total-size-cap=100MB
      -Dlogging.logback.rollingpolicy.max-history=2
      -Djeffrey.hub.workspaces.auto-create=true`;

const baseConf = `# helm/jeffrey-testapp-server/jeffrey-base.conf
# Read by \`provisioner init\` at container start (invoked by the jeffrey-jib entrypoint
# wrapper). The provisioner and async-profiler are baked into the image under
# /opt/jeffrey by the jeffrey-jib build extension.

# jeffrey-home and profiler-path are intentionally omitted:
# provisioner reads jeffrey-home from the JEFFREY_HOME env var (set by the
# Deployment from sharedVolume.mountPath) and names where recordings are written;
# profiler-path arrives as JEFFREY_PROFILER_PATH, baked by jeffrey-jib.

project {
    name  = \${JEFFREY_TESTAPP_MODE}"-jeffrey-testapp-server"
    label = "Jeffrey TestApp Server ("\${JEFFREY_TESTAPP_MODE}")"
}

perf-counters {
    enabled = true
}

heap-dump {
    enabled = true
    type = "crash"
}

additional-jvm-options = "-Xmx400m -Xms400m -XX:+UseG1GC -XX:+AlwaysPreTouch -Xlog:gc*=debug:file=<<JEFFREY:CURRENT_SESSION>>/gc.jvm-log:time,uptime,level,tags:filecount=3,filesize=20m"`;

const projectBlock = `project {
    name  = \${JEFFREY_TESTAPP_MODE}"-jeffrey-testapp-server"
    label = "Jeffrey TestApp Server ("\${JEFFREY_TESTAPP_MODE}")"
}`;

const configMapMount = `# In the testapp-server Deployment template
volumeMounts:
  - name: jeffrey-base-config
    mountPath: {{ .Values.jeffrey.baseConfigPath | dir }}    # /jeffrey
    readOnly: true

volumes:
  - name: jeffrey-base-config
    configMap:
      name: {{ include "jeffrey-testapp-server.fullname" . }}-jeffrey-base
      items:
        - key: jeffrey-base.conf
          path: {{ .Values.jeffrey.baseConfigPath | base }}  # jeffrey-base.conf`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Jeffrey Provisioner"
      icon="bi bi-terminal"
    />

    <div class="docs-content">
      <p>
        At container start, the
        <router-link to="/docs/hub/deployment/jeffrey-jib">JIB-wrapped entrypoint</router-link>
        runs <code>provisioner init</code> against the per-pod
        <code>jeffrey-base.conf</code>. The provisioner resolves paths against the
        <router-link to="/docs/hub/deployment/shared-volume">shared volume</router-link>,
        builds the JVM-arg response file, and hands control to <code>java</code>. This
        page documents the contract: which environment variables the provisioner expects, which
        ConfigMap-mounted file it reads, and what each block of that file does.
      </p>

      <h2 id="init-flow">Init Flow at Container Start</h2>
      <ol>
        <li>The wrapped entrypoint checks <code>JEFFREY_ENABLED</code>. If it is set to <code>false</code>, <code>0</code>, <code>no</code> or <code>off</code> (case-insensitive), it skips profiling entirely and <code>exec</code>s the original <code>java</code> command. Anything else, including leaving it unset, means profiling stays on.</li>
        <li>It reads <code>JEFFREY_BASE_CONFIG</code> (the path to the HOCON config inside the container).</li>
        <li>It reads <code>JEFFREY_PROVISIONER_PATH</code>, baked by the JIB extension to point at <code>/opt/jeffrey</code> in this image. On a multi-architecture image the path carries an <code>&#123;arch&#125;</code> placeholder the wrapper expands from <code>uname -m</code>.</li>
        <li>It calls <code>provisioner init</code>, which reads the HOCON config, takes the profiler path from <code>JEFFREY_PROFILER_PATH</code>, and emits a JVM-arg response file.</li>
        <li>It launches the JVM with <code>java @&lt;response-file&gt; @/app/jib-classpath-file &lt;MainClass&gt;</code>. The response file injects the <code>-agentpath</code> for async-profiler, the <code>-Djeffrey.heartbeat.*</code> properties, the <code>additional-jvm-options</code>, and the per-feature flags (heap-dump, perf-counters).</li>
      </ol>

      <h2 id="env-vars">Required Environment Variables</h2>
      <p>Three variables wire the provisioner into the pod. Every monitored Deployment in the testapp sets them identically:</p>

      <table>
        <thead>
          <tr>
            <th>Variable</th>
            <th>Default in chart</th>
            <th>Purpose</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>JEFFREY_ENABLED</code></td>
            <td><code>"true"</code></td>
            <td>Master toggle. Setting it to <code>"false"</code> bypasses profiling entirely without rebuilding the image.</td>
          </tr>
          <tr>
            <td><code>JEFFREY_HOME</code></td>
            <td><code>/mnt/jeffrey</code> (= <code>sharedVolume.mountPath</code>)</td>
            <td>Root of the shared volume, under which the application writes its recordings to <code>${JEFFREY_HOME}/workspaces/</code>. It no longer locates any binary — those are in the image.</td>
          </tr>
          <tr>
            <td><code>JEFFREY_BASE_CONFIG</code></td>
            <td><code>/jeffrey/jeffrey-base.conf</code></td>
            <td>Path inside the container to the HOCON config (mounted via ConfigMap).</td>
          </tr>
        </tbody>
      </table>

      <p>From
        <a href="https://github.com/petrbouda/jeffrey-testapp/blob/main/helm/jeffrey-testapp-server/templates/deployment.yaml" target="_blank" rel="noopener">
          <code>helm/jeffrey-testapp-server/templates/deployment.yaml</code></a>:
      </p>

      <DocsCodeBlock
        language="yaml"
        :code="envBlock"
      />

      <DocsCallout type="info">
        <strong><code>JEFFREY_TESTAPP_MODE</code></strong> is testapp-specific —
        not part of the provisioner contract. It's referenced from <code>jeffrey-base.conf</code>
        via HOCON variable substitution to produce two distinct project names from one
        chart.
      </DocsCallout>

      <h2 id="base-conf">Anatomy of jeffrey-base.conf</h2>
      <p>From
        <a href="https://github.com/petrbouda/jeffrey-testapp/blob/main/helm/jeffrey-testapp-server/jeffrey-base.conf" target="_blank" rel="noopener">
          <code>helm/jeffrey-testapp-server/jeffrey-base.conf</code></a>
        — the entire file, annotated below:
      </p>

      <DocsCodeBlock
        language="hocon"
        :code="baseConf"
      />

      <p>The file is delivered as a ConfigMap and mounted at the path that
        <code>JEFFREY_BASE_CONFIG</code> points to:</p>

      <DocsCodeBlock
        language="yaml"
        :code="configMapMount"
      />

      <h2 id="project-block">Project Block</h2>
      <p>
        Controls how the workload appears in Jeffrey Hub. The
        <code>name</code> is the logical identifier (used as the project key in the
        catalog DB); the <code>label</code> is the human-readable display string.
      </p>

      <DocsCodeBlock
        language="hocon"
        :code="projectBlock"
      />

      <p>
        The <code>${JEFFREY_TESTAPP_MODE}</code> reference is HOCON variable
        substitution — it resolves at <code>provisioner init</code> time against the
        environment. With <code>JEFFREY_TESTAPP_MODE=direct</code> the project becomes
        <code>direct-jeffrey-testapp-server</code>; with <code>JEFFREY_TESTAPP_MODE=dom</code>
        it becomes <code>dom-jeffrey-testapp-server</code>. One chart, two distinct
        projects in Jeffrey Hub, side by side for differential analysis.
      </p>

      <h2 id="capture-blocks">Capture Blocks</h2>
      <p>Each capture feature toggles independently:</p>

      <h3>perf-counters</h3>
      <p>
        Captures JVM perfdata counters (<code>hsperfdata</code>) every cycle and bundles
        them with the recording. Useful for cross-referencing GC / safepoint / classloading
        rates without reading them out of the JFR stream.
      </p>

      <h3>heap-dump</h3>
      <p>
        <code>type = "crash"</code> writes a heap dump only when the JVM crashes (via
        <code>-XX:HeapDumpOnOutOfMemoryError</code> + crash hooks). Set
        <code>type = "always"</code> for opt-in dump-per-cycle, or <code>enabled =
        false</code> to disable entirely.
      </p>

      <h2 id="jvm-options">Additional JVM Options</h2>
      <p>
        <code>additional-jvm-options</code> is appended verbatim to the JVM launch line —
        it's where the testapp pins heap size, GC choice, JVM unified logging
        (<code>-Xlog:…</code>), and any other JVM flags. The
        same placeholder system applies (<code>&lt;&lt;JEFFREY:CURRENT_SESSION&gt;&gt;</code>,
        <code>&lt;&lt;ENV:NAME&gt;&gt;</code>, etc. — see the
        <router-link to="/docs/provisioner/configuration#placeholders">placeholder reference</router-link>),
        so each <code>-Xlog</code> file lands in the per-cycle session directory. End the file name
        with <code>.jvm-log</code> and Jeffrey picks it up as a JVM log artifact.
      </p>

      <p>
        The same value can be set straight on the Deployment as
        <code>JEFFREY_ADDITIONAL_JVM_OPTIONS</code>. Because the provisioner appends the string
        verbatim and resolves placeholders in it, this is also how an application's own configuration
        reaches it and how that configuration can name the session directory — which is the usual way
        to put a log file beside that run's recordings. Jeffrey Hub is provisioned like any other Java
        application, so it serves as the example. Its image ships with
        <code>JEFFREY_ENABLED=false</code> baked in; the pod below opts in by setting it to
        <code>true</code>, and because the Hub carries the
        <router-link to="/docs/agent/heartbeat-library">heartbeat library</router-link>, a
        <code>heartbeat.enabled = true</code> declared for it is honoured rather than finishing the
        Hub's own session seconds after it starts:
      </p>

      <DocsCodeBlock
        language="yaml"
        :code="loggingEnv"
      />

      <p>
        The <code>logging.*</code> flags are <strong>Spring Boot's, not the provisioner's</strong> —
        it has no logging options of its own to offer an application and does not interpret the string
        at all. They apply because Jeffrey Hub is a Spring Boot application:
        <code>logging.file.name</code> adds Boot's rolling file appender, and
        <code>logging.threshold.console</code> holds the console at <code>INFO</code> while the level
        is <code>TRACE</code>. An application on another framework uses that framework's properties in
        exactly the same slot. See
        <router-link to="/docs/hub/configuration#logging">Hub &rarr; Configuration &rarr; Logging</router-link>
        for what each one does.
      </p>

      <DocsCallout type="tip">
        <code>additional-jvm-options</code> composes with the profiler agent flags
        <code>provisioner</code> injects automatically, so keep it to the application's own
        concerns — heap sizing, GC choice, logging. Do not tweak the profiler here; the agent
        command is its own setting, <code>profiler-command</code> (or the
        <code>JEFFREY_PROFILER_COMMAND</code> environment variable). Assemble one with
        <router-link to="/docs/microscope/profiler-builder">Profiler Builder</router-link>.
      </DocsCallout>

      <h2 id="omitted-paths">Why Paths Are Omitted</h2>
      <p>
        The conf file deliberately leaves out <code>jeffrey-home</code> and
        <code>profiler-path</code>. That's because:
      </p>

      <ul>
        <li><code>jeffrey-home</code> is read from the <code>JEFFREY_HOME</code> env var (set by the chart from <code>sharedVolume.mountPath</code>).</li>
        <li><code>profiler-path</code> comes from <code>JEFFREY_PROFILER_PATH</code>, baked by the JIB extension at <code>/opt/jeffrey/libasyncProfiler.so</code>.</li>
        <li>The provisioner itself is found the same way, through <code>JEFFREY_PROVISIONER_PATH</code>.</li>
      </ul>

      <p>
        Because the JIB extension bakes both paths at image build time, the conf file stays small
        and the same template works for every monitored pod. Each image pins its binaries through
        the version of the jeffrey-jib flavour it was built with (whose payload records the Jeffrey
        release it bundles), so two applications can run different provisioner versions side by
        side against the same Hub.
      </p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
