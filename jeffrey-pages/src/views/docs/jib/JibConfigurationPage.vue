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
  { id: 'properties', text: 'Properties', level: 2 },
  { id: 'custom-async-profiler', text: 'Using Your Own async-profiler', level: 2 },
  { id: 'build-time-vs-runtime', text: 'Build-time vs Runtime', level: 2 }
];

const customProfilerMaven = `<configuration implementation="cafe.jeffrey.jib.JeffreyJibConfig">
    <profilerPath>/opt/async-profiler/libasyncProfiler.so</profilerPath>
</configuration>`;

const customProfilerGradle = `properties = mapOf(
  "profilerPath" to "/opt/async-profiler/libasyncProfiler.so",
)`;

const customProfilerMultiArch = `"profilerPath" to "/opt/async-profiler/libasyncProfiler-{arch}.so"`;

const customProfilerRuntime = `env:
  - name: JEFFREY_PROFILER_PATH
    value: /opt/async-profiler/libasyncProfiler.so`;

const defaultAgentCommand = `-agentpath:<profiler-path>=start,alloc,lock,event=ctimer,jfrsync=default,\
loop=15m,chunksize=5m,file=<session>/profile-%t.jfr`;

const customProfilerCommand = `profiler-command = "start,event=itimer,file=<<JEFFREY:CURRENT_SESSION>>/profile-%t.jfr"`;

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="JIB Configuration"
        icon="bi bi-gear"
      />

      <div class="docs-content">
        <p>Every field has a sensible default &mdash; you only need to set a property when you want
          to override it. Non-null values are baked as image-level ENV defaults; Kubernetes
          pod-level env vars still override them at runtime. See
          <router-link to="/docs/jib/setup">Build Setup</router-link> for where these properties are
          declared in Gradle and Maven.</p>

        <h2 id="properties">Properties</h2>
        <div class="config-table">
          <table>
            <thead>
              <tr><th>Property</th><th>Image ENV</th><th>Default</th><th>Purpose</th></tr>
            </thead>
            <tbody>
              <tr>
                <td><code>enabled</code><br><span class="prop-type">boolean</span></td>
                <td>&mdash;</td>
                <td><code>true</code></td>
                <td><strong>Build-time</strong> gate. If <code>false</code>, the extension is a no-op &mdash; no wrapper layer, no entrypoint rewrite, plain JIB output.</td>
              </tr>
              <tr>
                <td><code>jeffreyHome</code></td>
                <td><code>JEFFREY_HOME</code></td>
                <td>&mdash; <span class="prop-type">(must be set)</span></td>
                <td>Root of the shared volume the application writes recordings to, under <code>&lt;home&gt;/workspaces/</code>. The provisioner also accepts <code>JEFFREY_WORKSPACES_DIR</code> as an alternative that names the recordings directory directly.</td>
              </tr>
              <tr>
                <td><code>baseConfig</code></td>
                <td><code>JEFFREY_BASE_CONFIG</code></td>
                <td><code>/jeffrey/jeffrey-base.conf</code></td>
                <td>Path to the base HOCON config inside the container.</td>
              </tr>
              <tr>
                <td><code>overrideConfig</code></td>
                <td><code>JEFFREY_OVERRIDE_CONFIG</code></td>
                <td><code>/jeffrey/jeffrey-overrides.conf</code></td>
                <td>Path to per-service override HOCON. The wrapper only passes it to <code>provisioner init</code> if the file actually exists, so it's effectively optional at runtime.</td>
              </tr>
              <tr>
                <td><code>profilerPath</code></td>
                <td><code>JEFFREY_PROFILER_PATH</code></td>
                <td>baked: <code>/opt/jeffrey/libasyncProfiler.so</code>, or <code>libasyncProfiler-&#123;arch&#125;.so</code> on a multi-platform build</td>
                <td>Explicit async-profiler path. Setting it declares that the image already provides the library, so the extension does not bake its own copy &mdash; see <a href="#custom-async-profiler">Using Your Own async-profiler</a>.</td>
              </tr>
              <tr>
                <td><code>projectName</code></td>
                <td><code>JEFFREY_PROJECT_NAME</code></td>
                <td>the Maven artifactId or Gradle project name</td>
                <td>The Jeffrey project name. It is a stable identity: it keys the project directory on the shared volume and links every session to the same project on Jeffrey Hub, so pin it here if you ever rename the module. Only the label is safe to change freely.</td>
              </tr>
              <tr>
                <td><code>argFile</code></td>
                <td><code>JEFFREY_ARG_FILE</code></td>
                <td><code>/tmp/jvm.args</code></td>
                <td>Argfile path. Must match the HOCON <code>arg-file</code> key.</td>
              </tr>
            </tbody>
          </table>
        </div>

        <DocsCallout type="info">
          <strong>The provisioner build is not a property.</strong> It is chosen by the flavour of the
          extension you declare as the plugin dependency: <code>jeffrey-jib-maven-native</code> /
          <code>jeffrey-jib-gradle-native</code> bake the GraalVM binary (~44&nbsp;MB per
          architecture, starts in milliseconds, assumes nothing of your JVM);
          <code>jeffrey-jib-maven-jar</code> / <code>jeffrey-jib-gradle-jar</code> bake the
          ~4&nbsp;MB architecture-neutral jar and run it on the application's own JVM &mdash;
          isolated from <code>JDK_JAVA_OPTIONS</code>, <code>JAVA_TOOL_OPTIONS</code> and
          <code>_JAVA_OPTIONS</code>, see
          <router-link to="/docs/jib#jar-provisioner-environment">the JVM environment</router-link>.
          Prefer <code>jar</code> for multi-architecture images: JIB layers are not per-platform, so
          <code>native</code> ships every architecture's binary in every image of the index. The
          extension records its choice in the image as <code>JEFFREY_PROVISIONER_KIND</code>. This is
          the only choice you have over the provisioner: its path is not configurable, because the
          layout it writes is the protocol Jeffrey Hub reads. The flavour's version is a jeffrey-jib
          release, not a Jeffrey one; which Jeffrey release's provisioner and which async-profiler it
          bundles is recorded in the payload and printed in the build log.
        </DocsCallout>

        <h2 id="custom-async-profiler">Using Your Own async-profiler</h2>
        <p>Jeffrey bakes the async-profiler build it was released with, but you can supply your own
          &mdash; a version you have qualified, a build with custom patches, or one your base image
          already ships. Point <code>profilerPath</code> at it. That declares <em>this image already
          has that library</em>, so the extension does not bake its own copy and the
          second one costs you nothing in image size. async-profiler is the only binary you can
          substitute this way: the provisioner has no such property, because the session layout and
          workspace events it writes are the protocol Jeffrey Hub reads, and neither side
          version-checks it.</p>

        <DocsCodeBlock
          language="xml"
          :code="customProfilerMaven"
        />

        <DocsCodeBlock
          language="kotlin"
          :code="customProfilerGradle"
        />

        <p>On a multi-architecture image, write the path once with the
          <code>&#123;arch&#125;</code> placeholder the entrypoint wrapper expands from
          <code>uname -m</code> at container start. It works for any value of the variable, not only
          the ones the extension bakes:</p>

        <DocsCodeBlock
          language="kotlin"
          :code="customProfilerMultiArch"
        />

        <p>You can also swap the library at deploy time on an image that was built with Jeffrey's,
          since a pod-level variable overrides the baked default. The image still carries the copy it
          was built with, so prefer <code>profilerPath</code> at build time when you know you will
          never use it:</p>

        <DocsCodeBlock
          language="yaml"
          :code="customProfilerRuntime"
        />

        <h3>What your build has to support</h3>
        <p>The path is only half the contract. The provisioner generates a fixed agent command, and
          your library has to accept every option in it:</p>

        <DocsCodeBlock
          language="text"
          :code="defaultAgentCommand"
        />

        <p><code>event=ctimer</code>, <code>jfrsync=default</code> and <code>chunksize</code> are the
          ones to check against an older build. Jeffrey currently pins async-profiler 4.1, and the
          build log names the exact version it baked, so a custom library of a comparable generation
          is the safe choice. If yours needs different options, replace the whole command with
          <code>profiler-command</code> rather than fighting the default. Give it the options
          alone and the provisioner passes them to the library <code>profilerPath</code> names;
          the <code>&lt;&lt;JEFFREY:CURRENT_SESSION&gt;&gt;</code> placeholder keeps it portable:</p>

        <DocsCodeBlock
          language="hocon"
          :code="customProfilerCommand"
        />

        <p>The same setting is reachable as the <code>JEFFREY_PROFILER_COMMAND</code> environment
          variable, which the extension does <em>not</em> bake &mdash; set it on the pod to change the
          agent command without rebuilding the image or mounting a ConfigMap. It takes precedence over
          a command in a configuration file. Every combination of library and command is listed under
          <router-link to="/docs/provisioner/configuration#choosing-the-profiler">Choosing the
          Profiler</router-link>.</p>

        <DocsCallout type="warning">
          <strong>A wrong library stops the application.</strong> The fail-open guarantee covers the
          provisioner, and only part of the agent. A missing provisioner is detected before the argfile
          exists, and a <code>profilerPath</code> naming a file that does not exist is caught by
          <code>provisioner init</code>, which logs a warning and leaves the agent out; either way the
          application starts unprofiled. A library that exists but is built for another architecture,
          or is unhappy with one of the options above, fails later &mdash; the argfile is already
          written, the JVM starts with an <code>-agentpath</code> it cannot load, and it exits.
          Nothing checks the path at build time. Start one container after the change before rolling
          it out.
        </DocsCallout>

        <h2 id="build-time-vs-runtime">Build-time vs Runtime</h2>
        <p>There are two layers of control. <code>enabled</code> is a <strong>build-time</strong> gate
          evaluated by the extension when the image is assembled &mdash; setting it to <code>false</code>
          produces a plain JIB image with no wrapper at all. The flavour you declare is build-time
          too: it decides what is baked, and leaves its trace in the image only as the
          <code>JEFFREY_PROVISIONER_KIND</code> the extension writes. The remaining properties become
          image-level <code>ENV</code> defaults that the entrypoint wrapper reads at container start,
          and each of those can be overridden at runtime by a pod-level environment variable of the
          same name.</p>

        <DocsCallout type="info">
          <strong>Runtime kill switch.</strong> Independently of the build-time <code>enabled</code> gate,
          you can disable profiling on an already-built image by setting
          <code>JEFFREY_ENABLED=false</code> (or <code>0</code>/<code>no</code>/<code>off</code>) on the
          pod &mdash; the wrapper then <code>exec</code>s the original <code>java</code> command verbatim.
          See <router-link to="/docs/jib">Overview &rarr; Runtime Kill Switch</router-link>.
        </DocsCallout>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* Config table */
.config-table {
  margin: 1.5rem 0;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
}

.config-table table {
  width: 100%;
  border-collapse: collapse;
  background: #fff;
}

.config-table th,
.config-table td {
  padding: 0.75rem 1rem;
  text-align: left;
  border-bottom: 1px solid #e2e8f0;
  font-size: 0.875rem;
  color: #374151;
  vertical-align: top;
}

.config-table thead th {
  background: #f8fafc;
  font-weight: 600;
  color: #1f2937;
  font-size: 0.8rem;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.config-table tbody tr:last-child td {
  border-bottom: none;
}

.config-table code {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.82rem;
  padding: 0.05rem 0.35rem;
  background: #f1f5f9;
  border-radius: 4px;
  color: #1e293b;
}

.config-table .prop-type {
  display: inline-block;
  margin-top: 0.25rem;
  font-size: 0.75rem;
  color: #64748b;
}
</style>
