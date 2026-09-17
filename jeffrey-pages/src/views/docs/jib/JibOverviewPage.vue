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
  { id: 'how-it-works', text: 'How It Works', level: 2 },
  { id: 'runtime-kill-switch', text: 'Runtime Kill Switch', level: 2 },
  { id: 'jar-provisioner-environment', text: 'The Jar Provisioner and the JVM Environment', level: 2 },
  { id: 'limitations', text: 'Limitations', level: 2 }
];

const provisionerJvmOptions = `env:
  - name: JEFFREY_PROVISIONER_JAVA_OPTIONS
    value: "-Xmx96m -Dfile.encoding=UTF-8"`;

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Jeffrey JIB"
        icon="bi bi-box-seam"
      />

      <div class="docs-content">
        <p><strong>Jeffrey JIB</strong> is a pair of
          <a href="https://github.com/GoogleContainerTools/jib" target="_blank" rel="noopener">JIB</a>
          plugin extensions (Gradle and Maven) that wrap the container entrypoint so Jeffrey
          profiling is initialised before the app starts &mdash; without forcing operators to
          override the container <code>command:</code> in Kubernetes YAML.</p>

        <p>This page explains how the extension works. For the full property reference see
          <router-link to="/docs/jib/configuration">Configuration</router-link>, and for the
          Gradle/Maven wiring see <router-link to="/docs/jib/setup">Build Setup</router-link>.</p>

        <DocsCallout type="info">
          <strong>Open Source Library:</strong> Jeffrey JIB lives in the Jeffrey monorepo under
          <a href="https://github.com/petrbouda/jeffrey/tree/master/utilities/jeffrey-jib" target="_blank" rel="noopener">utilities/jeffrey-jib</a>
          and is published to Maven Central as
          <code>cafe.jeffrey-analyst:jeffrey-jib-gradle</code> and
          <code>cafe.jeffrey-analyst:jeffrey-jib-maven</code>.
        </DocsCallout>

        <h2 id="how-it-works">How It Works</h2>
        <p>At image-build time, the extension modifies JIB's <code>ContainerBuildPlan</code>:</p>

        <div class="feature-list">
          <div class="feature-item">
            <i class="bi bi-check-circle-fill"></i>
            <div>Installs a small shell wrapper at <code>/usr/local/bin/jeffrey-entrypoint</code> as a new image layer.</div>
          </div>
          <div class="feature-item">
            <i class="bi bi-check-circle-fill"></i>
            <div>Replaces the image <code>ENTRYPOINT</code> with the wrapper.</div>
          </div>
          <div class="feature-item">
            <i class="bi bi-check-circle-fill"></i>
            <div>Moves JIB's auto-derived <code>java -cp @/app/jib-classpath-file &lt;MainClass&gt;</code> into <code>CMD</code>.</div>
          </div>
          <div class="feature-item">
            <i class="bi bi-check-circle-fill"></i>
            <div>Preserves JIB's main-class detection, classpath-file assembly, <code>jvmFlags</code>, base image, and target architecture.</div>
          </div>
        </div>

        <p>At container start, the wrapper runs <code>provisioner init</code> from
          <code>/opt/jeffrey</code> &mdash; where the extension installed it at build time &mdash; and then
          <code>exec</code>s the original JIB command with the provisioner-produced argfile inserted right
          after the <code>java</code> binary. Nothing is downloaded, copied or waited for at container
          start. If no provisioner is present (neither baked nor named by
          <code>JEFFREY_PROVISIONER_PATH</code>), the wrapper logs one line and starts the application
          without profiling &mdash; see <a href="#runtime-kill-switch">Runtime Kill Switch</a>.</p>

        <DocsCallout type="info">
          <strong>The image is self-contained.</strong> The extension fetches the provisioner and
          async-profiler through your build's own dependency resolution and installs them under
          <code>/opt/jeffrey</code> in their own layer. The shared volume is still needed &mdash; but only
          for the recordings your application writes to it, never to find its own tooling. Set
          <code>payloadVersion</code> to the jeffrey-jib release whose payload artifacts the image
          should carry &mdash; normally the extension's own version. The build log prints which
          Jeffrey release and async-profiler version those payloads bundle.
        </DocsCallout>

        <h2 id="runtime-kill-switch">Runtime Kill Switch</h2>
        <p><strong>Explicit opt-out.</strong> Set <code>JEFFREY_ENABLED=false</code> (or <code>0</code>,
          <code>no</code>, <code>off</code>, case-insensitive) in the container env to bypass profiling
          entirely. The wrapper skips <code>provisioner init</code>, async-profiler, and argfile
          injection, and <code>exec</code>s the JIB-produced <code>java</code> command verbatim &mdash;
          identical behaviour to a non-instrumented image, no rebuild required.</p>

        <p>Useful for emergency disablement, per-pod opt-out, dev/local runs without the shared
          volume, and A/B comparisons.</p>

        <p><strong>Implicit fallthrough (fail-open).</strong> If <code>JEFFREY_PROVISIONER_PATH</code>
          is unset at container start, the wrapper logs
          &ldquo;profiling DISABLED: JEFFREY_PROVISIONER_PATH is not set, so this image carries no
          provisioner&rdquo; and <code>exec</code>s the JIB command verbatim. The same happens when the
          binary it points at is missing or unreadable, when
          <code>JEFFREY_PROVISIONER_KIND</code> is neither <code>native</code> nor <code>jar</code>,
          and when <code>provisioner init</code> fails or produces no argfile. Misconfiguration can
          never prevent an app from booting &mdash; the worst case is profiling turning off, which
          one greppable <code>profiling DISABLED:</code> line surfaces in the pod logs.</p>

        <DocsCallout type="info">
          The &ldquo;app still starts&rdquo; guarantee holds only when a downstream command is
          actually present in the container. If the JIB CMD is missing entirely, the wrapper has
          nothing to exec and still exits non-zero &mdash; a configuration error, not a profiling
          concern.
        </DocsCallout>

        <h2 id="jar-provisioner-environment">The Jar Provisioner and the JVM Environment</h2>
        <p>With <code>provisionerSource=jar</code> the provisioner runs on the application's own
          <code>java</code>, as a short-lived second JVM before the application starts. Every JVM in the
          container reads the same environment, and three variables are honoured by any
          <code>java</code> launcher or HotSpot without being asked: <code>JDK_JAVA_OPTIONS</code> (the
          launcher, JDK&nbsp;9+), <code>JAVA_TOOL_OPTIONS</code> (every HotSpot JVM) and
          <code>_JAVA_OPTIONS</code> (every HotSpot JVM, applied last). Operators use them precisely
          because they reach the application JVM without touching the command line &mdash; which is
          also why they would reach the provisioner JVM:</p>

        <div class="feature-list feature-list-warning">
          <div class="feature-item feature-item-warning">
            <i class="bi bi-exclamation-triangle-fill"></i>
            <div><strong>A <code>-javaagent:</code> is loaded twice.</strong> An OpenTelemetry or APM
              agent in <code>JDK_JAVA_OPTIONS</code> instruments the provisioner too: slower init, and a
              second, short-lived instance of the service registering with the agent's backend on every
              pod start.</div>
          </div>
          <div class="feature-item feature-item-warning">
            <i class="bi bi-exclamation-triangle-fill"></i>
            <div><strong>Memory sized for the application is claimed by a JVM that needs 64&nbsp;MB.</strong>
              An <code>-Xmx</code> or <code>-XX:MaxRAMPercentage</code> meant for the application applies
              to the provisioner as well; with <code>-XX:+AlwaysPreTouch</code> it is touched at once and
              can push the pod over its memory limit before the application has even started.</div>
          </div>
          <div class="feature-item feature-item-warning">
            <i class="bi bi-exclamation-triangle-fill"></i>
            <div><strong>Diagnostics run twice.</strong> <code>-XX:StartFlightRecording</code>, GC logging
              or heap-dump settings produce a second set of output files from the provisioner, and
              <code>JDK_JAVA_OPTIONS</code> adds a <code>NOTE: Picked up JDK_JAVA_OPTIONS:</code> line to
              the startup log.</div>
          </div>
        </div>

        <p>For that reason the wrapper <strong>unsets all three variables for the provisioner JVM
          only</strong>, in a subshell, so the application's <code>exec</code> still sees them
          untouched. The provisioner does no network I/O and reads none of those settings, so nothing
          is lost. Options genuinely meant for the provisioner JVM go in the variable that exists for
          exactly that; it is a whitespace-separated list appended after the wrapper's own
          <code>-XX:TieredStopAtLevel=1 -XX:+UseSerialGC -Xmx64m</code>, so a later
          <code>-Xmx</code> wins:</p>

        <div class="code-block">
          <pre><code>{{ provisionerJvmOptions }}</code></pre>
        </div>

        <DocsCallout type="info">
          <strong>Two things this cannot fix.</strong> The jar is compiled for the JDK the Jeffrey
          release targets (currently 25); an older application JVM fails with
          <code>UnsupportedClassVersionError</code>, the wrapper prints a hint naming that error and
          starts the application unprofiled &mdash; use <code>native</code> there. And the
          <code>JEFFREY_*</code> variables, <code>JEFFREY_ADDITIONAL_JVM_OPTIONS</code> included, are
          <em>configuration</em> the provisioner reads and writes into the argfile for the application;
          they are never options for the provisioner's own JVM. The native provisioner is not a JVM
          and has none of these concerns.
        </DocsCallout>

        <h2 id="limitations">Limitations</h2>
        <div class="feature-list feature-list-warning">
          <div class="feature-item feature-item-warning">
            <i class="bi bi-exclamation-triangle-fill"></i>
            <div><strong>Requires a POSIX shell in the base image.</strong> True distroless images
              (<code>gcr.io/distroless/java-*</code>) lack <code>/bin/sh</code> and are incompatible.
              Use the status-quo Kubernetes <code>command:</code> pattern there.</div>
          </div>
          <div class="feature-item feature-item-warning">
            <i class="bi bi-exclamation-triangle-fill"></i>
            <div><strong>Fails the build on an unsupported target platform.</strong> Payloads exist for
              <code>linux/amd64</code> and <code>linux/arm64</code>; any other architecture, or a build
              plan with no Linux platform at all, stops the build rather than producing an image that
              cannot profile itself.</div>
          </div>
          <div class="feature-item feature-item-warning">
            <i class="bi bi-exclamation-triangle-fill"></i>
            <div><strong>Resolves the payload artifacts at build time</strong> from Maven Central, or
              whatever repositories your build is configured with &mdash; the <em>project's</em>
              repositories, not the plugin or <code>buildscript</code> ones, which matters behind split
              enterprise mirrors. An air-gapped build either mirrors the three
              <code>jeffrey-jib-payload-*</code> artifacts or points <code>provisionerPath</code> and
              <code>profilerPath</code> at binaries the base image already provides; with both set, no
              resolver is touched and no platform is checked.</div>
          </div>
        </div>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* Feature List */
.feature-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin: 1.5rem 0;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  background: #f0fdf4;
  border-radius: 8px;
  border: 1px solid #bbf7d0;
}

.feature-item i {
  color: #10b981;
  font-size: 1rem;
}

.feature-item div {
  font-size: 0.875rem;
  color: #374151;
  line-height: 1.5;
}

.feature-item strong {
  color: #1f2937;
}

.feature-item code {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.82rem;
  padding: 0.05rem 0.35rem;
  background: rgba(16, 185, 129, 0.1);
  border-radius: 4px;
}

/* Warning variant */
.feature-list-warning .feature-item-warning {
  background: #fffbeb;
  border-color: #fde68a;
}

.feature-list-warning .feature-item-warning i {
  color: #d97706;
}

.feature-list-warning .feature-item-warning code {
  background: rgba(217, 119, 6, 0.1);
}
</style>
