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
  { id: 'configuration', text: 'Configuration', level: 2 },
  { id: 'runtime-kill-switch', text: 'Runtime Kill Switch', level: 2 },
  { id: 'gradle-setup', text: 'Gradle Setup', level: 2 },
  { id: 'maven-setup', text: 'Maven Setup', level: 2 },
  { id: 'limitations', text: 'Limitations', level: 2 }
];

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

        <p>At container start, the wrapper runs <code>jeffrey-cli init</code> &mdash; resolved from
          <code>${JEFFREY_HOME}/libs/current/jeffrey-cli-&lt;arch&gt;</code> on a shared volume populated by
          Jeffrey Server's <code>copy-libs</code> feature &mdash; and then <code>exec</code>s the original JIB
          command with the CLI-produced argfile inserted right after the <code>java</code> binary.
          If the shared-volume root is not configured at runtime (neither <code>JEFFREY_HOME</code> nor
          <code>JEFFREY_CLI_PATH</code> is set), the wrapper logs a warning and skips init entirely &mdash;
          see <a href="#runtime-kill-switch">Runtime Kill Switch</a>.</p>

        <DocsCallout type="info">
          <strong>Why a shared volume?</strong> The extension does <strong>not</strong> bake Jeffrey
          binaries into your image. It relies on a Jeffrey Server running elsewhere in the cluster
          with <code>copy-libs.enabled=true</code> to populate the shared <code>jeffrey-home</code>
          volume that your app pods also mount. Keeps the extension JAR tiny and versioning automatic.
        </DocsCallout>

        <h2 id="configuration">Configuration</h2>
        <p>Every field has a sensible default &mdash; you only need to set a property when you want
          to override it. Non-null values are baked as image-level ENV defaults; Kubernetes
          pod-level env vars still override them at runtime.</p>

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
                <td>Shared-volume root. Wrapper resolves the CLI at <code>&lt;home&gt;/libs/current/jeffrey-cli-&lt;arch&gt;</code>. If neither this nor <code>cliPath</code> is set, the wrapper warns and falls through &mdash; the container still starts, just without profiling.</td>
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
                <td>Path to per-service override HOCON. The wrapper only passes it to <code>jeffrey-cli init</code> if the file actually exists, so it's effectively optional at runtime.</td>
              </tr>
              <tr>
                <td><code>cliPath</code></td>
                <td><code>JEFFREY_CLI_PATH</code></td>
                <td>derived from <code>jeffreyHome</code></td>
                <td>Explicit CLI binary path. Bypasses the <code>&lt;home&gt;/libs/current/*</code> resolution when you bundle the CLI into your image yourself.</td>
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

        <h2 id="runtime-kill-switch">Runtime Kill Switch</h2>
        <p><strong>Explicit opt-out.</strong> Set <code>JEFFREY_ENABLED=false</code> (or <code>0</code>,
          <code>no</code>, <code>off</code>, case-insensitive) in the container env to bypass profiling
          entirely. The wrapper skips <code>jeffrey-cli init</code>, async-profiler, and argfile
          injection, and <code>exec</code>s the JIB-produced <code>java</code> command verbatim &mdash;
          identical behaviour to a non-instrumented image, no rebuild required.</p>

        <p>Useful for emergency disablement, per-pod opt-out, dev/local runs without the shared
          volume, and A/B comparisons.</p>

        <p><strong>Implicit fallthrough (fail-open).</strong> If neither <code>JEFFREY_HOME</code> nor
          <code>JEFFREY_CLI_PATH</code> is set at container start, the wrapper logs a warning to
          stderr (&ldquo;Jeffrey is disabled, starting application without profiling&rdquo;) and
          <code>exec</code>s the JIB command verbatim. Misconfiguration can never prevent an app
          from booting &mdash; the worst case is profiling silently turning off, which the warning
          surfaces in the pod logs.</p>

        <DocsCallout type="info">
          The &ldquo;app still starts&rdquo; guarantee holds only when a downstream command is
          actually present in the container. If the JIB CMD is missing entirely, the wrapper has
          nothing to exec and still exits non-zero &mdash; a configuration error, not a profiling
          concern.
        </DocsCallout>

        <h2 id="gradle-setup">Gradle Setup</h2>
        <p>Add the extension as a dependency of the JIB Gradle plugin, then reference it from
          <code>pluginExtensions</code>. The one property that must always be reachable is
          <code>jeffreyHome</code> &mdash; either baked as an image <code>ENV</code> default at
          build time (shown below) or provided at runtime via a <code>JEFFREY_HOME</code> env var
          on the pod. If neither is set, the wrapper logs a warning and starts the app without
          profiling.</p>

        <div class="code-block">
          <pre><code>jib {
  pluginExtensions {
    pluginExtension {
      implementation = "cafe.jeffrey.jib.gradle.JeffreyJibGradleExtension"
      properties = mapOf(
        "jeffreyHome" to "/shared/disk/jeffrey",
      )
    }
  }
}</code></pre>
        </div>

        <p>This builds an image whose wrapper resolves the CLI from
          <code>${JEFFREY_HOME}/libs/current/jeffrey-cli-&lt;arch&gt;</code> on the shared volume you
          mount at that path. Every other property has a sensible default; you only set them to
          override.</p>

        <DocsCallout type="warning">
          <strong><code>jeffreyHome</code> must point at a shared volume / disk.</strong>
          Jeffrey Server (with <code>copy-libs.enabled=true</code>) writes the CLI binaries and
          libs to this path, and every monitored application pod must mount the <em>same</em>
          volume at the <em>same</em> path so its entrypoint wrapper can resolve
          <code>${JEFFREY_HOME}/libs/current/jeffrey-cli-&lt;arch&gt;</code> at container start. A
          host-local directory or a per-pod ephemeral volume will not work &mdash; both endpoints
          need to see the bytes Jeffrey Server published.
        </DocsCallout>

        <p>Add explicit overrides via the string <code>properties</code> DSL &mdash; for example,
          gating via a Gradle property and pointing at a non-default shared volume. Referencing the
          key constants on <code>JeffreyJibConfig</code> keeps the build file typo-proof and
          auto-completable in the IDE:</p>

        <div class="code-block">
          <pre><code>import cafe.jeffrey.jib.JeffreyJibConfig
import cafe.jeffrey.jib.gradle.JeffreyJibGradleExtension

jib {
  pluginExtensions {
    pluginExtension {
      implementation = JeffreyJibGradleExtension::class.java.name
      properties = mapOf(
        JeffreyJibConfig.JEFFREY_HOME to "/shared/disk/jeffrey",
        JeffreyJibConfig.OVERRIDE_CONFIG to "/jeffrey/jeffrey-overrides.conf",
      )
    }
  }
}</code></pre>
        </div>

        <p>The string form (<code>"enabled"</code>, <code>"jeffreyHome"</code>, …) works just as
          well and avoids the imports if you prefer a zero-dependency build script. Both are
          equivalent at runtime.</p>

        <DocsCallout type="info">
          <strong>Why the <code>properties</code> DSL?</strong> It works on every Gradle version
          JIB supports. The typed <code>configuration(Action&lt;JeffreyJibConfig&gt;) { … }</code> form
          is also accepted but is fragile across Gradle versions &mdash; prefer
          <code>properties</code> for portability.
        </DocsCallout>

        <p>Add the artifact to your build-script classpath (<code>buildscript.dependencies</code> or
          the <code>jib</code> plugin's <code>dependencies</code> block, depending on how you apply the
          plugin).</p>

        <h2 id="maven-setup">Maven Setup</h2>
        <p>Attach the extension as a plugin dependency and reference it from
          <code>pluginExtensions</code>. As with Gradle, <code>jeffreyHome</code> must be reachable
          either here (baked as an image <code>ENV</code> default) or at runtime via a
          <code>JEFFREY_HOME</code> env var &mdash; otherwise the wrapper warns and starts the app
          without profiling.</p>

        <div class="code-block">
          <pre><code>&lt;plugin&gt;
  &lt;groupId&gt;com.google.cloud.tools&lt;/groupId&gt;
  &lt;artifactId&gt;jib-maven-plugin&lt;/artifactId&gt;
  &lt;dependencies&gt;
    &lt;dependency&gt;
      &lt;groupId&gt;cafe.jeffrey-analyst&lt;/groupId&gt;
      &lt;artifactId&gt;jeffrey-jib-maven&lt;/artifactId&gt;
      &lt;version&gt;${jeffrey-jib.version}&lt;/version&gt;
    &lt;/dependency&gt;
  &lt;/dependencies&gt;
  &lt;configuration&gt;
    &lt;pluginExtensions&gt;
      &lt;pluginExtension&gt;
        &lt;implementation&gt;cafe.jeffrey.jib.maven.JeffreyJibMavenExtension&lt;/implementation&gt;
        &lt;properties&gt;
          &lt;jeffreyHome&gt;/shared/disk/jeffrey&lt;/jeffreyHome&gt;
        &lt;/properties&gt;
      &lt;/pluginExtension&gt;
    &lt;/pluginExtensions&gt;
  &lt;/configuration&gt;
&lt;/plugin&gt;</code></pre>
        </div>

        <p>Add more properties the same way &mdash; each property name matches a setter on
          <code>JeffreyJibConfig</code>:</p>

        <div class="code-block">
          <pre><code>&lt;properties&gt;
  &lt;enabled&gt;true&lt;/enabled&gt;
  &lt;jeffreyHome&gt;/shared/disk/jeffrey&lt;/jeffreyHome&gt;
  &lt;overrideConfig&gt;/jeffrey/jeffrey-overrides.conf&lt;/overrideConfig&gt;
&lt;/properties&gt;</code></pre>
        </div>

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
            <div><strong>Requires Jeffrey Server elsewhere in the cluster</strong> with
              <code>copy-libs.enabled=true</code>, writing to the shared <code>jeffrey-home</code>
              volume your app pods mount. Without it, the wrapper cannot locate
              <code>jeffrey-cli-&lt;arch&gt;</code> at runtime.</div>
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
  align-items: flex-start;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  background: #f0fdf4;
  border-radius: 8px;
  border: 1px solid #bbf7d0;
}

.feature-item i {
  color: #10b981;
  font-size: 1rem;
  margin-top: 0.125rem;
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

/* Code Block */
.code-block {
  margin: 1rem 0;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
}

.code-block pre {
  margin: 0;
  padding: 1rem;
  background: #1e293b;
  overflow-x: auto;
}

.code-block code {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.85rem;
  color: #e2e8f0;
  line-height: 1.5;
}
</style>
